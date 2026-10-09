package com.example.moekeyboard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_bookmarks")
data class BookmarkItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)
