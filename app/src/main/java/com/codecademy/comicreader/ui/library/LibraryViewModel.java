package com.codecademy.comicreader.ui.library;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import com.codecademy.comicreader.model.Folder;

public class LibraryViewModel extends ViewModel {

    private final MutableLiveData<String> addFolderLibrary;
    private final MutableLiveData<Boolean> folderAdded = new MutableLiveData<>(false);
    private final MutableLiveData<List<Folder>> foldersLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> folderRemoved = new MutableLiveData<>(false);

    public LibraryViewModel() {
        addFolderLibrary = new MutableLiveData<>();
        addFolderLibrary.setValue("Use the “ +” button to add the folder\n" +
                "   containing the .cbz or cbr files");
        foldersLiveData.setValue(new ArrayList<>());
    }

    public LiveData<String> getAddFolderLibrary() {
        return addFolderLibrary;
    }

    public LiveData<List<Folder>> getFolders() {
        return foldersLiveData;
    }

    public void setFolders(List<Folder> folders) {
        foldersLiveData.postValue(new ArrayList<>(folders)); // Ensure LiveData updates UI
    }

    public LiveData<Boolean> getFolderAdded() {
        return folderAdded;
    }

    public void notifyFolderAdded() {
        folderAdded.setValue(true); //  Notifies ComicFragment
    }

    public void resetFolderAddedFlag() {
        folderAdded.setValue(false); // Prevents repeated triggers
    }

    public void notifyFolderRemoved() {
        folderRemoved.setValue(true);
    }

    public void notifyFolderRemovedHandled() {
        folderRemoved.setValue(false);
    }

    public LiveData<Boolean> getFolderRemoved() {
        return folderRemoved;
    }




}