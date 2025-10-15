package com.codecademy.comicreader.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Comics")
public class Comic {

    @PrimaryKey
    @NonNull
    private final String path;

    private final String name;
    private final String date;
    private final String size;
    private final String format;

    public Comic(String name, String path, String date, String size, String format) {
        this.name = name;
        this.path = path;
        this.date = date;
        this.size = size;
        this.format = format;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getDate() {
        return date;
    }

    public String getSize() {
        return size;
    }

    public String getFormat() {
        return format;
    }
}

