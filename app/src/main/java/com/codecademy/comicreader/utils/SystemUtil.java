package com.codecademy.comicreader.utils;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SystemUtil {

    public static int getRamInGB(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return (int) (memoryInfo.totalMem / (1024L * 1024L * 1024L)); // bytes to GB
    }

    public static ExecutorService createSmartExecutor(Context context) {
        int cores = Runtime.getRuntime().availableProcessors();
        int ram = getRamInGB(context);

        int recommendedThreads;
        if (cores <= 2 || ram <= 2) {
            recommendedThreads = 1;
        } else if (cores <= 4 || ram == 3) {
            recommendedThreads = 2;
        } else {
            recommendedThreads = 3;
        }

        return getThreadPoolExecutor(recommendedThreads);
    }

    @NonNull
    private static ThreadPoolExecutor getThreadPoolExecutor(int recommendedThreads) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                recommendedThreads,
                recommendedThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("SmartExecutor-" + t.getId());
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

}