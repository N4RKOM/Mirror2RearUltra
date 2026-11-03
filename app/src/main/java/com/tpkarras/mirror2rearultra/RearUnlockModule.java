package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.annotation.Nullable;

/**
 * Whether the Rear Unlock LSPosed module is really working in the running system.
 *
 * <p>An installed package and an enabled switch prove nothing: the module reaches
 * {@code system_server} only after a reboot. Once it is there it writes
 * {@code <module versionCode>:<boot count>} to a secure setting, which any app can read.
 * A stamp from an earlier boot - or left by a module that has since been disabled - carries an
 * older boot count and does not count.
 */
final class RearUnlockModule {
    static final String PACKAGE = "com.n4r.rearunlock";
    /** Where the release APK is published. */
    static final String RELEASES_URL = "https://github.com/N4RKOM/Mirror2RearUltra/releases/latest";

    /** Keep in step with the module's {@code LoadStamp.KEY}. */
    private static final String STAMP_KEY = "m2ru_rearunlock_stamp";
    /** The oldest module that writes the stamp; older ones are asked to update. */
    static final int MIN_VERSION = 5;

    enum State {
        /** Loaded in this boot, at a current version. */
        LOADED,
        /** Installed or loaded, but older than {@link #MIN_VERSION}. */
        OUTDATED,
        /** Installed, but not loaded in this boot: not enabled, wrong scope, or no reboot yet. */
        NOT_LOADED,
        NOT_INSTALLED
    }

    private RearUnlockModule() {
    }

    static State state(Context context) {
        android.content.ContentResolver resolver = context.getContentResolver();
        String stamp = null;
        int boot = -1;
        try {
            stamp = Settings.Secure.getString(resolver, STAMP_KEY);
            boot = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException ignored) {
            // Unreadable counts as absent.
        }
        long installed = installedVersion(context);
        return classify(stamp, boot, installed);
    }

    /**
     * @param stamp the module's stamp, or null
     * @param bootCount this boot's count, or -1 when unknown
     * @param installedVersion the installed module's versionCode, or -1 when it is not installed
     */
    static State classify(@Nullable String stamp, int bootCount, long installedVersion) {
        int[] parsed = parse(stamp);
        if (parsed != null && bootCount >= 0 && parsed[1] == bootCount) {
            return parsed[0] >= MIN_VERSION ? State.LOADED : State.OUTDATED;
        }
        if (installedVersion < 0) {
            return State.NOT_INSTALLED;
        }
        return installedVersion < MIN_VERSION ? State.OUTDATED : State.NOT_LOADED;
    }

    /** @return {version, boot}, or null when the text is not a stamp */
    @Nullable
    private static int[] parse(@Nullable String stamp) {
        if (stamp == null) {
            return null;
        }
        int colon = stamp.indexOf(':');
        if (colon <= 0 || colon == stamp.length() - 1) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(stamp.substring(0, colon)),
                    Integer.parseInt(stamp.substring(colon + 1))
            };
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static long installedVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException error) {
            return -1;
        }
    }
}
