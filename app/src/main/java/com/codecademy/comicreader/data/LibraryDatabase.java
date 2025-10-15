package com.codecademy.comicreader.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.codecademy.comicreader.data.dao.LibraryDao;
import com.codecademy.comicreader.model.Folder;

// Database class for handling database operations using Room
@Database(entities = {Folder.class}, version = 1, exportSchema = false)
public abstract class LibraryDatabase extends RoomDatabase {

    // Access DAO methods
    public abstract LibraryDao folderItemDao();

    private static volatile LibraryDatabase INSTANCE;

    // Singleton pattern to ensure only one instance of the database is created
    public static LibraryDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (LibraryDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    LibraryDatabase.class, "library_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
