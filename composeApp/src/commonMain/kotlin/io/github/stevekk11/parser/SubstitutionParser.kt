package io.github.stevekk11.parser

import io.github.stevekk11.dtos.*
import kotlinx.serialization.json.*

object SubstitutionParser {

    // --- Regex Constants ---
    private val PARENTHESES_REGEX = """\(([A-Z][a-z]?)\)""".toRegex() // Matches (Su), (M)
    private val TEACHER_CODE_REGEX = """\b[A-Z][a-z]\b""".toRegex() // Strict 2-letter code
    private val ROOM_REGEX = """(?:uč\.?\s*)?(\d+[a-z]?|TV|TH)(?=\s|$)""".toRegex(RegexOption.IGNORE_CASE)
    private val GROUP_REGEX = """\b\d+/\d+\b""".toRegex() // 1/2, 2/2
    private val POSUN_TARGET_REGEX = """posun\s+(?:za|z)?\s*(\d+\.?\s*h\.?|[^\s]+)""".toRegex(RegexOption.IGNORE_CASE)

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

    fun parseDaySchedule(daySchedule: Map<String, JsonElement>): Map<String, List<SubstitutedLesson>> {
        val result = mutableMapOf<String, List<SubstitutedLesson>>()

        for ((className, value) in daySchedule) {
            if (className == "ABSENCE") continue

            if (value is JsonArray) {
                val lessons = mutableListOf<SubstitutedLesson>()
                value.forEachIndexed { index, element ->
                    val text = element.jsonPrimitive.contentOrNull
                    if (!text.isNullOrBlank()) {
                        val lesson = parseSubstitutionText(text, index + 1)
                        lessons.add(lesson)
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
     * Core Parsing Logic
     * Strategy:
     * 1. Extract Flags (odpadá, posun, etc.)
     * 2. Anchor: Find (Missing) teacher.
     * 3. Look-behind: If the token immediately before (Missing) is a 2-letter code, it is the SubTeacher.
     * 4. Extract Room & Group.
     * 5. Whatever remains is Subject (if short) or Note (if long).
     */
    fun parseSubstitutionText(text: String, hour: Int): SubstitutedLesson {
        // Normalize spaces
        var workingText = text.trim().replace(Regex("\\s+"), " ")

        var group: String? = null
        var subject: String? = null
        var room: String? = null
        var substitutingTeacher: String? = null
        var missingTeacher: String? = null

        // Status Flags
        val isDropped = workingText.containsOneOf("odpadá", "0", "odučeno") ||
                (workingText.contains("oběd", true) && !workingText.contains("(")) // oběd is dropped only if not a subject with sub
        val isJoined = workingText.containsOneOf("spoj", "joined")
        val isSeparated = workingText.containsOneOf("rozděl")
        val roomChanged = workingText.containsOneOf("změna", "výměna")
        val isShifted = workingText.contains("posun", ignoreCase = true)

        var shiftTarget: String? = null

        // --- Step 1: Handle Shift (Posun) specifics ---
        if (isShifted) {
            val match = POSUN_TARGET_REGEX.find(workingText)
            if (match != null) {
                shiftTarget = match.groupValues[1]
                // Remove the "posun za X" text so it doesn't interfere
                workingText = workingText.replace(match.value, "").trim()
            } else {
                // Just remove the word posun
                workingText = workingText.replace("posun", "", true).trim()
            }
        }

        // --- Step 2: The Anchor (Missing Teacher) ---
        // We look for (XX). If found, we capture XX as missing.
        // CRITICAL: We check the text immediately *before* the parentheses for the substituting teacher.
        val parenthesesMatch = PARENTHESES_REGEX.find(workingText)
        if (parenthesesMatch != null) {
            missingTeacher = parenthesesMatch.groupValues[1]

            // Check text before the match for Substituting Teacher
            val rangeBefore = workingText.substring(0, parenthesesMatch.range.first).trim()
            val tokensBefore = rangeBefore.split(" ")
            val lastToken = tokensBefore.lastOrNull()

            if (lastToken != null && lastToken.matches(TEACHER_CODE_REGEX) && lastToken != "TV") {
                // Found the sub teacher anchored to the missing teacher
                substitutingTeacher = lastToken
                // Remove both from working text
                workingText = workingText.replace("$substitutingTeacher ${parenthesesMatch.value}", "")
                workingText = workingText.replace("$substitutingTeacher${parenthesesMatch.value}", "") // Case without space
            }

            // Always remove the (Missing) part
            workingText = workingText.replace(parenthesesMatch.value, "").trim()
        }

        // --- Step 3: Extract Room ---
        // We handle "uč 8", "uč.8", "14a", "TV"
        val roomMatch = ROOM_REGEX.find(workingText)
        if (roomMatch != null) {
            room = roomMatch.groupValues[1]
            workingText = workingText.replace(roomMatch.value, "").trim()
        }

        // --- Step 4: Extract Group (1/2, 2/2 etc) ---
        val groupMatch = GROUP_REGEX.find(workingText)
        if (groupMatch != null) {
            group = groupMatch.value
            workingText = workingText.replace(group, "").trim()
        }

        // --- Step 5: Clean up keywords ---
        // Remove status words so they don't get parsed as Subject/Note
        val keywordsToRemove = listOf(
            "odpadá", "oběd", "spoj.", "spoj", "rozděl.", "rozděl",
            "změna", "výměna", "úklid", "supl.", "volno", "dupl.", "0", "odučeno"
        )
        keywordsToRemove.forEach { kw ->
            workingText = workingText.replace(kw, "", ignoreCase = true)
        }

        // --- Step 6: Analyze Remaining Tokens ---
        // What is left is usually: [Subject] [Note] OR just [Note] OR [Subject]
        val tokens = workingText.split(" ").filter { it.isNotBlank() }

        if (tokens.isNotEmpty()) {
            val first = tokens[0]

            // If we haven't found a sub teacher yet, and there is a lone 2-letter code,
            // it is usually the Subject (e.g., "Tv", "Ch", "Aj").
            // Exception: If the text was just "Kn" and we had no missing teacher logic,
            // it might be a teacher. But we handled the main teacher case in Step 2.

            if (first.length <= 4 && first[0].isUpperCase()) {
                subject = first
            }

            // If there are more tokens, or if the first wasn't a subject, it's a note
            val noteTokens = if (subject != null) tokens.drop(1) else tokens
            val noteText = noteTokens.joinToString(" ")

            // Clean up note (remove punctuation if it's just a dot)
            val finalNote = if (noteText.length > 1) noteText else null

            return SubstitutedLesson(
                hour = hour,
                group = group,
                subject = subject,
                room = room,
                substitutingTeacher = substitutingTeacher,
                missingTeacher = missingTeacher,
                isDropped = isDropped,
                isJoined = isJoined,
                isSeparated = isSeparated,
                roomChanged = roomChanged,
                isShifted = isShifted,
                shiftTarget = shiftTarget,
                note = finalNote
            )
        }

        return SubstitutedLesson(
            hour = hour,
            group = group,
            subject = subject,
            room = room,
            substitutingTeacher = substitutingTeacher,
            missingTeacher = missingTeacher,
            isDropped = isDropped,
            isJoined = isJoined,
            isSeparated = isSeparated,
            roomChanged = roomChanged,
            isShifted = isShifted,
            shiftTarget = shiftTarget,
            note = null
        )
    }

    // Helper extension
    private fun String.containsOneOf(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}

/**
 * Represents a single day's substitution schedule with all classes and absences.
 */
@kotlinx.serialization.Serializable
data class DailySchedule(
    val date: String,
    val isPriprava: Boolean,
    val classSubs: Map<String, List<SubstitutedLesson>>,
    val absences: List<TeacherAbsence>
)

/**
 * Complete schedule with daily schedules and status information.
 */
@kotlinx.serialization.Serializable
data class ScheduleWithAbsences(
    val dailySchedules: List<DailySchedule>,
    val status: SubstitutionStatus
)
