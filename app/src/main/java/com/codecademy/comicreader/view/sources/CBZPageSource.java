package com.codecademy.comicreader.view.sources;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import android.util.Log;


import com.codecademy.comicreader.utils.MappedFileInStream;

import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * CBZPageSource - reads ZIP/CBZ archives and exposes pages as Bitmap
 */
public class CBZPageSource extends BitmapPageSource {

    private final ParcelFileDescriptor pfd;
    private final MappedFileInStream inStream;
    private final IInArchive archive;
    private final List<Integer> imageIndices;

    public CBZPageSource(Context context, android.net.Uri uri) throws IOException, SevenZipNativeInitializationException {
        super(context);
        // Keep PFD alive
        pfd = context.getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new RuntimeException("Cannot open CBZ URI: " + uri);

        FileChannel channel = new FileInputStream(pfd.getFileDescriptor()).getChannel();
        inStream = new MappedFileInStream(channel);

        SevenZip.initSevenZipFromPlatformJAR();
        archive = SevenZip.openInArchive(null, inStream);

        // Build image indices
        List<Integer> indices = new ArrayList<>();
        int numItems = archive.getNumberOfItems();
        for (int i = 0; i < numItems; i++) {
            Boolean isFolder = (Boolean) archive.getProperty(i, PropID.IS_FOLDER);
            if (Boolean.TRUE.equals(isFolder)) continue;

            String path = String.valueOf(archive.getProperty(i, PropID.PATH)).toLowerCase();
            if (path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".png") || path.endsWith(".webp")) {
                indices.add(i);
            }
        }

        // Sort alphabetically by path
        indices.sort((a, b) -> {
            String pa;
            try {
                pa = String.valueOf(archive.getProperty(a, PropID.PATH));
            } catch (SevenZipException e) {
                throw new RuntimeException(e);
            }
            String pb;
            try {
                pb = String.valueOf(archive.getProperty(b, PropID.PATH));
            } catch (SevenZipException e) {
                throw new RuntimeException(e);
            }
            return pa.compareTo(pb);
        });

        imageIndices = Collections.unmodifiableList(indices);
    }

    @Override
    public int getPageCount() {
        return imageIndices.size();
    }

    @Override
    public synchronized Bitmap getPageBitmap(int index) {
        Bitmap cached = getCached(index);
        if (cached != null) return cached;

        if (index < 0 || index >= imageIndices.size()) {
            return createCorruptPlaceholder("Missing page " + index);
        }

        try {
            int itemIndex = imageIndices.get(index);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            archive.extractSlow(itemIndex, data -> {
                try {
                    baos.write(data);
                } catch (Exception e) {
                    Log.e("CBZPageSource", "Error writing bytes", e);
                }
                return data.length;
            });

            byte[] bytes = baos.toByteArray();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (bmp != null) {
                cache(index, bmp);
                return bmp;
            } else {
                return createCorruptPlaceholder("Corrupt page " + index);
            }

        } catch (Exception e) {
            Log.e("CBZPageSource", "Failed to decode page " + index, e);
            return createCorruptPlaceholder("Failed page " + index);
        }
    }

    @Override
    public void closeSource() {
        super.closeSource();
        try { archive.close(); } catch (Exception ignored) {}
        try { inStream.close(); } catch (Exception ignored) {}
        try { pfd.close(); } catch (Exception ignored) {}
    }
}



