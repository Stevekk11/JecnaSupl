package io.github.stevekk11.parser

import io.github.stevekk11.dtos.*
import kotlinx.serialization.json.*

/**
 * Parser for substitution schedule JSON data.
 * Implements waterfall parsing logic to extract structured data from semi-structured text entries.
 */
object SubstitutionParser {

    /**
     * Parse the complete substitution response from JSON string.
     */
    fun parseSubstitutionJson(jsonString: String): SubstitutionResponse {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString<SubstitutionResponse>(jsonString)
    }

    /**
     * Parse a day's schedule (one element from the schedule array).
     * Returns a map of class names to their substituted lessons.
     */
    fun parseDaySchedule(daySchedule: Map<String, JsonElement>): Map<String, List<SubstitutedLesson>> {
        val result = mutableMapOf<String, List<SubstitutedLesson>>()

        for ((className, value) in daySchedule) {
            // Skip the ABSENCE key as it's handled separately
            if (className == "ABSENCE") continue

            if (value is JsonArray) {
                val lessons = mutableListOf<SubstitutedLesson>()
                value.forEachIndexed { index, element ->
                    if (element is JsonPrimitive && !element.isString) {
                        // null entry - no substitution for this hour
                        return@forEachIndexed
                    }

                    val text = element.jsonPrimitive.contentOrNull
                    if (text != null && text.isNotBlank()) {
                        val lesson = parseSubstitutionText(text, index + 1) // hours are 1-indexed
                        lessons.add(lesson.copy(hour = index + 1))
                    }
                }
                if (lessons.isNotEmpty()) {
                    result[className] = lessons
                }
            }
        }

        return result
    }

    /**
     * Parse teacher absences from the ABSENCE array in a day's schedule.
     */
    fun parseTeacherAbsences(daySchedule: Map<String, JsonElement>): List<TeacherAbsence> {
        val absenceElement = daySchedule["ABSENCE"] ?: return emptyList()

        if (absenceElement !is JsonArray) return emptyList()

        val json = Json { ignoreUnknownKeys = true }
        return absenceElement.map { element ->
            json.decodeFromJsonElement<TeacherAbsence>(element)
        }
    }

