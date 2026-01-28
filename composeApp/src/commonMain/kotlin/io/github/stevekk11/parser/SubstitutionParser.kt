package io.github.stevekk11.parser

import io.github.stevekk11.dtos.*
import kotlinx.serialization.json.*

object SubstitutionParser {

    // --- Regex Constants ---
    private val PARENTHESES_REGEX = """\(([A-Z][a-z]?)(?:,\s*([A-Z][a-z]?))?\)""".toRegex()
    // Matches (Su), (M)
    private val GROUP_REGEX = """\b\d+/\d+\b""".toRegex() // 1/2, 2/2

    fun parseSubstitutionJson(jsonString: String): SubstitutionResponse {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(jsonString)
    }

    fun parseCompleteSchedule(jsonString: String): ScheduleWithAbsences {
        val response = parseSubstitutionJson(jsonString)
        val dailySchedules = mutableListOf<DailySchedule>()

        response.schedule.forEachIndexed { index, daySchedule ->
            val props = response.props.getOrNull(index)

            // Handle absence array specifically
            val absences = parseTeacherAbsences(daySchedule)

            // Handle lessons
            val lessons = parseDaySchedule(daySchedule)

            dailySchedules.add(
                DailySchedule(
                    date = props?.date ?: "unknown",
                    isPriprava = props?.priprava ?: false,
                    classSubs = lessons,
                    absences = absences
                )
            )
        }

        return ScheduleWithAbsences(dailySchedules, response.status)
    }

    fun parseStatus(jsonString: String): SubstitutionStatus {
        val response = parseSubstitutionJson(jsonString)
        return response.status
    }

    fun parseDaySchedule(daySchedule: Map<String, JsonElement>): Map<String, List<SubstitutedLesson>> {
        val result = mutableMapOf<String, List<SubstitutedLesson>>()

        for ((className, value) in daySchedule) {
            if (className == "ABSENCE") continue

            if (value is JsonArray) {
                val lessons = mutableListOf<SubstitutedLesson>()
                value.forEachIndexed { index, element ->
                    val text = element.jsonPrimitive.contentOrNull
                    if (!text.isNullOrBlank()) {
                        val lessonsForHour = parseSubstitutionText(text, index + 1)
                        lessons.addAll(lessonsForHour)
                    }
                }
                if (lessons.isNotEmpty()) {
                    result[className] = lessons
                }
            }
        }
        return result
    }

    fun parseTeacherAbsences(daySchedule: Map<String, JsonElement>): List<TeacherAbsence> {
        val absenceElement = daySchedule["ABSENCE"] ?: return emptyList()
        if (absenceElement !is JsonArray) return emptyList()
        val json = Json { ignoreUnknownKeys = true }
        return absenceElement.map { json.decodeFromJsonElement(it) }
    }

    /**
     * HOLY raškus PARSER STARTS HERE ⛪DO NOT CONTINUE TO KEEP YOUR SANITY⛪
     * Core Parsing Logic
     * Strategy:
     * 1. Extract Flags (odpadá, posun, etc.)
     * 2. Anchor: Find (Missing) teacher.
     * 3. Look-behind: If the token immediately before (Missing) is a 2-letter code, it is the SubTeacher.
     * 4. Extract Room & Group.
     * 5. Whatever remains is Subject (if short) or Note (if long).
     */
    fun parseSubstitutionText(text: String, hour: Int): List<SubstitutedLesson> {
        // Check if text contains multiple groups separated by commas
        val commaParts = text.split(",").map { it.trim() }.filter { it.isNotBlank() }

        // If only one part or no commas, parse as single
        if (commaParts.size <= 1) {
            return listOf(parseSingleSubstitutionText(text, hour))
        }

        // Check if multiple parts have different groups
        val hasMultipleGroups = commaParts.any { part ->
            GROUP_REGEX.containsMatchIn(part)
        } && commaParts.count { GROUP_REGEX.containsMatchIn(it) } > 1

        if (!hasMultipleGroups) {
            // Multiple parts but same group, parse as single
            return listOf(parseSingleSubstitutionText(text, hour))
        }

        // Parse each part separately
        return commaParts.map { part ->
            parseSingleSubstitutionText(part, hour)
        }
    }

