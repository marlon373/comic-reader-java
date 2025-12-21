package com.codecademy.comicreader.ui.library;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import com.codecademy.comicreader.model.Folder;

public class LibraryViewModel extends ViewModel {

    private static final String KEY_FOLDER_STACK = "folder_stack";
    private static final String KEY_IN_NAVIGATION = "in_folder_navigation";

    private final SavedStateHandle savedStateHandle;

    private final MutableLiveData<String> addFolderLibrary = new MutableLiveData<>();
    private final MutableLiveData<List<Folder>> foldersLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> folderAdded = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> folderRemoved = new MutableLiveData<>(false);

    public LibraryViewModel(SavedStateHandle handle) {
        this.savedStateHandle = handle;

        addFolderLibrary.setValue(
                "Use the “+” button to add the folder\ncontaining the .cbz or .cbr files"
        );

        foldersLiveData.setValue(new ArrayList<>());
    }

    // ---------- UI DATA ----------

    public LiveData<String> getAddFolderLibrary() {
        return addFolderLibrary;
    }

    public LiveData<List<Folder>> getFolders() {
        return foldersLiveData;
    }

    public void setFolders(List<Folder> folders) {
        foldersLiveData.setValue(new ArrayList<>(folders));
    }

    // ---------- EVENTS ----------

    public LiveData<Boolean> getFolderAdded() {
        return folderAdded;
    }

    public LiveData<Boolean> getFolderRemoved() {
        return folderRemoved;
    }

    public void notifyFolderAdded() {
        folderAdded.setValue(true);
    }

    public void notifyFolderRemoved() {
        folderRemoved.setValue(true);
    }

    public void resetFolderAdded() {
        folderAdded.setValue(false);
    }

    public void resetFolderRemoved() {
        folderRemoved.setValue(false);
    }

    // ---------- NAVIGATION STATE ----------

    public void saveFolderStack(List<String> stack) {
        savedStateHandle.set(KEY_FOLDER_STACK, stack);
    }

    public List<String> restoreFolderStack() {
        List<String> stack = savedStateHandle.get(KEY_FOLDER_STACK);
        return stack != null ? stack : new ArrayList<>();
    }

    public void setInFolderNavigation(boolean value) {
        savedStateHandle.set(KEY_IN_NAVIGATION, value);
    }

    public boolean isInFolderNavigation() {
        Boolean value = savedStateHandle.get(KEY_IN_NAVIGATION);
        return value != null && value;
    }
}