    /**
     * Parse a single substitution text entry using waterfall logic.
     *
     * Waterfall Logic:
     * 1. Extract Parentheses: Find (XX) → This is always missingTeacher
     * 2. Check Status Keywords: Look for odpadá, 0, oběd, posun, spoj., rozděl.
     * 3. Find the Room: Look for \d+[a-z]? or prefix uč.
     * 4. Identify Teacher: Look for 2-letter ID that is not the Subject or Room
     * 5. Remaining Text: Assign to group and subject
     */
    fun parseSubstitutionText(text: String, hour: Int): SubstitutedLesson {
        var workingText = text.trim()

        var group: String? = null
        var subject: String? = null
        var room: String? = null
        var substitutingTeacher: String? = null
        var missingTeacher: String? = null
        var isDropped = false
        var isJoined = false
        var isSeparated = false
        var roomChanged = false
        var isShifted = false
        var shiftTarget: String? = null
        var note: String? = null

        // Step 1: Extract missing teacher from parentheses (XX)
        val parenthesesRegex = """\(([A-Z][a-z])\)""".toRegex()
        val parenthesesMatch = parenthesesRegex.find(workingText)
        if (parenthesesMatch != null) {
            missingTeacher = parenthesesMatch.groupValues[1]
            workingText = workingText.replace(parenthesesMatch.value, "").trim()
        }

        // Step 2: Check status keywords
        when {
            workingText.contains("odpadá", ignoreCase = true) -> {
                isDropped = true
            }
            workingText.contains("spoj.", ignoreCase = true) -> {
                isJoined = true
            }
            workingText.contains("rozděl", ignoreCase = true) -> {
                isSeparated = true
            }
            workingText.contains("posun", ignoreCase = true) -> {
                isShifted = true
            }
            workingText.contains("změna uč.", ignoreCase = true) -> {
                roomChanged = true
            }
        }

        // Extract shift target if present (e.g., "posun za 6. h." or "posun úklid")
        if (isShifted) {
            val shiftRegex = """posun\s+(?:za\s+)?(\d+\.?\s*h\.?|úklid|[^\s]+)""".toRegex(RegexOption.IGNORE_CASE)
            val shiftMatch = shiftRegex.find(workingText)
            if (shiftMatch != null) {
                shiftTarget = shiftMatch.groupValues[1].trim()
            }
        }

        // Check for special cases
        if (workingText.contains("oběd", ignoreCase = true)) {
            subject = "oběd"
        }

        if (workingText.contains(" 0 ", ignoreCase = true) || workingText.contains("odučeno", ignoreCase = true)) {
            isDropped = true
        }

        // Step 3: Find room - either "uč. XX" or standalone number with optional letter
        val roomWithPrefixRegex = """uč\.\s*(\d+[a-z]?)""".toRegex(RegexOption.IGNORE_CASE)
        val roomPrefixMatch = roomWithPrefixRegex.find(workingText)
        if (roomPrefixMatch != null) {
            room = roomPrefixMatch.groupValues[1]
            workingText = workingText.replace(roomPrefixMatch.value, "").trim()
        } else {
            // Look for standalone room number (must be preceded by space or start of string)
            val roomRegex = """(?:^|\s)(\d+[a-z]?)(?=\s|$)""".toRegex()
            val roomMatch = roomRegex.find(workingText)
            if (roomMatch != null) {
                val potentialRoom = roomMatch.groupValues[1]
                // Verify it's not a subject code or part of a longer word
                if (potentialRoom.length <= 3) {
                    room = potentialRoom
                    workingText = workingText.replace(roomMatch.value, " ").trim()
                }
            }
        }

        // Step 4 & 5: Parse remaining tokens
        val tokens = workingText.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val processedTokens = mutableSetOf<String>()

        // Extract group prefix (e.g., "1/2", "2/2", "3/3")
        for (token in tokens) {
            if (token.matches("""[1-3]/[1-3]""".toRegex())) {
                group = token
                processedTokens.add(token)
                break
            }
        }

        // Extract teacher codes (2-letter uppercase codes)
        val teacherCodes = mutableListOf<String>()
        for (token in tokens) {
            if (token.matches("""[A-Z][a-z]""".toRegex()) && !processedTokens.contains(token)) {
                // Check if it's not already identified as missingTeacher
                if (token != missingTeacher) {
                    teacherCodes.add(token)
                    processedTokens.add(token)
                }
            }
        }

        // First teacher code is typically the substituting teacher
        if (teacherCodes.isNotEmpty()) {
            substitutingTeacher = teacherCodes[0]
        }

        // Extract subject codes (typically 1-4 characters, uppercase with possible numbers)
        val subjectCandidates = mutableListOf<String>()
        for (token in tokens) {
            if (!processedTokens.contains(token)) {
                // Subject patterns: M, F, Ch, IT, TV, EnM, PSS, etc.
                if (token.matches("""[A-Z]{1,4}\d*""".toRegex()) ||
                    token.matches("""[A-Z][a-z]{1,2}""".toRegex())) {
                    // Exclude known non-subject patterns
                    if (token !in listOf("odpadá", "spoj", "posun", "rozděl", "méně", "vysv", "změna")) {
                        subjectCandidates.add(token)
                        processedTokens.add(token)
                    }
                }
            }
        }

        if (subjectCandidates.isNotEmpty() && subject == null) {
            subject = subjectCandidates.joinToString(" ")
        }

        // Extract class names (e.g., A1a, C2b, E3, 19c, L1)
        val classNameRegex = """[A-E]\d+[a-c]?|\d+[a-c]|L\d+|D\d+""".toRegex()
        for (token in tokens) {
            if (classNameRegex.matches(token) && !processedTokens.contains(token)) {
                // This could be part of the group identifier
                if (group == null) {
                    group = token
                } else if (!group.contains(token)) {
                    group = "$group $token"
                }
                processedTokens.add(token)
            }
        }

        // Remaining unprocessed tokens become part of the note
        val remainingTokens = tokens.filter { !processedTokens.contains(it) }
            .filter { it !in listOf("odpadá", "spoj.", "posun", "rozděl.", "méně", "žáků",
                                     "úklid", "změna", "vysv", "vysvědčení", "přednáška",
                                     "exkurze", "databáze", "(bude", "upřesněno)") }

        if (remainingTokens.isNotEmpty() && note == null) {
            note = remainingTokens.joinToString(" ")
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
            note = note
        )
    }

    /**
     * Parse the complete schedule data with labeled absences per day.
     */
    fun parseCompleteSchedule(jsonString: String): ScheduleWithAbsences {
        val response = parseSubstitutionJson(jsonString)

        val dailySchedules = mutableListOf<DailySchedule>()

        response.schedule.forEachIndexed { index, daySchedule ->
            val date = if (index < response.props.size) {
                response.props[index].date
            } else {
                "unknown"
            }

            val isPriprava = if (index < response.props.size) {
                response.props[index].priprava
            } else {
                false
            }

            val lessons = parseDaySchedule(daySchedule)
            val absences = parseTeacherAbsences(daySchedule)

            dailySchedules.add(
                DailySchedule(
                    date = date,
                    isPriprava = isPriprava,
                    classSubs = lessons,
                    absences = absences
                )
            )
        }

        return ScheduleWithAbsences(
            dailySchedules = dailySchedules,
            status = response.status
        )
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
