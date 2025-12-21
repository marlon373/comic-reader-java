package com.codecademy.comicreader.utils;


import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class SystemUtil {

    /**
     * Returns total device RAM in GB.
     * @param context Application or Activity context
     * @return Total RAM rounded down to GB
     */
    public static int getRamInGB(Context context) {
        ActivityManager am =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);

        // Convert bytes → GB
        return (int) (info.totalMem / (1024L * 1024L * 1024L));
    }

    /**
     * Determines an optimal IO thread count based on:
     * - CPU cores
     * - Total RAM
     * - ARM64 support
     * This is optimized for IO-heavy tasks:
     * - CBR/CBZ extraction
     * - Image decoding
     * - Disk caching
     */
    public static int getRecommendedIOThreadCount(Context context) {
        int cores = Runtime.getRuntime().availableProcessors();
        int ram = getRamInGB(context);

        boolean isArm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.contains("arm64")) {
                isArm64 = true;
                break;
            }
        }

        // Ultra high-end devices
        if (cores >= 8 && ram >= 16) return 6;
        if (cores >= 8 && ram >= 8) return 5;

        // High-end devices
        if (isArm64 && ram >= 6) return 4;
        if (isArm64 && ram >= 4) return 3;

        // Mid-range / low-end
        if (cores <= 2 || ram <= 2) return 1;
        if (cores <= 4 || ram == 3) return 2;

        // Safe default
        return 3;
    }

    /**
     * Returns a conservative thread count for
     * CPU-light background tasks.
     * Limits max threads to 3 to avoid:
     * - UI jank
     * - GC pressure
     * - Battery drain
     */
    public static int getThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(cores / 2, 3));
    }

    /**
     * Creates an ExecutorService optimized for IO work.
     * Use this for:
     * - Archive extraction
     * - Image decoding
     * - File reads/writes
     */
    public static ExecutorService createIOExecutor(Context context) {
        int threads = getRecommendedIOThreadCount(context);
        return Executors.newFixedThreadPool(threads);
    }

    /**
     * Creates a general-purpose ExecutorService.
     * Use this for:
     * - Lightweight background tasks
     * - Non-blocking operations
     */
    public static ExecutorService createExecutor() {
        int threads = getThreadCount();
        return Executors.newFixedThreadPool(threads);
    }
}