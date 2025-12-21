package com.codecademy.comicreader.utils;

import net.sf.sevenzipjbinding.IInStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * IInStream implementation backed by a memory-mapped FileChannel.
 * Advantages:
 * - Zero-copy file access
 * - Very fast for large archives (CBR / CBZ / 7z)
 * - Low heap usage (uses direct buffers)
 * Note:
 * Android does not support unmapping MappedByteBuffer explicitly.
 * Keep mappings small to avoid file-lock issues.
 */
public final class MappedFileInStream implements IInStream {

    // File channel backing this stream
    private final FileChannel channel;
    // Current read position
    private long position = 0L;

    public MappedFileInStream(FileChannel channel) {
        this.channel = channel;
    }

    /**
     * Reads bytes into the provided buffer.
     *
     * @param buffer destination buffer
     * @return number of bytes read, or 0 if EOF
     */
    @Override
    public int read(byte[] buffer) {
        try {
            int size = buffer.length;

            long channelSize = channel.size();
            long remaining = channelSize - position;

            if (remaining <= 0) {
                return 0; // EOF
            }

            int toRead = (int) Math.min(size, remaining);

            ByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    position,
                    toRead
            );

            mapped.get(buffer, 0, toRead);
            position += toRead;

            return toRead;

        } catch (IOException e) {
            // MUST NOT throw IOException here
            throw new RuntimeException("MappedFileInStream read failed", e);
        }
    }

    /**
     * Moves the current read position.
     *
     * @param offset byte offset
     * @param origin SEEK_SET, SEEK_CUR, or SEEK_END
     * @return new position
     */
    @Override
    public long seek(long offset, int origin) {
        try {
            switch (origin) {
                case IInStream.SEEK_SET:
                    position = offset;
                    break;

                case IInStream.SEEK_CUR:
                    position += offset;
                    break;

                case IInStream.SEEK_END:
                    position = channel.size() + offset;
                    break;
            }
            return position;

        } catch (IOException e) {
            throw new RuntimeException("MappedFileInStream seek failed", e);
        }
    }

    /**
     * Closes the underlying FileChannel.
     */
    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}


