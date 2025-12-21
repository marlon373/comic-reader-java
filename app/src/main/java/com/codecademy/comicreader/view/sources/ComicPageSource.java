package com.codecademy.comicreader.view.sources;

import android.graphics.Bitmap;

import java.util.concurrent.Future;

/**
 * ComicPageSource - defines interface for page sources (PDF/CBZ/CBR/etc.)
 */
public interface ComicPageSource {


    // Returns the total number of pages in the source.
    int getPageCount();

    // Synchronously returns the Bitmap for a given page index.
    Bitmap getPageBitmap(int index);

    /**
     * Asynchronously loads a page Bitmap.
     * @param index Page index
     * @param callback Called with the loaded Bitmap (can be null if failed)
     * @return Future<?> representing the async task, can be cancelled
     */
    Future<?> loadPageAsync(int index, BitmapPageSource.PageCallback callback);

    /**
     * Cancels loading of a specific page. Optional implementation.
     * @param index Page index to cancel
     */
    default void cancelLoad(int index) {
        // default no-op
    }

    // Closes the page source, releasing resources. Optional implementation.
    default void closeSource() {
        // default no-op
    }
}


