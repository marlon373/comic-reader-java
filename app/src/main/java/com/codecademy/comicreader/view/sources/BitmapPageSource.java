package com.codecademy.comicreader.view.sources;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import com.codecademy.comicreader.utils.SystemUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class BitmapPageSource implements ComicPageSource {

    // Small in-memory page cache
    protected final LruCache<Integer, Bitmap> bitmapCache = new LruCache<>(5);

    /**
     * IO executor = equivalent of Dispatchers.IO
     * DO NOT interrupt threads → prevents ClosedByInterruptException
     */
    private final ExecutorService executor;

    /**
     * Track running tasks (equivalent to Job map in Kotlin)
     */
    private final Map<Integer, Future<?>> jobs = new ConcurrentHashMap<>();

    /**
     * Main thread handler (equivalent to Dispatchers.Main)
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    protected BitmapPageSource(Context context) {
        this.executor = SystemUtil.createIOExecutor(context);
    }

    // Cache helpers
    protected void cache(int index, Bitmap bmp) {
        if (bmp != null && !bmp.isRecycled()) {
            bitmapCache.put(index, bmp);
        }
    }

    protected Bitmap getCached(int index) {
        Bitmap bmp = bitmapCache.get(index);
        return (bmp != null && !bmp.isRecycled()) ? bmp : null;
    }

    // Cancel
    @Override
    public void cancelLoad(int index) {
        Future<?> future = jobs.remove(index);
        if (future != null) {
            future.cancel(false);
        }
    }

    // Async page load
    @Override
    public Future<?> loadPageAsync(int index, PageCallback callback) {
        // Cancel any previous task
        cancelLoad(index);

        Future<?> future = executor.submit(() -> {
            try {
                // Check cache first
                Bitmap cached = getCached(index);
                if (cached != null) {
                    postResult(callback, cached);
                    return;
                }

                // Load page bitmap
                Bitmap bmp;
                try {
                    bmp = getPageBitmap(index);
                } catch (Throwable t) {
                    Log.e("BitmapPageSource", "Error decoding page " + index, t);
                    bmp = null;
                }

                if (bmp != null) cache(index, bmp);


                postResult(callback, bmp);

            } finally {
                jobs.remove(index);
            }
        });

        jobs.put(index, future);
        return future;
    }

    private void postResult(PageCallback callback, Bitmap bmp) {
        mainHandler.post(() -> callback.onPageLoaded(bmp));
    }


    // Close
    @Override
    public void closeSource() {
        for (Future<?> f : jobs.values()) {
            f.cancel(false); // cooperative
        }
        jobs.clear();

        bitmapCache.evictAll();
        executor.shutdown();
    }

    // Abstract
    @Override
    public abstract Bitmap getPageBitmap(int index);

    // Shared corrupt placeholder
    protected Bitmap createCorruptPlaceholder(String msg) {
        int width = 800;
        int height = 1200;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.DKGRAY);

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(40f);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText(msg, width / 2f, height / 2f, paint);
        return bmp;
    }

    // Page callback interface
    public interface PageCallback {
        void onPageLoaded(Bitmap bitmap);
    }
}



