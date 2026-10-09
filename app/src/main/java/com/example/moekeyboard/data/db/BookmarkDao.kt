package com.example.moekeyboard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM browser_bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkItem): Long

    @Update
    suspend fun update(bookmark: BookmarkItem)

    @Delete
    suspend fun delete(bookmark: BookmarkItem)

    @Query("DELETE FROM browser_bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
