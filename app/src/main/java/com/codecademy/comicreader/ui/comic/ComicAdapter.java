package com.codecademy.comicreader.ui.comic;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import com.codecademy.comicreader.R;
import com.codecademy.comicreader.dialog.InfoDialog;
import com.codecademy.comicreader.dialog.RemoveFileDialog;
import com.github.junrar.Archive;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.codecademy.comicreader.model.Comic;

public class ComicAdapter extends RecyclerView.Adapter<ComicAdapter.ViewHolder> {

    private final List<Comic> comicList;          // list of comics displayed
    private final ComicClickListener listener;    // callback when comic is clicked
    private final boolean isGridView;             // grid or list layout mode

    // ---------- Static cache + sharedExecutor ----------
    private static ExecutorService sharedExecutor;      // background worker threads
    private static boolean cleanupDone = false;   // ensure cleanup runs once per launch
    private static final int MAX_CACHE_SIZE =
            (int) (Runtime.getRuntime().maxMemory() / 1024 / 4); // 1/4 of heap

    //  Memory cache for thumbnails
    private static final LruCache<String, Bitmap> memoryCache = new LruCache<>(MAX_CACHE_SIZE) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getByteCount() / 1024; // size in KB
        }
    };

    // Listener interface
    public interface ComicClickListener {
        void onComicClick(Comic comic);
    }

    // Constructor
    public ComicAdapter(List<Comic> comicList, ComicClickListener listener, boolean isGridView, Context context,ExecutorService executor) {
        this.comicList = comicList;
        this.listener = listener;
        this.isGridView = isGridView;

        // Ensure background sharedExecutor is available
        if (executor != null) {
            sharedExecutor = executor;
        }

        // Run disk cache cleanup once per app launch
        if (!cleanupDone) {
            cleanupOldThumbnails(context.getApplicationContext());
            cleanupDone = true;
        }
    }

    // Clear in-memory cache manually
    public static void clearMemoryCache() {
        memoryCache.evictAll();
    }

    // Clean disk cache (remove old thumbnails older than 30 days)
    public static void cleanupOldThumbnails(Context context) {
        try {
            File cacheDir = context.getCacheDir();
            long now = System.currentTimeMillis();
            long maxAgeMillis = 30L * 24 * 60 * 60 * 1000; // 30 days

            File[] files = cacheDir.listFiles((dir, name) ->
                    name.startsWith("thumb_") && name.endsWith(".jpg"));

            if (files != null) {
                for (File file : files) {
                    if (now - file.lastModified() > maxAgeMillis) {
                        if (!file.delete()) {
                            Log.w("ComicAdapter", "Failed to delete old thumbnail: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ComicAdapter", "Failed to clean thumbnails", e);
        }
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isGridView ? R.layout.comic_grid_view_display : R.layout.comic_list_view_display;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Comic item = comicList.get(position);
        Context ctx = holder.itemView.getContext();

        // Skip missing/removed files
        DocumentFile file = DocumentFile.fromSingleUri(ctx, Uri.parse(item.getPath()));
        if (file == null || !file.exists()) {
            Log.w("ComicAdapter", "Skipping missing comic: " + item.getPath());
            return;
        }

        //  Setup popup menu (Remove / Info)
        holder.ibtnComicMenu.setOnClickListener(v -> {
            View popupView = LayoutInflater.from(ctx).inflate(R.layout.custom_popup_menu, null);
            PopupWindow popupWindow = new PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );
            popupWindow.setElevation(10f);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setFocusable(true);
            popupWindow.showAsDropDown(holder.ibtnComicMenu);

            TextView tvComicRemove = popupView.findViewById(R.id.tv_Pop_Menu_Remove);
            TextView tvComicInfo = popupView.findViewById(R.id.tv_Pop_Menu_Info);

            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return;

            // Remove comic option
            tvComicRemove.setOnClickListener(x -> {
                RemoveFileDialog dialog = RemoveFileDialog.newInstance(() -> {
                    Comic comicToRemove = comicList.get(currentPosition);
                    SharedPreferences prefs = ctx.getSharedPreferences("removed_comics", Context.MODE_PRIVATE);
                    Set<String> removedPaths = new HashSet<>(prefs.getStringSet("removed_paths", new HashSet<>()));
                    removedPaths.add(comicToRemove.getPath());
                    prefs.edit().putStringSet("removed_paths", removedPaths).apply();

                    comicList.remove(currentPosition);
                    notifyItemRemoved(currentPosition);
                });
                if (ctx instanceof AppCompatActivity) {
                    dialog.show(((AppCompatActivity) ctx).getSupportFragmentManager(), "removeComicDialog");
                }
                popupWindow.dismiss();
            });

            // Info option
            tvComicInfo.setOnClickListener(x -> {
                String readablePath = getReadablePath(ctx, Uri.parse(item.getPath()));
                InfoDialog dialog = InfoDialog.newInstance(item.getName(), readablePath, item.getDate(), item.getSize());
                if (ctx instanceof AppCompatActivity) {
                    dialog.show(((AppCompatActivity) ctx).getSupportFragmentManager(), "infoDialog");
                }
                popupWindow.dismiss();
            });
        });

        // Bind comic data + thumbnail
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return comicList.size();
    }

    // Replace entire comic list
    public void updateComicList(List<Comic> newComics) {
        comicList.clear();
        comicList.addAll(newComics);
        notifyDataSetChanged();
    }

    // Append new comics incrementally
    public void appendComics(List<Comic> newComics) {
        int start = comicList.size();
        comicList.addAll(newComics);
        notifyItemRangeInserted(start, newComics.size());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageButton ibtnComicRead;
        ImageButton ibtnComicMenu;
        TextView tvComicTitle, tvComicDate, tvComicSize, tvComicFormat;

        ViewHolder(View itemView) {
            super(itemView);
            ibtnComicRead = itemView.findViewById(R.id.ibtn_comic_read);
            ibtnComicMenu = itemView.findViewById(R.id.ibtn_comic_menu);
            tvComicTitle = itemView.findViewById(R.id.tv_comic_title);
            tvComicDate = itemView.findViewById(R.id.tv_comic_date);
            tvComicSize = itemView.findViewById(R.id.tv_comic_size);
            tvComicFormat = itemView.findViewById(R.id.tv_comic_format);
        }

        void bind(Comic item, ComicClickListener listener) {
            // Show comic metadata
            tvComicTitle.setText(item.getName());
            tvComicDate.setText(item.getDate());
            tvComicSize.setText(item.getSize());
            tvComicFormat.setText(item.getFormat());

            ibtnComicRead.setImageDrawable(null);
            ibtnComicRead.setTag(item.getPath());

            // Load thumbnail depending on format
            switch (item.getFormat().toLowerCase(Locale.ROOT)) {
                case "cbz":
                    loadCbzThumbnailAsync(item.getPath(), ibtnComicRead);
                    break;
                case "cbr":
                    loadCbrThumbnailAsync(item.getPath(), ibtnComicRead);
                    break;
                case "pdf":
                    loadPdfThumbnailAsync(item.getPath(), ibtnComicRead);
                    break;
            }

            // Click = open comic reader
            ibtnComicRead.setOnClickListener(v -> listener.onComicClick(item));
        }

        // Thumbnail Cache Helper
        private void useCachedOrGenerate(String filePath, ImageView imageView, ThumbGenerator generator) {
            Bitmap cached = memoryCache.get(filePath);

            // Memory cache hit
            if (cached != null && !cached.isRecycled()) {
                if (filePath.equals(imageView.getTag())) imageView.setImageBitmap(cached);
                return;
            }

            // Disk cache hit
            File thumbFile = new File(imageView.getContext().getCacheDir(), "thumb_" + filePath.hashCode() + ".jpg");
            if (thumbFile.exists()) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (filePath.equals(imageView.getTag())) {
                        loadImageIntoView(imageView, filePath, Uri.fromFile(thumbFile));
                    }
                });
                return;
            }

            // Generate thumbnail in background
            sharedExecutor.execute(() -> {
                try {
                    generator.generate(thumbFile);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (filePath.equals(imageView.getTag())) {
                            loadImageIntoView(imageView, filePath, Uri.fromFile(thumbFile));
                        }
                    });
                } catch (Exception e) {
                    Log.e("ComicAdapter", "Thumbnail generation failed", e);
                }
            });
        }

        // CBZ thumbnail loader
        private void loadCbzThumbnailAsync(String filePath, ImageView imageView) {
            useCachedOrGenerate(filePath, imageView, thumbFile -> {
                Context ctx = imageView.getContext();
                String firstImageName = null;

                // First pass → find first image name
                try (InputStream is1 = ctx.getContentResolver().openInputStream(Uri.parse(filePath));
                     ZipInputStream zis = new ZipInputStream(is1)) {
                    List<String> imageNames = new ArrayList<>();
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName().matches("(?i).+\\.(jpg|jpeg|png|webp)$")) {
                            imageNames.add(entry.getName());
                        }
                    }
                    if (!imageNames.isEmpty()) {
                        imageNames.sort(String::compareTo);
                        firstImageName = imageNames.get(0);
                    }
                }

                // Second pass → extract and downscale
                if (firstImageName != null) {
                    try (InputStream is2 = ctx.getContentResolver().openInputStream(Uri.parse(filePath));
                         ZipInputStream zis2 = new ZipInputStream(is2)) {
                        ZipEntry entry;
                        while ((entry = zis2.getNextEntry()) != null) {
                            if (!entry.isDirectory() && entry.getName().equals(firstImageName)) {
                                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                byte[] tmp = new byte[8192];
                                int n;
                                while ((n = zis2.read(tmp)) != -1) {
                                    buffer.write(tmp, 0, n);
                                }
                                byte[] bytes = buffer.toByteArray();

                                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                if (bmp != null) {
                                    Bitmap scaled = Bitmap.createScaledBitmap(
                                            bmp, 400,
                                            (int) (400f / bmp.getWidth() * bmp.getHeight()), true
                                    );
                                    try (FileOutputStream fos = new FileOutputStream(thumbFile)) {
                                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                                    }
                                    bmp.recycle();
                                    scaled.recycle();
                                }
                                break;
                            }
                        }
                    }
                }
            });
        }

        // CBR thumbnail loader
        private void loadCbrThumbnailAsync(String filePath, ImageView imageView) {
            useCachedOrGenerate(filePath, imageView, thumbFile -> {
                Context ctx = imageView.getContext();
                Uri uri = Uri.parse(filePath);

                // Copy .cbr file to cache because junrar needs a File
                File tempFile = new File(ctx.getCacheDir(), "temp_" + filePath.hashCode() + ".cbr");
                try (InputStream input = ctx.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(tempFile)) {
                    if (input != null) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = input.read(buf)) != -1) {
                            output.write(buf, 0, len);
                        }
                    } else {
                        Log.e("ComicAdapter", "InputStream was null for: " + filePath);
                        return;
                    }
                }

                try (Archive archive = new Archive(tempFile)) {
                    List<com.github.junrar.rarfile.FileHeader> entries = new ArrayList<>();
                    for (com.github.junrar.rarfile.FileHeader fh : archive.getFileHeaders()) {
                        if (!fh.isDirectory() && fh.getFileName().matches("(?i).+\\.(jpg|jpeg|png|webp)$")) {
                            entries.add(fh);
                        }
                    }
                    entries.sort(Comparator.comparing(com.github.junrar.rarfile.FileHeader::getFileName));

                    if (!entries.isEmpty()) {
                        com.github.junrar.rarfile.FileHeader firstImageHeader = entries.get(0);
                        // Extract first image to temp file
                        File tempImg = new File(ctx.getCacheDir(), "cbr_img_" + filePath.hashCode() + ".jpg");
                        try (FileOutputStream fos = new FileOutputStream(tempImg)) {
                            archive.extractFile(firstImageHeader, fos);
                        }

                        // Decode + downscale
                        Bitmap bmp = BitmapFactory.decodeFile(tempImg.getPath());
                        if (bmp != null) {
                            Bitmap scaled = Bitmap.createScaledBitmap(
                                    bmp, 400,
                                    (int) (400f / bmp.getWidth() * bmp.getHeight()), true
                            );
                            try (FileOutputStream fos = new FileOutputStream(thumbFile)) {
                                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                            }
                            bmp.recycle();
                            scaled.recycle();
                        }
                        if (!tempImg.delete()) {
                            Log.w("ComicAdapter", "Failed to delete temp image: " + tempImg.getPath());
                        }
                    }
                }

                if (!tempFile.delete()) {
                    Log.w("ComicAdapter", "Failed to delete temp CBR: " + tempFile.getPath());
                }
            });
        }

        // PDF thumbnail loader
        private void loadPdfThumbnailAsync(String filePath, ImageView imageView) {
            useCachedOrGenerate(filePath, imageView, thumbFile -> {
                Context ctx = imageView.getContext();
                try (android.os.ParcelFileDescriptor pfd =
                             ctx.getContentResolver().openFileDescriptor(Uri.parse(filePath), "r")) {
                    if (pfd != null) {
                        try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                            PdfRenderer.Page page = renderer.openPage(0);
                            int targetWidth = 400;
                            int targetHeight = (int) (400f / page.getWidth() * page.getHeight());

                            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                            try (FileOutputStream fos = new FileOutputStream(thumbFile)) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                            }
                            bitmap.recycle();
                            page.close();
                        }
                    }
                }
            });
        }

        // Load image from disk cache into ImageView + memory cache
        private void loadImageIntoView(ImageView imageView, String cacheKey, Uri uri) {
            Context ctx = imageView.getContext();
            sharedExecutor.execute(() -> {
                try (InputStream input = ctx.getContentResolver().openInputStream(uri)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (cacheKey.equals(imageView.getTag())) {
                                memoryCache.put(cacheKey, bitmap);
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e("ComicAdapter", "Bitmap load failed", e);
                }
            });
        }
    }

    // Functional interface for generating thumbnails
    private interface ThumbGenerator {
        void generate(File thumbFile) throws Exception;
    }

    // Convert content:// or SAF Uri to a readable path string
    public static String getReadablePath(Context context, Uri uri) {
        try {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] parts = docId.split(":");
            if (parts.length == 2) {
                String volume = parts[0];
                String path = parts[1];
                if ("primary".equals(volume)) {
                    return "/storage/emulated/0/" + path;
                } else {
                    return "/storage/" + volume + "/" + path;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            DocumentFile file = DocumentFile.fromSingleUri(context, uri);
            if (file != null && file.getName() != null) return file.getName();
        } catch (Exception ignored) {}
        return uri.toString();
    }
}





