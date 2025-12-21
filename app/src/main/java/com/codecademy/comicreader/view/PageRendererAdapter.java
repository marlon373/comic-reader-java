
package com.codecademy.comicreader.view;


import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.codecademy.comicreader.view.sources.BitmapPageSource;
import com.codecademy.comicreader.view.sources.ComicPageSource;
import com.github.chrisbanes.photoview.PhotoView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


/**
 * Adapter for rendering pages from a ComicPageSource or BitmapPageSource
 */
public class PageRendererAdapter extends RecyclerView.Adapter<PageRendererAdapter.PageViewHolder> {

    private final ComicPageSource pageSource;
    private final ExecutorService executor;

    private final Map<Integer, WeakReference<PageViewHolder>> holderRefs = new ConcurrentHashMap<>();
    private final Map<Integer, Future<?>> futures = new ConcurrentHashMap<>();

    public PageRendererAdapter(ComicPageSource pageSource, ExecutorService executor) {
        this.pageSource = pageSource;
        this.executor = executor;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(parent.getContext());
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return new PageViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        FrameLayout container = (FrameLayout) holder.itemView;
        container.removeAllViews();
        container.addView(holder.photoView);
        container.addView(holder.progressBar);

        holder.progressBar.setVisibility(View.VISIBLE);
        holder.photoView.setImageBitmap(null);

        holderRefs.put(position, new WeakReference<>(holder));

        // Cancel previous decode
        pageSource.cancelLoad(position);
        Future<?> previous = futures.remove(position);
        if (previous != null) previous.cancel(true);

        // Async loading if BitmapPageSource
        Future<?> future;// Update UI on main thread
        if (pageSource instanceof BitmapPageSource) {
            future = pageSource.loadPageAsync(position, bitmap -> {
                WeakReference<PageViewHolder> ref = holderRefs.get(position);
                if (ref == null) return;
                PageViewHolder vh = ref.get();
                if (vh == null) return;
                if (vh.getBindingAdapterPosition() != position) return;

                vh.progressBar.setVisibility(View.GONE);
                vh.photoView.setImageBitmap(bitmap);
            });
        } else {
            // Fallback: synchronous loading in executor
            future = executor.submit(() -> {
                Bitmap bmp;
                try {
                    bmp = pageSource.getPageBitmap(position);
                } catch (Exception e) {
                    bmp = null;
                }

                // Update UI on main thread
                Bitmap finalBmp = bmp;
                holder.photoView.post(() -> {
                    if (holder.getBindingAdapterPosition() != position) return;
                    holder.progressBar.setVisibility(View.GONE);
                    holder.photoView.setImageBitmap(finalBmp);
                });
            });
        }
        futures.put(position, future);
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        int pos = holder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            holderRefs.remove(pos);

            pageSource.cancelLoad(pos);
            Future<?> f = futures.remove(pos);
            if (f != null) f.cancel(true);
        }

        holder.photoView.setImageDrawable(null);
        holder.photoView.setScale(1f, false);

        super.onViewRecycled(holder);
    }

    public void resetZoomAt(int position) {
        WeakReference<PageViewHolder> ref = holderRefs.get(position);
        if (ref != null) {
            PageViewHolder vh = ref.get();
            if (vh != null) vh.photoView.setScale(1f, true);
        }
    }

    public void shutdown() {
        for (Future<?> f : futures.values()) {
            f.cancel(true);
        }
        futures.clear();
        holderRefs.clear();
        pageSource.closeSource();
    }

    @Override
    public int getItemCount() {
        return pageSource.getPageCount();
    }

    public static class PageViewHolder extends RecyclerView.ViewHolder {
        public final PhotoView photoView;
        public final ProgressBar progressBar;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            FrameLayout container = (FrameLayout) itemView;

            photoView = new PhotoView(container.getContext());
            photoView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            progressBar = new ProgressBar(container.getContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
            );
            progressBar.setLayoutParams(params);

            container.addView(photoView);
            container.addView(progressBar);
        }
    }
}

