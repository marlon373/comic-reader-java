package com.codecademy.comicreader.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    private static final String PREFS_NAME = "comicPrefs";
    private static final String KEY_THEME = "isNightMode";

    // Get current theme mode
    public static boolean isNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_THEME, false);
    }

    // Apply theme based on preference
    public static void applyTheme(Context context) {
        boolean isNight = isNightMode(context);
        AppCompatDelegate.setDefaultNightMode(
                isNight ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    // Toggle between dark/light mode
    public static void toggleTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isNight = prefs.getBoolean(KEY_THEME, false);

        // Save toggled preference
        prefs.edit().putBoolean(KEY_THEME, !isNight).apply();

        // Apply theme immediately
        AppCompatDelegate.setDefaultNightMode(
                isNight ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES
        );

        // Recreate activity to apply theme
        activity.recreate();
    }
}