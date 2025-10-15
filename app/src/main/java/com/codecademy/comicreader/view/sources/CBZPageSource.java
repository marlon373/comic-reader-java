package com.codecademy.comicreader.view.sources;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CBZPageSource extends BitmapPageSource {

    private final Context context;
    private final Uri uri;
    private final Map<Integer, String> imageMap = new HashMap<>();

    public CBZPageSource(Context context, Uri uri) {
        this.context = context;
        this.uri = uri;
        preloadImageList();
    }

    private void preloadImageList() {
        List<String> imageNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(context.getContentResolver().openInputStream(uri))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.getDefault());
                if (!entry.isDirectory() && name.matches(".*\\.(jpg|jpeg|png|webp)$")) {
                    imageNames.add(entry.getName());
                }
            }
        } catch (Exception e) {
            Log.e("CBZPageSource", "Error indexing zip", e);
        }
        imageNames.sort(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < imageNames.size(); i++) {
            imageMap.put(i, imageNames.get(i));
        }
    }

    @Override
    public int getPageCount() {
        return imageMap.size();
    }

    @Override
    public synchronized Bitmap getPageBitmap(int index) {
        Bitmap cached = getCached(index);
        if (cached != null) return cached;

        String target = imageMap.get(index);
        if (target == null) return null;

        try (ZipInputStream zis = new ZipInputStream(context.getContentResolver().openInputStream(uri))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(target)) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap bmp = BitmapFactory.decodeStream(zis, null, options);
                    if (bmp == null) bmp = createCorruptPlaceholder("Corrupt page " + index);
                    cache(index, bmp);
                    return bmp;
                }
            }
        } catch (Exception e) {
            Log.e("CBZPageSource", "Error reading image: " + target, e);
        }
        return createCorruptPlaceholder("Error page " + index);
    }
}



