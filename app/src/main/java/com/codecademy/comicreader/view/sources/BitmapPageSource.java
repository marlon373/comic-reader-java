package com.codecademy.comicreader.view.sources;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.LruCache;

public abstract class BitmapPageSource implements ComicPageSource {

    protected final LruCache<Integer, Bitmap> bitmapCache = new LruCache<>(5); // Tune size per device

    protected void cache(int index, Bitmap bmp) {
        if (bmp != null && !bmp.isRecycled()) {
            bitmapCache.put(index, bmp);
        }
    }

    protected Bitmap getCached(int index) {
        Bitmap bmp = bitmapCache.get(index);
        return (bmp != null && !bmp.isRecycled()) ? bmp : null;
    }

    public void clear() {
        bitmapCache.evictAll();
    }

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
}


