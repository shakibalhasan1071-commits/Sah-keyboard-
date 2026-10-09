package com.example.moekeyboard.ime

import android.content.Context
import android.content.SharedPreferences

/**
 * Smart Word Suggestions, Predictive Next-Word Typing & Bangla Auto-Correction Engine.
 * Features:
 * 1. Continuous Next-Word Prediction Chain (e.g., "I" -> "love" -> "you" -> "❤️", "আমি" -> "তোমাকে" -> "ভালোবাসি" -> "❤️")
 * 2. Prefix completion (e.g., "lo" -> "love", "বাং" -> "বাংলা", "বাংলাদেশ")
 * 3. Bangla & Banglish typo auto-correction (e.g., "amj" -> "ami", "tmkr" -> "tomar", "কাছ" -> "কাজ")
 * 4. User-adaptive vocabulary & emoji phrase learning (e.g., "ummmmmmmhaaa ❤️", "love you ❤️")
 * 5. N-gram Transition Learning (learns which word/emoji frequently follows another)
 */
object SmartSuggestionEngine {

    private const val PREFS_NAME = "moe_user_vocab"
    private const val TRANSITIONS_PREFS = "moe_user_transitions"
    private var prefs: SharedPreferences? = null
    private var transPrefs: SharedPreferences? = null

    private val userFreqMap = java.util.concurrent.ConcurrentHashMap<String, Int>()
    // Transitions map: key = prevWord.lowercase(), value = mapOf(nextWord to frequency)
    private val userTransitionsMap = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Int>>()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    val appContext = context.applicationContext
                    prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    
                    // Pre-seed user email to ensure it is always remembered and suggested
                    if (!prefs!!.contains("shakibalhasan1024@gmail.com")) {
                        prefs!!.edit().putInt("shakibalhasan1024@gmail.com", 100).apply()
                    }

                    // Pre-seed user's romantic couple pet phrases & slangs
                    val couplePhrases = listOf(
                        "ummmmmmmhaaa ❤️", "love you so much ❤️", "amar bow ❤️ jannn",
                        "ummmmmmmmah jan 😇🥹🥹", "onk vlobashi jaan", "sona paki amar ❤️",
                        "goman jannnnnnnn ❤️🔥💙", "goamnnn tataaaa ummmmmmmhaaa ❤️",
                        "gomaw jaan ummmmmmmmmmmmah jan 😇🥹🥹", "amio gomaitsi jaan ummmmmmmmmmmmah jan 😇🥹🥹",
                        "good night love you so much 🔥 my husband onk vlobashi jaan ummmmmmmmmmmmah jan 😇🥹🥹"
                    )
                    couplePhrases.forEach { phrase ->
                        if (!prefs!!.contains(phrase)) {
                            prefs!!.edit().putInt(phrase, 80).apply()
                        }
                    }
                    
                    prefs?.all?.forEach { (key, value) ->
                        if (value is Int) {
                            userFreqMap[key] = value
                        }
                    }

