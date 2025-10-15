package com.codecademy.comicreader.view.sources;

import android.graphics.Bitmap;

public interface ComicPageSource {
    int getPageCount();
    Bitmap getPageBitmap(int index);
}


