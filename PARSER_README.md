# SubstitutionParser

A comprehensive parser for SPŠE Ječná substitution schedule JSON data.

## Overview

The `SubstitutionParser` is designed to parse semi-structured substitution text entries into strongly-typed data structures. It follows a "waterfall" parsing logic to extract information that doesn't depend on position in the text.

## Features

- **Position-independent parsing**: Extracts data based on patterns, not position
- **Comprehensive substitution types**: Handles dropped lessons, joined classes, room changes, shifts, and more
- **Teacher absence tracking**: Parses various absence types (whole day, single hour, ranges, excursions)
- **Multi-day schedule support**: Parse complete weekly schedules with all metadata

## Data Structures

### SubstitutedLesson
Represents a single substituted lesson with:
- `hour`: Lesson number (1-10)
- `group`: Group identifier (e.g., "1/2", "2/2")
- `subject`: Subject code (e.g., "M", "IT", "EnM")
- `room`: Room number/identifier
- `substitutingTeacher`: Teacher code replacing the missing teacher
- `missingTeacher`: Original teacher code (extracted from parentheses)
- Flags: `isDropped`, `isJoined`, `isSeparated`, `roomChanged`, `isShifted`
- `shiftTarget`: Where the lesson is shifted to
- `note`: Additional information

### TeacherAbsence
Represents teacher absence with:
- `teacher`: Full teacher name
- `teacherCode`: 2-letter teacher code
- `type`: "wholeDay", "single", "range", "exkurze"
- `hours`: Affected hours (from/to)

### DailySchedule
Complete schedule for one day:
- `date`: Date string (YYYY-MM-DD)
- `isPriprava`: Preparation day flag
- `classSubs`: Map of class names to their substituted lessons
- `absences`: List of teacher absences

## Parsing Logic (Waterfall Approach)

The parser follows these steps in order:

1. **Extract Parentheses**: `(XX)` → Always the missing teacher code
2. **Check Status Keywords**: 
   - `odpadá` → lesson dropped
   - `spoj.` → classes joined
   - `rozděl.` → classes separated
   - `posun` → lesson shifted
   - `změna uč.` → room changed
3. **Find Room**: 
   - `uč. XX` prefix pattern
   - Standalone number with optional letter (e.g., "8", "20", "19c")
4. **Identify Teacher**: 2-letter uppercase code (e.g., "Mu", "Bo", "Sv")
5. **Extract Subject**: 1-4 letter code (e.g., "M", "IT", "EnM", "PSS")
6. **Remaining Text**: Assigned to group identifier or note

## Usage

### Parse Complete JSON Response

```kotlin
val jsonString = """{"schedule":[...], "props":[...], "status":{...}}"""
val schedule = SubstitutionParser.parseCompleteSchedule(jsonString)

// Access status
println("Last Updated: ${schedule.status.lastUpdated}")

// Iterate through days
schedule.dailySchedules.forEach { day ->
    println("Date: ${day.date}")
    
    // View absences
    day.absences.forEach { absence ->
        println("Absent: ${absence.teacher} (${absence.type})")
    }
    
    // View substitutions
    day.classSubs.forEach { (className, lessons) ->
        println("Class $className:")
        lessons.forEach { lesson ->
            println("  Hour ${lesson.hour}: ${lesson.subject} - ${lesson.substitutingTeacher}")
        }
    }
}
```

### Parse Single Day

```kotlin
val daySchedule: Map<String, JsonElement> = // ... from schedule array
val lessons = SubstitutionParser.parseDaySchedule(daySchedule)
val absences = SubstitutionParser.parseAbsences(daySchedule)
```

### Parse Individual Text Entry

```kotlin
val text = "M 16 Kp(Mu)+"
val lesson = SubstitutionParser.parseSubstitutionText(text, hour = 3)

println("Subject: ${lesson.subject}")          // "M"
println("Room: ${lesson.room}")                // "16"
println("Teacher: ${lesson.substitutingTeacher}") // "Kp"
println("Missing: ${lesson.missingTeacher}")   // "Mu"
```

## Example Parsing Results

| Input Text | Parsed Result |
|------------|---------------|
| `M 16 (Mu) odpadá` | Subject: M, Room: 16, Missing: Mu, isDropped: true |
| `F 16 Rk(Lc)+` | Subject: F, Room: 16, Teacher: Rk, Missing: Lc |
| `ZE 1 Ki(Ht) spoj.úklid` | Subject: ZE, Room: 1, Teacher: Ki, Missing: Ht, isJoined: true |
| `1/2 A 6 Ju(Ry)+` | Group: 1/2, Subject: A, Room: 6, Teacher: Ju, Missing: Ry |
| `C 15 Mr(Bo) posun za 6. h.` | Subject: C, Room: 15, Teacher: Mr, Missing: Bo, isShifted: true, shiftTarget: "6. h." |
| `uč. 8 přednáška PČR Vl` | Room: 8, Note: "přednáška PČR", Teacher: Vl |
| `2/2 CIT L1(Sv) odpadá` | Group: 2/2, Subject: CIT L1, Missing: Sv, isDropped: true |

## Status Keywords

The parser recognizes these Czech keywords:
- `odpadá` / `0` / `odučeno` - Lesson dropped
- `spoj.` - Classes joined
- `rozděl.` - Classes separated
- `posun` - Lesson shifted
- `změna uč.` - Room changed
- `oběd` - Lunch
- `úklid` - Cleaning
- `vysvědčení` / `vysv` - Report cards
- `přednáška` - Lecture
- `exkurze` - Excursion

## Dependencies

Required dependencies in `build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

Required plugin:

```kotlin
plugins {
    kotlin("plugin.serialization") version "2.3.0"
}
```

## Testing

See `ParserUsageExample.kt` for comprehensive usage examples and test cases.

## License

This parser is part of the JecnaSupl library, licensed under GNU GPL v3.0.
