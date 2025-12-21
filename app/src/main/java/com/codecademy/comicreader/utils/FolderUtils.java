package com.codecademy.comicreader.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.codecademy.comicreader.model.Folder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for saving and loading Folder objects
 * using SharedPreferences + Gson.
 *
 * This is the Java equivalent of the Kotlin `object FolderUtils`.
 */
public class FolderUtils {

    // SharedPreferences file name
    private static final String PREFS_NAME = "folders";
    // Key used to store the folder list JSON
    private static final String KEY_FOLDER_LIST = "folder_list";

    // Gson instance for JSON serialization
    private static final Gson gson = new Gson();
    /**
     * Type token for:
     * MutableList<Folder>
     *
     * Required because of Java type erasure.
     */
    private static final Type FOLDER_LIST_TYPE = new TypeToken<List<Folder>>() {}.getType();

    /**
     * Saves a list of folders into SharedPreferences as JSON.
     *
     * @param context Android context
     * @param folders Mutable list of Folder objects
     */
    public static void saveFolders(Context context, List<Folder> folders) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Convert folder list to JSON
        String json = gson.toJson(folders);
        // Save JSON string
        prefs.edit().putString(KEY_FOLDER_LIST, json).apply();
    }

    /**
     * Loads the folder list from SharedPreferences.
     *
     * @param context Android context
     * @return Mutable list of Folder objects (never null)
     */
    public static List<Folder> loadFolders(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_FOLDER_LIST, null);
        // No saved data → return empty list
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            List<Folder> folders = gson.fromJson(json, FOLDER_LIST_TYPE);
            // Safety check (Gson may return null)
            return folders != null ? folders : new ArrayList<>();
        } catch (Exception e) {
            // Corrupted JSON or schema mismatch
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
