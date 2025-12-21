package com.codecademy.comicreader.view.sources;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;


/**
 * PDFPageSource - exposes PDF pages as Bitmaps
 */
public class PDFPageSource extends BitmapPageSource {

    private final ParcelFileDescriptor pfd;
    private final PdfRenderer renderer;

    public PDFPageSource(Context context, android.net.Uri uri) throws IOException {
        super(context);
        pfd = context.getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new IllegalArgumentException("Unable to open PDF Uri: " + uri);

        renderer = new PdfRenderer(pfd);
    }

    @Override
    public int getPageCount() {
        return renderer.getPageCount();
    }

    @Override
    public synchronized Bitmap getPageBitmap(int index) {
        Bitmap cached = getCached(index);
        if (cached != null) return cached;

        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(index);
            int width = page.getWidth();
            int height = page.getHeight();

            if (width <= 0 || height <= 0) {
                return createCorruptPlaceholder("Invalid page " + index);
            }

            int maxSize = 1280;
            float scale = Math.min(maxSize / (float) width, maxSize / (float) height);
            int scaledWidth = Math.max(1, (int) (width * scale));
            int scaledHeight = Math.max(1, (int) (height * scale));

            Bitmap bmp = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            cache(index, bmp);
            return bmp;

        } catch (Exception e) {
            Log.e("PDFPageSource", "Error rendering page " + index, e);
            return createCorruptPlaceholder("Error page " + index);
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void closeSource() {
        super.closeSource();
        try { renderer.close(); } catch (Exception ignored) {}
        try { pfd.close(); } catch (Exception ignored) {}
    }
}



