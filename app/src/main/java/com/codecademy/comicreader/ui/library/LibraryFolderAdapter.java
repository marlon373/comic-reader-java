package com.codecademy.comicreader.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.codecademy.comicreader.R;

import java.util.List;

import com.codecademy.comicreader.model.Folder;

public class LibraryFolderAdapter extends RecyclerView.Adapter<LibraryFolderAdapter.ViewHolder> {

    private final List<Folder> comicsList;
    private final OnFolderClickListener listener;
    private final OnFolderLongClickListener longClickListener;
    private Folder selectedFolder;

    // Constructor to initialize the adapter with a list of folder items and click listeners
    public LibraryFolderAdapter(List<Folder> folderItems, OnFolderClickListener listener, OnFolderLongClickListener longClickListener) {
        this.comicsList = folderItems;
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    // Sets the selected folder and refreshes the UI
    public void setSelectedFolder(Folder folder) {
        this.selectedFolder = folder;
        notifyDataSetChanged(); // Refresh selection
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder_library, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Folder item = comicsList.get(position);
        holder.bind(item, listener, longClickListener);

        // Ensure correct background state is applied
        holder.itemView.setBackgroundResource(R.drawable.folder_selector);
        holder.itemView.setActivated(item == selectedFolder);
    }

    @Override
    public int getItemCount() {
        return comicsList.size();
    }

    // Interface for handling folder click events
    public interface OnFolderClickListener {
        void onFolderClick(Folder item);
    }

    // Interface for handling folder long-click events
    public interface OnFolderLongClickListener {
        void onFolderLongClick(Folder item, View itemView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivLibraryIcon;
        private final TextView tvLibraryName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivLibraryIcon = itemView.findViewById(R.id.iv_library_icon);
            tvLibraryName = itemView.findViewById(R.id.tv_library_name);
        }

        // Binds folder data to the UI elements and sets up click listeners
        public void bind(Folder item, OnFolderClickListener listener, OnFolderLongClickListener longClickListener) {
            tvLibraryName.setText(item.getName());
            ivLibraryIcon.setImageResource(item.isFolder() ? R.drawable.ic_folder_library : R.drawable.ic_file_library);

            itemView.setOnClickListener(v -> listener.onFolderClick(item));

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onFolderLongClick(item, itemView);
                }
                return true;
            });
        }
    }
}


