package com.example.moekeyboard.ime

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CredentialStep(
    val label: String,      // e.g. "First name", "Login", "Password", "Email", "Last name"
    val value: String       // e.g. "Wojtek", "wostekv_der_", "tOwXiYDf2GNr"
)

data class CredentialGroup(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val rawText: String,
    val steps: List<CredentialStep>,
    var currentStepIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

class SmartCredentialAutofillManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sah_credential_autofill_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: SmartCredentialAutofillManager? = null

        fun getInstance(context: Context): SmartCredentialAutofillManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartCredentialAutofillManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val KEY_SAVED_GROUPS = "saved_credential_groups_json_v1"
        private const val KEY_ACTIVE_GROUP_ID = "active_group_id_v1"
        private const val KEY_CURRENT_STEP_INDEX = "current_step_index_v1"
        private const val KEY_LAST_PROCESSED_RAW_TEXT = "last_processed_raw_text_v1"
    }

    interface AutofillUpdateListener {
        fun onAutofillStateChanged()
    }

    private val listeners = mutableListOf<AutofillUpdateListener>()

    fun addListener(listener: AutofillUpdateListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
    }

    fun removeListener(listener: AutofillUpdateListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun notifyStateChanged() {
        synchronized(listeners) {
            listeners.forEach { it.onAutofillStateChanged() }
        }
    }

    fun getSavedGroups(): List<CredentialGroup> {
        val jsonStr = prefs.getString(KEY_SAVED_GROUPS, "[]") ?: "[]"
        val list = mutableListOf<CredentialGroup>()
        try {
            val jsonArr = JSONArray(jsonStr)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val title = obj.optString("title", "Credential")
                val rawText = obj.optString("rawText", "")
                val stepIdx = obj.optInt("currentStepIndex", 0)
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

                val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
                val stepsList = mutableListOf<CredentialStep>()
                for (s in 0 until stepsArr.length()) {
                    val sObj = stepsArr.getJSONObject(s)
                    val label = sObj.optString("label", "Field ${s + 1}")
                    val value = sObj.optString("value", "")
                    if (value.isNotBlank()) {
                        stepsList.add(CredentialStep(label = label, value = value))
                    }
                }

                if (stepsList.isNotEmpty()) {
                    list.add(
                        CredentialGroup(
                            id = id,
                            title = title,
                            rawText = rawText,
                            steps = stepsList,
                            currentStepIndex = stepIdx.coerceIn(0, (stepsList.size - 1).coerceAtLeast(0)),
                            timestamp = timestamp
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveGroups(groups: List<CredentialGroup>, notify: Boolean = true) {
        val jsonArr = JSONArray()
        for (g in groups.take(30)) {
            val obj = JSONObject().apply {
                put("id", g.id)
                put("title", g.title)
                put("rawText", g.rawText)
                put("currentStepIndex", g.currentStepIndex)
                put("timestamp", g.timestamp)
                val sArr = JSONArray()
                for (step in g.steps) {
                    val sObj = JSONObject().apply {
                        put("label", step.label)
                        put("value", step.value)
                    }
                    sArr.put(sObj)
                }
                put("steps", sArr)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_GROUPS, jsonArr.toString()).apply()
        if (notify) {
            notifyStateChanged()
        }
    }

    /**
     * Returns the currently active group, or null if no group is actively being autofilled.
     * When null, the toolbar button shows its default original icon.
     */
    fun getActiveGroup(): CredentialGroup? {
        val activeId = prefs.getString(KEY_ACTIVE_GROUP_ID, null) ?: return null
        val groups = getSavedGroups()
        if (groups.isEmpty()) return null
        val group = groups.firstOrNull { it.id == activeId } ?: return null
        val savedStepIdx = prefs.getInt(KEY_CURRENT_STEP_INDEX, group.currentStepIndex)
        val validIdx = if (group.steps.isNotEmpty()) {
            savedStepIdx.coerceIn(0, group.steps.size - 1)
        } else {
            0
        }
        group.currentStepIndex = validIdx
        return group
    }

    fun setActiveGroup(group: CredentialGroup, resetStep: Boolean = false) {
        val currentActiveId = prefs.getString(KEY_ACTIVE_GROUP_ID, null)
        val isDifferentGroup = (currentActiveId != group.id)
        val targetStepIndex = if (resetStep || isDifferentGroup) {
            0
        } else {
            prefs.getInt(KEY_CURRENT_STEP_INDEX, group.currentStepIndex).coerceIn(0, (group.steps.size - 1).coerceAtLeast(0))
        }

        prefs.edit()
            .putString(KEY_ACTIVE_GROUP_ID, group.id)
            .putInt(KEY_CURRENT_STEP_INDEX, targetStepIndex)
            .apply()

        // Sync with groups list
        updateGroupStepInStorage(group.id, targetStepIndex)
        notifyStateChanged()
    }

    fun clearActiveGroup() {
        prefs.edit()
            .remove(KEY_ACTIVE_GROUP_ID)
            .remove(KEY_CURRENT_STEP_INDEX)
            .remove(KEY_LAST_PROCESSED_RAW_TEXT)
            .apply()
        notifyStateChanged()
    }

    fun resetActiveGroupStep() {
        val active = getActiveGroup() ?: return
        prefs.edit().putInt(KEY_CURRENT_STEP_INDEX, 0).apply()
        updateGroupStepInStorage(active.id, 0)
        notifyStateChanged()
    }

    fun setCurrentStepIndex(index: Int) {
        val group = getActiveGroup() ?: return
        val validIdx = if (group.steps.isNotEmpty()) index.coerceIn(0, group.steps.size - 1) else 0
        prefs.edit().putInt(KEY_CURRENT_STEP_INDEX, validIdx).apply()
        updateGroupStepInStorage(group.id, validIdx)
        notifyStateChanged()
    }

    private fun updateGroupStepInStorage(groupId: String, stepIndex: Int) {
        val list = getSavedGroups().toMutableList()
        val idx = list.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            val updated = list[idx].copy(currentStepIndex = stepIndex)
            list[idx] = updated
            saveGroups(list, notify = false)
        }
    }

    /**
     * Retrieves the current step, advances step index to next, and returns StepResult.
     * When the last step (e.g. 3rd step) is completed:
     * - Clears the active group so the keyboard button immediately reverts to the default original icon!
     * - When the user copies something again later, a new autofill cycle will start.
     */
    fun getNextStepAndAdvance(): StepResult? {
        val group = getActiveGroup() ?: return null
        if (group.steps.isEmpty()) return null

        val currentIdx = prefs.getInt(KEY_CURRENT_STEP_INDEX, group.currentStepIndex).coerceIn(0, group.steps.size - 1)
        val currentStep = group.steps[currentIdx]
        val stepNum = currentIdx + 1
        val totalSteps = group.steps.size

        val isLastStep = (currentIdx >= group.steps.size - 1)

        if (isLastStep) {
            // Completed all steps!
            // 1. Reset saved group's internal step index to 0 for next time
            updateGroupStepInStorage(group.id, 0)
            // 2. Clear active group so toolbar button reverts back to original button
            clearActiveGroup()
        } else {
            val nextIdx = currentIdx + 1
            // Persist the advanced step index immediately to SharedPreferences & group list
            prefs.edit().putInt(KEY_CURRENT_STEP_INDEX, nextIdx).apply()
            updateGroupStepInStorage(group.id, nextIdx)
            notifyStateChanged()
        }

        val nextLabel = if (isLastStep) {
            "সম্পন্ন (আগের বাটনে ফিরে গেছে)"
        } else {
            val nextStep = group.steps[currentIdx + 1]
            "${currentIdx + 2}ম ধাপ: ${nextStep.label}"
        }

        return StepResult(
            step = currentStep,
            stepNumber = stepNum,
            totalSteps = totalSteps,
            nextStepLabel = nextLabel,
            isCompleted = isLastStep
        )
    }

    data class StepResult(
        val step: CredentialStep,
        val stepNumber: Int,
        val totalSteps: Int,
        val nextStepLabel: String,
        val isCompleted: Boolean
    )

    /**
     * Intelligently parses raw text blocks into multi-step credentials.
     * Supports formats like:
     * First name: Wojtek
     * Login: wostekv_der_
     * Password: tOwXiYDf2GNr
     *
     * As well as inline, key-value, comma-separated, pipe-separated, and multi-line formats.
     */
    fun parseTextToCredentialGroup(text: String): CredentialGroup? {
        val trimmed = text.trim()
        if (trimmed.length < 3) return null

        var loginVal: String? = null

        // 1. Line-by-line parsing for labeled fields
        val lines = trimmed.split("\n", "\r").map { it.trim() }.filter { it.isNotBlank() }

        for (line in lines) {
            val lower = line.lowercase()
            if (lower.startsWith("login:") || lower.startsWith("login=") || lower.startsWith("login -") ||
                lower.startsWith("username:") || lower.startsWith("username=") || lower.startsWith("username -") ||
                lower.startsWith("user:") || lower.startsWith("user=") || lower.startsWith("user -") ||
                lower.startsWith("id:") || lower.startsWith("id=") || lower.startsWith("id -")
            ) {
                val delimiter = if (line.contains(":")) ":" else if (line.contains("=")) "=" else "-"
                val v = line.substringAfter(delimiter).trim()
                if (v.isNotBlank()) {
                    loginVal = v
                    break
                }
            }
        }

        // 2. Inline regex pattern search (e.g. First name: Wojtek Login: wostekv_der_ Password: ...)
        if (loginVal.isNullOrBlank()) {
            val loginMatch = Regex("(?:Login|Username|User|Id)\\s*[:=-]\\s*([^\\n,;|]+)", RegexOption.IGNORE_CASE).find(trimmed)
            if (loginMatch != null) {
                loginVal = loginMatch.groupValues[1].trim()
            }
        }

        // 3. Pipe or tab delimited parts (e.g. Name | Login | Password)
        if (loginVal.isNullOrBlank() && (trimmed.contains("|") || trimmed.contains("\t"))) {
            val parts = trimmed.split(Regex("[|\t]")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) {
                // If 3 or more parts: index 1 is Login
                // If 2 parts: index 0 is Login
                loginVal = if (parts.size >= 3) parts[1] else parts[0]
            }
        }

        // 4. Multi-line without labels (e.g. 3 lines: Wojtek \n wostekv_der_ \n tOwXiYDf2GNr)
        if (loginVal.isNullOrBlank() && lines.size in 2..6) {
            // When 3 lines or more are copied: 2nd line is the Login
            // When 2 lines: 1st line is the Login
            loginVal = if (lines.size >= 3) lines[1] else lines[0]
        }

        if (loginVal.isNullOrBlank()) {
            return null
        }

        // Clean login value
        val cleanLogin = loginVal.trim().trim('"', '\'')
        if (cleanLogin.isBlank()) return null

        // Per user requirement: ONLY the login step is kept, nothing else!
        val steps = listOf(CredentialStep("Login", cleanLogin))

        return CredentialGroup(
            title = cleanLogin,
            rawText = trimmed,
            steps = steps,
            currentStepIndex = 0,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun formatLabel(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("first") && lower.contains("name") -> "First name"
            lower.contains("last") && lower.contains("name") -> "Last name"
            lower == "name" || lower == "full name" -> "Name"
            lower.contains("login") -> "Login"
            lower.contains("user") -> "Username"
            lower.contains("pass") || lower.contains("pwd") -> "Password"
            lower.contains("mail") -> "Email"
            lower.contains("phone") || lower.contains("mobile") -> "Phone"
            lower.contains("code") || lower.contains("otp") -> "OTP Code"
            else -> raw.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Checks if new text copied to clipboard is a multi-step credential block.
     * If isNewCopy is true (e.g. user just copied text): starts/resets the 3-step cycle.
     * If isNewCopy is false (e.g. routine keyboard open during autofill sequence): preserves step progress.
     */
    fun processCopiedText(text: String, isManualImport: Boolean = false, isNewCopy: Boolean = false): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 5) return false

        val lastProcessed = prefs.getString(KEY_LAST_PROCESSED_RAW_TEXT, "") ?: ""
        val activeGroup = getActiveGroup()

        // If clipboard contains identical text to what is already active and this is just a passive keyboard open:
        // KEEP existing step progress! Do NOT reset to step 0!
        if (!isManualImport && !isNewCopy && (trimmed == lastProcessed || (activeGroup != null && activeGroup.rawText.trim() == trimmed))) {
            return true
        }

        val group = parseTextToCredentialGroup(trimmed) ?: return false

        // Check if an existing group with this exact raw text already exists
        val existingList = getSavedGroups().toMutableList()
        val existingIdx = existingList.indexOfFirst { it.rawText.trim() == group.rawText.trim() }

        if (existingIdx != -1 && !isManualImport) {
            val existing = existingList[existingIdx]
            // If it's already the active group and not an explicit new copy: keep step progress
            if (activeGroup?.id == existing.id && !isNewCopy) {
                prefs.edit().putString(KEY_LAST_PROCESSED_RAW_TEXT, trimmed).apply()
                return true
            } else {
                // If it is a new copy or no active group, activate this group from step 0
                prefs.edit().putString(KEY_LAST_PROCESSED_RAW_TEXT, trimmed).apply()
                setActiveGroup(existing, resetStep = true)
                return true
            }
        }

        // New credential block copied!
        existingList.removeAll { it.rawText.trim() == group.rawText.trim() }
        existingList.add(0, group)

        prefs.edit().putString(KEY_LAST_PROCESSED_RAW_TEXT, trimmed).apply()
        saveGroups(existingList, notify = false)
        setActiveGroup(group, resetStep = true)

        return true
    }

    fun deleteGroup(groupId: String) {
        val existingList = getSavedGroups().toMutableList()
        existingList.removeAll { it.id == groupId }
        saveGroups(existingList, notify = false)
        if (prefs.getString(KEY_ACTIVE_GROUP_ID, "") == groupId) {
            val next = existingList.firstOrNull()
            if (next != null) {
                setActiveGroup(next, resetStep = false)
            } else {
                prefs.edit()
                    .remove(KEY_ACTIVE_GROUP_ID)
                    .remove(KEY_CURRENT_STEP_INDEX)
                    .remove(KEY_LAST_PROCESSED_RAW_TEXT)
                    .apply()
                notifyStateChanged()
            }
        } else {
            notifyStateChanged()
        }
    }
}
