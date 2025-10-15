package com.codecademy.comicreader.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioButton;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.codecademy.comicreader.R;


public class SortDialog extends DialogFragment {

    private static final String TAG = "SortDialog";
    private OnSortListener onSortListener;
    private boolean isAscending = true;

    public static SortDialog newInstance() {
        return new SortDialog();
    }

    public void setOnSortListener(OnSortListener listener) {
        if (listener == null) {
            Log.w(TAG, "setOnSortListener: Listener is null!");
        }
        this.onSortListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_sort_by, container, false);

        RadioButton sortName = view.findViewById(R.id.rb_sort_by_name);
        RadioButton sortSize = view.findViewById(R.id.rb_sort_by_size);
        RadioButton sortDate = view.findViewById(R.id.rb_sort_by_date);
        Button sortDescending = view.findViewById(R.id.btn_descending_button);
        Button sortAscending = view.findViewById(R.id.btn_ascending_button);

        Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Activity is null. Cannot load sorting preferences.");
            return view;
        }

        SharedPreferences prefs = activity.getSharedPreferences("SortPrefs", Context.MODE_PRIVATE);
        String savedCriteria = prefs.getString("sort_criteria", "name");
        isAscending = prefs.getBoolean("sort_order", true);

        Log.d(TAG, "Loaded sort preferences: criteria=" + savedCriteria + ", isAscending=" + isAscending);

        switch (savedCriteria) {
            case "name":
                sortName.setChecked(true);
                break;
            case "size":
                sortSize.setChecked(true);
                break;
            case "date":
                sortDate.setChecked(true);
                break;
            default:
                Log.w(TAG, "Unknown sorting criteria: " + savedCriteria);
                sortName.setChecked(true); // Default to name
        }

        sortAscending.setOnClickListener(v -> {
            isAscending = true;
            Log.d(TAG, "Ascending button clicked.");
            applySort(sortName, sortSize, sortDate);
        });

        sortDescending.setOnClickListener(v -> {
            isAscending = false;
            Log.d(TAG, "Descending button clicked.");
            applySort(sortName, sortSize, sortDate);
        });

        return view;
    }

    private void applySort(RadioButton sortName, RadioButton sortSize, RadioButton sortDate) {
        Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Activity is null. Cannot apply sorting.");
            return;
        }

        String criteria = "";
        if (sortName.isChecked()) {
            criteria = "name";
        } else if (sortSize.isChecked()) {
            criteria = "size";
        } else if (sortDate.isChecked()) {
            criteria = "date";
        }

        if (criteria.isEmpty()) {
            Log.w(TAG, "No sorting criteria selected. Defaulting to 'name'.");
            criteria = "name";
        }

        Log.d(TAG, "Applying sort: criteria=" + criteria + ", isAscending=" + isAscending);

        SharedPreferences prefs = activity.getSharedPreferences("SortPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("sort_criteria", criteria);
        editor.putBoolean("sort_order", isAscending);

        boolean success = editor.commit();  // Debugging: Use commit() instead of apply()
        Log.d(TAG, "SharedPreferences commit status: " + success);

        if (onSortListener != null) {
            Log.d(TAG, "onSortListener is set. Calling onSort()");
            onSortListener.onSort(criteria, isAscending);
        } else {
            Log.e(TAG, "onSortListener is NULL! Sorting will NOT apply.");
        }

        if (isAdded()) {
            dismiss();
        } else {
            Log.w(TAG, "SortDialog is not attached. Cannot dismiss.");
        }
    }

    public interface OnSortListener {
        void onSort(String criteria, boolean isAscending);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();

            // Get screen width
            DisplayMetrics metrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenWidth = metrics.widthPixels;

            int orientation = getResources().getConfiguration().orientation;
            double widthPercent;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                widthPercent = 0.52; // smaller width for landscape
            } else {
                widthPercent = 0.90; // normal for portrait
            }

            // Set dialog width to 90% of screen width
            int dialogWidth = (int) (screenWidth * widthPercent);
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getDialog() != null && getDialog().isShowing() && !isRemoving()) {
            dismissAllowingStateLoss();
        }
    }
}