    private fun parseSingleSubstitutionText(text: String, hour: Int): SubstitutedLesson {

        var workingText = text.replace("\n", " ").trim().replace(Regex("\\s+"), " ")
        val substitutionText = workingText
        var group: String? = null
        var subject: String? = null
        var room: String?
        var substitutingTeacher: String?
        var missingTeacher: String? = null
        var isDropped: Boolean
        var isJoined: Boolean
        var isSeparated: Boolean
        var roomChanged: Boolean
        var isShifted: Boolean
        var shiftTarget: String? = null

        // 1. ANCHOR: Extract Missing Teacher (XX)
        val parenthesesMatch = PARENTHESES_REGEX.find(workingText)
        if (parenthesesMatch != null) {
            val first = parenthesesMatch.groupValues[1]
            val second = parenthesesMatch.groupValues.getOrNull(2) ?: ""
            missingTeacher = first + if (second.isNotBlank()) ", $second" else ""
            workingText = workingText.replace(parenthesesMatch.value, " ").trim()
        }

        // 2. FLAGS: Extract status before they get eaten by Subject/Note
        isDropped = workingText.containsOneOf("odpadá", "0", "odučeno", "oběd")
        isJoined = workingText.containsOneOf("spoj")
        isSeparated = workingText.containsOneOf("rozděl")
        roomChanged = workingText.containsOneOf("změna", "výměna")
        isShifted = workingText.contains("posun", ignoreCase = true)

        // 3. SHIFT TARGET: Specific hour extraction
        val shiftMatch = """posun\s+(?:za|z)?\s*(\d+\.?\s*h\.?)""".toRegex(RegexOption.IGNORE_CASE).find(workingText)
        if (shiftMatch != null) {
            shiftTarget = shiftMatch.groupValues[1]
            workingText = workingText.replace(shiftMatch.value, "").trim()
        }

        // 4. TOKENIZATION & CLASSIFICATION
        // Split by space and comma, remove plus noise
        val rawTokens = workingText.split(Regex("[\\s,]+"))
            .filter { it.isNotEmpty() && it != "+" }

        val remainingTokens = mutableListOf<String>()
        val rooms = mutableListOf<String>()
        val teachers = mutableListOf<String>()

        for (token in rawTokens) {
            when {
                // Group (1/2, 2/2)
                token.matches(GROUP_REGEX) -> group = token

                // Room (18ab, 15, uč. 8, D6, L2)
                // Rooms usually start with a digit OR are specific codes like D6/L2
                token.matches("""(?:uč\.?\s*)?\d+[a-z]{0,2}""".toRegex(RegexOption.IGNORE_CASE)) ||
                        token.matches("""[A-Z]\d+""".toRegex()) -> {
                    rooms.add(token.replace("uč.", "").trim())
                }

                // Teacher (2-letter Code)
                // If it's 2 letters
                token.matches("""^[A-Z][a-z]$""".toRegex()) -> {
                    if (subject == null) {
                        subject = token
                    } else {
                        teachers.add(token)
                    }
                }

                // Subject (Usually uppercase or first token)
                subject == null && token.length <= 4 -> subject = token

                // Everything else is a note
                else -> {
                    val cleanedToken = token.lowercase()
                    if (cleanedToken !in listOf("odpadá", "posun", "spoj.", "spoj", "změna", "úklid")) {
                        remainingTokens.add(token)
                    }
                }
            }
        }

        // Set final values
        room = rooms.joinToString(",").ifBlank { null }
        substitutingTeacher = teachers.joinToString(",").ifBlank { null }

        // Final cleanup for Subject/Note
        // If subject is "posun" or "spoj", it's not a subject.
        if (subject?.containsOneOf("posun", "spoj", "změna", "uč") == true) subject = null

        var note = remainingTokens.joinToString(" ").trim()

        // 1. The TV Rule: If subject is TV, room must be TV (and vice versa if room is TV)
        if (subject?.uppercase() == "TV") {
            room = "TV"
        } else if (room?.uppercase() == "TV") {
            // If the room is the gym, the subject is almost certainly TV
            if (subject == null) subject = "TV"
        }
        if (room == "0" || room == "oběd") {
            room = null
            isDropped = true
        }
        if (subject == "+" || subject?.lowercase() == "uč" || subject?.lowercase() == "oběd") {
            subject = null
        }
        if (note.trim() == "+" || note.isBlank()) {
            note = ""
        }


        return SubstitutedLesson(
            hour = hour, group = group, subject = subject, room = room,
            substitutingTeacher = substitutingTeacher, missingTeacher = missingTeacher,
            isDropped = isDropped, isJoined = isJoined, isSeparated = isSeparated,
            roomChanged = roomChanged, isShifted = isShifted, shiftTarget = shiftTarget,
            note = note.ifBlank { null }, originalText = substitutionText
        )
    }

    private fun String.containsOneOf(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
