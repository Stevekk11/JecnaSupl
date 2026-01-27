package io.github.stevekk11.examples

import io.github.stevekk11.parser.SubstitutionParser

/**
 * Example usage of SubstitutionParser.
 *
 * This demonstrates how to parse the JSON response from the substitution endpoint
 * and extract structured data.
 */
object ParserUsageExample {

    /**
     * Example: Parse complete JSON and get structured schedule.
     */
    fun parseCompleteSchedule(jsonString: String) {
        val schedule = SubstitutionParser.parseCompleteSchedule(jsonString)

        // Access status information
        println("Last Updated: ${schedule.status.lastUpdated}")
        println("Update Schedule: ${schedule.status.currentUpdateSchedule} minutes")
        schedule.status.message?.let { println("Message: $it") }

        // Iterate through each day
        schedule.dailySchedules.forEach { day ->
            println("\n=== Date: ${day.date} (Příprava: ${day.isPriprava}) ===")

            // Print teacher absences
            println("\nTeacher Absences:")
            day.absences.forEach { absence ->
                println("  ${absence.teacher ?: "Unknown"} (${absence.teacherCode}): ${absence.type}")
                when (absence.type) {
                    "single" -> println("    Hour: ${absence.hours?.from}")
                    "range" -> println("    Hours: ${absence.hours?.from}-${absence.hours?.to}")
                }
            }

            // Print substitutions by class
            println("\nSubstitutions by Class:")
            day.classSubs.forEach { (className, lessons) ->
                println("  Class $className:")
                lessons.forEach { lesson ->
                    println("    Hour ${lesson.hour}: ${formatLesson(lesson)}")
                }
            }
        }
    }

    /**
     * Example: Parse a single day's schedule.
     */
    fun parseSingleDay(dayScheduleMap: Map<String, kotlinx.serialization.json.JsonElement>) {
        val lessons = SubstitutionParser.parseDaySchedule(dayScheduleMap)
        val absences = SubstitutionParser.parseTeacherAbsences(dayScheduleMap)

        println("Classes with substitutions: ${lessons.keys.joinToString(", ")}")
        println("Teacher absences: ${absences.size}")
    }

    /**
     * Example: Parse individual substitution text.
     */
    fun parseIndividualEntry() {
        val examples = listOf(
            "M 16 (Mu) odpadá",
            "M 16 Kp(Mu)+",
            "F 16 Rk(Lc)+",
            "Ch 1 (Bo) odpadá",
            "ZE 1 Ki(Ht) spoj.úklid",
            "Ele 3 Zn(Su)+",
            "DC L2,D6 Pt,Kt(Kr) rozděl. méně žáků.",
            "F 15 Rk(Sv)+",
            "C 15 Mr(Bo) posun za 6. h.",
            "M 15 Hr posun úklid",
            "1/2 A 6 Ju(Ry)+",
            "IT 17ab Me(Bo)+",
            "EnM 6 Nv(Su)+",
            "TV (Lc) odpadá",
            "uč. 8 přednáška PČR Vl",
            "2/2 WA 17a Pp(PV)+",
            "posun CIT 1/2 Nm 17b",
            "2/2 CIT L1(Sv) odpadá",
            "1/2 CIT D6 Pr(Sv)+, 2/2 Nm 17b",
            "2/2 PSS 8a Jk(Ms)",
            "A 23,24 Kn,Ir posun",
            "PSS 22 Jk(Ms)+",
            "1/2 PSS 8a Jk(Ms)",
            "1/2 C 27 Ja(Ry)+ úklid",
            "DS 13 Ka(Su)+",
            "2/2 CEL L4 Nv(Ry)",
            "M 21 Ng(Mu)+"
        )

        examples.forEach { text ->
            val lesson = SubstitutionParser.parseSubstitutionText(text, 1)
            println("Input: $text")
            println("  Parsed: ${formatLesson(lesson)}")
            println()
        }
    }

    private fun formatLesson(lesson: io.github.stevekk11.dtos.SubstitutedLesson): String {
        val parts = mutableListOf<String>()

        lesson.group?.let { parts.add("Group: $it") }
        lesson.subject?.let { parts.add("Subject: $it") }
        lesson.room?.let { parts.add("Room: $it") }
        lesson.substitutingTeacher?.let { parts.add("Teacher: $it") }
        lesson.missingTeacher?.let { parts.add("Missing: $it") }

        val flags = mutableListOf<String>()
        if (lesson.isDropped) flags.add("DROPPED")
        if (lesson.isJoined) flags.add("JOINED")
        if (lesson.isSeparated) flags.add("SEPARATED")
        if (lesson.roomChanged) flags.add("ROOM_CHANGED")
        if (lesson.isShifted) flags.add("SHIFTED")

        if (flags.isNotEmpty()) parts.add("Flags: ${flags.joinToString(", ")}")
        lesson.shiftTarget?.let { parts.add("Shift to: $it") }
        lesson.note?.let { parts.add("Note: $it") }

        return parts.joinToString(" | ")
    }
}
