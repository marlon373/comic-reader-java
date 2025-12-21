package com.codecademy.comicreader.ui.comic;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import com.codecademy.comicreader.R;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import com.codecademy.comicreader.dialog.InfoDialog;
import com.codecademy.comicreader.dialog.RemoveFileDialog;
import com.codecademy.comicreader.model.Comic;

public class ComicAdapter extends RecyclerView.Adapter<ComicAdapter.ViewHolder> {

    private final List<Comic> comicList;
    private final Context context;
    public boolean isGridView;
    private final OnComicClickListener listener;

    // Track thumbnail loading tasks per ImageView (hashCode keyed)
    private final Map<Integer, Future<?>> thumbnailTasks = new HashMap<>();

    public interface OnComicClickListener {
        void onComicClick(Comic comic);
    }

    public ComicAdapter(List<Comic> comicList, OnComicClickListener listener, boolean isGrid, Context context) {
        this.comicList = comicList;
        this.listener = listener;
        this.isGridView = isGrid;
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        return isGridView ? 1 : 2; // VIEW_TYPE_GRID = 1, VIEW_TYPE_LIST = 2
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutId = (viewType == 1) ? R.layout.comic_grid_view_display : R.layout.comic_list_view_display;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Comic item = comicList.get(position);
        holder.bind(item, listener);

        int key = holder.ivComicRead.hashCode();

        // Cancel previous thumbnail task
        Future<?> prevTask = thumbnailTasks.get(key);
        if (prevTask != null) {
            prevTask.cancel(false);
            thumbnailTasks.remove(key);
        }

        // Clear previous image
        holder.ivComicRead.setImageDrawable(null);

        // Start new thumbnail task
        Future<?> task = ThumbnailManager.loadThumbnailAsync(context, Uri.parse(item.getPath()), item.getFormat(), holder.ivComicRead, null, 1);
        thumbnailTasks.put(key, task);

        // Popup menu
        holder.ivComicMenu.setOnClickListener(v -> showPopupMenu(holder, item));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);

        int key = holder.ivComicRead.hashCode();
        Future<?> task = thumbnailTasks.get(key);
        if (task != null) {
            task.cancel(false);
            thumbnailTasks.remove(key);
        }

        holder.ivComicRead.setImageDrawable(null);
    }

    @Override
    public int getItemCount() {
        return comicList.size();
    }

    public void updateComicList(List<Comic> newComics) {
        comicList.clear();
        comicList.addAll(newComics);
        notifyDataSetChanged();
    }

    public void appendComics(List<Comic> newComics) {
        int start = comicList.size();
        comicList.addAll(newComics);
        notifyItemRangeInserted(start, newComics.size());
    }

    private void showPopupMenu(ViewHolder holder, Comic item) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.custom_popup_menu, null);
        PopupWindow popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setElevation(10f);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.showAsDropDown(holder.ivComicMenu);

        TextView tvComicRemove = popupView.findViewById(R.id.tv_Pop_Menu_Remove);
        TextView tvComicInfo = popupView.findViewById(R.id.tv_Pop_Menu_Info);

        int currentPosition = holder.getBindingAdapterPosition();
        if (currentPosition == RecyclerView.NO_POSITION) return;

        // Remove comic
        tvComicRemove.setOnClickListener(x -> {
            RemoveFileDialog dialog = RemoveFileDialog.newInstance(() -> {
                Comic comicToRemove = comicList.get(currentPosition);
                SharedPreferences prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE);
                Set<String> removedPaths = new HashSet<>(prefs.getStringSet("removed_paths", new HashSet<>()));
                removedPaths.add(comicToRemove.getPath());
                prefs.edit().putStringSet("removed_paths", removedPaths).apply();

                comicList.remove(currentPosition);
                notifyItemRemoved(currentPosition);
            });
            if (context instanceof AppCompatActivity) {
                dialog.show(((AppCompatActivity) context).getSupportFragmentManager(), "removeComicDialog");
            }
            popupWindow.dismiss();
        });

        // Info option
        tvComicInfo.setOnClickListener(x -> {
            String readablePath = getReadablePath(context, Uri.parse(item.getPath()));
            InfoDialog dialog = InfoDialog.newInstance(item.getName(), readablePath, item.getDate(), item.getSize());
            if (context instanceof AppCompatActivity) {
                dialog.show(((AppCompatActivity) context).getSupportFragmentManager(), "infoDialog");
            }
            popupWindow.dismiss();
        });
    }

    public static String getReadablePath(Context context, Uri uri) {
        try {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] parts = docId.split(":");
            if (parts.length == 2) {
                String volume = parts[0];
                String path = parts[1];
                if ("primary".equals(volume)) return "/storage/emulated/0/" + path;
                else return "/storage/" + volume + "/" + path;
            } else return uri.toString();
        } catch (Exception e1) {
            try {
                DocumentFile file = DocumentFile.fromSingleUri(context, uri);
                if (file != null && file.getName() != null) return file.getName();
            } catch (Exception ignored) {}
            return uri.toString();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ImageView ivComicRead;
        public final ImageView ivComicMenu;
        private final TextView tvComicTitle;
        private final TextView tvComicDate;
        private final TextView tvComicSize;
        private final TextView tvComicFormat;

        public ViewHolder(View itemView) {
            super(itemView);
            ivComicRead = itemView.findViewById(R.id.iv_comic_read);
            ivComicMenu = itemView.findViewById(R.id.iv_comic_menu);
            tvComicTitle = itemView.findViewById(R.id.tv_comic_title);
            tvComicDate = itemView.findViewById(R.id.tv_comic_date);
            tvComicSize = itemView.findViewById(R.id.tv_comic_size);
            tvComicFormat = itemView.findViewById(R.id.tv_comic_format);

            // Safety: prevent background/tint interfering with thumbnail
            ivComicRead.setBackground(null);
            ivComicRead.setImageTintList(null);
            ivComicRead.setWillNotDraw(false);
        }

        public void bind(Comic item, OnComicClickListener listener) {
            tvComicTitle.setText(item.getName());
            tvComicDate.setText(item.getDate());
            tvComicSize.setText(item.getSize());
            tvComicFormat.setText(item.getFormat());

            ivComicRead.setOnClickListener(v -> listener.onComicClick(item));
        }
    }
}





