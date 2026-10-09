package com.example.moekeyboard.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.moekeyboard.data.db.ClipboardItem
import com.example.moekeyboard.data.db.MoeDatabase
import com.example.moekeyboard.tempmail.TempMailManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserGoogleProfile(
    val isSignedIn: Boolean = false,
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val googleId: String = "",
    val lastSyncedTime: String = "Never",
    val cloudBackupEnabled: Boolean = true
)

class GoogleSyncManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("moe_google_cloud_sync_prefs", Context.MODE_PRIVATE)
    private val database = MoeDatabase.getDatabase(context)
    private val tempMailManager = TempMailManager.getInstance(context)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserGoogleProfile> = _userProfile.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: GoogleSyncManager? = null

        fun getInstance(context: Context): GoogleSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoogleSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val KEY_IS_SIGNED_IN = "is_signed_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_GOOGLE_ID = "google_id"
        private const val KEY_LAST_SYNCED = "last_synced_time"
        private const val KEY_CLOUD_BACKUP_ENABLED = "cloud_backup_enabled"
        private const val KEY_CLOUD_STORAGE_BUFFER = "cloud_storage_buffer"
    }

    private fun loadProfile(): UserGoogleProfile {
        return UserGoogleProfile(
            isSignedIn = prefs.getBoolean(KEY_IS_SIGNED_IN, false),
            email = prefs.getString(KEY_USER_EMAIL, "") ?: "",
            displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
            avatarUrl = prefs.getString(KEY_AVATAR_URL, "") ?: "",
            googleId = prefs.getString(KEY_GOOGLE_ID, "") ?: "",
            lastSyncedTime = prefs.getString(KEY_LAST_SYNCED, "Never") ?: "Never",
            cloudBackupEnabled = prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, true)
        )
    }

    suspend fun signInWithGoogle(
        email: String = "user.moekeyboard@gmail.com",
        displayName: String = "Google User",
        avatarUrl: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val googleId = "gid_" + System.currentTimeMillis()
        val nowStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

        prefs.edit()
            .putBoolean(KEY_IS_SIGNED_IN, true)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_AVATAR_URL, avatarUrl)
            .putString(KEY_GOOGLE_ID, googleId)
            .putString(KEY_LAST_SYNCED, nowStr)
            .apply()

        _userProfile.value = loadProfile()

        // Perform initial backup upon sign-in
        syncNow()
        true
    }

    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean(KEY_IS_SIGNED_IN, false)
            .putString(KEY_USER_EMAIL, "")
            .putString(KEY_DISPLAY_NAME, "")
            .putString(KEY_AVATAR_URL, "")
            .putString(KEY_GOOGLE_ID, "")
            .apply()

        _userProfile.value = loadProfile()
        true
    }

    suspend fun setCloudBackupEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_CLOUD_BACKUP_ENABLED, enabled).apply()
        _userProfile.value = loadProfile()
    }

    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        val profile = _userProfile.value
        if (!profile.isSignedIn) return@withContext false

        try {
            // 1. Gather all local clipboard items (pinned & unpinned)
            val clipboardList = database.clipboardDao().getAllItems().first()
            val clipboardArray = JSONArray()
            for (item in clipboardList) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("timestamp", item.timestamp)
                    put("isPinned", item.isPinned)
                }
                clipboardArray.put(obj)
            }

            // 2. Gather Temp Mail accounts
            val tempMailAccounts = tempMailManager.getSavedAccounts()
            val tempMailArray = JSONArray()
            for (acc in tempMailAccounts) {
                val obj = JSONObject().apply {
                    put("email", acc.email)
                    put("password", acc.password)
                    put("domain", acc.domain)
                    put("username", acc.username)
                    put("createdAt", acc.createdAt)
                    put("token", acc.token)
                }
                tempMailArray.put(obj)
            }

            // 3. Gather Bookmarks
            val bookmarksList = database.bookmarkDao().getAllBookmarks().first()
            val bookmarkArray = JSONArray()
            for (b in bookmarksList) {
                val obj = JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("url", b.url)
                    put("timestamp", b.timestamp)
                }
                bookmarkArray.put(obj)
            }

            // Combine into unified cloud sync JSON payload
            val rootCloudObj = JSONObject().apply {
                put("userEmail", profile.email)
                put("googleId", profile.googleId)
                put("syncedAt", System.currentTimeMillis())
                put("clipboardItems", clipboardArray)
                put("tempMailAccounts", tempMailArray)
                put("bookmarks", bookmarkArray)
            }

            // Save to persistent cloud storage buffer
            prefs.edit()
                .putString(KEY_CLOUD_STORAGE_BUFFER, rootCloudObj.toString())
                .putString(KEY_LAST_SYNCED, SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()))
                .apply()

            _userProfile.value = loadProfile()
            return@withContext true
        } catch (_: Exception) {
            return@withContext false
        }
    }

    suspend fun restoreFromCloud(): Boolean = withContext(Dispatchers.IO) {
        val cloudData = prefs.getString(KEY_CLOUD_STORAGE_BUFFER, null) ?: return@withContext false
        try {
            val rootObj = JSONObject(cloudData)

            // Restore clipboard items
            val clipboardArr = rootObj.optJSONArray("clipboardItems")
            if (clipboardArr != null) {
                for (i in 0 until clipboardArr.length()) {
                    val itemObj = clipboardArr.getJSONObject(i)
                    val text = itemObj.getString("text")
                    val isPinned = itemObj.optBoolean("isPinned", false)

                    val existing = database.clipboardDao().findByText(text)
                    if (existing == null) {
                        database.clipboardDao().insert(
                            ClipboardItem(
                                text = text,
                                isPinned = isPinned,
                                timestamp = itemObj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    } else if (isPinned && !existing.isPinned) {
                        database.clipboardDao().update(existing.copy(isPinned = true))
                    }
                }
            }

            return@withContext true
        } catch (_: Exception) {
            return@withContext false
        }
    }
}
