package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Mirror extends Activity implements
        TextureView.SurfaceTextureListener,
        SensorEventListener,
        DisplayManager.DisplayListener,
        MirrorState.Listener,
        MirrorSettings.Listener {
    private static final String TAG = "Mirror2Rear";
    private static final String POWER_INTERFACE = "android.os.IPowerManager";
    private static final int TRANSACTION_WAKE_REAR_SCREEN = 16777210;
    private static final int TRANSACTION_SLEEP_REAR_SCREEN = 16777211;
    private static final long AOD_DIM_DELAY_MILLIS = 30_000L;
    private static final long IDLE_FADE_STEP_MILLIS = 16L;
    private static final int IDLE_FADE_STEPS = 125;
    /**
     * How many moves the backlight makes on its way into AOD.
     *
     * <p>Every one of them is a root shell, which takes about a tenth of a
     * second to start, so the panel cannot follow the overlay's sixty frames a
     * second. Ten moves across the fade is one every fifth of a second: still
     * a fade rather than a snap, and slow enough that each shell finishes
     * before the next is asked for.
     */
    private static final int AOD_BACKLIGHT_FADE_STEPS = 10;
    /** How far a finger may stray and still be pressing the shutter. */
    private static final float SHUTTER_TAP_SLOP_PIXELS = 12f;
    private static final long SHUTTER_TAP_MILLIS = 500L;
    static final String EXTRA_SESSION_HAS_PROJECTION = "session_has_projection";
    static final String EXTRA_DASHBOARD_ONLY = "dashboard_only";

    private TextureView textureView;
    private View brightnessOverlay;
    private CalibrationGridView calibrationGrid;
    private CropMaskView cropMask;
    private View shutterFlash;
    private PanelShutter panelShutter;
    /** Where the finger went down, for telling a tap from a swipe. */
    private float touchDownX;
    private float touchDownY;
    private long touchDownAt;
    /** Whether the profile in use has a frame that leaves part of the panel black. */
    private boolean cropMaskWanted;
    private RearDashboardView dashboardView;
    /** The dashboard page on screen, which decides which way round it sits. */
    private int dashboardPage = 1;
    private FrameLayout mirrorLayout;
    private RearDashboardController dashboardController;
    private RearBrightnessController brightnessController;
    private AutoBrightnessSensor autoBrightness;
    /** What the room is worth, as a share of the profile's brightness. */
    private float ambientFactor = 1f;
    private DeviceHealthMonitor deviceHealthMonitor;
    private ForegroundAppMonitor foregroundAppMonitor;
    private MirrorControlOverlay mirrorControlOverlay;
    private CropFrameOverlay cropFrameOverlay;
    /**
     * The profile as it was before the frame came up, or null when no frame
     * is up. Moving the frame changes the live profile only; this is what
     * Cancel puts back.
     */
    @Nullable
    private MirrorProfile framingOriginal;
    private DisplayManager displayManager;
    private SensorManager sensorManager;
    private Sensor screenDownSensor;
    private PanelPostureSensor postureSensor;
    /** Set while the phone is in a pocket: every touch is the lining. */
    private boolean touchLocked;
    /** Set while the panel is the side lying on the table. */
    private boolean panelFacingDown;
    /**
     * Which way up the phone is being held, in quarter turns clockwise.
     *
     * <p>A landscape page has two ways round it could be drawn, and which one
     * reads the right way up depends on which edge the phone is leaning on.
     */
    private int deviceQuarterTurn = PanelPostureSensor.TURN_UNKNOWN;
    private Surface projectionSurface;
    private int projectionBufferWidth;
    private int projectionBufferHeight;
    private int originalSubscreenSwitch;
    private boolean listenersRegistered;
    private boolean automaticOutputVisible;
    private boolean outputVisibilityInitialized;
    private boolean appOutputAllowed;
    /**
     * Whether the assigned-apps rule currently lets the mirrored image through.
     *
     * <p>Separate from {@link #appOutputAllowed} because the rule is about the
     * image, not about the panel.
     */
    private boolean appProjectionAllowed;
    /** Profile-app mirroring is opt-in for each foreground app visit. */
    private boolean manualProjectionEnabled;
    private boolean healthOutputAllowed = true;
    private MirrorProfile activeProfile;
    private DashboardSettings dashboardSettings;
    private RearContentMode sessionContentMode;
    private boolean sessionHasProjection;
    private String appliedDashboardProfileId;
    /** The template the surroundings asked for, or null when none applies. */
    private String activeTriggerSlot;
    private boolean charging;
    private final Handler triggerHandler = new Handler(Looper.getMainLooper());
    private final Runnable triggerTick = new Runnable() {
        @Override
        public void run() {
            refreshTriggers();
            triggerHandler.postDelayed(this,
                    PanelTriggers.millisUntilNextEdge(Mirror.this, System.currentTimeMillis()));
        }
    };
    /**
     * A ringing call brings the panel back and puts itself on it.
     *
     * <p>It has to reach past the idle timer, which may have dimmed the panel
     * to its AOD level, and past the content mode, which may have given the
     * whole panel over to the mirrored image.
     */
    private final CallWidgetState.Listener callListener = snapshot -> runOnUiThread(() -> {
        if (snapshot.ringing) {
            resetIdleTimer();
        }
        applyContentVisibility();
    });
    private final BroadcastReceiver chargingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            charging = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
            refreshTriggers();
        }
    };
    /**
     * Which run of the session this activity belongs to.
     *
     * <p>Switching tiles stops one session and starts another in a single
     * click. This activity's onDestroy arrives after the new session has
     * already been armed, so without knowing whose run it is it would clear
     * the state the new one had just set and put the panel back to sleep.
     */
    private int sessionGeneration;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private boolean idleDimmed;
    private boolean idleFadeRunning;
    private int idleFadeStep;
    private float idleFadeStartOverlayAlpha;
    private final Runnable idleAction = this::beginIdleFade;
    private final Runnable idleFadeFrame = this::runIdleFadeFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MirrorState.isActive()) {
            finish();
            return;
        }

        MirrorState.addListener(this);
        sessionGeneration = MirrorState.generation();
        activeProfile = MirrorSettings.loadActiveProfile(this);
        // Whose panel is up, for the app-based profile switch to compare
        // against. The profile's snapshot is deliberately not applied here:
        // it belongs to the moment the profile changes, and the profile has
        // not changed. Restoring it on every session start threw away
        // everything arranged since - full-screen widgets switched on after
        // the snapshot came back off, and widgets moved to the pages the
        // snapshot remembered - the moment a session opened.
        appliedDashboardProfileId = activeProfile.id;
        MirrorSettings.addListener(this);
        dashboardSettings = MirrorSettings.loadDashboardSettings(this);
        sessionContentMode = dashboardSettings.contentMode;
        if (getIntent().getBooleanExtra(EXTRA_DASHBOARD_ONLY, false)) {
            sessionContentMode = RearContentMode.DASHBOARD;
        }
        sessionHasProjection = getIntent().getBooleanExtra(
                EXTRA_SESSION_HAS_PROJECTION,
                sessionContentMode.usesProjection()
        );
        originalSubscreenSwitch = Settings.System.getInt(
                getContentResolver(),
                "subscreen_switch",
                0
        );
        displayManager = getSystemService(DisplayManager.class);
        sensorManager = getSystemService(SensorManager.class);
        screenDownSensor = findScreenDownSensor(sensorManager);
        postureSensor = new PanelPostureSensor(this, (pocketed, facingDown, quarterTurn) ->
                runOnUiThread(() -> applyPosture(pocketed, facingDown, quarterTurn)));

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setShowWhenLocked(true);
        // Touchable whatever the mode: the idle timer is reset by a touch on
        // the panel, and a mirror-only session used to refuse touches, so the
        // fifteen and thirty second timers ended the session with no way to
        // keep it alive. Nothing in a mirror-only session reacts to a touch -
        // the image, the overlay and the grid are all unclickable and the
        // widgets are gone - so letting them through costs nothing else.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        setContentView(R.layout.mirror_surface);
        textureView = findViewById(R.id.mirror);
        mirrorLayout = findViewById(R.id.mirror_layout);
        brightnessOverlay = findViewById(R.id.brightness_overlay);
        calibrationGrid = findViewById(R.id.calibration_grid);
        cropMask = findViewById(R.id.crop_mask);
        shutterFlash = findViewById(R.id.shutter_flash);
        panelShutter = new PanelShutter();
        dashboardView = findViewById(R.id.rear_dashboard);
        textureView.setClickable(false);
        brightnessOverlay.setClickable(false);
        calibrationGrid.setClickable(false);
        dashboardView.setClickable(true);
        dashboardView.setDashboardSettings(dashboardSettings, sessionContentMode);
        mirrorControlOverlay = new MirrorControlOverlay(this, new MirrorControlOverlay.Listener() {
            @Override
            public void onToggleRequested() {
                runOnUiThread(() -> setManualProjectionEnabled(!manualProjectionEnabled));
            }

            @Override
            public void onFrameRequested() {
                runOnUiThread(Mirror.this::startFraming);
            }
        });
        cropFrameOverlay = new CropFrameOverlay(this, new CropFrameOverlay.Listener() {
            @Override
            public void onFramePreview(CropFrame.Rect frame) {
                previewFrame(frame);
            }

            @Override
            public void onFrameSettled() {
                // The capture resolution follows the zoom, and changing it
                // re-attaches the surface: worth doing once the finger is
                // off, not on every move.
                updateProjectionBufferFromSettings();
                resetIdleTimer();
            }

            @Override
            public void onFrameFinished(boolean keep, @Nullable CropFrame.Rect frame) {
                endFraming(keep, frame);
            }
        });
        // Each page can be turned a different way round, and the rotation is
        // applied out here because it needs the panel's own dimensions.
        dashboardView.setOnPageChangedListener(page -> {
            dashboardPage = page;
            applyDashboardOrientation();
        });
        mirrorLayout.post(this::applyDashboardOrientation);
        updateMirrorControl(AutoProfileState.get().profileId);
        applyAppVisibility(null);
        textureView.setVisibility(View.INVISIBLE);
        brightnessController = new RearBrightnessController((hardwareControlActive, appliedPercent) ->
                runOnUiThread(() -> onHardwareBrightnessApplied(hardwareControlActive, appliedPercent))
        );
        autoBrightness = new AutoBrightnessSensor(this, factor -> runOnUiThread(() -> {
            ambientFactor = factor;
            applyProfileBrightness();
        }));
        startAutoBrightnessIfWanted();
        configureProjectionSurfaceSize();
        if (usesProjection()) {
            textureView.setSurfaceTextureListener(this);
        }
        if (sessionContentMode.showsDashboard()) {
            dashboardController = new RearDashboardController(
                    this,
                    dashboardSettings,
                    snapshot -> runOnUiThread(() -> dashboardView.setSnapshot(snapshot))
            );
            dashboardController.start();
        }
        configureAutoProfileMonitoring();
        deviceHealthMonitor = new DeviceHealthMonitor(this, snapshot ->
                runOnUiThread(() -> applyDeviceHealth(snapshot))
        );
        deviceHealthMonitor.start();
        updateOutputVisibility();
        updateCalibrationGridVisibility();
        if (automaticOutputVisible) {
            applyProfileBrightness();
        }
        resetIdleTimer();
    }

    private void configureProjectionSurfaceSize() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int side = Math.max(metrics.widthPixels, metrics.heightPixels);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textureView.getLayoutParams();
        layoutParams.width = side;
        layoutParams.height = side;
        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        textureView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerRuntimeListeners();
    }

    @Override
    protected void onStop() {
        // The frame is only any good against a live panel.
        if (cropFrameOverlay != null) {
            cropFrameOverlay.cancel();
        }
        unregisterRuntimeListeners();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        idleHandler.removeCallbacks(idleAction);
        idleHandler.removeCallbacks(idleFadeFrame);
        MirrorState.removeListener(this);
        MirrorSettings.removeListener(this);
        unregisterRuntimeListeners();
        detachProjectionSurface();
        stopAutoProfileMonitoring();
        if (cropFrameOverlay != null) {
            cropFrameOverlay.cancel();
        }
        if (mirrorControlOverlay != null) {
            mirrorControlOverlay.close();
        }
        if (deviceHealthMonitor != null) {
            deviceHealthMonitor.stop();
        }
        if (dashboardController != null) {
            dashboardController.stop();
        }
        if (autoBrightness != null) {
            autoBrightness.stop();
        }
        if (panelShutter != null) {
            panelShutter.close();
        }
        AutoProfileState.clear();

        if (!isChangingConfigurations()) {
            if (brightnessController != null) {
                brightnessController.closeAndRestore();
            }
            boolean ownsSession = MirrorState.generation() == sessionGeneration;
            if (ownsSession) {
                // The service is told to stop only when this activity is the
                // one ending the session. Telling it again after whoever
                // switched the session off had already done so recreated the
                // service just to deliver the message.
                if (MirrorState.isActive()) {
                    MirrorState.setActive(this, false);
                    if (sessionHasProjection && ForegroundService.isRunning()) {
                        startService(ForegroundService.createStopIntent(this));
                    }
                }
                rearScreenSwitch(false);
                restoreXiaomiRearScreenUi();
            }
        } else if (brightnessController != null) {
            brightnessController.closeWithoutRestore();
        }
        super.onDestroy();
    }

    @Override
    public void onMirrorStateChanged(boolean active) {
        if (!active) {
            runOnUiThread(this::finishAndRemoveTask);
        }
    }

    @Override
    public void onMirrorSettingsChanged(MirrorProfile profile) {
        runOnUiThread(() -> {
            dashboardSettings = MirrorSettings.loadDashboardSettings(this)
                    .withContentMode(sessionContentMode);
            dashboardView.setDashboardSettings(dashboardSettings, sessionContentMode);
            applyDashboardOrientation();
            if (dashboardController != null) {
                dashboardController.updateSettings(dashboardSettings);
            }
            configureAutoProfileMonitoring();
            // The switch lives outside the profile, so a running session only
            // learns about it here.
            startAutoBrightnessIfWanted();
            AutoProfileState.Snapshot automatic = AutoProfileState.get();
            String automaticProfileId = automatic.packageName == null
                    ? null
                    : MirrorSettings.assignedProfileId(this, automatic.packageName);
            if (!sameValue(automatic.profileId, automaticProfileId)) {
                AutoProfileState.set(automaticProfileId, automatic.packageName);
            }
            if (MirrorSettings.isAutoProfileEnabled(this) && automaticProfileId != null) {
                activeProfile = MirrorSettings.loadProfile(this, automaticProfileId);
            } else {
                activeProfile = profile;
            }
            applyDashboardForProfile(activeProfile);
            if (dashboardController != null) {
                dashboardController.setActiveProfile(activeProfile);
            }
            applyAppVisibility(automaticProfileId);
            if (deviceHealthMonitor != null) {
                deviceHealthMonitor.refresh();
            }
            updateOutputVisibility();
            updateCalibrationGridVisibility();
            updateProjectionBufferFromSettings();
            if (automaticOutputVisible) {
                applyProfileBrightness();
                applyRotationTransform();
            }
            resetIdleTimer();
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Swallowed rather than passed on: in a pocket every one of these is
        // the lining, and letting them through would wake the panel, turn its
        // pages and restyle its widgets all the way to the shops.
        if (touchLocked) {
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            resetIdleTimer();
        }
        if (handleShutterTouch(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * A tap on the panel while it is showing a camera, as the shutter.
     *
     * <p>Only while the image is up, where the panel answers to nothing else:
     * the widgets are away, so there is no gesture to take away from them.
     * Only over a camera too - the key would open the camera app anywhere
     * else, which is the last thing a tap on a map should do.
     *
     * @return whether the touch was used up here
     */
    private boolean handleShutterTouch(MotionEvent event) {
        if (!shouldShowProjection() || !isCameraInFront()
                || !DashboardWidgetLayout.isShutterOnTapEnabled(this)) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                touchDownAt = event.getEventTime();
                return true;
            case MotionEvent.ACTION_UP:
                float moved = (float) Math.hypot(
                        event.getX() - touchDownX, event.getY() - touchDownY);
                boolean tap = moved <= SHUTTER_TAP_SLOP_PIXELS
                        && event.getEventTime() - touchDownAt <= SHUTTER_TAP_MILLIS;
                if (tap && panelShutter != null && panelShutter.fire()) {
                    flashShutter();
                }
                return true;
            default:
                return true;
        }
    }

    /**
     * Whether what is being mirrored is a camera.
     *
     * <p>The profile answers for the built-in one; the classifier answers for
     * a camera app somebody has given a profile of its own.
     */
    private boolean isCameraInFront() {
        if (activeProfile != null && MirrorProfile.CAMERA_ID.equals(activeProfile.id)) {
            return true;
        }
        String packageName = AutoProfileState.get().packageName;
        return packageName != null
                && MirrorProfile.CAMERA_ID.equals(AppProfileClassifier.classify(packageName));
    }

    /**
     * Finds the camera's shutter button while the shot is still being framed.
     *
     * <p>The search reads the app's layout and takes seconds; a tap on the
     * panel has to be answered at once. So it happens when the image goes up,
     * which is always well before the first tap, and once for each app and
     * each way round the screen.
     */
    private void prepareShutter() {
        if (panelShutter == null) {
            return;
        }
        if (!shouldShowProjection() || !isCameraInFront()
                || !DashboardWidgetLayout.isShutterOnTapEnabled(this)) {
            return;
        }
        Display mainDisplay = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        panelShutter.prepare(AutoProfileState.get().packageName,
                mainDisplay == null ? Surface.ROTATION_0 : mainDisplay.getRotation());
    }

    /** A blink of the panel, since the shot itself happens out of sight. */
    private void flashShutter() {
        if (shutterFlash == null) {
            return;
        }
        shutterFlash.animate().cancel();
        shutterFlash.setAlpha(1f);
        shutterFlash.setVisibility(View.VISIBLE);
        shutterFlash.animate().alpha(0f).setDuration(220L)
                .withEndAction(() -> shutterFlash.setVisibility(View.GONE));
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        }
    }

    /**
     * Acts on which way the panel is facing and what is in front of the phone.
     *
     * <p>Either half can be switched off, so the sensor's opinion is asked for
     * only where it is wanted.
     */
    private void applyPosture(boolean pocketed, boolean facingDown, int quarterTurn) {
        if (quarterTurn != deviceQuarterTurn) {
            deviceQuarterTurn = quarterTurn;
            applyDashboardOrientation();
        }
        boolean lock = pocketed && DashboardWidgetLayout.isPocketLockEnabled(this);
        if (lock && !touchLocked && dashboardView != null) {
            // The lock may have come down mid-gesture, and the release that
            // would have ended it is about to be swallowed.
            dashboardView.cancelTouchInProgress();
        }
        touchLocked = lock;
        boolean dark = facingDown && DashboardWidgetLayout.isFaceDownOffEnabled(this);
        if (dark == panelFacingDown) {
            return;
        }
        panelFacingDown = dark;
        if (dark) {
            // By the backlight, not by the display's power: this panel's
            // display carries the session's own activity, and sleeping it
            // stops the activity, which starts again and wakes the display.
            Log.i(TAG, "Panel is face down; putting it out");
            if (brightnessController != null) {
                brightnessController.blank();
            }
            return;
        }
        Log.i(TAG, "Panel is up again");
        applyProfileBrightness();
    }

    @Override
    public void onSurfaceTextureAvailable(
            @NonNull SurfaceTexture surfaceTexture,
            int width,
            int height
    ) {
        if (!usesProjection()) {
            return;
        }
        configureProjectionBuffer(surfaceTexture, width, height);
        if (shouldShowProjection()) {
            projectionSurface = new Surface(surfaceTexture);
            attachProjectionSurface(projectionBufferWidth, projectionBufferHeight);
            applyRotationTransform();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            @NonNull SurfaceTexture surfaceTexture,
            int width,
            int height
    ) {
        if (!usesProjection()) {
            return;
        }
        configureProjectionBuffer(surfaceTexture, width, height);
        if (shouldShowProjection()) {
            attachProjectionSurface(projectionBufferWidth, projectionBufferHeight);
            applyRotationTransform();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        detachProjectionSurface();
        projectionBufferWidth = 0;
        projectionBufferHeight = 0;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Xiaomi's own screen-down sensor, which means the panel is the side
        // facing up. It must not argue with the posture reading below it.
        if (automaticOutputVisible && !panelFacingDown) {
            rearScreenSwitch(true);
            applyProfileBrightness();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDisplayAdded(int displayId) {
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        Display currentDisplay = getDisplay();
        if (currentDisplay != null && currentDisplay.getDisplayId() == displayId) {
            MirrorState.setActive(this, false);
        }
    }

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            // A frame drawn for the screen one way round means nothing once
            // it has turned: the same numbers crop a different part of it.
            Display mainDisplay = displayManager == null ? null : displayManager.getDisplay(displayId);
            if (cropFrameOverlay != null && cropFrameOverlay.isShowing() && mainDisplay != null
                    && mainDisplay.getRotation() != cropFrameOverlay.rotation()) {
                cropFrameOverlay.cancel();
            }
            applyRotationTransform();
            prepareShutter();
        }
    }

    private void registerRuntimeListeners() {
        if (listenersRegistered) {
            return;
        }
        if (displayManager != null) {
            displayManager.registerDisplayListener(this, null);
        }
        if (sensorManager != null && screenDownSensor != null) {
            sensorManager.registerListener(this, screenDownSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        IntentFilter power = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        power.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ContextCompat.registerReceiver(this, chargingReceiver, power,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = sticky == null ? -1
                : sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        triggerHandler.post(triggerTick);
        if (postureSensor != null) {
            postureSensor.start();
        }
        CallWidgetState.addListener(callListener);
        listenersRegistered = true;
    }

    private void unregisterRuntimeListeners() {
        if (!listenersRegistered) {
            return;
        }
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(this);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        triggerHandler.removeCallbacks(triggerTick);
        if (postureSensor != null) {
            postureSensor.stop();
        }
        // What the sensor last said is kept rather than dropped. Clearing it
        // used to leave the backlight at nought with nothing left to say so,
        // and the sensor's next word - "not face down" - then matched the
        // cleared flag and did no work. The first reading after every start
        // is sent whether or not it has changed, so a stale answer here is
        // corrected within the second rather than believed.
        CallWidgetState.removeListener(callListener);
        try {
            unregisterReceiver(chargingReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already gone.
        }
        listenersRegistered = false;
    }

    private void attachProjectionSurface(int width, int height) {
        if (!usesProjection()
                || !MirrorState.isActive()
                || projectionSurface == null
                || !projectionSurface.isValid()) {
            return;
        }
        try {
            startService(ForegroundService.createAttachSurfaceIntent(
                    this,
                    projectionSurface,
                    width,
                    height,
                    getResources().getDisplayMetrics().densityDpi
            ));
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to send the projection surface to the service", error);
            MirrorState.setActive(this, false);
        }
    }

    private void detachProjectionSurface() {
        if (projectionSurface == null) {
            return;
        }
        if (MirrorState.isActive()) {
            try {
                startService(ForegroundService.createDetachSurfaceIntent(this));
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to detach the projection surface", error);
            }
        }
        projectionSurface.release();
        projectionSurface = null;
    }

    private void applyRotationTransform() {
        if (textureView == null || textureView.getWidth() == 0 || textureView.getHeight() == 0) {
            return;
        }
        Display mainDisplay = displayManager == null
                ? null
                : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        int rotation = mainDisplay == null ? Surface.ROTATION_0 : mainDisplay.getRotation();
        MirrorProfile profile = activeProfile == null
                ? MirrorSettings.loadActiveProfile(this)
                : activeProfile;
        DisplayMetrics rearMetrics = getResources().getDisplayMetrics();
        Display.Mode mode = mainDisplay == null ? null : mainDisplay.getMode();
        float pivotX = textureView.getWidth() / 2f;
        float pivotY = textureView.getHeight() / 2f;
        // A frame answers every question this method asks - which way round,
        // how big, where - so when the profile has one nothing below runs.
        CropFrame.Projection framed = framedProjection(profile, rotation, mode, rearMetrics);
        if (framed != null) {
            Matrix framedMatrix = new Matrix();
            framedMatrix.setRotate(framed.degrees, pivotX, pivotY);
            framedMatrix.postScale(framed.scaleX, framed.scaleY, pivotX, pivotY);
            framedMatrix.postTranslate(framed.translateX, framed.translateY);
            textureView.setScaleX(1f);
            textureView.setScaleY(1f);
            textureView.setTranslationX(0f);
            textureView.setTranslationY(0f);
            textureView.setTransform(framedMatrix);
            showCropMask(framed);
            return;
        }
        showCropMask(null);
        RotationTransform transform = RotationTransform.forRotation(
                rotation,
                profile.rotationDegrees,
                profile.mirrorHorizontally
        );

        ProjectionGeometry.Scale scale = ProjectionGeometry.calculateScale(
                profile.scaleMode,
                rearMetrics.widthPixels,
                rearMetrics.heightPixels,
                textureView.getWidth(),
                mode == null ? rearMetrics.widthPixels : mode.getPhysicalWidth(),
                mode == null ? rearMetrics.heightPixels : mode.getPhysicalHeight(),
                profile.rotationDegrees
        );

        float calibrationZoom = profile.zoomPercent / 100f;
        float combinedScaleX = transform.scaleX * scale.x * calibrationZoom;
        float combinedScaleY = transform.scaleY * scale.y * calibrationZoom;

        Matrix matrix = new Matrix();
        matrix.setRotate(
                transform.degrees,
                pivotX,
                pivotY
        );
        matrix.postScale(combinedScaleX, combinedScaleY, pivotX, pivotY);
        // The offsets slide the image inside the texture rather than the
        // texture across the panel. The texture is exactly as tall as the
        // panel and clips what it draws to its own edges, so moving the view
        // down left a black band above it instead of showing the zoomed-in
        // screen that was there all along - a vertical offset could hide the
        // top of the screen but never show any more of it.
        matrix.postTranslate(
                Math.round(ProjectionGeometry.calculateTranslation(
                        rearMetrics.widthPixels, profile.horizontalOffsetPercent)),
                Math.round(ProjectionGeometry.calculateTranslation(
                        rearMetrics.heightPixels, profile.verticalOffsetPercent)));
        textureView.setScaleX(1f);
        textureView.setScaleY(1f);
        textureView.setTranslationX(0f);
        textureView.setTranslationY(0f);
        textureView.setTransform(matrix);
    }

    /**
     * How the profile's frame lands on the panel, or null when it has none.
     *
     * <p>Each way round the screen can be held has a frame of its own, since
     * an app laid out sideways is a different picture rather than the same one
     * turned. Until the way in hand has been framed, the older zoom and
     * offsets take over.
     */
    @Nullable
    private CropFrame.Projection framedProjection(
            MirrorProfile profile, int rotation, @Nullable Display.Mode mode,
            DisplayMetrics rearMetrics) {
        // Held against the way the screen is turned now, not the way it was
        // framed: an upright frame and a sideways one are kept apart, and the
        // one that matches is the one that applies.
        MirrorProfile.Crop crop = profile.cropFor(rotation);
        if (crop == null) {
            return null;
        }
        return cropFrameFor(profile, rotation, mode, rearMetrics)
                .projectionFor(new CropFrame.Rect(crop.left, crop.top, crop.right, crop.bottom));
    }

    private CropFrame cropFrameFor(
            MirrorProfile profile, int rotation, @Nullable Display.Mode mode,
            DisplayMetrics rearMetrics) {
        int naturalWidth = mode == null ? rearMetrics.widthPixels : mode.getPhysicalWidth();
        int naturalHeight = mode == null ? rearMetrics.heightPixels : mode.getPhysicalHeight();
        // The mode reports the screen the way it was built, never the way it
        // is being held; a frame is in the held screen's own fractions.
        boolean turned = rotation % 2 != 0;
        return new CropFrame(
                rearMetrics.widthPixels,
                rearMetrics.heightPixels,
                turned ? naturalHeight : naturalWidth,
                turned ? naturalWidth : naturalHeight,
                rotation,
                profile
        );
    }

    private void showCropMask(@Nullable CropFrame.Projection projection) {
        cropMaskWanted = projection != null;
        if (cropMask == null) {
            return;
        }
        if (projection == null) {
            cropMask.setVisibility(View.GONE);
            return;
        }
        cropMask.setVisibleSize(projection.visibleWidth, projection.visibleHeight);
        cropMask.setVisibility(shouldShowProjection() && !CallWidgetState.get().ringing
                ? View.VISIBLE : View.GONE);
    }

    /**
     * How far the image is blown up, which is what the capture has to be
     * sharp enough for.
     *
     * <p>A frame says it by its own size; without one it is the zoom slider.
     */
    private int magnificationPercent(MirrorProfile profile) {
        Display mainDisplay = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        int rotation = mainDisplay == null ? Surface.ROTATION_0 : mainDisplay.getRotation();
        CropFrame.Projection framed = framedProjection(profile, rotation,
                mainDisplay == null ? null : mainDisplay.getMode(),
                getResources().getDisplayMetrics());
        return framed == null ? profile.zoomPercent : framed.magnificationPercent;
    }

    private void configureProjectionBuffer(
            SurfaceTexture surfaceTexture,
            int reportedWidth,
            int reportedHeight
    ) {
        if (!usesProjection()) {
            return;
        }
        int viewWidth = textureView.getWidth() > 0 ? textureView.getWidth() : reportedWidth;
        int viewHeight = textureView.getHeight() > 0 ? textureView.getHeight() : reportedHeight;
        ProjectionQuality quality = MirrorSettings.loadProjectionQuality(this);
        MirrorProfile profile = activeProfile == null
                ? MirrorSettings.loadActiveProfile(this)
                : activeProfile;
        int magnification = magnificationPercent(profile);
        int desiredWidth = quality.bufferDimension(viewWidth, magnification);
        int desiredHeight = quality.bufferDimension(viewHeight, magnification);
        if (projectionBufferWidth == desiredWidth && projectionBufferHeight == desiredHeight) {
            return;
        }
        surfaceTexture.setDefaultBufferSize(desiredWidth, desiredHeight);
        projectionBufferWidth = desiredWidth;
        projectionBufferHeight = desiredHeight;
        Log.i(TAG, "Projection buffer: " + desiredWidth + "x" + desiredHeight
                + " (" + quality + ", magnified=" + magnification + "%, resolution="
                + quality.bufferPercent(magnification) + "%)");
    }

    private void updateProjectionBufferFromSettings() {
        if (!usesProjection()) {
            return;
        }
        SurfaceTexture surfaceTexture = textureView == null ? null : textureView.getSurfaceTexture();
        if (surfaceTexture == null || projectionSurface == null || !projectionSurface.isValid()) {
            return;
        }
        int oldWidth = projectionBufferWidth;
        int oldHeight = projectionBufferHeight;
        configureProjectionBuffer(surfaceTexture, textureView.getWidth(), textureView.getHeight());
        if (oldWidth != projectionBufferWidth || oldHeight != projectionBufferHeight) {
            attachProjectionSurface(projectionBufferWidth, projectionBufferHeight);
        }
    }

    private void applyProfileBrightness() {
        if (activeProfile == null || brightnessOverlay == null) {
            return;
        }
        // Out on purpose. Everything that moves the brightness comes through
        // here, so this is the one place that has to know.
        if (panelFacingDown) {
            return;
        }
        if (idleFadeRunning) {
            return;
        }
        if (idleDimmed) {
            if (dimsAodWithBacklight(DashboardWidgetLayout.loadIdleMode(this))) {
                brightnessOverlay.setAlpha(aodVeilAlpha());
                applyAodBacklight();
            } else {
                brightnessOverlay.setAlpha(aodDimOverlayAlpha());
            }
            return;
        }
        applyBrightnessPercent(ambientAdjusted(activeProfile.brightnessPercent));
    }

    /**
     * The brightness the room asks for, never more than the profile allows.
     *
     * <p>The slider stays the ceiling. Automatic light only takes the panel
     * down, which is the thing that needed fixing: one set for daylight is
     * painful in a dark room, and nobody wants the reverse surprise of a
     * panel that brightens itself past what they chose.
     */
    private int ambientAdjusted(int percent) {
        if (autoBrightness == null || !DashboardWidgetLayout.isAutoBrightnessEnabled(this)) {
            return percent;
        }
        // The sensor's factor is a share of the light the panel puts out, not
        // of the slider's travel, so it is applied on that side of the curve.
        // Scaling the position instead would make the same factor mean a far
        // darker panel than it did when it was tuned against the room.
        return Math.max(1, PerceptualBrightness.toPercent(
                PerceptualBrightness.toLinear(percent) * ambientFactor));
    }

    private void startAutoBrightnessIfWanted() {
        if (autoBrightness == null) {
            return;
        }
        if (DashboardWidgetLayout.isAutoBrightnessEnabled(this) && autoBrightness.isAvailable()) {
            autoBrightness.start();
        } else {
            autoBrightness.stop();
            ambientFactor = 1f;
        }
    }

    private void applyBrightnessPercent(int percent) {
        int clamped = Math.max(1, Math.min(100, percent));
        // Software dimming is the fallback for a panel without root, and it
        // used to be laid on before every root write in case that write
        // failed. The write takes a shell to start, so the panel went dark for
        // as long as that took and then snapped back - a flash nobody noticed
        // while brightness only moved when a slider did, and a flicker once
        // the light sensor started moving it. Where the hardware has already
        // answered for itself, it is left to answer again.
        if (!DeviceCapabilityState.get().hardwareBrightnessActive) {
            applyBrightnessOverlay(false, clamped);
        }
        setPanelBacklight(clamped);
    }

    private void applyBrightnessOverlay(boolean hardwareControlActive, int percent) {
        if (brightnessOverlay == null) {
            return;
        }
        brightnessOverlay.setAlpha(
                hardwareControlActive ? 0f : 1f - percent / 100f
        );
    }

    private void onHardwareBrightnessApplied(boolean hardwareControlActive, int percent) {
        // An asynchronous root response must not remove the software fade or
        // wake an already dimmed AOD frame.
        if (idleFadeRunning || idleDimmed) {
            return;
        }
        applyBrightnessOverlay(hardwareControlActive, percent);
    }

    private void resetIdleTimer() {
        idleHandler.removeCallbacks(idleAction);
        idleHandler.removeCallbacks(idleFadeFrame);
        boolean wasFading = idleFadeRunning;
        idleFadeRunning = false;
        boolean wasDimmed = idleDimmed;
        idleDimmed = false;
        if (wasDimmed || wasFading) {
            applyProfileBrightness();
        }
        idleHandler.postDelayed(idleAction, idleDelayMillis());
    }

    private long idleDelayMillis() {
        DashboardWidgetLayout.IdleMode mode = DashboardWidgetLayout.loadIdleMode(this);
        return mode == DashboardWidgetLayout.IdleMode.TIMEOUT_15
                ? 15_000L : mode == DashboardWidgetLayout.IdleMode.TIMEOUT_30
                ? 30_000L : AOD_DIM_DELAY_MILLIS;
    }

    private void beginIdleFade() {
        if (!MirrorState.isActive() || activeProfile == null) {
            return;
        }
        // A live image is its own reason to stay lit. The timers here are for
        // a dashboard that nobody is looking at; a mirrored viewfinder is
        // watched rather than touched, and went dark in the middle of a shot.
        // Asked again rather than switched off at the source: the image can
        // stop for a dozen reasons - the app in front, the button, the heat
        // limit - and each would otherwise have to remember to wind the timer
        // back up.
        if (shouldShowProjection()) {
            idleHandler.postDelayed(idleAction, idleDelayMillis());
            return;
        }
        idleFadeRunning = true;
        idleFadeStep = 0;
        idleFadeStartOverlayAlpha = brightnessOverlay.getAlpha();
        idleHandler.post(idleFadeFrame);
    }

    private void runIdleFadeFrame() {
        if (!idleFadeRunning || !MirrorState.isActive()) {
            return;
        }
        idleFadeStep++;
        float progress = idleFadeStep / (float) IDLE_FADE_STEPS;
        DashboardWidgetLayout.IdleMode mode = DashboardWidgetLayout.loadIdleMode(this);
        boolean backlit = dimsAodWithBacklight(mode);
        float targetAlpha;
        if (mode != DashboardWidgetLayout.IdleMode.ALWAYS_ON) {
            targetAlpha = 1f;
        } else {
            targetAlpha = backlit ? aodVeilAlpha() : aodDimOverlayAlpha();
        }
        brightnessOverlay.setAlpha(idleFadeStartOverlayAlpha
                + (targetAlpha - idleFadeStartOverlayAlpha) * progress);
        if (backlit) {
            stepAodBacklight(progress);
        }
        if (idleFadeStep < IDLE_FADE_STEPS) {
            idleHandler.postDelayed(idleFadeFrame, IDLE_FADE_STEP_MILLIS);
            return;
        }
        idleFadeRunning = false;
        idleDimmed = true;
        if (mode != DashboardWidgetLayout.IdleMode.ALWAYS_ON) {
            MirrorState.setActive(this, false);
        }
    }

    private float aodDimOverlayAlpha() {
        int percent = DashboardWidgetLayout.loadAodMinBrightnessPercent(this);
        return 1f - percent / 100f;
    }

    /** Whether AOD can dim the panel itself instead of veiling what it shows. */
    private boolean dimsAodWithBacklight(DashboardWidgetLayout.IdleMode mode) {
        return mode == DashboardWidgetLayout.IdleMode.ALWAYS_ON
                && brightnessController != null
                && DeviceCapabilityState.get().hardwareBrightnessActive;
    }

    private void applyAodBacklight() {
        setPanelBacklight(DashboardWidgetLayout.loadAodMinBrightnessPercent(this));
    }

    /**
     * The one door to the panel's backlight.
     *
     * <p>Three callers reached past it before - the profile, the AOD level
     * and the fade between them - and each would have needed its own copy of
     * the guard below. The fade did not get one, so a panel put out for lying
     * face down was lit again thirty seconds later by a dimming it could not
     * be seen through.
     */
    private void setPanelBacklight(int percent) {
        if (panelFacingDown || brightnessController == null) {
            return;
        }
        brightnessController.applyPercent(percent);
    }

    /** Walks the panel down to the AOD level on a beat a root shell can keep. */
    private void stepAodBacklight(float progress) {
        int framesPerMove = IDLE_FADE_STEPS / AOD_BACKLIGHT_FADE_STEPS;
        if (idleFadeStep % framesPerMove != 0 && idleFadeStep != IDLE_FADE_STEPS) {
            return;
        }
        if (activeProfile == null) {
            return;
        }
        int from = ambientAdjusted(activeProfile.brightnessPercent);
        int to = DashboardWidgetLayout.loadAodMinBrightnessPercent(this);
        setPanelBacklight(Math.round(from + (to - from) * progress));
    }

    /**
     * What the overlay still has to hide once the panel is as dim as it goes.
     *
     * <p>AOD asks for far less light than the panel will give on its own, so
     * the panel goes down to its floor and the rest comes out of the picture.
     * The panel still does most of the work - it drops from three quarters of
     * full output to a tenth - and what the overlay is left holding is a
     * fraction of what it used to.
     */
    private float aodVeilAlpha() {
        int percent = DashboardWidgetLayout.loadAodMinBrightnessPercent(this);
        return PerceptualBrightness.veilAlpha(
                PerceptualBrightness.toRequestedShare(percent),
                PerceptualBrightness.toLinear(percent)
        );
    }

    private void configureAutoProfileMonitoring() {
        boolean shouldMonitor = (MirrorSettings.isAutoProfileEnabled(this)
                || MirrorSettings.isAutoVisibilityEnabled(this))
                && ForegroundAppMonitor.hasUsageAccess(this);
        if (!shouldMonitor) {
            stopAutoProfileMonitoring();
            AutoProfileState.clear();
            return;
        }
        if (foregroundAppMonitor != null) {
            return;
        }
        ForegroundAppMonitor monitor = new ForegroundAppMonitor(this, (profileId, packageName) ->
                runOnUiThread(() -> applyAutomaticProfile(profileId, packageName))
        );
        if (monitor.start()) {
            foregroundAppMonitor = monitor;
        } else {
            monitor.stop();
        }
    }

    private void stopAutoProfileMonitoring() {
        if (foregroundAppMonitor != null) {
            foregroundAppMonitor.stop();
            foregroundAppMonitor = null;
        }
    }

    private void applyAutomaticProfile(String profileId, String packageName) {
        if (!MirrorSettings.isAutoProfileEnabled(this)
                && !MirrorSettings.isAutoVisibilityEnabled(this)) {
            return;
        }
        Log.i(TAG, "Automatic profile: package=" + packageName
                + ", profile=" + (profileId == null ? "manual fallback" : profileId));
        // A frame belongs to the app it was laid over, and so does the
        // place its shutter button sits.
        if (cropFrameOverlay != null) {
            cropFrameOverlay.cancel();
        }
        if (panelShutter != null) {
            panelShutter.forget();
        }
        AutoProfileState.set(profileId, packageName);
        // Entering another app is a new explicit decision. Never carry an
        // active capture into it, even when both apps use the same profile.
        manualProjectionEnabled = false;
        if (MirrorSettings.isAutoProfileEnabled(this)) {
            activeProfile = profileId == null
                    ? MirrorSettings.loadActiveProfile(this)
                    : MirrorSettings.loadProfile(this, profileId);
            applyDashboardForProfile(activeProfile);
            if (dashboardController != null) {
                dashboardController.setActiveProfile(activeProfile);
            }
        }
        applyAppVisibility(profileId);
        updateMirrorControl(profileId);
        updateOutputVisibility();
        if (automaticOutputVisible) {
            updateProjectionBufferFromSettings();
            applyProfileBrightness();
            applyRotationTransform();
        }
        resetIdleTimer();
    }

    /**
     * Applies the "only for assigned apps" rule.
     *
     * <p>The rule is about the mirrored image: show it only while one of the
     * assigned apps is in front, and pause it on the way out. It used to switch
     * the whole rear panel off instead, so in the dashboard-only mode - where
     * there is no image to gate - the panel went black and stayed black with
     * nothing anywhere saying why. The widgets now stay put; only the image
     * waits for an assigned app.
     *
     * @param automaticProfileId the profile the app in front is assigned to,
     *     or null when the app in front has none
     */
    private void applyAppVisibility(@Nullable String automaticProfileId) {
        appProjectionAllowed = !MirrorSettings.isAutoVisibilityEnabled(this)
                || automaticProfileId != null;
        appOutputAllowed = appProjectionAllowed || sessionContentMode.showsDashboard();
    }

    /**
     * Re-reads the triggers and moves the panel if they now say something else.
     *
     * <p>Charging and the clock name a saved template between them; when
     * neither does, the panel goes back to the profile's own.
     */
    private void refreshTriggers() {
        String wanted = PanelTriggers.activeSlot(this, charging, System.currentTimeMillis());
        if (sameValue(wanted, activeTriggerSlot)) {
            return;
        }
        activeTriggerSlot = wanted;
        if (wanted != null) {
            applyDashboardSlot(wanted);
        } else if (activeProfile != null) {
            applyDashboardSlot(activeProfile.id);
        }
    }

    private void applyDashboardForProfile(MirrorProfile profile) {
        if (profile == null) return;
        // A trigger outranks the profile while it holds: the profile's panel
        // is what the panel returns to, not what interrupts.
        if (activeTriggerSlot != null) {
            appliedDashboardProfileId = profile.id;
            return;
        }
        applyDashboardSlot(profile.id);
    }

    private void applyDashboardSlot(String slot) {
        if (slot == null || slot.equals(appliedDashboardProfileId)) return;
        // The same keeping as on a hand-made switch: the app-based one changes
        // profile just as thoroughly, and the panel it leaves behind is the
        // one the outgoing profile should come back to.
        if (appliedDashboardProfileId != null) {
            DashboardTemplateStore.save(this, appliedDashboardProfileId);
        }
        appliedDashboardProfileId = slot;
        if (!DashboardTemplateStore.apply(this, slot)) return;
        dashboardSettings = MirrorSettings.loadDashboardSettings(this)
                .withContentMode(sessionContentMode);
        if (dashboardView != null) {
            dashboardView.setDashboardSettings(dashboardSettings, sessionContentMode);
            applyDashboardOrientation();
        }
        if (dashboardController != null) dashboardController.updateSettings(dashboardSettings);
    }

    private void setAutomaticOutputVisible(boolean visible) {
        if (outputVisibilityInitialized && automaticOutputVisible == visible) {
            applyContentVisibility();
            ensureProjectionSurface();
            updateCalibrationGridVisibility();
            return;
        }
        outputVisibilityInitialized = true;
        automaticOutputVisible = visible;
        if (textureView == null) {
            return;
        }
        applyContentVisibility();
        if (!visible) {
            detachProjectionSurface();
            if (brightnessOverlay != null) {
                brightnessOverlay.setAlpha(1f);
            }
            if (brightnessController != null) {
                brightnessController.restoreWhileOpen();
            }
            rearScreenSwitch(false);
            Log.i(TAG, "Rear output paused");
            updateCalibrationGridVisibility();
            return;
        }

        rearScreenSwitch(true);
        ensureProjectionSurface();
        updateCalibrationGridVisibility();
        Log.i(TAG, "Rear output resumed");
    }

    private void applyContentVisibility() {
        boolean showProjection = shouldShowProjection();
        // A ringing call stands in front of whatever the panel was showing,
        // the mirrored image included: it is the one thing on here worth
        // interrupting for.
        boolean ringing = CallWidgetState.get().ringing;
        textureView.setVisibility(showProjection && !ringing ? View.VISIBLE : View.INVISIBLE);
        if (cropMask != null) {
            // The mask belongs to the image. Left up on its own it would lay
            // black bars over the widgets or over a ringing call.
            cropMask.setVisibility(cropMaskWanted && showProjection && !ringing
                    ? View.VISIBLE : View.GONE);
        }
        prepareShutter();
        if (dashboardView != null) {
            // The widgets step aside for the image rather than sitting on
            // top of it. In the mixed mode the image is usually a camera
            // viewfinder, and something to frame a shot in is worth more than
            // widgets over the middle of it. The mode still differs from plain
            // mirroring: the widgets are what the panel shows whenever the
            // image is not up.
            dashboardView.setVisibility(
                    ringing || (automaticOutputVisible && sessionContentMode.showsDashboard()
                            && !showProjection)
                            ? View.VISIBLE
                            : View.GONE
            );
        }
        if (!showProjection) {
            detachProjectionSurface();
        }
    }

    private boolean shouldShowProjection() {
        return automaticOutputVisible && usesProjection() && appProjectionAllowed
                && (manualProjectionEnabled || !requiresManualProjection());
    }

    /**
     * Whether the image is waiting for the floating button to be pressed.
     *
     * <p>Only while an assigned app is in front. That is deliberate rather
     * than incidental: the panel was built for a camera to preview itself on,
     * the stock camera app puts the same choice behind the same kind of
     * button, and matching it is the point. Everywhere else mirroring starts
     * on its own, as it always has.
     *
     * <p>Demanding the button everywhere left a session started from the tile
     * with a blank panel and no control anywhere to turn the image on, and did
     * the same whenever permission to draw over other apps was missing, since
     * the button cannot appear at all without it.
     */
    private boolean requiresManualProjection() {
        return AutoProfileState.get().profileId != null
                && mirrorControlOverlay != null && mirrorControlOverlay.canShow();
    }

    private void ensureProjectionSurface() {
        if (!shouldShowProjection() || textureView == null) {
            detachProjectionSurface();
            return;
        }
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (surfaceTexture != null && projectionSurface == null) {
            configureProjectionBuffer(surfaceTexture, textureView.getWidth(), textureView.getHeight());
            projectionSurface = new Surface(surfaceTexture);
            attachProjectionSurface(projectionBufferWidth, projectionBufferHeight);
        }
    }

    private void setManualProjectionEnabled(boolean enabled) {
        manualProjectionEnabled = enabled && appProjectionAllowed && usesProjection();
        applyContentVisibility();
        ensureProjectionSurface();
        updateCalibrationGridVisibility();
        updateMirrorControl(AutoProfileState.get().profileId);
        if (manualProjectionEnabled) {
            updateProjectionBufferFromSettings();
            applyProfileBrightness();
            applyRotationTransform();
        }
        Log.i(TAG, "Profile-app projection " + (manualProjectionEnabled ? "enabled" : "disabled"));
        resetIdleTimer();
    }

    private void updateMirrorControl(@Nullable String profileId) {
        if (mirrorControlOverlay != null) {
            // Out of the way while framing: it would sit inside the frame.
            boolean framing = cropFrameOverlay != null && cropFrameOverlay.isShowing();
            mirrorControlOverlay.show(profileId != null && usesProjection() && !framing,
                    manualProjectionEnabled);
        }
    }

    /**
     * Lays a frame over the app in front for choosing what the panel shows.
     *
     * <p>Mirroring is switched on for it if it was not: the frame is only
     * worth anything with the panel following it.
     */
    private void startFraming() {
        if (cropFrameOverlay == null || cropFrameOverlay.isShowing() || !usesProjection()
                || AutoProfileState.get().profileId == null || !appProjectionAllowed) {
            return;
        }
        if (!manualProjectionEnabled) {
            setManualProjectionEnabled(true);
        }
        MirrorProfile profile = activeProfile == null
                ? MirrorSettings.loadActiveProfile(this)
                : activeProfile;
        // Set before the frame is shown: showing it reports where it stands.
        framingOriginal = profile;
        DisplayMetrics panel = getResources().getDisplayMetrics();
        if (!cropFrameOverlay.show(profile, panel.widthPixels, panel.heightPixels)) {
            framingOriginal = null;
            return;
        }
        Log.i(TAG, "Framing " + profile.id);
        updateMirrorControl(AutoProfileState.get().profileId);
        resetIdleTimer();
    }

    private void previewFrame(CropFrame.Rect frame) {
        if (framingOriginal == null || cropFrameOverlay == null) {
            return;
        }
        activeProfile = framingOriginal.withCrop(new MirrorProfile.Crop(
                frame.left, frame.top, frame.right, frame.bottom, cropFrameOverlay.rotation()));
        applyRotationTransform();
        resetIdleTimer();
    }

    private void endFraming(boolean keep, @Nullable CropFrame.Rect frame) {
        MirrorProfile original = framingOriginal;
        framingOriginal = null;
        if (original == null) {
            return;
        }
        if (keep && frame != null && cropFrameOverlay != null) {
            MirrorProfile framed = original.withCrop(new MirrorProfile.Crop(
                    frame.left, frame.top, frame.right, frame.bottom, cropFrameOverlay.rotation()));
            activeProfile = framed;
            Log.i(TAG, "Framed " + framed.id + ": " + frame.left + ", " + frame.top
                    + " to " + frame.right + ", " + frame.bottom);
            // Saved like the sliders save, and picked up the same way.
            MirrorSettings.saveCalibration(this, framed);
        } else {
            activeProfile = original;
            updateProjectionBufferFromSettings();
            applyRotationTransform();
        }
        updateMirrorControl(AutoProfileState.get().profileId);
    }

    private void applyDashboardOrientation() {
        if (dashboardView == null || mirrorLayout == null
                || mirrorLayout.getWidth() == 0 || mirrorLayout.getHeight() == 0) return;
        DashboardWidgetLayout.Orientation orientation =
                DashboardWidgetLayout.loadPageOrientation(this, dashboardPage);
        int parentWidth = mirrorLayout.getWidth();
        int parentHeight = mirrorLayout.getHeight();
        boolean rotate = (orientation == DashboardWidgetLayout.Orientation.LANDSCAPE
                && parentHeight > parentWidth)
                || (orientation == DashboardWidgetLayout.Orientation.PORTRAIT
                && parentWidth > parentHeight);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dashboardView.getLayoutParams();
        params.width = rotate ? parentHeight : FrameLayout.LayoutParams.MATCH_PARENT;
        params.height = rotate ? parentWidth : FrameLayout.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        dashboardView.setLayoutParams(params);
        dashboardView.setPivotX(params.width > 0 ? params.width / 2f : dashboardView.getWidth() / 2f);
        dashboardView.setPivotY(params.height > 0 ? params.height / 2f : dashboardView.getHeight() / 2f);
        // Which way round a rotated page is drawn follows the phone. Leaning
        // on its right edge turns the page the other way, so it still reads
        // the right way up; lying flat the phone leans no way at all, and the
        // last answer stands rather than the page spinning under the reader.
        dashboardView.setRotation(rotate
                ? (deviceQuarterTurn == 3 ? 270f : 90f)
                : 0f);
    }

    private boolean usesProjection() {
        return sessionHasProjection && sessionContentMode.usesProjection();
    }

    private void updateOutputVisibility() {
        setAutomaticOutputVisible(appOutputAllowed && healthOutputAllowed);
    }

    private void applyDeviceHealth(DeviceHealthState.Snapshot snapshot) {
        boolean wasAllowed = healthOutputAllowed;
        healthOutputAllowed = snapshot.outputAllowed();
        updateOutputVisibility();
        if (healthOutputAllowed && (!wasAllowed || automaticOutputVisible)) {
            updateProjectionBufferFromSettings();
            applyProfileBrightness();
            applyRotationTransform();
        } else if (!healthOutputAllowed) {
            Log.w(TAG, "Device protection paused rear output: " + snapshot.pauseReason);
        }
    }

    private void updateCalibrationGridVisibility() {
        if (calibrationGrid != null) {
            calibrationGrid.setVisibility(
                    automaticOutputVisible && MirrorSettings.isCalibrationGridEnabled(this)
                            && shouldShowProjection()
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    private static boolean sameValue(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static Sensor findScreenDownSensor(SensorManager manager) {
        if (manager == null) {
            return null;
        }
        for (Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL)) {
            if (sensor.getName().matches("screen_down.*") && !sensor.isWakeUpSensor()) {
                return sensor;
            }
        }
        Log.w(TAG, "Xiaomi screen_down sensor is unavailable");
        return null;
    }

    private void restoreXiaomiRearScreenUi() {
        if (originalSubscreenSwitch != 1) {
            return;
        }
        Intent intent = new Intent()
                .setComponent(ComponentName.createRelative(
                        "com.xiaomi.misubscreenui",
                        ".SubScreenMainActivity"
                ))
                .addFlags(
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                                | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                );
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Log.w(TAG, "Unable to restore Xiaomi rear screen UI", error);
        }
    }

    static boolean rearScreenSwitch(boolean enabled) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POWER_INTERFACE);
            int transactionCode;
            if (enabled) {
                transactionCode = TRANSACTION_WAKE_REAR_SCREEN;
                data.writeStrongBinder(new Binder());
                data.writeLong(SystemClock.uptimeMillis());
                data.writeInt(1);
                data.writeString("CAMERA_CALL");
            } else {
                transactionCode = TRANSACTION_SLEEP_REAR_SCREEN;
                data.writeLong(SystemClock.uptimeMillis());
                data.writeString("CAMERA_CALL");
            }

            Method getService = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class);
            IBinder powerService = (IBinder) getService.invoke(null, "power");
            if (powerService == null) {
                Log.e(TAG, "Power service binder is unavailable");
                return false;
            }
            boolean handled = powerService.transact(transactionCode, data, reply, IBinder.FLAG_ONEWAY);
            if (!handled) {
                Log.e(TAG, "Rear screen transaction was rejected: " + transactionCode);
            }
            return handled;
        } catch (RemoteException | ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException && error.getCause() != null
                    ? error.getCause()
                    : error;
            Log.e(TAG, "Unable to change rear screen power state", cause);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
