package com.codecademy.comicreader.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    private static final String PREFS_NAME = "comicPrefs";
    private static final String KEY_THEME = "isNightMode";

    // Prevent instantiation (Kotlin object equivalent)
    private ThemeManager() {
    }

    public static boolean isNightMode(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_THEME, false);
    }

    /**
     * Apply theme safely.
     * - Always runs on main thread
     * - Avoids re-applying if mode is already active
     * - NO Activity recreation here (handled externally)
     */
    public static void applyTheme(Context context) {
        boolean isNight = isNightMode(context);

        // Ensure theme change runs on main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> applyTheme(context));
            return;
        }

        int mode = isNight
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;

        // Avoid redundant re-application (prevents flicker)
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    /**
     * Toggle theme without causing immediate Activity recreation.
     * Caller must handle recreation safely if needed.
     */
    public static void toggleTheme(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean current = prefs.getBoolean(KEY_THEME, false);
        prefs.edit()
                .putBoolean(KEY_THEME, !current)
                .apply();

        // Apply new theme, but DO NOT recreate here
        applyTheme(context);
    }
}