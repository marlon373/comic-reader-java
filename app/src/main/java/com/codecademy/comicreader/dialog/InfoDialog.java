package com.codecademy.comicreader.dialog;


import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.codecademy.comicreader.R;


// Problem layout-size on other devices
public class InfoDialog extends DialogFragment {

    public static InfoDialog newInstance(String name, String path, String date, String size) {
        InfoDialog fragment = new InfoDialog();
        Bundle args = new Bundle();
        args.putString("name", name);
        args.putString("path", path);
        args.putString("date", date);
        args.putString("size", size);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_info, container, false);

        Button btnInfoClose = view.findViewById(R.id.btn_info_close);
        btnInfoClose.setOnClickListener(v -> dismiss());

        comicInfo(view);  // Call this with the root view

        return view;
    }

    private void comicInfo(View view) {
        Bundle args = getArguments();
        if (args == null) return;

        String name = args.getString("name", "N/A");
        String path = args.getString("path", "N/A");
        String date = args.getString("date", "N/A");
        String size = args.getString("size", "N/A");

        TextView tvName = view.findViewById(R.id.tv_info_name_value);
        TextView tvPath = view.findViewById(R.id.tv_info_path_value);
        TextView tvDate = view.findViewById(R.id.tv_info_last_mod_date_value);
        TextView tvSize = view.findViewById(R.id.tv_info_size_value);

        tvName.setText(name);
        tvPath.setText(path);
        tvDate.setText(date);
        tvSize.setText(size);
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
                widthPercent = 0.50; // smaller width for landscape
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

