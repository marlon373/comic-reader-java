package com.codecademy.comicreader.ui.settings;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.codecademy.comicreader.R;
import com.codecademy.comicreader.databinding.FragmentSettingsBinding;
import com.codecademy.comicreader.ui.comic.ComicViewModel;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        new ViewModelProvider(this).get(ComicViewModel.class);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Load SettingPreferenceFragment (Preferences)
        if (savedInstanceState == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingPreferenceFragment())
                    .commit();
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}