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
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.codecademy.comicreader.R;


public class SelectPageDialog extends DialogFragment {

    private OnComicSelectPageListener listener;

    public static SelectPageDialog newInstance(OnComicSelectPageListener listener) {
        SelectPageDialog fragment = new SelectPageDialog();
        fragment.listener = listener;
        return  fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_select_page, container, false);

        Button btnComicSelectOk = view.findViewById(R.id.btn_select_ok);
        Button btnComicSelectCancel = view.findViewById(R.id.btn_select_cancel);

        btnComicSelectOk.setOnClickListener(v -> comicSelect());
        btnComicSelectCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    private void comicSelect() {
        EditText etComicSelectPage = requireView().findViewById(R.id.et_select_page_value);
        String input = etComicSelectPage.getText().toString().trim();

        if (!input.isEmpty()) {
            try {
                int pageNumber = Integer.parseInt(input) - 1; // ViewPager uses 0-based index
                if (listener != null) {
                    listener.onComicSelect(pageNumber);
                }
                dismiss(); // Close dialog
            } catch (NumberFormatException e) {
                etComicSelectPage.setError("Invalid page number");
            }
        } else {
            etComicSelectPage.setError("Page number required");
        }
    }

    public interface OnComicSelectPageListener {
        void onComicSelect(int pageNumber);
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
