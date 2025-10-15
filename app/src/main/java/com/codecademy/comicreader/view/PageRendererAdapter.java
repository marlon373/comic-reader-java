
package com.codecademy.comicreader.view;


import android.content.Context;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;


public class PageRendererAdapter extends RecyclerView.Adapter<PageRendererAdapter.PageViewHolder> {

    private final ComicPageSource pageSource;
    private final Map<Integer, WeakReference<PageViewHolder>> holderRefs = new HashMap<>();
    private final ExecutorService executor;

    public PageRendererAdapter(Context context, ComicPageSource source, ExecutorService sharedExecutor) {
        this.pageSource = source;
        this.executor = sharedExecutor;
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
        FrameLayout parent = (FrameLayout) holder.itemView;
        parent.removeAllViews();
        parent.addView(holder.photoView);
        parent.addView(holder.progressBar);

        holder.progressBar.setVisibility(View.VISIBLE);
        holder.photoView.setImageBitmap(null);

        holderRefs.put(position, new WeakReference<>(holder));

        executor.execute(() -> {
            Bitmap bmp = pageSource.getPageBitmap(position);

            holder.photoView.post(() -> {
                if (holder.getBindingAdapterPosition() != position) return;
                holder.progressBar.setVisibility(View.GONE);
                holder.photoView.setImageBitmap(bmp);
            });
        });
    }

    @Override
    public int getItemCount() {
        return pageSource.getPageCount();
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        super.onViewRecycled(holder);
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            holderRefs.remove(position);
        }
        holder.photoView.setImageDrawable(null);
    }

    public void resetZoomAt(int position) {
        WeakReference<PageViewHolder> ref = holderRefs.get(position);
        if (ref != null) {
            PageViewHolder holder = ref.get();
            if (holder != null) {
                holder.photoView.setScale(1f, true);
            }
        }
    }

    public void shutdownExecutor() {

        // Clear cached Bitmaps if using BitmapPageSource
        if (pageSource instanceof BitmapPageSource) {
            ((BitmapPageSource) pageSource).clear();
        }
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
            FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            pbParams.gravity = Gravity.CENTER;
            progressBar.setLayoutParams(pbParams);
            progressBar.setIndeterminate(true);

            container.addView(photoView);
            container.addView(progressBar);
        }
    }
}