                    transPrefs = appContext.getSharedPreferences(TRANSITIONS_PREFS, Context.MODE_PRIVATE)
                    transPrefs?.all?.forEach { (key, value) ->
                        // format in transPrefs: "prevWord|||nextWord" -> count
                        if (value is Int && key.contains("|||")) {
                            val parts = key.split("|||", limit = 2)
                            if (parts.size == 2) {
                                val prev = parts[0].lowercase()
                                val next = parts[1]
                                val map = userTransitionsMap.getOrPut(prev) { java.util.concurrent.ConcurrentHashMap() }
                                map[next] = value
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-correction mapping for common Bangla & English typos
    // Focuses on Banglish typo correction (staying in Roman script) as per user request
    val AUTO_CORRECTIONS = mapOf(
        // User requested: "amj" -> "ami", "tmkr" -> "tomar", "tmke" -> "tomake"
        "amj" to "ami",
        "amk" to "amake",
        "tmk" to "tomake",
        "tmke" to "tomake",
        "amr" to "amar",
        "tmr" to "tomar",
        "tmkr" to "tomar",
        "apnr" to "apnar",
        "korki" to "korchi",
        "korsis" to "korchis",
        "korsen" to "korchen",
        "aschi" to "aschi",
        "jacci" to "jacci",
        "khub" to "khub",
        "vlo" to "valo",
        "bhalo" to "valo",
        "valoa" to "valo",
        "kisu" to "kichu",
        "shob" to "sob",
        "shobai" to "sobai",
        "bondhu" to "bondhu",
        "khobor" to "khobor",
        "kalke" to "kalke",
        "ajke" to "ajke",
        "asho" to "aso",
        "khacchi" to "khacci",
        "khabo" to "khabo",
        "bashay" to "basay",
        "shathe" to "sathe",
        
        // Typo Corrections (Bangla script to Bangla script)
        "কাছ" to "কাজ",
        "আমী" to "আমি",
        "ভাল" to "ভালো",
        "হইসে" to "হয়েছে",
        "করতিছি" to "করছি",
        "করবনা" to "করব না",
        "করসি" to "করেছি",
        "আসতেসি" to "আসছি",
        "খবরর" to "খবর",
        "ধন্যবাদদ" to "ধন্যবাদ",
        "ধন্নবাদ" to "ধন্যবাদ",
        "সালামুআলাইকুম" to "আসসালামু আলাইকুম",
        "কেম্ন" to "কেমন",
        "বাংলদেশ" to "বাংলাদেশ",
        "ব্যাবহার" to "ব্যবহার",
        "কিসু" to "কিছু",
        "সুপ্রভাত" to "শুভ সকাল",
        
        // English Typos
        "teh" to "the",
        "adn" to "and",
        "waht" to "what",
        "yuo" to "you",
        "loev" to "love",
        "wrok" to "work",
        "peopel" to "people"
    )

    // Next-word prediction table (N-gram)
    private val NEXT_WORD_PREDICTIONS = mapOf(
        // English Next-Word Predictions & Chains
        "i" to listOf("love", "am", "want", "think", "need", "miss", "will", "have"),
        "I" to listOf("love", "am", "want", "think", "will", "miss", "have", "know"),
        "love" to listOf("you", "u", "you so much", "❤️", "🥰", "😘", "💖", "😍", "💕", "forever"),
        "you" to listOf("are", "too", "so much", "❤️", "🥰", "😘", "💖", "have", "can"),
        "u" to listOf("too", "are", "so much", "❤️", "🥰", "😘", "know"),
        "miss" to listOf("you", "u", "you so much", "❤️", "🥰", "😘", "💖", "kori"),
        "my" to listOf("love", "name", "friend", "heart", "life", "❤️", "🥰", "💖"),
        "how" to listOf("are", "is", "about", "do", "you"),
        "what" to listOf("is", "are", "do", "happened", "about"),
        "thank" to listOf("you", "you so much", "u", "God", "very much"),
        "good" to listOf("morning", "night", "afternoon", "evening", "job", "luck"),
        "morning" to listOf("☀️", "have a great day", "dear", "bhai", "❤️", "🥰"),
        "night" to listOf("🌙", "sweet dreams", "dear", "bhai", "❤️", "🥰"),
        "happy" to listOf("birthday", "new year", "anniversary", "journey", "❤️", "🥰", "🎂"),
        "birthday" to listOf("🎂", "to you", "brother", "friend", "❤️", "🥰", "celebration"),
        "take" to listOf("care", "it easy", "time", "your time"),
        "see" to listOf("you", "you soon", "later", "tomorrow"),
        "we" to listOf("are", "can", "will", "have", "need"),
        "he" to listOf("is", "was", "has", "can", "said"),
        "she" to listOf("is", "was", "has", "can", "said"),
        "it" to listOf("is", "was", "will", "can", "works"),
        "this" to listOf("is", "was", "will", "app", "time"),
        "let" to listOf("me", "us", "go", "know"),

        // Cute / Love / Emotional Expressions (User requested: ummmmmmmhaaa ❤️, etc.)
        "gomaw" to listOf("jaan ummmmmmmmmmmmah jan 😇🥹🥹", "jan 😇🥹🥹", "amio gomaitsi", "❤️", "🥰"),
        "goman" to listOf("jannnnnnnn ❤️🔥💙", "tataaaa ummmmmmmhaaa ❤️", "amio gomamu", "jan"),
        "goamnnn" to listOf("tataaaa ummmmmmmhaaa ❤️", "jannn", "amio gomamu jannn"),
        "tataaaa" to listOf("jaan paki jai jaam tmio jaw jan gmaw jan amio gomaitsi jaan ummmmmmmmmmmmah jan 😇🥹🥹", "jaan", "sona paki amar 😇🥰😌"),
        "husband" to listOf("onk vlobashi jaan ummmmmmmmmmmmah jan 😇🥹🥹", "amar", "❤️"),
        "onk" to listOf("vlobashi jaan ummmmmmmmmmmmah jan 😇🥹🥹", "valo", "❤️", "🥰"),
        "vlobashi" to listOf("jaan ummmmmmmmmmmmah jan 😇🥹🥹", "jaaaaan valo ❤️", "tomake", "🥰"),
        "sona" to listOf("paki amar onk vlobashi jaaaaan valo ❤️", "babu", "jan", "🥰"),
        "paki" to listOf("amar onk vlobashi jaaaaan valo ❤️", "jaan", "🥰"),
        "ummmmmmmhaaa" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "love you", "babu", "jan", "shona"),
        "ummmmmmmhaa" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "love you", "babu", "jan"),
        "ummmha" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "love you", "babu", "jan"),
        "ummah" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "love you", "babu", "jan"),
        "ummm" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "love you", "babu", "jan"),
        "umma" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "love you", "babu", "jan"),
        "kiss" to listOf("you", "❤️", "🥰", "😘", "💖", "😍", "💋", "baby"),
        "baby" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "kemon aso", "ki koro", "love you"),
        "babu" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "kemon aso", "ki koro", "khobor ki", "kheyecho"),
        "jan" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "kemon aso", "ki koro", "valo aso", "love you"),
        "jaan" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "kemon aso", "ki koro", "valo aso", "love you"),
        "shona" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "kemon aso", "ki koro", "khobor ki"),

        // Bangla Next-Word Predictions & Chains
        "আমার" to listOf("নাম", "সোনার", "দেশ", "ভালোবাসা", "বন্ধু", "❤️", "🥰", "💖"),
        "আমি" to listOf("তোমাকে", "ভালো", "আছি", "ভালোবাসি", "করছি", "যাব", "বলছি"),
        "তোমাকে" to listOf("ভালোবাসি", "অনেক", "চাই", "মিস করছি", "ছাড়া", "❤️", "🥰", "😘", "💖"),
        "ভালোবাসি" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "অনেক", "বাবু", "সোনা", "জান", "তোমাকে"),
        "তুমি" to listOf("কেমন", "কোথায়", "কি", "আছো", "বল", "যাবে", "আমার"),
        "কেমন" to listOf("আছো", "আছেন", "হলো", "চলছে", "লাগল"),
        "কি" to listOf("খবর", "করছ", "করছেন", "হয়েছে", "হলো", "অবস্থা"),
        "শুভ" to listOf("সকাল", "রাত্রি", "জন্মদিন", "কামনা", "দিন", "সন্ধ্যা", "নববর্ষ"),
        "জন্মদিন" to listOf("🎂", "এর শুভেচ্ছা", "অনেক অনেক শুভেচ্ছা", "অভিনন্দন", "❤️", "🥰"),
        "জন্মদিনের" to listOf("অনেক শুভেচ্ছা", "শুভেচ্ছা", "অভিনন্দন", "❤️", "🥰", "🎂"),
        "অনেক" to listOf("ভালোবাসা", "ধন্যবাদ", "সুন্দর", "ভালো", "দেরি", "❤️", "🥰"),
        "আসসালামু" to listOf("আলাইকুম", "আলাইকুম ওয়া"),
        "কাজ" to listOf("করছি", "শেষ", "হয়েছে", "করব", "আছে"),
        "কোথায়" to listOf("আছো", "যাচ্ছ", "গেলে", "আছেন"),
        "তোমার" to listOf("নাম", "বাড়ি", "খবর", "সাথে", "কথা", "মন"),
        "বাংলাদেশ" to listOf("আমার", "দেশ", "জিন্দাবাদ", "ক্রিকেট"),
        "সবাইকে" to listOf("ধন্যবাদ", "স্বাগতম", "শুভেচ্ছা", "সালাম"),
        "খুব" to listOf("ভালো", "সুন্দর", "সুন্দর হয়েছে", "তাড়াতাড়ি", "ভালোবাসি"),
        "ধন্যবাদ" to listOf("ভাই", "আপনাকে", "তোমাকে", "সবাইকে", "অনেক", "❤️", "🥰"),

        // Banglish Next-Word Predictions & Chains ("ami" -> "tmke" -> "bhalobashi" -> "❤️")
        "ami" to listOf("tmke", "tomake", "valo", "bhalobashi", "valobashi", "achi", "jabo", "aschi", "kori", "bolchi"),
        "tmke" to listOf("bhalobashi", "valobashi", "onek", "chai", "miss kori", "❤️", "🥰", "😘", "💖"),
        "tomake" to listOf("bhalobashi", "valobashi", "onek", "chai", "miss kori", "❤️", "🥰", "😘", "💖"),
        "bhalobashi" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "onek", "shona", "babu", "jan", "tmke", "tomake"),
        "valobashi" to listOf("❤️", "🥰", "😘", "💖", "😍", "💕", "💋", "onek", "shona", "babu", "jan", "tmke", "tomake"),
        "tmkr" to listOf("nam", "sathe", "khobor", "katha", "bari", "phone", "chobi", "mon"),
        "tomar" to listOf("nam", "sathe", "khobor", "katha", "bari", "phone", "chobi", "mon"),
        "amr" to listOf("nam", "mon", "desh", "kaje", "bondhu", "bari", "phone", "bhalobasha"),
        "amar" to listOf("nam", "mon", "desh", "kaje", "bondhu", "bari", "phone", "bhalobasha"),
        "tumi" to listOf("kemon", "kothay", "ki", "aso", "acho", "bol", "yabe"),
        "apni" to listOf("kemon", "kothay", "ki", "asen", "bolen", "jaben"),
        "kemon" to listOf("aso", "acho", "achen", "asen", "cholche", "laglo"),
        "ki" to listOf("khobor", "korso", "korchen", "korcho", "obostha", "bolbo"),
        "kothay" to listOf("aso", "acho", "jaco", "asen", "geso"),
        "valo" to listOf("achi", "lagse", "lago", "thako", "❤️", "🥰", "💖"),
        "bhalo" to listOf("achi", "lagse", "lago", "thako", "❤️", "🥰", "💖"),
        "khub" to listOf("valo", "bhalo", "sundor", "taratari", "bhalobashi"),
        "dhonnobad" to listOf("bhai", "apnake", "tomake", "sobaike", "onek", "❤️", "🥰"),
        "assalamu" to listOf("alaikum", "alaikum wa"),
        "kalke" to listOf("jabo", "dekha", "hobe", "asbo"),
        "ajke" to listOf("jabo", "dekha", "hobe", "asbo", "kaje")
    )

    // Predictive Visual Word Relationship Maps (Focus on Banglish & English)
    val WORD_RELATIONSHIP_MAP = mapOf(
        // English Relationship Mappings
        "th" to listOf("the", "this", "that", "think", "there", "though", "thing", "three"),
        "wh" to listOf("what", "where", "when", "why", "which", "who", "whose"),
        "sh" to listOf("she", "should", "shall", "share", "short", "show"),
        "ch" to listOf("change", "check", "chance", "child", "choose"),
        "ha" to listOf("have", "has", "had", "happy", "happen", "hand"),
        "wi" to listOf("with", "will", "wish", "win", "winter", "without"),
        "yo" to listOf("you", "your", "yours", "yourself", "young"),

        // Rich Banglish Relationship Maps
        "am" to listOf("ami", "amar", "amake", "amader", "amra", "amio"),
        "tm" to listOf("tumi", "tomar", "tomake", "tomader", "tomra"),
        "tom" to listOf("tumi", "tomar", "tomake", "tomader", "tomra"),
        "kor" to listOf("kore", "koro", "korbo", "korchi", "koris", "korchen"),
        "bha" to listOf("bhalo", "bhalobasha", "bhabhi", "bhabi", "bhablam", "bhalobasi"),
        "vlo" to listOf("valo", "valoi", "valobasha", "valobasi", "valobashi"),
        "val" to listOf("valo", "valoi", "valobasha", "valobasi", "valobashi"),
        "kha" to listOf("khabo", "khacci", "khao", "khelam", "khabar", "khacchi"),
        "ja" to listOf("jabo", "jacci", "jao", "jantam", "jawa", "jacchen"),
        "as" to listOf("asbo", "aschi", "aso", "ashol", "aslama", "aschen"),
        "bol" to listOf("bolbo", "bolchi", "bolo", "bollum", "bolla", "bolte"),
        "dek" to listOf("dekhi", "dekho", "dekhbo", "dekhlam", "dekhchi"),
        "ki" to listOf("kintu", "kichu", "khobor", "kibhabe", "ki", "kine"),
        "ap" to listOf("apni", "apnar", "apnake", "apnader"),
        "khe" to listOf("khela", "khelbo", "khelchi", "khelos"),
        "pa" to listOf("pabo", "pachi", "pao", "palam", "pawa"),
        "dha" to listOf("dhonnobad", "dhaka", "dhoro", "dhori"),
        "gho" to listOf("ghor", "ghure", "ghurbo", "ghoray"),
        "som" to listOf("somoy", "somosya", "sundor", "somoyot"),
        "sho" to listOf("shob", "shobai", "shundor", "shuru", "shudhu"),
        "cho" to listOf("chole", "cholbo", "cholchi", "cholo"),
        "no" to listOf("now", "not", "note", "noito", "naki"),
        "le" to listOf("likhe", "likhbo", "likhchi", "lekhok"),
        "um" to listOf("ummmmmmmhaaa ❤️", "ummmmmmmhaaa", "ummah", "ummm", "umma")
    )

    // Common word lists for word completions
    private val COMMON_WORDS_BN = listOf(
        "আমি", "আমার", "আমাদের", "তুমি", "তোমার", "তোমাদের", "আপনি", "আপনার",
        "ভালো", "ভালোবাসা", "কাজ", "করছি", "করব", "করবেন", "কেমন", "কোথায়",
        "খবর", "শুভ", "সকাল", "রাত", "ধন্যবাদ", "স্বাগতম", "বাংলাদেশ", "বাংলা",
        "সুন্দর", "সবাই", "সবাইকে", "এখন", "আজকে", "কালকে", "বন্ধু", "ভাই",
        "বই", "ফোন", "সময়", "কথা", "দেখা", "হবে", "আছে", "ছিল", "হয়েছে", "যাব"
    )

    private val COMMON_WORDS_EN = listOf(
        "the", "and", "you", "that", "was", "for", "are", "with", "his", "they",
        "this", "have", "from", "one", "had", "word", "what", "some", "we", "can",
        "out", "other", "were", "all", "there", "when", "up", "use", "your", "how",
        "said", "an", "each", "she", "which", "do", "their", "time", "if", "will",
        "way", "about", "many", "then", "them", "write", "would", "like", "so",
        "these", "her", "long", "make", "thing", "see", "him", "two", "has", "look",
        "more", "day", "could", "go", "come", "did", "number", "sound", "no", "most",
        "people", "my", "over", "know", "water", "than", "call", "first", "who",
        "may", "down", "side", "been", "now", "find", "head", "stand", "own", "page",
        "should", "country", "found", "answer", "school", "grow", "study", "still",
        "learn", "plant", "cover", "food", "sun", "four", "between", "state", "keep",
        "eye", "never", "last", "let", "thought", "city", "tree", "cross", "farm",
        "hard", "start", "might", "story", "saw", "far", "sea", "draw", "left",
        "late", "run", "don't", "while", "press", "close", "night", "real", "life",
        "few", "north", "open", "seem", "together", "next", "white", "children",
        "begin", "got", "walk", "example", "ease", "paper", "group", "always",
        "music", "those", "both", "mark", "often", "letter", "until", "mile",
        "river", "car", "feet", "care", "second", "book", "carry", "took", "rain",
        "eat", "room", "friend", "began", "idea", "fish", "mountain", "stop",
        "once", "base", "hear", "horse", "cut", "sure", "watch", "color", "face",
        "wood", "main", "enough", "plain", "girl", "usual", "young", "ready",
        "above", "ever", "red", "list", "though", "feel", "talk", "bird", "soon",
        "body", "dog", "family", "direct", "pose", "leave", "song", "measure",
        "door", "product", "black", "short", "numeral", "class", "wind", "question",
        "happen", "complete", "ship", "area", "half", "rock", "order", "fire",
        "south", "problem", "piece", "told", "knew", "pass", "since", "top", "whole",
        "king", "street", "inch", "multiply", "nothing", "course", "stay", "wheel",
        "full", "force", "blue", "object", "decide", "surface", "deep", "moon",
        "island", "foot", "system", "busy", "test", "record", "boat", "common",
        "gold", "possible", "plane", "stead", "dry", "wonder", "laugh", "thousand",
        "ago", "ran", "check", "game", "shape", "equate", "hot", "miss", "brought",
        "heat", "snow", "tire", "bring", "yes", "distant", "fill", "east", "paint",
        "language", "among", "unit", "power", "town", "fine", "certain", "fly",
        "fall", "lead", "cry", "dark", "machine", "note", "wait", "plan", "figure",
        "star", "box", "noun", "field", "rest", "correct", "able", "pound", "done",
        "beauty", "drive", "stood", "contain", "front", "teach", "week", "final",
        "gave", "green", "oh", "quick", "develop", "ocean", "warm", "free", "minute",
        "strong", "special", "mind", "behind", "clear", "tail", "produce", "fact",
        "space", "heard", "best", "hour", "better", "true", "during", "hundred",
        "five", "remember", "step", "early", "hold", "west", "ground", "interest",
        "reach", "fast", "verb", "sing", "listen", "six", "table", "travel", "less",
        "morning", "ten", "simple", "several", "vowel", "toward", "war", "lay",
        "against", "pattern", "slow", "center", "love", "person", "money", "serve",
        "appear", "road", "map", "rain", "rule", "govern", "pull", "cold", "notice",
        "voice", "unit", "power", "town", "fine", "certain", "fly", "fall", "lead",
        "shakib", "welcome", "please", "thanks", "hello", "hi", "how"
    )

    /**
     * Get suggestions based on current text buffer and current active word.
     * Supports CONTINUOUS NEXT-WORD PREDICTIONS when currentWord is empty!
     */
    fun getSuggestions(
        currentWord: String,
        previousWord: String? = null,
        isBengali: Boolean = false
    ): List<String> {
        val results = linkedSetOf<String>()
        val trimmedCurrent = currentWord.trim()
        val trimmedPrev = previousWord?.trim()?.lowercase() ?: ""
        val originalPrev = previousWord?.trim() ?: ""

        // CASE 1: Continuous Next-Word Prediction Chain!
        // When user just completed a word (currentWord is empty) and we have a previousWord (e.g. user typed "I", picked "love")
        // We MUST return the next-word predictions (e.g. for "love" -> ["you", "u", "you so much", "❤️"])!
        if (trimmedCurrent.isEmpty()) {
            if (trimmedPrev.isNotEmpty()) {
                // 1. Check user-learned transitions for previousWord (Highest Priority!)
                userTransitionsMap[trimmedPrev]?.entries
                    ?.sortedByDescending { it.value }
                    ?.map { it.key }
                    ?.forEach { results.add(it) }

                // 2. Built-in Next-Word predictions
                NEXT_WORD_PREDICTIONS[originalPrev]?.forEach { results.add(it) }
                NEXT_WORD_PREDICTIONS[trimmedPrev]?.forEach { results.add(it) }

                // 3. Check learned multi-word phrases that start with previousWord (e.g. "ummmmmmmhaaa ❤️")
                userFreqMap.entries
                    .filter { it.key.startsWith("$originalPrev ", ignoreCase = true) }
                    .sortedByDescending { it.value }
                    .forEach { entry ->
                        val remainder = entry.key.substring(originalPrev.length).trim()
                        if (remainder.isNotEmpty()) {
                            results.add(remainder)
                        }
                    }

                // 4. Return top predictions if any found
                if (results.isNotEmpty()) {
                    return results.take(8).toList()
                }
            }

            // If no previous word or empty, return empty list so the default symbol/emoji fallback strip is shown!
            return emptyList()
        }

        // CASE 2: Active Word Typing & Prefix Matching
        // Pre-seed and prioritize user's Gmail(s)
        val email = "shakibalhasan1024@gmail.com"
        if (!isBengali && trimmedCurrent.isNotEmpty()) {
            val lowerCurrent = trimmedCurrent.lowercase()
            if (email.startsWith(lowerCurrent) || 
                lowerCurrent == "@" || 
                (lowerCurrent.startsWith("gma") && email.contains(lowerCurrent)) ||
                (email.contains(lowerCurrent) && lowerCurrent.length >= 3)) {
                results.add(email)
            }
        }
        // 1. ABSOLUTE TOP PRIORITY: User learned words & phrases matching typed prefix (including emojis like "ummmmmmmhaaa ❤️"!)
        val matchingLearnedWords = userFreqMap.entries
            .filter { entry ->
                val key = entry.key
                key.startsWith(trimmedCurrent, ignoreCase = true) ||
                (trimmedCurrent.length >= 2 && key.contains(trimmedCurrent, ignoreCase = true))
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value } // Highest frequency first
                    .thenBy { !it.key.startsWith(trimmedCurrent, ignoreCase = true) } // Exact prefix priority
                    .thenBy { it.key.length } // Closer length priority
            )
            .map { it.key }

        for (learned in matchingLearnedWords) {
            results.add(learned)
            if (results.size >= 5) break
        }

        // 2. Next-word learned transitions if previous word is set and matches prefix
        if (trimmedPrev.isNotEmpty()) {
            userTransitionsMap[trimmedPrev]?.entries
                ?.filter { it.key.startsWith(trimmedCurrent, ignoreCase = true) }
                ?.sortedByDescending { it.value }
                ?.map { it.key }
                ?.forEach { results.add(it) }
        }

        // 3. HIGHEST PRIORITY AUTO-CORRECTIONS (e.g. tmkr -> tomar, amj -> ami)
        AUTO_CORRECTIONS[trimmedCurrent]?.let { results.add(it) }
        AUTO_CORRECTIONS[trimmedCurrent.lowercase()]?.let { results.add(it) }

        // 4. Word Relationship Map (Visual Predictive Mapping for English & Banglish)
        val lower = trimmedCurrent.lowercase()
        WORD_RELATIONSHIP_MAP[lower]?.forEach { results.add(it) }
        
        WORD_RELATIONSHIP_MAP.entries
            .filter { it.key.startsWith(lower) }
            .flatMap { it.value }
            .filter { it.startsWith(lower, ignoreCase = true) }
            .forEach { results.add(it) }

        // 5. Next-word prediction matching typed prefix
        if (trimmedPrev.isNotEmpty()) {
            val list = NEXT_WORD_PREDICTIONS[originalPrev] ?: NEXT_WORD_PREDICTIONS[trimmedPrev]
            list?.filter { it.startsWith(trimmedCurrent, ignoreCase = true) }
                ?.take(4)
                ?.forEach { results.add(it) }
        }

        // 6. Dictionary prefix completions (Bengali / English)
        val dict = if (isBengali) COMMON_WORDS_BN else COMMON_WORDS_EN
        dict.filter { it.startsWith(trimmedCurrent, ignoreCase = true) && it != trimmedCurrent }
            .take(4)
            .forEach { results.add(it) }

        // If still empty or very few, show current word itself
        if (!results.contains(trimmedCurrent) && !trimmedCurrent.contains("\n") && trimmedCurrent.length <= 40) {
            results.add(trimmedCurrent)
        }

        return results.take(8).toList()
    }

    /**
     * Check if a word should be auto-corrected upon space press.
     * Returns the corrected word if found, or null if no correction is needed.
     */
    fun checkAutoCorrection(word: String): String? {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return null
        return AUTO_CORRECTIONS[trimmed] ?: AUTO_CORRECTIONS[trimmed.lowercase()]
    }

    /**
     * Record transition from prevWord to nextWord (e.g. "i" -> "love", "love" -> "you", "ummmmmmmhaaa" -> "❤️")
     */
    fun learnTransition(prevWord: String, nextWord: String, boost: Int = 1) {
        val prev = prevWord.trim().lowercase().trim { " \n\t,।;:\"'".contains(it) }
        val next = nextWord.trim().trim { " \n\t,।;:\"'".contains(it) }
        if (prev.isNotEmpty() && next.isNotEmpty() && prev != next) {
            val map = userTransitionsMap.getOrPut(prev) { java.util.concurrent.ConcurrentHashMap() }
            val count = (map[next] ?: 0) + boost
            map[next] = count
            transPrefs?.edit()?.putInt("$prev|||$next", count)?.apply()
        }
    }

    /**
     * Record typed/seen word or emoji expression to learn user's vocabulary and frequency.
     * Preserves emojis and compound phrases intact (e.g. "ummmmmmmhaaa ❤️").
     */
    fun learnWord(word: String, boost: Int = 1) {
        // Strip edge whitespace & punctuation but keep emojis and symbols!
        val trimmed = word.trim().trim { " \n\t,।;:\"'()[]{}/\\".contains(it) }
        if (trimmed.length in 1..60) {
            val count = (userFreqMap[trimmed] ?: 0) + boost
            userFreqMap[trimmed] = count
            prefs?.edit()?.putInt(trimmed, count)?.apply()

            // Also keep lowercase entry updated for case-insensitive lookup
            val lower = trimmed.lowercase()
            if (lower != trimmed) {
                val lowerCount = (userFreqMap[lower] ?: 0) + boost
                userFreqMap[lower] = lowerCount
                prefs?.edit()?.putInt(lower, lowerCount)?.apply()
            }
        }
    }

    /**
     * Learn multi-word and emoji phrases (e.g. "ummmmmmmhaaa ❤️", "love you ❤️", "good morning ☀️")
     */
    fun learnPhrase(phrase: String, boost: Int = 3) {
        val trimmed = phrase.trim()
        if (trimmed.isNotEmpty() && trimmed.length <= 60) {
            learnWord(trimmed, boost = boost)
            val tokens = trimmed.split(" ").filter { it.isNotBlank() }
            for (i in 0 until tokens.size - 1) {
                learnTransition(tokens[i], tokens[i + 1], boost = boost)
            }
            for (tok in tokens) {
                learnWord(tok, boost = 1)
            }
        }
    }

    /**
     * Learn words and transitions from full text (e.g. copied text, pasted messages)
     */
    fun learnWordsFromText(text: String) {
        val words = text.split(" ", "\n", "\t", "।", ",", ";").filter { it.isNotBlank() }
        for (i in 0 until words.size - 1) {
            val w1 = words[i].trim().trim { ",.!?।;:\"'()[]{}/\\-_".contains(it) }
            val w2 = words[i + 1].trim().trim { ",.!?।;:\"'()[]{}/\\-_".contains(it) }
            if (w1.length in 1..40 && w2.length in 1..40) {
                learnTransition(w1, w2, boost = 1)
            }
        }
        for (w in words) {
            val cleaned = w.trim().trim { ",.!?।;:\"'()[]{}/\\-_".contains(it) }
            if (cleaned.length in 1..40) {
                learnWord(cleaned, boost = 1)
            }
        }
    }
}
