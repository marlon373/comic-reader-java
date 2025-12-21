package com.codecademy.comicreader.ui.comic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.widget.ImageView;

import com.codecademy.comicreader.utils.MappedFileInStream;
import com.codecademy.comicreader.utils.SystemUtil;

import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ThumbnailManager - Java version with cancellable jobs (Future)
 * Supports PDF / CBZ / CBR thumbnails
 * Uses memory cache + disk cache
 * Uses ExecutorService for background tasks
 */
public final class ThumbnailManager {

    // Memory cache
    private static final LruCache<String, Bitmap> memoryCache =
            new LruCache<>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8));

    // Disk cleanup guard
    private static boolean cleanupDone = false;

    //  ExecutorService + Handler
    //  Dedicated thread pool for thumbnails, prevents UI lag.
    private static ExecutorService executor;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ThumbnailManager() {}


    private static synchronized void init() {
        if (executor == null) {
            executor = SystemUtil.createExecutor();
        }
    }

    // Disk path for thumbnail cache
    private static File getDiskThumbnail(Context context, Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) name = "thumb";
        else if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        return new File(context.getCacheDir(), name + "_thumb.jpg");
    }

    //  Public API: load thumbnail + return Cancellable Future
    public static Future<?> loadThumbnailAsync(
            Context context,
            Uri uri,
            String type,
            ImageView imageView,
            Integer placeholderRes,
            int maxAgeDays
    ) {
        init();

        final String key = uri.toString();
        imageView.setTag(key);

        if (!cleanupDone) {
            cleanupOldThumbnails(context.getCacheDir(), maxAgeDays);
        }

        File thumbFile = getDiskThumbnail(context, uri);

        // Show memory cache or placeholder immediately to avoid flicker
        Bitmap cached = memoryCache.get(key);
        if (cached != null) {
            imageView.setImageBitmap(cached);
        } else if (placeholderRes != null) {
            imageView.setImageResource(placeholderRes);
        }

        // Submit task
        return executor.submit(() -> {
            try {
                // Check cancellation
                if (Thread.currentThread().isInterrupted()) return;

                // Disk cache
                if (thumbFile.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
                    if (bmp != null) {
                        memoryCache.put(key, bmp);
                        mainHandler.post(() -> {
                            if (key.equals(imageView.getTag())) {
                                imageView.setImageBitmap(bmp);
                            }
                        });
                    }
                    return;
                }

                if (Thread.currentThread().isInterrupted()) return;

                // Generate thumbnail
                Bitmap bmp;
                String lower = type.toLowerCase(Locale.US);

                bmp = switch (lower) {
                    case "pdf" -> loadPdfThumbnail(context, uri);
                    case "cbz" -> loadCbzThumbnail(context, uri);
                    case "cbr" -> loadCbrThumbnail(context, uri);
                    default -> null;
                };

                if (bmp == null) return;

                if (Thread.currentThread().isInterrupted()) return;

                // Save to disk safely
                try (FileOutputStream fos = new FileOutputStream(thumbFile)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                } catch (Exception ignored) {}

                memoryCache.put(key, bmp);

                // Update ImageView if still valid
                Bitmap finalBmp = bmp;
                mainHandler.post(() -> {
                    if (key.equals(imageView.getTag())) {
                        imageView.setAlpha(0f);
                        imageView.setImageBitmap(finalBmp);
                        imageView.animate().alpha(1f).setDuration(200).start();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Cleanup old thumbnails
    private static void cleanupOldThumbnails(File cacheDir, int maxAgeDays) {
        if (cleanupDone || cacheDir == null || !cacheDir.exists()) return;

        long now = System.currentTimeMillis();
        long limit = maxAgeDays * 24L * 60L * 60L * 1000L;

        File[] files = cacheDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.getName().endsWith("_thumb.jpg")) {
                if (now - file.lastModified() > limit) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        System.err.println("Failed to delete old thumbnail: " + file.getAbsolutePath());
                    }
                }
            }
        }
        cleanupDone = true;
    }

    // PDF thumbnail (SAFE RENDER)
    private static Bitmap loadPdfThumbnail(Context context, Uri uri) {
        try (ParcelFileDescriptor pfd =
                     context.getContentResolver().openFileDescriptor(uri, "r")) {

            if (pfd == null) return null;

            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                try (PdfRenderer.Page page = renderer.openPage(0)) {

                    int width = 400;
                    int height = (int) (400f / page.getWidth() * page.getHeight());

                    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return bmp;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // CBZ (ZIP) streaming thumbnail (NO TEMP FILES)
    private static Bitmap loadCbzThumbnail(Context context, Uri uri) {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(
                        context.getContentResolver().openInputStream(uri)))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (Thread.currentThread().isInterrupted()) return null;

                if (!entry.isDirectory() &&
                        entry.getName().matches("(?i).+\\.(jpg|jpeg|png|webp)$")) {

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 3;

                    return BitmapFactory.decodeStream(zis, null, opts);
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // CBR (RAR) Streaming thumbnail - SEVENZIPJBINDING
    private static Bitmap loadCbrThumbnail(Context context, Uri uri) {
        try (ParcelFileDescriptor pfd =
                     context.getContentResolver().openFileDescriptor(uri, "r")) {

            if (pfd == null) return null;

            try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                 FileChannel channel = fis.getChannel()) {

                MappedFileInStream inStream = new MappedFileInStream(channel);

                SevenZip.initSevenZipFromPlatformJAR();
                try (IInArchive archive = SevenZip.openInArchive(null, inStream)) {

                    int selectedIndex = -1;
                    String bestPath = null;

                    for (int i = 0; i < archive.getNumberOfItems(); i++) {
                        if (Thread.currentThread().isInterrupted()) return null;

                        Boolean isFolder = (Boolean) archive.getProperty(i, PropID.IS_FOLDER);
                        if (Boolean.TRUE.equals(isFolder)) continue;

                        String path = String.valueOf(archive.getProperty(i, PropID.PATH))
                                .toLowerCase(Locale.US);

                        if (path.endsWith(".jpg") || path.endsWith(".jpeg")
                                || path.endsWith(".png") || path.endsWith(".webp")) {

                            if (bestPath == null || path.compareTo(bestPath) < 0) {
                                bestPath = path;
                                selectedIndex = i;
                            }
                        }
                    }

                    if (selectedIndex == -1) return null;

                    ByteArrayOutputStream baos = new ByteArrayOutputStream(2 * 1024 * 1024);

                    archive.extractSlow(selectedIndex, data -> {
                        if (Thread.currentThread().isInterrupted()) return 0;
                        baos.write(data, 0, data.length);
                        return data.length;
                    });

                    byte[] bytes = baos.toByteArray();

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 3;

                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                } finally {
                    inStream.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
