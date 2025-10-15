package com.codecademy.comicreader.view.sources;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;


public class PDFPageSource extends BitmapPageSource {

    private final PdfRenderer renderer;

    public PDFPageSource(Context context, Uri uri) {
        try {
            ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (fd == null) {
                throw new IllegalArgumentException("Unable to open file descriptor for: " + uri);
            }
            this.renderer = new PdfRenderer(fd);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open PDF Uri", e);
        }
    }

    @Override
    public int getPageCount() {
        return renderer.getPageCount();
    }

    @Override
    public synchronized Bitmap getPageBitmap(int index) {
        Bitmap cached = getCached(index);
        if (cached != null) return cached;

        try (PdfRenderer.Page page = renderer.openPage(index)) {
            int width = page.getWidth();
            int height = page.getHeight();
            if (width <= 0 || height <= 0) {
                return createCorruptPlaceholder("Invalid page " + index);
            }

            int maxSize = 1280;
            float scale = Math.min((float) maxSize / width, (float) maxSize / height);
            int scaledWidth = Math.max(1, Math.round(width * scale));
            int scaledHeight = Math.max(1, Math.round(height * scale));

            Bitmap bmp = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            cache(index, bmp);
            return bmp;
        } catch (Exception e) {
            Log.e("PDFPageSource", "Error rendering page " + index, e);
            return createCorruptPlaceholder("Error page " + index);
        }
    }

    public void close() {
        renderer.close();
    }
}



