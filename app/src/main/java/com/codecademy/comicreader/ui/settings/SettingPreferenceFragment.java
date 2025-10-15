package com.codecademy.comicreader.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.codecademy.comicreader.R;

public class SettingPreferenceFragment extends PreferenceFragmentCompat {

    private static final String PREFS_NAME = "comicPrefs";
    private static final String KEY_THEME = "isNightMode";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);

        setupNightModeSwitch();
        setupOtherPreferences(); // optional placeholders
    }

    private void setupNightModeSwitch() {
        SwitchPreferenceCompat nightModePref = findPreference("night_mode");

        if (nightModePref != null) {
            // Read current value from SharedPreferences
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
            boolean isNightMode = prefs.getBoolean(KEY_THEME, false);
            nightModePref.setChecked(isNightMode);

            nightModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enableNight = (Boolean) newValue;

                // Save preference
                prefs.edit().putBoolean(KEY_THEME, enableNight).apply();

                // Apply theme
                AppCompatDelegate.setDefaultNightMode(
                        enableNight ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                );

                requireActivity().recreate(); // Refresh the UI
                return true; // Allow value to be saved
            });
        }
    }

    private void setupOtherPreferences() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        SwitchPreferenceCompat resumePref = findPreference("open_last_file");
        if (resumePref != null) {
            resumePref.setChecked(prefs.getBoolean("open_last_file", false));
            resumePref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefs.edit().putBoolean("open_last_file", (Boolean) newValue).apply();
                return true;
            });
        }

        SwitchPreferenceCompat volumeScrollPref = findPreference("scroll_with_volume");
        if (volumeScrollPref != null) {
            volumeScrollPref.setChecked(prefs.getBoolean("scroll_with_volume", false));
            volumeScrollPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefs.edit().putBoolean("scroll_with_volume", (Boolean) newValue).apply();
                return true;
            });
        }
    }

}