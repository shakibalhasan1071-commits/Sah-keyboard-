package com.example.moekeyboard.tempmail

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class TempMailDomain(
    val domain: String,
    val displayName: String,
    val category: String = "standard",
    val durationHours: Int = 24,
    val provider: String = "1secmail" // "1secmail" or "mailtm"
)

data class AuthCredential(
    val firstName: String,
    val login: String,
    val password: String
)

data class SavedTempMailAccount(
    val email: String,
    val password: String,
    val domain: String,
    val username: String,
    val createdAt: String,
    val token: String = "",
    val messageCount: Int = 0,
    val provider: String = "1secmail",
    val lastReceivedOtp: String = "",
    val lastSubject: String = ""
)

data class TempMailMessage(
    val id: String,
    val from: String,
    val subject: String,
    val date: String,
    var body: String = "",
    var htmlBody: String = "",
    var extractedOtp: String? = null
)

class TempMailManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sah_temp_mail_history", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: TempMailManager? = null

        fun getInstance(context: Context): TempMailManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TempMailManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val API_TEMPMAIL_IO = "https://api.internal.temp-mail.io/api/v3"
        private const val API_MAILGW = "https://api.mail.gw"
        private const val API_MAILTM = "https://api.mail.tm"
        private const val API_1SECMAIL = "https://www.1secmail.com/api/v1/"

        private const val KEY_SAVED_ACCOUNTS = "saved_accounts_json_v3"
        private const val KEY_CURRENT_EMAIL = "current_email_v3"
        private const val KEY_CURRENT_PASS = "current_pass_v3"
        private const val KEY_CURRENT_TOKEN = "current_token_v3"
        private const val KEY_CURRENT_DOMAIN = "current_domain_v3"
        private const val KEY_CURRENT_USER = "current_user_v3"
        private const val KEY_CURRENT_PROVIDER = "current_provider_v3"
        private const val KEY_ACTIVE_API_URL = "active_api_url_v3"
        private const val KEY_SHEET_ACCOUNTS = "sheet_accounts_json_v1"
        private const val KEY_CREDENTIALS = "auth_credentials_json_v1"
        private const val KEY_IMPORTANT_NOTES = "important_notes_json_v1"

        // Official live domains from https://temp-mail.io/en
        val DEFAULT_DOMAINS = listOf(
            TempMailDomain("olipii.com", "@olipii.com", "temp_mail_io", 24, "temp_mail_io"),
            TempMailDomain("ooynib.com", "@ooynib.com", "temp_mail_io", 24, "temp_mail_io"),
            TempMailDomain("gmeenramy.com", "@gmeenramy.com", "temp_mail_io", 24, "temp_mail_io"),
            TempMailDomain("ruutukf.com", "@ruutukf.com", "temp_mail_io", 24, "temp_mail_io"),
            TempMailDomain("lnovic.com", "@lnovic.com", "temp_mail_io", 24, "temp_mail_io"),
            TempMailDomain("yzcalo.com", "@yzcalo.com", "temp_mail_io", 24, "temp_mail_io"),
            TempMailDomain("ozsaip.com", "@ozsaip.com", "temp_mail_io", 24, "temp_mail_io")
        )

        private const val ENCRYPTED_TITLE_B64 = "RGV2ZWxvcGVkIGJ5IEJsYWNrIEtub3dsZWRnZQ=="
        private const val ENCRYPTED_LINK_B64 = "aHR0cHM6Ly95b3V0dWJlLmNvbS9AYmxhY2trbm93bGVkZ2VfMTkwP3NpPXRzZjZxXzNmdmFZQWVfdDE="

        fun getDecodedTitle(): String {
            return try {
                String(Base64.decode(ENCRYPTED_TITLE_B64, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                "Developed by Black Knowledge"
            }
        }

        fun getDecodedLink(): String {
            return try {
                String(Base64.decode(ENCRYPTED_LINK_B64, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                "https://youtube.com/@blackknowledge_190"
            }
        }
    }

    var activeApiBaseUrl: String = API_TEMPMAIL_IO
        private set

    var currentEmail: String = ""
        private set

    var currentPassword: String = ""
        private set

    var currentUsername: String = ""
        private set

    var currentToken: String = ""
        private set

    var currentDomain: String = "olipii.com"
        private set

    var currentProvider: String = "temp_mail_io"
        private set

    var availableDomains: List<TempMailDomain> = DEFAULT_DOMAINS
        private set

    init {
        val savedEmail = prefs.getString(KEY_CURRENT_EMAIL, null)
        val savedPass = prefs.getString(KEY_CURRENT_PASS, null)
        val savedUser = prefs.getString(KEY_CURRENT_USER, null)
        val savedToken = prefs.getString(KEY_CURRENT_TOKEN, null)
        val savedDomain = prefs.getString(KEY_CURRENT_DOMAIN, null)
        val savedProvider = prefs.getString(KEY_CURRENT_PROVIDER, null)
        activeApiBaseUrl = API_TEMPMAIL_IO

        if (!savedEmail.isNullOrBlank()) {
            currentEmail = savedEmail
            currentPassword = savedPass ?: ""
            currentUsername = savedUser ?: savedEmail.substringBefore("@")
            currentToken = savedToken ?: ""
            currentDomain = savedDomain ?: "olipii.com"
            currentProvider = savedProvider ?: "temp_mail_io"

            // Auto-upgrade legacy accounts (e.g. 1secmail or mail.gw) to temp-mail.io
            if (currentDomain.contains("1secmail") || currentDomain.contains("mail.") || currentDomain.contains("vjupq") || currentProvider != "temp_mail_io") {
                currentEmail = ""
                currentDomain = "olipii.com"
                currentProvider = "temp_mail_io"
                currentToken = ""
            }
        }
    }

    fun fetchWithTimeout(
        urlStr: String,
        method: String = "GET",
        payloadJson: String? = null,
        token: String? = null,
        timeoutMs: Int = 6000
    ): Pair<Int, String?> {
        try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Application-Name", "web")
                setRequestProperty("Application-Brand", "tempmail")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (payloadJson != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (payloadJson != null) {
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payloadJson)
                writer.flush()
                writer.close()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                return Pair(code, resp)
            }
            return Pair(code, null)
        } catch (_: Exception) {
            return Pair(-1, null)
        }
    }

    private fun fetchDomainsFromHydraApi(baseUrl: String): List<TempMailDomain> {
        val list = mutableListOf<TempMailDomain>()
        val (code, resp) = fetchWithTimeout("$baseUrl/domains", timeoutMs = 6000)
        if (code == 200 && !resp.isNullOrBlank()) {
            try {
                val json = JSONObject(resp)
                val memberArr = json.optJSONArray("hydra:member")
                if (memberArr != null) {
                    for (i in 0 until memberArr.length()) {
                        val obj = memberArr.getJSONObject(i)
                        val isInactive = obj.optBoolean("isInactive", false)
                        val dName = obj.optString("domain", "")
                        if (!isInactive && dName.isNotBlank()) {
                            list.add(TempMailDomain(dName, "@$dName", "primary", 24, "hydra_api"))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return list
    }

    suspend fun refreshDomainList(): List<TempMailDomain> = withContext(Dispatchers.IO) {
        val fetchedDomains = mutableListOf<TempMailDomain>()

        // 1. Primary: Official domains from https://temp-mail.io/en
        try {
            val (code, resp) = fetchWithTimeout("$API_TEMPMAIL_IO/domains", timeoutMs = 6000)
            if (code == 200 && !resp.isNullOrBlank()) {
                val json = JSONObject(resp)
                val domainsArr = json.optJSONArray("domains")
                if (domainsArr != null) {
                    for (i in 0 until domainsArr.length()) {
                        val obj = domainsArr.getJSONObject(i)
                        val dName = obj.optString("name", "")
                        if (dName.isNotBlank()) {
                            fetchedDomains.add(TempMailDomain(dName, "@$dName", "temp_mail_io", 24, "temp_mail_io"))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (fetchedDomains.isNotEmpty()) {
            availableDomains = fetchedDomains
            return@withContext fetchedDomains
        }

        availableDomains = DEFAULT_DOMAINS
        DEFAULT_DOMAINS
    }

    suspend fun generateNewAccount(domainName: String? = null): String = withContext(Dispatchers.IO) {
        // 1. Delete previous email on temp-mail.io if it had a token, to delete/rotate cleanly
        if (currentEmail.isNotBlank() && currentToken.isNotBlank()) {
            try {
                val delJson = JSONObject().apply { put("token", currentToken) }
                fetchWithTimeout("$API_TEMPMAIL_IO/email/$currentEmail", method = "DELETE", payloadJson = delJson.toString(), timeoutMs = 4000)
            } catch (_: Exception) {}
        }

        var newEmail = ""
        var newToken = ""

        try {
            val reqPayload = JSONObject().apply {
                if (domainName != null) {
                    put("domain", domainName)
                } else {
                    put("min_name_length", 10)
                    put("max_name_length", 10)
                }
            }
            val (code, resp) = fetchWithTimeout(
                "$API_TEMPMAIL_IO/email/new",
                method = "POST",
                payloadJson = reqPayload.toString(),
                timeoutMs = 6000
            )
            if (code in 200..299 && !resp.isNullOrBlank()) {
                val obj = JSONObject(resp)
                newEmail = obj.optString("email", "")
                newToken = obj.optString("token", "")
            }
        } catch (_: Exception) {}

        if (newEmail.isBlank() || !newEmail.contains("@")) {
            try {
                val reqPayload = JSONObject().apply {
                    put("min_name_length", 10)
                    put("max_name_length", 10)
                }
                val (code, resp) = fetchWithTimeout(
                    "$API_TEMPMAIL_IO/email/new",
                    method = "POST",
                    payloadJson = reqPayload.toString(),
                    timeoutMs = 8000
                )
                if (code in 200..299 && !resp.isNullOrBlank()) {
                    val obj = JSONObject(resp)
                    newEmail = obj.optString("email", "")
                    newToken = obj.optString("token", "")
                }
            } catch (_: Exception) {}
        }

        if (newEmail.isNotBlank()) {
            currentEmail = newEmail
            currentToken = newToken
            currentDomain = newEmail.substringAfter("@", "olipii.com")
            currentUsername = newEmail.substringBefore("@")
            currentProvider = "temp_mail_io"

            saveCurrentAccountState()

            val nowStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            saveAccountToHistory(
                SavedTempMailAccount(
                    email = newEmail,
                    password = "",
                    domain = currentDomain,
                    username = currentUsername,
                    createdAt = nowStr,
                    token = currentToken,
                    provider = "temp_mail_io"
                )
            )
        }

        return@withContext newEmail
    }

    suspend fun generateNewTempMailLolAccount(): String = withContext(Dispatchers.IO) {
        var newEmail = ""
        var newToken = ""
        try {
            val (code, resp) = fetchWithTimeout("https://api.tempmail.lol/v2/inbox/create", method = "GET", timeoutMs = 6000)
            if (code in 200..299 && !resp.isNullOrBlank()) {
                val obj = JSONObject(resp)
                newEmail = obj.optString("address", "")
                newToken = obj.optString("token", "")
            }
        } catch (_: Exception) {}

        if (newEmail.isBlank()) {
            try {
                val (code, resp) = fetchWithTimeout("https://api.tempmail.lol/v2/inbox/create", method = "POST", timeoutMs = 6000)
                if (code in 200..299 && !resp.isNullOrBlank()) {
                    val obj = JSONObject(resp)
                    newEmail = obj.optString("address", "")
                    newToken = obj.optString("token", "")
                }
            } catch (_: Exception) {}
        }

        if (newEmail.isNotBlank()) {
            currentEmail = newEmail
            currentToken = newToken
            currentDomain = newEmail.substringAfter("@", "tempmail.lol")
            currentUsername = newEmail.substringBefore("@")
            currentProvider = "tempmail_lol"

            saveCurrentAccountState()

            val nowStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            saveAccountToHistory(
                SavedTempMailAccount(
                    email = newEmail,
                    password = "",
                    domain = currentDomain,
                    username = currentUsername,
                    createdAt = nowStr,
                    token = currentToken,
                    provider = "tempmail_lol"
                )
            )
        }

        return@withContext newEmail
    }

    fun updateCurrentAccountFromWeb(email: String, provider: String = "temp_mail_io") {
        val clean = email.trim()
        if (clean.isNotBlank() && clean.contains("@") && clean != currentEmail) {
            currentEmail = clean
            currentDomain = clean.substringAfter("@")
            currentUsername = clean.substringBefore("@")
            currentProvider = provider
            saveCurrentAccountState()
            val nowStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            saveAccountToHistory(
                SavedTempMailAccount(
                    email = clean,
                    password = "",
                    domain = currentDomain,
                    username = currentUsername,
                    createdAt = nowStr,
                    provider = provider
                )
            )
        }
    }

    private fun fetchHydraJwtToken(email: String, pass: String, baseUrl: String = activeApiBaseUrl): String {
        try {
            val payload = JSONObject().apply {
                put("address", email)
                put("password", pass)
            }
            val (code, resp) = fetchWithTimeout("$baseUrl/token", method = "POST", payloadJson = payload.toString(), timeoutMs = 6000)
            if (code == 200 && !resp.isNullOrBlank()) {
                val json = JSONObject(resp)
                return json.optString("token", "")
            }
        } catch (_: Exception) {}
        return ""
    }

    suspend fun fetchInbox(): List<TempMailMessage> = withContext(Dispatchers.IO) {
        if (currentEmail.isBlank()) {
            generateNewAccount()
        }

        if (currentProvider == "temp_mail_io") {
            return@withContext fetchTempMailIoInbox()
        } else if (currentProvider == "tempmail_lol") {
            return@withContext fetchTempMailLolInbox()
        } else if (currentProvider == "1secmail") {
            return@withContext fetch1SecMailInbox()
        } else {
            val result = fetchHydraInbox()
            if (result.first == 401) {
                // Token expired -> Auto-create new account / refresh credentials
                if (currentEmail.isNotBlank() && currentPassword.isNotBlank()) {
                    currentToken = fetchHydraJwtToken(currentEmail, currentPassword, activeApiBaseUrl)
                    if (currentToken.isBlank()) {
                        generateNewAccount()
                    }
                } else {
                    generateNewAccount()
                }
                return@withContext fetchHydraInbox().second
            }
            return@withContext result.second
        }
    }

    private suspend fun fetchTempMailIoInbox(): List<TempMailMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<TempMailMessage>()
        try {
            val (code, resp) = fetchWithTimeout("$API_TEMPMAIL_IO/email/$currentEmail/messages", timeoutMs = 6000)
            if (code == 200 && !resp.isNullOrBlank()) {
                val jsonArr = JSONArray(resp)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val id = obj.optString("id", i.toString())
                    val from = obj.optString("from", "Unknown")
                    val subject = obj.optString("subject", "(No Subject)")
                    val createdAt = obj.optString("created_at", "")
                    val bodyText = obj.optString("body_text", "")
                    val htmlBody = obj.optString("body_html", "")

                    val cleanBody = bodyText.ifBlank { escapeHTML(htmlBody) }
                    val otp = extractOtpCode(subject + " " + cleanBody) ?: extractOtpCode(htmlBody)

                    messages.add(
                        TempMailMessage(
                            id = id,
                            from = from,
                            subject = subject,
                            date = formatDate(createdAt),
                            body = cleanBody,
                            htmlBody = htmlBody,
                            extractedOtp = otp
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        val foundMsg = messages.firstOrNull { !it.extractedOtp.isNullOrBlank() }
        val otpCode = foundMsg?.extractedOtp
        if (foundMsg != null && !otpCode.isNullOrBlank()) {
            updateSavedAccountOtp(currentEmail, otpCode, foundMsg.subject)
        }

        messages
    }

    private suspend fun fetchTempMailLolInbox(): List<TempMailMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<TempMailMessage>()
        if (currentToken.isBlank()) return@withContext messages
        try {
            val (code, resp) = fetchWithTimeout("https://api.tempmail.lol/v2/inbox?token=$currentToken", timeoutMs = 6000)
            if (code in 200..299 && !resp.isNullOrBlank()) {
                val root = JSONObject(resp)
                val emailsArr = root.optJSONArray("emails")
                if (emailsArr != null) {
                    for (i in 0 until emailsArr.length()) {
                        val obj = emailsArr.getJSONObject(i)
                        val id = i.toString()
                        val from = obj.optString("from", "Unknown")
                        val subject = obj.optString("subject", "(No Subject)")
                        val body = obj.optString("body", "")
                        val html = obj.optString("html", "")
                        val date = obj.optString("date", "")
                        val cleanBody = body.ifBlank { escapeHTML(html) }
                        val otp = extractOtpCode("$subject $cleanBody") ?: extractOtpCode(html)
                        messages.add(
                            TempMailMessage(
                                id = id,
                                from = from,
                                subject = subject,
                                date = date,
                                body = cleanBody,
                                htmlBody = html,
                                extractedOtp = otp
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        val foundMsg = messages.lastOrNull { !it.extractedOtp.isNullOrBlank() }
        val otpCode = foundMsg?.extractedOtp
        if (foundMsg != null && !otpCode.isNullOrBlank()) {
            updateSavedAccountOtp(currentEmail, otpCode, foundMsg.subject)
        }

        messages
    }

    private suspend fun fetch1SecMailInbox(): List<TempMailMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<TempMailMessage>()
        try {
            val (code, resp) = fetchWithTimeout("${API_1SECMAIL}?action=getMessages&login=$currentUsername&domain=$currentDomain", timeoutMs = 6000)
            if (code == 200 && !resp.isNullOrBlank()) {
                val jsonArr = JSONArray(resp)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val id = obj.optString("id")
                    val from = obj.optString("from", "Unknown")
                    val subject = obj.optString("subject", "(No Subject)")
                    val date = obj.optString("date", "")

                    val details = read1SecMailMessageDetails(id)
                    val bodyText = details?.body ?: subject
                    val otp = extractOtpCode(subject + " " + bodyText) ?: extractOtpCode(details?.htmlBody ?: "")

                    messages.add(
                        TempMailMessage(
                            id = id,
                            from = from,
                            subject = subject,
                            date = formatDate(date),
                            body = bodyText,
                            htmlBody = details?.htmlBody ?: "",
                            extractedOtp = otp
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        messages
    }

    private suspend fun fetchHydraInbox(): Pair<Int, List<TempMailMessage>> = withContext(Dispatchers.IO) {
        if (currentToken.isBlank() && currentPassword.isNotBlank()) {
            currentToken = fetchHydraJwtToken(currentEmail, currentPassword, activeApiBaseUrl)
            saveCurrentAccountState()
        }
        if (currentToken.isBlank()) return@withContext Pair(401, emptyList())

        val messages = mutableListOf<TempMailMessage>()
        val (code, resp) = fetchWithTimeout("$activeApiBaseUrl/messages", method = "GET", token = currentToken, timeoutMs = 6000)

        if (code == 401) {
            return@withContext Pair(401, emptyList())
        }

        if (code == 200 && !resp.isNullOrBlank()) {
            try {
                val memberArray = if (resp.trim().startsWith("{")) {
                    JSONObject(resp).optJSONArray("hydra:member") ?: JSONArray()
                } else {
                    JSONArray(resp)
                }

                for (i in 0 until memberArray.length()) {
                    val item = memberArray.getJSONObject(i)
                    val id = item.optString("id", i.toString())
                    val fromObj = item.optJSONObject("from")
                    val fromAddr = fromObj?.optString("address") ?: item.optString("from", "Unknown")
                    val subject = item.optString("subject", "(No Subject)")
                    val intro = item.optString("intro", "")
                    val createdAt = item.optString("createdAt", "")

                    val dateFormatted = formatDate(createdAt)
                    var otp = extractOtpCode(subject + " " + intro)
                    var bodyText = intro

                    if (otp == null) {
                        val details = readHydraMessageDetails(id)
                        if (details != null) {
                            bodyText = details.body.ifBlank { intro }
                            otp = details.extractedOtp
                        }
                    }

                    messages.add(
                        TempMailMessage(
                            id = id,
                            from = fromAddr,
                            subject = subject,
                            date = dateFormatted,
                            body = bodyText,
                            extractedOtp = otp
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        Pair(code, messages)
    }

    suspend fun readMessageDetails(msgId: String): TempMailMessage? = withContext(Dispatchers.IO) {
        if (currentProvider == "1secmail") {
            return@withContext read1SecMailMessageDetails(msgId)
        } else {
            return@withContext readHydraMessageDetails(msgId)
        }
    }

    private suspend fun readHydraMessageDetails(msgId: String): TempMailMessage? = withContext(Dispatchers.IO) {
        if (currentToken.isBlank()) return@withContext null
        val (code, resp) = fetchWithTimeout("$activeApiBaseUrl/messages/$msgId", method = "GET", token = currentToken, timeoutMs = 6000)

        if (code == 200 && !resp.isNullOrBlank()) {
            try {
                val obj = JSONObject(resp)
                val fromObj = obj.optJSONObject("from")
                val fromAddr = fromObj?.optString("address") ?: obj.optString("from", "Unknown")
                val subject = obj.optString("subject", "(No Subject)")
                val textBody = obj.optString("text", "")
                val htmlArr = obj.optJSONArray("html")
                val htmlBody = if (htmlArr != null && htmlArr.length() > 0) htmlArr.getString(0) else ""
                val createdAt = obj.optString("createdAt", "")

                val cleanBody = textBody.ifBlank { escapeHTML(htmlBody) }
                val otp = extractOtpCode(subject + " " + cleanBody) ?: extractOtpCode(htmlBody)

                return@withContext TempMailMessage(
                    id = msgId,
                    from = fromAddr,
                    subject = subject,
                    date = formatDate(createdAt),
                    body = cleanBody,
                    htmlBody = htmlBody,
                    extractedOtp = otp
                )
            } catch (_: Exception) {}
        }
        null
    }

    suspend fun deleteMessage(msgId: String): Boolean = withContext(Dispatchers.IO) {
        if (currentProvider == "hydra_api" || currentProvider == "mailtm") {
            try {
                val (code, _) = fetchWithTimeout("$activeApiBaseUrl/messages/$msgId", method = "DELETE", token = currentToken, timeoutMs = 5000)
                return@withContext code in 200..299
            } catch (_: Exception) {}
        }
        return@withContext true
    }

    fun escapeHTML(input: String): String {
        if (input.isBlank()) return ""
        return input.replace(Regex("<[^>]*>"), "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    fun formatDate(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        return try {
            if (rawDate.contains("T")) {
                rawDate.take(16).replace("T", " ")
            } else {
                rawDate
            }
        } catch (_: Exception) {
            rawDate
        }
    }

    private suspend fun read1SecMailMessageDetails(msgId: String): TempMailMessage? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${API_1SECMAIL}?action=readMessage&login=$currentUsername&domain=$currentDomain&id=$msgId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()

                val obj = JSONObject(resp)
                val from = obj.optString("from", "Unknown")
                val subject = obj.optString("subject", "(No Subject)")
                val textBody = obj.optString("textBody", "")
                val htmlBody = obj.optString("htmlBody", "")
                val date = obj.optString("date", "")

                val cleanBody = textBody.ifBlank { htmlBody.replace(Regex("<[^>]*>"), "") }
                val otp = extractOtpCode(subject + " " + cleanBody) ?: extractOtpCode(htmlBody)

                return@withContext TempMailMessage(
                    id = msgId,
                    from = from,
                    subject = subject,
                    date = date,
                    body = cleanBody,
                    htmlBody = htmlBody,
                    extractedOtp = otp
                )
            }
        } catch (_: Exception) {}
        null
    }

    private suspend fun readMailTmMessageDetails(msgId: String): TempMailMessage? = withContext(Dispatchers.IO) {
        if (currentToken.isBlank()) return@withContext null
        try {
            val url = URL("$API_MAILTM/messages/$msgId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Authorization", "Bearer $currentToken")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()

                val obj = JSONObject(resp)
                val fromObj = obj.optJSONObject("from")
                val fromAddr = fromObj?.optString("address") ?: obj.optString("from", "Unknown")
                val subject = obj.optString("subject", "(No Subject)")
                val textBody = obj.optString("text", "")
                val htmlArr = obj.optJSONArray("html")
                val htmlBody = if (htmlArr != null && htmlArr.length() > 0) htmlArr.getString(0) else ""
                val createdAt = obj.optString("createdAt", "")

                val cleanBody = textBody.ifBlank { htmlBody.replace(Regex("<[^>]*>"), "") }
                val otp = extractOtpCode(subject + " " + cleanBody) ?: extractOtpCode(htmlBody)

                return@withContext TempMailMessage(
                    id = msgId,
                    from = fromAddr,
                    subject = subject,
                    date = createdAt.take(16).replace("T", " "),
                    body = cleanBody,
                    htmlBody = htmlBody,
                    extractedOtp = otp
                )
            }
        } catch (_: Exception) {}
        null
    }

    fun extractOtpCode(content: String): String? {
        if (content.isBlank()) return null

        // 1. High precision regex patterns for Confirmation code, Meta, FB, Google, OTP
        val keyPatterns = listOf(
            Regex("(?:confirmation\\s+code|verification\\s+code|security\\s+code|confirm\\s+code|login\\s+code|passcode)\\D{0,35}?([0-9]{6})\\b", RegexOption.IGNORE_CASE),
            Regex("(?:FB|Meta|Facebook|Google|G|WhatsApp|IG|Instagram)\\s*[-:_]?\\s*([0-9]{6})\\b", RegexOption.IGNORE_CASE),
            Regex("(?:code|otp|pin)\\s*[:=-]?\\s*([0-9]{6})\\b", RegexOption.IGNORE_CASE),
            Regex("\\b([0-9]{3}[-\\s][0-9]{3})\\b"),
            Regex("(?:is|is:)\\s*([0-9]{6})\\b", RegexOption.IGNORE_CASE),
            Regex("(?:confirmation\\s+code|verification\\s+code|security\\s+code|confirm\\s+code|login\\s+code|passcode)\\D{0,35}?([0-9]{4,8})\\b", RegexOption.IGNORE_CASE),
            Regex("(?:FB|Meta|Facebook|Google|G|WhatsApp|IG|Instagram)\\s*[-:_]?\\s*([0-9]{4,8})", RegexOption.IGNORE_CASE),
            Regex("(?:code|otp|pin|passcode|verification|security|confirm|confirmation)\\s*[:=-]?\\s*([0-9]{4,8})", RegexOption.IGNORE_CASE)
        )

        for (regex in keyPatterns) {
            val match = regex.find(content)
            if (match != null) {
                val candidate = match.groupValues.lastOrNull()?.replace("-", "")?.replace(" ", "")
                if (!candidate.isNullOrBlank() && candidate.length in 4..8) {
                    return candidate
                }
            }
        }

        // 2. If content is an auth or confirmation email, extract genuine 6-digit code
        val lower = content.lowercase(Locale.ROOT)
        if (lower.contains("confirmation") || lower.contains("verification") || lower.contains("security") ||
            lower.contains("facebook") || lower.contains("meta") || lower.contains("google") ||
            lower.contains("whatsapp") || lower.contains("instagram") || lower.contains("code")
        ) {
            val sixDigitRegex = Regex("\\b([0-9]{6})\\b")
            val matches = sixDigitRegex.findAll(content)
            for (m in matches) {
                val num = m.value
                if (num != "202400" && num != "202500" && num != "202600") {
                    return num
                }
            }
        }

        return null
    }

    private fun saveCurrentAccountState() {
        prefs.edit()
            .putString(KEY_CURRENT_EMAIL, currentEmail)
            .putString(KEY_CURRENT_PASS, currentPassword)
            .putString(KEY_CURRENT_USER, currentUsername)
            .putString(KEY_CURRENT_TOKEN, currentToken)
            .putString(KEY_CURRENT_DOMAIN, currentDomain)
            .putString(KEY_CURRENT_PROVIDER, currentProvider)
            .apply()
    }

    fun getSavedAccounts(): List<SavedTempMailAccount> {
        val jsonStr = prefs.getString(KEY_SAVED_ACCOUNTS, "[]") ?: "[]"
        val list = mutableListOf<SavedTempMailAccount>()
        try {
            val jsonArr = JSONArray(jsonStr)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                list.add(
                    SavedTempMailAccount(
                        email = obj.optString("email"),
                        password = obj.optString("password"),
                        domain = obj.optString("domain"),
                        username = obj.optString("username"),
                        createdAt = obj.optString("createdAt"),
                        token = obj.optString("token"),
                        provider = obj.optString("provider", "1secmail"),
                        lastReceivedOtp = obj.optString("lastReceivedOtp", ""),
                        lastSubject = obj.optString("lastSubject", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun updateSavedAccountOtp(email: String, otp: String, subject: String = "") {
        if (email.isBlank() || otp.isBlank()) return
        val currentList = getSavedAccounts().toMutableList()
        val index = currentList.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        if (index != -1) {
            val old = currentList[index]
            currentList[index] = old.copy(lastReceivedOtp = otp, lastSubject = subject.ifBlank { old.lastSubject })
        } else {
            val nowStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            currentList.add(0, SavedTempMailAccount(
                email = email,
                password = currentPassword,
                domain = currentDomain,
                username = currentUsername,
                createdAt = nowStr,
                token = currentToken,
                provider = currentProvider,
                lastReceivedOtp = otp,
                lastSubject = subject
            ))
        }
        val trimmed = currentList.take(50)
        val jsonArr = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("email", item.email)
                put("password", item.password)
                put("domain", item.domain)
                put("username", item.username)
                put("createdAt", item.createdAt)
                put("token", item.token)
                put("provider", item.provider)
                put("lastReceivedOtp", item.lastReceivedOtp)
                put("lastSubject", item.lastSubject)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_ACCOUNTS, jsonArr.toString()).apply()
    }

    fun getSheetAccounts(): List<SavedTempMailAccount> {
        val list = mutableListOf<SavedTempMailAccount>()
        val json = prefs.getString(KEY_SHEET_ACCOUNTS, null) ?: return list
        try {
            val jsonArr = JSONArray(json)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                list.add(
                    SavedTempMailAccount(
                        email = obj.optString("email"),
                        password = obj.optString("password"),
                        domain = obj.optString("domain"),
                        username = obj.optString("username"),
                        createdAt = obj.optString("createdAt"),
                        token = obj.optString("token"),
                        provider = obj.optString("provider", "temp_mail_io"),
                        lastReceivedOtp = obj.optString("lastReceivedOtp", ""),
                        lastSubject = obj.optString("lastSubject", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveAccountToSheet(account: SavedTempMailAccount) {
        val currentList = getSheetAccounts().toMutableList()
        currentList.removeAll { it.email.equals(account.email, ignoreCase = true) }
        currentList.add(0, account)
        val jsonArr = JSONArray()
        for (item in currentList) {
            val obj = JSONObject().apply {
                put("email", item.email)
                put("password", item.password)
                put("domain", item.domain)
                put("username", item.username)
                put("createdAt", item.createdAt)
                put("token", item.token)
                put("provider", item.provider)
                put("lastReceivedOtp", item.lastReceivedOtp)
                put("lastSubject", item.lastSubject)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SHEET_ACCOUNTS, jsonArr.toString()).apply()
    }

    fun deleteSheetAccount(email: String) {
        val currentList = getSheetAccounts().toMutableList()
        currentList.removeAll { it.email.equals(email, ignoreCase = true) }
        val jsonArr = JSONArray()
        for (item in currentList) {
            val obj = JSONObject().apply {
                put("email", item.email)
                put("password", item.password)
                put("domain", item.domain)
                put("username", item.username)
                put("createdAt", item.createdAt)
                put("token", item.token)
                put("provider", item.provider)
                put("lastReceivedOtp", item.lastReceivedOtp)
                put("lastSubject", item.lastSubject)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SHEET_ACCOUNTS, jsonArr.toString()).apply()
    }

    fun clearSheet() {
        prefs.edit().remove(KEY_SHEET_ACCOUNTS).apply()
    }

    fun saveCredential(login: String, password: String) {
        val nowStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val obj = JSONObject().apply {
            put("login", login)
            put("password", password)
            put("createdAt", nowStr)
        }
        prefs.edit().putString(KEY_CREDENTIALS, obj.toString()).apply()
    }

    fun getLatestCredential(): Pair<String, String>? {
        val jsonStr = prefs.getString(KEY_CREDENTIALS, null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            Pair(obj.getString("login"), obj.getString("password"))
        } catch (_: Exception) { null }
    }

    suspend fun extractLatestOtp(excludeOtp: String? = null): Pair<String?, String?> = withContext(Dispatchers.IO) {
        if (currentEmail.isBlank()) return@withContext Pair(null, null)
        try {
            val messages = fetchInbox()
            // Messages on tempmail.lol / inbox are appended; reverse to inspect the newest message at the bottom first
            val reversed = messages.asReversed()

            // 1. Look for a fresh OTP that does not match excludeOtp (critical for 2-step verification)
            for (msg in reversed) {
                val otp = msg.extractedOtp ?: extractOtpCode("${msg.subject} ${msg.body}")
                if (!otp.isNullOrBlank() && (excludeOtp == null || otp != excludeOtp)) {
                    updateSavedAccountOtp(currentEmail, otp, msg.subject)
                    return@withContext Pair(otp, msg.subject)
                }
            }

            // 2. Fallback to any valid OTP from the newest messages
            for (msg in reversed) {
                val otp = msg.extractedOtp ?: extractOtpCode("${msg.subject} ${msg.body}")
                if (!otp.isNullOrBlank()) {
                    updateSavedAccountOtp(currentEmail, otp, msg.subject)
                    return@withContext Pair(otp, msg.subject)
                }
            }

            if (reversed.isNotEmpty()) {
                val latest = reversed.first()
                val details = readMessageDetails(latest.id)
                val otp = details?.extractedOtp ?: extractOtpCode("${details?.subject ?: ""} ${details?.body ?: ""}")
                if (!otp.isNullOrBlank()) {
                    updateSavedAccountOtp(currentEmail, otp, details?.subject ?: "")
                    return@withContext Pair(otp, details?.subject ?: "")
                }
            }
        } catch (_: Exception) {}
        Pair(null, null)
    }

    private fun saveAccountToHistory(account: SavedTempMailAccount) {
        val currentList = getSavedAccounts().toMutableList()
        currentList.removeAll { it.email.equals(account.email, ignoreCase = true) }
        currentList.add(0, account)
        val trimmed = currentList.take(50)

        val jsonArr = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("email", item.email)
                put("password", item.password)
                put("domain", item.domain)
                put("username", item.username)
                put("createdAt", item.createdAt)
                put("token", item.token)
                put("provider", item.provider)
                put("lastReceivedOtp", item.lastReceivedOtp)
                put("lastSubject", item.lastSubject)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_ACCOUNTS, jsonArr.toString()).apply()
    }

    suspend fun switchToAccount(account: SavedTempMailAccount): String = withContext(Dispatchers.IO) {
        currentEmail = account.email
        currentPassword = account.password
        currentUsername = account.username
        currentDomain = account.domain
        currentProvider = account.provider
        currentToken = account.token

        if (currentProvider == "mailtm" && currentToken.isBlank()) {
            currentToken = fetchHydraJwtToken(account.email, account.password)
        }
        saveCurrentAccountState()
        account.email
    }

    fun clearActiveOtpState() {
        val current = currentEmail
        if (current.isNotBlank()) {
            val currentList = getSavedAccounts().toMutableList()
            val index = currentList.indexOfFirst { it.email.equals(current, ignoreCase = true) }
            if (index != -1) {
                val old = currentList[index]
                currentList[index] = old.copy(lastReceivedOtp = "", lastSubject = "")
                val jsonArr = JSONArray()
                for (item in currentList) {
                    val obj = JSONObject().apply {
                        put("email", item.email)
                        put("password", item.password)
                        put("domain", item.domain)
                        put("username", item.username)
                        put("createdAt", item.createdAt)
                        put("token", item.token)
                        put("provider", item.provider)
                        put("lastReceivedOtp", item.lastReceivedOtp)
                        put("lastSubject", item.lastSubject)
                    }
                    jsonArr.put(obj)
                }
                prefs.edit().putString(KEY_SAVED_ACCOUNTS, jsonArr.toString()).apply()
            }
        }
    }

    fun getSavedCredentials(): List<AuthCredential> {
        val json = prefs.getString(KEY_CREDENTIALS, "[]") ?: "[]"
        val list = mutableListOf<AuthCredential>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AuthCredential(
                        obj.optString("firstName"),
                        obj.optString("login"),
                        obj.optString("password")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCredential(cred: AuthCredential) {
        val currentList = getSavedCredentials().toMutableList()
        // Keep only 2
        currentList.add(0, cred)
        val trimmed = currentList.take(2)
        val jsonArr = JSONArray()
        for (item in trimmed) {
            jsonArr.put(JSONObject().apply {
                put("firstName", item.firstName)
                put("login", item.login)
                put("password", item.password)
            })
        }
        prefs.edit().putString(KEY_CREDENTIALS, jsonArr.toString()).apply()
    }

    fun getImportantNotes(): List<String> {
        val json = prefs.getString(KEY_IMPORTANT_NOTES, "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun getActiveStarredNote(): String? {
        val saved = prefs.getString("key_active_starred_note_v1", null)
        val list = getImportantNotes()
        if (saved != null && list.contains(saved)) {
            return saved
        }
        return list.firstOrNull()
    }

    fun setActiveStarredNote(note: String?) {
        if (note.isNullOrBlank()) {
            prefs.edit().remove("key_active_starred_note_v1").apply()
        } else {
            prefs.edit().putString("key_active_starred_note_v1", note).apply()
        }
    }

    fun saveImportantNote(note: String) {
        if (note.isBlank()) return
        val currentList = getImportantNotes().toMutableList()
        currentList.removeAll { it == note }
        currentList.add(0, note)
        val trimmed = currentList.take(30) // Keep up to 30
        val jsonArr = JSONArray()
        for (item in trimmed) {
            jsonArr.put(item)
        }
        prefs.edit().putString(KEY_IMPORTANT_NOTES, jsonArr.toString()).apply()
        if (getActiveStarredNote() == null) {
            setActiveStarredNote(note)
        }
    }

    fun deleteImportantNote(note: String) {
        val currentList = getImportantNotes().toMutableList()
        currentList.remove(note)
        val jsonArr = JSONArray()
        for (item in currentList) {
            jsonArr.put(item)
        }
        prefs.edit().putString(KEY_IMPORTANT_NOTES, jsonArr.toString()).apply()
        if (prefs.getString("key_active_starred_note_v1", null) == note) {
            setActiveStarredNote(currentList.firstOrNull())
        }
    }

    fun deleteSavedAccount(email: String) {
        val currentList = getSavedAccounts().toMutableList()
        currentList.removeAll { it.email.equals(email, ignoreCase = true) }

        val jsonArr = JSONArray()
        for (item in currentList) {
            val obj = JSONObject().apply {
                put("email", item.email)
                put("password", item.password)
                put("domain", item.domain)
                put("username", item.username)
                put("createdAt", item.createdAt)
                put("token", item.token)
                put("provider", item.provider)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_ACCOUNTS, jsonArr.toString()).apply()
    }
}
