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
    static final String EXTRA_SESSION_HAS_PROJECTION = "session_has_projection";
    static final String EXTRA_DASHBOARD_ONLY = "dashboard_only";

    private TextureView textureView;
    private View brightnessOverlay;
    private CalibrationGridView calibrationGrid;
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
    private DisplayManager displayManager;
    private SensorManager sensorManager;
    private Sensor screenDownSensor;
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
        dashboardView = findViewById(R.id.rear_dashboard);
        textureView.setClickable(false);
        brightnessOverlay.setClickable(false);
        calibrationGrid.setClickable(false);
        dashboardView.setClickable(true);
        dashboardView.setDashboardSettings(dashboardSettings, sessionContentMode);
        mirrorControlOverlay = new MirrorControlOverlay(this, () ->
                runOnUiThread(() -> setManualProjectionEnabled(!manualProjectionEnabled)));
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
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            resetIdleTimer();
        }
        return super.dispatchTouchEvent(event);
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
        if (automaticOutputVisible) {
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
            applyRotationTransform();
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
        RotationTransform transform = RotationTransform.forRotation(
                rotation,
                profile.rotationDegrees,
                profile.mirrorHorizontally
        );

        DisplayMetrics rearMetrics = getResources().getDisplayMetrics();
        Display.Mode mode = mainDisplay == null ? null : mainDisplay.getMode();
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
        float pivotX = textureView.getWidth() / 2f;
        float pivotY = textureView.getHeight() / 2f;

        Matrix matrix = new Matrix();
        matrix.setRotate(
                transform.degrees,
                pivotX,
                pivotY
        );
        matrix.postScale(combinedScaleX, combinedScaleY, pivotX, pivotY);
        textureView.setScaleX(1f);
        textureView.setScaleY(1f);
        textureView.setTranslationX(Math.round(ProjectionGeometry.calculateTranslation(
                rearMetrics.widthPixels,
                profile.horizontalOffsetPercent
        )));
        textureView.setTranslationY(Math.round(ProjectionGeometry.calculateTranslation(
                rearMetrics.heightPixels,
                profile.verticalOffsetPercent
        )));
        textureView.setTransform(matrix);
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
        int desiredWidth = quality.bufferDimension(viewWidth, profile.zoomPercent);
        int desiredHeight = quality.bufferDimension(viewHeight, profile.zoomPercent);
        if (projectionBufferWidth == desiredWidth && projectionBufferHeight == desiredHeight) {
            return;
        }
        surfaceTexture.setDefaultBufferSize(desiredWidth, desiredHeight);
        projectionBufferWidth = desiredWidth;
        projectionBufferHeight = desiredHeight;
        Log.i(TAG, "Projection buffer: " + desiredWidth + "x" + desiredHeight
                + " (" + quality + ", zoom=" + profile.zoomPercent + "%, resolution="
                + quality.bufferPercent(profile.zoomPercent) + "%)");
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
        if (idleFadeRunning) {
            return;
        }
        if (idleDimmed) {
            brightnessOverlay.setAlpha(aodDimOverlayAlpha());
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
        return Math.max(1, Math.round(percent * ambientFactor));
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
        if (brightnessController != null) {
            brightnessController.applyPercent(clamped);
        }
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
        DashboardWidgetLayout.IdleMode mode = DashboardWidgetLayout.loadIdleMode(this);
        long delay = mode == DashboardWidgetLayout.IdleMode.TIMEOUT_15
                ? 15_000L : mode == DashboardWidgetLayout.IdleMode.TIMEOUT_30
                ? 30_000L : AOD_DIM_DELAY_MILLIS;
        idleHandler.postDelayed(idleAction, delay);
    }

    private void beginIdleFade() {
        if (!MirrorState.isActive() || activeProfile == null) {
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
        float targetAlpha = mode == DashboardWidgetLayout.IdleMode.ALWAYS_ON
                ? aodDimOverlayAlpha() : 1f;
        brightnessOverlay.setAlpha(idleFadeStartOverlayAlpha
                + (targetAlpha - idleFadeStartOverlayAlpha) * progress);
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
        textureView.setVisibility(showProjection ? View.VISIBLE : View.INVISIBLE);
        if (dashboardView != null) {
            // The widgets step aside for the image rather than sitting on
            // top of it. In the mixed mode the image is usually a camera
            // viewfinder, and something to frame a shot in is worth more than
            // widgets over the middle of it. The mode still differs from plain
            // mirroring: the widgets are what the panel shows whenever the
            // image is not up.
            dashboardView.setVisibility(
                    automaticOutputVisible && sessionContentMode.showsDashboard()
                            && !showProjection
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
            mirrorControlOverlay.show(profileId != null && usesProjection(), manualProjectionEnabled);
        }
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
        dashboardView.setRotation(rotate ? 90f : 0f);
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
