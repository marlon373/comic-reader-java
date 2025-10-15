package com.codecademy.comicreader.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Entity class representing a Folder or Folder in the database
@Entity(tableName = "Folder")
public class Folder {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private String path;
    private final boolean isFolder;

    // Constructor to initialize a Folder object
    public Folder(String name, String path, boolean isFolder){
        this.name = name;
        this.path = path;
        this.isFolder = isFolder;
    }

    // Getter for ID
    public int getId() {
        return id;
    }

    // Setter for ID
    public void setId(int id) {
        this.id = id;
    }

    // Getter for Name
    public String getName() {
        return name;
    }

    // Setter for Name
    public void setName(String name) {
        this.name = name;
    }

    // Getter for Path
    public String getPath() {
        return path;
    }

    // Setter for Path
    public void setPath(String path) {
        this.path = path;
    }

    // Getter for checking if it's a folder
    public boolean isFolder() {
        return isFolder;
    }


}
