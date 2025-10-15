package com.codecademy.comicreader.ui.recent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.codecademy.comicreader.model.Comic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RecentViewModel extends ViewModel {

    private final MutableLiveData<List<Comic>> recentComics = new MutableLiveData<>(new ArrayList<>());

    public RecentViewModel() {
    }
    public LiveData<List<Comic>> getRecentComics() {
        return recentComics;
    }

    public void addComicToRecent(Comic comic) {
        List<Comic> currentList = new ArrayList<>(Objects.requireNonNull(recentComics.getValue()));
        currentList.removeIf(c -> c.getPath().equals(comic.getPath())); // Avoid duplicates
        currentList.add(0, comic); // Add to top

        // Limit recent list to 20
        if (currentList.size() > 20) {
            currentList = currentList.subList(0, 20);
        }

        recentComics.setValue(currentList);
    }

    public void setRecentComics(List<Comic> comics) {
        recentComics.setValue(comics);
    }

}