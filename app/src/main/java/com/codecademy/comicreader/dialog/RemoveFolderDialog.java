package com.codecademy.comicreader.dialog;

import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

import com.codecademy.comicreader.R;

// DialogFragment to confirm and handle folder removal from the UI
public class RemoveFolderDialog extends DialogFragment {

    private Uri folderUri;
    private OnFolderRemoveListener onFolderRemoveListener;

    // Factory method to create an instance of the dialog with folder URI as an argument
    public static RemoveFolderDialog newInstances(Uri folderUri) {
        RemoveFolderDialog deleteFolderDialog = new RemoveFolderDialog();
        Bundle args = new Bundle();
        args.putParcelable("folderUri", folderUri);
        deleteFolderDialog.setArguments(args);
        return  deleteFolderDialog;
    }

    // Sets the listener for folder removal
    public void setOnFolderRemoveListener(OnFolderRemoveListener listener) {
        this.onFolderRemoveListener = listener;
    }

    // Inflates the dialog layout and sets up click listeners
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_remove_folder, container, false);

        Button folderOk = view.findViewById(R.id.btn_remove_folder_ok);
        Button folderCancel = view.findViewById(R.id.btn_remove_folder_cancel);

        // Set up the remove button action
        folderOk.setOnClickListener(v -> removeFolder());

        // Cancel button dismisses the dialog
        folderCancel.setOnClickListener(v -> dismiss());


        return view;
    }

    // Handles folder removal (removes from UI only, not from storage)
    private void removeFolder() {
        if (getArguments() != null) {
            folderUri = getArguments().getParcelable("folderUri");
        }

        if (folderUri != null) {
            Log.d("RemoveFolderDialog", "Removing folder from UI only: " + folderUri);
            if (onFolderRemoveListener != null) {
                onFolderRemoveListener.onFolderRemove(folderUri.toString()); // Only notify, don't delete storage
            }
            dismiss();
        } else {
            Log.e("RemoveFolderDialog", "Invalid folder URI");
        }
    }

    // Interface for folder removal event callback
    public interface OnFolderRemoveListener {
        void onFolderRemove(String folderPath);
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