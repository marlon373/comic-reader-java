package com.codecademy.comicreader.view.sources;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


public class CBRPageSource extends BitmapPageSource {

    private final File tempFile;
    private final Archive archive;
    private final List<FileHeader> imageHeaders;

    public CBRPageSource(Context context, Uri uri) {
        this.tempFile = saveTempFile(context, uri);
        try {
            this.archive = new Archive(tempFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open .cbr archive", e);
        }
        this.imageHeaders = archive.getFileHeaders().stream()
                .filter(h -> !h.isDirectory() &&
                        h.getFileName().toLowerCase(Locale.getDefault())
                                .matches(".*\\.(jpg|jpeg|png|webp)$"))
                .sorted(Comparator.comparing(FileHeader::getFileName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private File saveTempFile(Context context, Uri uri) {
        try {
            File temp = File.createTempFile("comic_", ".cbr", context.getCacheDir());
            InputStream in = context.getContentResolver().openInputStream(uri);
            try (in; OutputStream out = new FileOutputStream(temp)) {
                if (in == null) {
                    throw new IOException("Failed to open input stream for " + uri);
                }
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            return temp;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save temp CBR file", e);
        }
    }

    @Override
    public int getPageCount() {
        return imageHeaders.size();
    }

    @Override
    public synchronized Bitmap getPageBitmap(int index) {
        Bitmap cached = getCached(index);
        if (cached != null) return cached;
        if (index < 0 || index >= imageHeaders.size()) return null;

        try {
            FileHeader header = imageHeaders.get(index);
            InputStream in = archive.getInputStream(header);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, options);
            if (bmp == null) bmp = createCorruptPlaceholder("Corrupt page " + index);
            cache(index, bmp);
            return bmp;
        } catch (Exception e) {
            Log.e("CBRPageSource", "Failed to decode page " + index, e);
            return createCorruptPlaceholder("Failed page " + index);
        }
    }

    public void close() {
        try {
            archive.close();
        } catch (IOException e) {
            Log.e("CBRPageSource", "Failed to close archive", e);
        }
        if (tempFile.exists() && !tempFile.delete()) {
            Log.w("CBRPageSource", "Failed to delete temp file: " + tempFile.getAbsolutePath());
        }
    }
}



