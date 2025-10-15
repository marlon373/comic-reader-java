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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.codecademy.comicreader.R;

public class RemoveFileDialog extends DialogFragment {

    private OnComicRemoveListener listener;

    public static RemoveFileDialog newInstance(OnComicRemoveListener listener) {
        RemoveFileDialog fragment = new RemoveFileDialog();
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_remove_file, container,false);

        Button btnComicRemoveOk = view.findViewById(R.id.btn_remove_file_ok);
        Button btnComicRemoveCancel = view.findViewById(R.id.btn_remove_file_cancel);

        btnComicRemoveOk.setOnClickListener(v -> comicRemove());
        btnComicRemoveCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    private void comicRemove() {
        if (listener != null) {
            listener.onComicRemove();
        }
        dismiss();
    }

    public interface OnComicRemoveListener {
        void onComicRemove();
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
