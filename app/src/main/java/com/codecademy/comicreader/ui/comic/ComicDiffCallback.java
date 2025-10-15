package com.codecademy.comicreader.ui.comic;

import androidx.recyclerview.widget.DiffUtil;

import com.codecademy.comicreader.model.Comic;

import java.util.List;

public class ComicDiffCallback extends DiffUtil.Callback {

    private final List<Comic> oldList;
    private final List<Comic> newList;

    public ComicDiffCallback(List<Comic> oldList, List<Comic> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).getPath().equals(newList.get(newItemPosition).getPath());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
    }
}

