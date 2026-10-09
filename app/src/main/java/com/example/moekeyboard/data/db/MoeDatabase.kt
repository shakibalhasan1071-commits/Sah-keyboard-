package com.example.moekeyboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClipboardItem::class, BookmarkItem::class],
    version = 1,
    exportSchema = false
)
abstract class MoeDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: MoeDatabase? = null

        fun getDatabase(context: Context): MoeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MoeDatabase::class.java,
                    "moe_keyboard_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
