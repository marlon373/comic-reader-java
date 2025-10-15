package com.codecademy.comicreader.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.codecademy.comicreader.model.Folder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FolderUtils {

    private static final String PREFS_NAME = "folders";
    private static final String KEY_FOLDER_LIST = "folder_list";

    private static final Gson gson = new Gson();
    private static final Type folderListType = new TypeToken<List<Folder>>() {}.getType();

    // Save folders
    public static void saveFolders(Context context, List<Folder> folders) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = gson.toJson(folders);
        prefs.edit().putString(KEY_FOLDER_LIST, json).apply();
    }

    // Load folders
    public static List<Folder> loadFolders(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_FOLDER_LIST, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            return gson.fromJson(json, folderListType);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
