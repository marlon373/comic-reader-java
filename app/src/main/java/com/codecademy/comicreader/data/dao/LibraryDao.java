package com.codecademy.comicreader.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import com.codecademy.comicreader.model.Folder;

@Dao
// DAO (Data Access Object) interface for interacting with the database
public interface LibraryDao {

    // Inserts a new folder into the database
    @Insert
    void insert(Folder folder);

    // Deletes a specific folder
    @Delete
    void delete(Folder folder);

    // Retrieves all folders/comics from the database
    @Query("SELECT * FROM Folder")
    List<Folder> getAllFolders();

    // Retrieves a folder by its path (if it exists)
    @Query("SELECT * FROM Folder WHERE path = :folderPath LIMIT 1")
    Folder getFolderByPath(String folderPath);


}