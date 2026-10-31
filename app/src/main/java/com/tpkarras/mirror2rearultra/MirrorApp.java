package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** Applies the app's own theme before any screen is drawn. */
public class MirrorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLook.applyStored(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            // Before the screen inflates, so every view picks up the wallpaper accent.
            @Override
            public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof AppCompatActivity && AppLook.monet(activity)) {
                    activity.getTheme().applyStyle(R.style.ThemeOverlay_M2R_Monet, true);
                }
            }

            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
