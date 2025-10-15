package com.codecademy.comicreader.ui.comic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;



public class ComicViewModel extends ViewModel {

    private final MutableLiveData<String> noComicsMessage;
    private final MutableLiveData<String> addOnLibraryMessage;
    private final MutableLiveData<String> noComicsFolderMessage;

    public ComicViewModel() {
        noComicsMessage = new MutableLiveData<>("No comics found");
        noComicsFolderMessage = new MutableLiveData<>("No comic folder found");
        addOnLibraryMessage = new MutableLiveData<>("Add on Library");
    }

    public LiveData<String> getNoComicsMessage() {
        return noComicsMessage;
    }

    public LiveData<String> getAddOnLibraryMessage() {
        return addOnLibraryMessage;
    }

    public LiveData<String> getNoComicFolderMessage(){
        return noComicsFolderMessage;
    }
}
