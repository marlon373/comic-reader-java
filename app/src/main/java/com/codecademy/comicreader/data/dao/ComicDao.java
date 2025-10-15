package com.codecademy.comicreader.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.codecademy.comicreader.model.Comic;

@Dao
public interface ComicDao {

    //  Get all comics in the database
    @Query("SELECT * FROM Comics")
    List<Comic> getAllComics();

    //  Insert or replace multiple comics (avoids duplicates)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Comic> comics);

    //  Delete all comics
    @Query("DELETE FROM Comics")
    void deleteAll();

    //  Delete a single comic by its exact path
    @Query("DELETE FROM Comics WHERE path = :comicPath")
    void deleteComicByPath(String comicPath);

    //  Delete all comics from a folder (matches prefix)
    @Query("DELETE FROM Comics WHERE path LIKE :folderPath || '%'")
    void deleteComicsByFolderPath(String folderPath);
}