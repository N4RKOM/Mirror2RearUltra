package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The update sheet: looks for a newer release, shows its version and changelog,
 * downloads it on request, asks for the install permission once the file is
 * there, and hands it to the system installer.
 *
 * <p>Closing the sheet stops a download that is still running.
 */
final class UpdateSheet {
    private enum State { CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, NEEDS_PERMISSION, FAILED }

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String currentVersion;

    @Nullable private BottomSheetDialog dialog;
    private TextView title;
    private TextView versionLine;
    private ProgressBar progress;
    private TextView status;
    private View notesBlock;
    private TextView notesTitle;
    private MaxHeightScrollView notesScroll;
    private TextView notes;
    private MaterialButton action;
    private MaterialButton close;

    private State state = State.CHECKING;
    @Nullable private UpdateChecker.Release release;
    @Nullable private File downloaded;
    @Nullable private AtomicBoolean cancelDownload;
    @StringRes private int failure;
    /** Bumped whenever the sheet starts over, so a late answer from before is ignored. */
    private int generation;

    UpdateSheet(Activity activity) {
        this.activity = activity;
        String name = "";
        try {
            name = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // Our own package is always installed.
        }
        this.currentVersion = name == null ? "" : name;
    }

    /** Opens the sheet and starts looking for the latest release. */
    void show() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.sheet_update, null);
        sheet.setContentView(content);
        // The layout paints its own rounded surface; the dialog's default one would show behind it.
        ((View) content.getParent()).setBackgroundColor(Color.TRANSPARENT);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.setOnDismissListener(shown -> onDismissed());

        title = content.findViewById(R.id.update_sheet_title);
        versionLine = content.findViewById(R.id.update_sheet_version);
        progress = content.findViewById(R.id.update_sheet_progress);
        status = content.findViewById(R.id.update_sheet_status);
        notesBlock = content.findViewById(R.id.update_sheet_notes_block);
        notesTitle = content.findViewById(R.id.update_sheet_notes_title);
        notesScroll = content.findViewById(R.id.update_sheet_notes_scroll);
        notes = content.findViewById(R.id.update_sheet_notes);
        action = content.findViewById(R.id.update_sheet_action);
        close = content.findViewById(R.id.update_sheet_close);
        notesScroll.setMaxHeight((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.36f));
        close.setOnClickListener(view -> sheet.dismiss());
        action.setOnClickListener(view -> onAction());

        dialog = sheet;
        release = null;
        downloaded = null;
        sheet.show();
        startCheck();
    }

    /** Back from the permission page (or anywhere else): carry on if the install is now allowed. */
    void onActivityResumed() {
        if (dialog != null && state == State.NEEDS_PERMISSION && UpdateInstaller.canInstall(activity)) {
            install();
        }
    }

    /** The activity is going away: drop the sheet and anything still running. */
    void dispose() {
        generation++;
        stopDownload();
        executor.shutdownNow();
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            dialog = null;
        }
    }

    private void onDismissed() {
        generation++;
        stopDownload();
        dialog = null;
    }

    private void stopDownload() {
        if (cancelDownload != null) {
            cancelDownload.set(true);
        }
    }

    private void startCheck() {
        int ticket = ++generation;
        setState(State.CHECKING);
        executor.execute(() -> {
            UpdateChecker.Release found = null;
            try {
                found = UpdateChecker.fetchLatest();
            } catch (IOException | RuntimeException unavailable) {
                // Shown as a failed check below.
            }
            UpdateChecker.Release result = found;
            post(ticket, () -> {
                if (result == null) {
                    fail(R.string.update_failed);
                    return;
                }
                release = result;
                setState(UpdateChecker.isNewer(currentVersion, result.tag)
                        ? State.AVAILABLE : State.UP_TO_DATE);
            });
        });
    }

    private void onAction() {
        switch (state) {
            case AVAILABLE:
                startDownload();
                break;
            case DOWNLOADING:
                generation++;
                stopDownload();
                setState(State.AVAILABLE);
                break;
            case NEEDS_PERMISSION:
                requestInstallPermission();
                break;
            case FAILED:
                if (release == null) {
                    startCheck();
                } else {
                    startDownload();
                }
                break;
            default:
                break;
        }
    }

    private void startDownload() {
        UpdateChecker.Release target = release;
        if (target == null) {
            return;
        }
        if (target.apkUrl == null) {
            // Nothing to install from here: the release page is the next best thing.
            openLink(target.pageUrl);
            return;
        }
        int ticket = ++generation;
        AtomicBoolean cancelled = new AtomicBoolean();
        cancelDownload = cancelled;
        setProgress(0, target.apkSize);
        setState(State.DOWNLOADING);
        executor.execute(() -> {
            File apk = null;
            int error = 0;
            try {
                apk = UpdateInstaller.download(activity, target,
                        (received, total) -> post(ticket, () -> setProgress(received, total)),
                        cancelled);
            } catch (UpdateInstaller.Cancelled stopped) {
                return;
            } catch (UpdateInstaller.Damaged damaged) {
                error = R.string.update_damaged;
            } catch (IOException | RuntimeException failed) {
                error = R.string.update_download_failed;
            }
            File finished = apk;
            int reason = error;
            post(ticket, () -> {
                if (finished == null) {
                    fail(reason);
                    return;
                }
                downloaded = finished;
                afterDownload();
            });
        });
    }

    /** The file is in hand: install it, or ask for the permission that lets us. */
    private void afterDownload() {
        if (UpdateInstaller.canInstall(activity)) {
            install();
        } else {
            setState(State.NEEDS_PERMISSION);
            requestInstallPermission();
        }
    }

    private void requestInstallPermission() {
        try {
            activity.startActivity(UpdateInstaller.permissionIntent(activity));
        } catch (ActivityNotFoundException unavailable) {
            fail(R.string.update_install_failed);
        }
    }

    private void install() {
        File apk = downloaded;
        if (apk == null) {
            return;
        }
        try {
            activity.startActivity(UpdateInstaller.installIntent(activity, apk));
            if (dialog != null) {
                dialog.dismiss();
            }
        } catch (ActivityNotFoundException | IllegalArgumentException unavailable) {
            fail(R.string.update_install_failed);
        }
    }

    private void openLink(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            // No browser installed: nothing to open.
        }
    }

    private void fail(@StringRes int reason) {
        failure = reason;
        setState(State.FAILED);
    }

    /** Runs on the main thread, unless the sheet was closed or restarted since {@code ticket}. */
    private void post(int ticket, Runnable work) {
        activity.runOnUiThread(() -> {
            if (ticket == generation && dialog != null && !activity.isFinishing()
                    && !activity.isDestroyed()) {
                work.run();
            }
        });
    }

    private void setProgress(long received, long total) {
        if (total > 0) {
            int percent = (int) Math.min(100, received * 100 / total);
            progress.setIndeterminate(false);
            progress.setProgress(percent);
            status.setText(activity.getString(R.string.update_downloading_percent, percent,
                    Formatter.formatShortFileSize(activity, received),
                    Formatter.formatShortFileSize(activity, total)));
        } else {
            progress.setIndeterminate(true);
            status.setText(activity.getString(R.string.update_downloading_bytes,
                    Formatter.formatShortFileSize(activity, received)));
        }
    }

    private void setState(State next) {
        state = next;
        if (dialog == null) {
            return;
        }
        UpdateChecker.Release shown = release;
        boolean working = next == State.CHECKING || next == State.DOWNLOADING;
        progress.setVisibility(working ? View.VISIBLE : View.GONE);
        if (next == State.CHECKING) {
            progress.setIndeterminate(true);
        }
        status.setVisibility(View.GONE);
        action.setVisibility(View.GONE);
        close.setVisibility(View.VISIBLE);

        switch (next) {
            case CHECKING:
                title.setText(R.string.update_checking);
                versionLine.setText(activity.getString(R.string.update_current_version, currentVersion));
                break;
            case UP_TO_DATE:
                title.setText(R.string.update_up_to_date);
                versionLine.setText(activity.getString(R.string.update_current_version, currentVersion));
                break;
            case AVAILABLE:
                title.setText(R.string.update_available_title);
                versionLine.setText(activity.getString(R.string.update_version_change,
                        currentVersion, shown == null ? "" : shown.version()));
                if (shown != null && shown.apkUrl != null && shown.apkSize > 0) {
                    setAction(activity.getString(R.string.update_download_size,
                            Formatter.formatShortFileSize(activity, shown.apkSize)));
                } else if (shown != null && shown.apkUrl != null) {
                    setAction(activity.getString(R.string.update_download));
                } else {
                    setAction(activity.getString(R.string.update_release_page));
                }
                break;
            case DOWNLOADING:
                title.setText(R.string.update_downloading);
                versionLine.setText(activity.getString(R.string.update_version_change,
                        currentVersion, shown == null ? "" : shown.version()));
                status.setVisibility(View.VISIBLE);
                setAction(activity.getString(R.string.update_cancel));
                close.setVisibility(View.GONE);
                break;
            case NEEDS_PERMISSION:
                title.setText(R.string.update_permission_title);
                versionLine.setText(activity.getString(R.string.update_version_change,
                        currentVersion, shown == null ? "" : shown.version()));
                status.setVisibility(View.VISIBLE);
                status.setText(R.string.update_permission_message);
                setAction(activity.getString(R.string.update_permission_open));
                break;
            case FAILED:
                title.setText(R.string.update_failed_title);
                versionLine.setText(activity.getString(R.string.update_current_version, currentVersion));
                status.setVisibility(View.VISIBLE);
                status.setText(failure);
                setAction(activity.getString(R.string.update_retry));
                break;
            default:
                break;
        }
        showNotes(shown, next);
    }

    private void setAction(String label) {
        action.setText(label);
        action.setVisibility(View.VISIBLE);
    }

    private void showNotes(@Nullable UpdateChecker.Release shown, State current) {
        boolean has = shown != null && (current == State.AVAILABLE || current == State.UP_TO_DATE
                || current == State.DOWNLOADING || current == State.NEEDS_PERMISSION);
        notesBlock.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) {
            return;
        }
        notesTitle.setText(activity.getString(R.string.update_notes_title, shown.version()));
        if (shown.notes.isEmpty()) {
            notes.setText(R.string.update_notes_empty);
        } else {
            notes.setText(shown.notes);
        }
    }
}
