package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How the app's own screens look: the theme and the language.
 *
 * <p>Both only touch this app's windows. The rear panel has its own theme in
 * the dashboard settings and is not changed by them.
 */
final class AppLook {
    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

    private static final String PREFS = "app_look";
    private static final String KEY_THEME = "theme";

    /** The languages the app has its own strings for; others fall back to English. */
    private static final String[] LANGUAGE_TAGS = {
            "en", "ru", "ar", "cs", "el", "es", "et", "fa", "fi", "fr", "ga", "he", "hi", "it",
            "ja", "nl", "no", "ro", "sv", "sw", "uk", "zh"};

    private static final String KEY_MONET = "monet";
    private static final String KEY_BLUR = "blur";

    private AppLook() { }

    /** Whether the wallpaper colours replace the blue accent (Android 12 and newer). */
    static boolean monet(Context context) {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && prefs(context).getBoolean(KEY_MONET, false);
    }

    static void setMonet(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_MONET, on).apply();
    }

    /** Whether the bottom bar is frosted glass over the list instead of a solid pill. */
    static boolean blur(Context context) {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && prefs(context).getBoolean(KEY_BLUR, false);
    }

    static void setBlur(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_BLUR, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int theme(Context context) {
        int theme = prefs(context).getInt(KEY_THEME, THEME_SYSTEM);
        return theme == THEME_LIGHT || theme == THEME_DARK ? theme : THEME_SYSTEM;
    }

    /** Stores the choice and applies it, which recreates the open screens. */
    static void setTheme(Context context, int theme) {
        prefs(context).edit().putInt(KEY_THEME, theme).apply();
        applyTheme(theme);
    }

    /** Called once at start; the theme is not kept by the system between runs. */
    static void applyStored(Context context) {
        applyTheme(theme(context));
    }

    private static void applyTheme(int theme) {
        AppCompatDelegate.setDefaultNightMode(theme == THEME_LIGHT
                ? AppCompatDelegate.MODE_NIGHT_NO
                : theme == THEME_DARK ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /** Language tags in the order the chooser lists them, after "system default". */
    static List<String> languageTags() {
        List<String> tags = new ArrayList<>();
        Collections.addAll(tags, LANGUAGE_TAGS);
        tags.sort((a, b) -> nativeName(a).compareToIgnoreCase(nativeName(b)));
        return tags;
    }

    /** A language written in itself, so it can be found without reading the current one. */
    static String nativeName(String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        String name = locale.getDisplayName(locale);
        return name.isEmpty() ? tag
                : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /** The language chosen in the app, or an empty string for the system's. */
    static String language() {
        LocaleListCompat chosen = AppCompatDelegate.getApplicationLocales();
        return chosen.isEmpty() || chosen.get(0) == null ? "" : chosen.get(0).getLanguage();
    }

    static void setLanguage(String tag) {
        AppCompatDelegate.setApplicationLocales(tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(tag));
    }
}
