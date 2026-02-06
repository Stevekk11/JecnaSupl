package io.github.stevekk11.examples

import io.github.stevekk11.api.SubstitutionClient
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Example usage of SubstitutionClient.
 * This is the main client class that users of the library will interact with.
 */

class SubstitutionClientExample {

    /**
     * Basic usage: Fetch substitutions for a specific class.
     */
    @Test
    fun testExampleText(){
        val client = SubstitutionClient()
        val lesson = client.getExampleSubstitution("1/2 A 6 Ju(Pa), 2/2 A 29 Km(Ry)")
        println(lesson)
    }

    suspend fun basicUsage() {
        val client = SubstitutionClient()

        // Configure the client
        client.setEndpointUrl("https://jecnarozvrh.jzitnik.dev/versioned/v2")
        client.setClassSymbol("C2b")

        // Get substitutions for the configured class
        val substitutions = client.getSubstitutions()

        println("Substitutions for class:")
        if (substitutions != null)
        {
            for (substitution in substitutions) {
                println(substitution)
            }
        }
    }

    /**
     * Get teacher absences for all classes.
     */
    suspend fun getAbsences() {
        val client = SubstitutionClient()
        client.setEndpointUrl("https://jecnarozvrh.jzitnik.dev/versioned/v1")

        val absences = client.getTeacherAbsences()

        println("Teacher Absences:")
        absences?.forEach { dayAbsences ->
            println("\nDate: ${dayAbsences.date}")
            dayAbsences.absences.forEach { absence ->
                println("  ${absence.teacher ?: "Unknown"} (${absence.teacherCode})")
                println("    Type: ${absence.type}")
                when (absence.type) {
                    "single" -> println("    Hour: ${absence.hours?.from}")
                    "range" -> println("    Hours: ${absence.hours?.from}-${absence.hours?.to}")
                    "wholeDay" -> println("    Whole day")
                    "exkurze" -> println("    Excursion")
                }
            }
        }
    }

    /**
     * Get status information.
     */
    suspend fun getStatus() {
        val client = SubstitutionClient()
        client.setEndpointUrl("https://jecnarozvrh.jzitnik.dev/versioned/v1")

        val status = client.getSubstitutionsStatus()

        println("Substitution Status:")
        println("  Last Updated: ${status.lastUpdated}")
        println("  Update Schedule: Every ${status.currentUpdateSchedule} minutes")

        if (status.isOffline == true) {
            println("Endpoint na suplování není dostupný!")
        }
    }

    /**
     * Get complete schedule with all classes.
     */
    suspend fun getCompleteSchedule() {
        val client = SubstitutionClient()
        client.setEndpointUrl("https://jecnarozvrh.jzitnik.dev/versioned/v1")

        val schedule = client.getCompleteSchedule()

        println("Complete Schedule:")
        println("Last Updated: ${schedule?.status?.lastUpdated}\n")

        schedule?.dailySchedules?.forEach { day ->
            println("=== ${day.date} ${if (day.isPriprava) "(Příprava)" else ""} ===")

            // Show teacher absences
            if (day.absences.isNotEmpty()) {
                println("\nAbsent Teachers:")
                day.absences.forEach { absence ->
                    println("  - ${absence.teacherCode} (${absence.type})")
                }
            }

            // Show substitutions by class
            println("\nSubstitutions:")
            day.classSubs.forEach { (className, lessons) ->
                println("  Class $className (${lessons.size} lessons):")
                lessons.forEach { lesson ->
                    val info = buildString {
                        append(lesson)
                    }
                    println(info)
                }
            }
            println()
        }
    }

    /**
     * Get raw JSON data (for debugging or custom parsing).
     */
    suspend fun getRawData() {
        val client = SubstitutionClient()
        client.setEndpointUrl("https://jecnarozvrh.jzitnik.dev/versioned/v1")

        val rawJson = client.getRawSubstitutionData()
        println("Raw JSON response:")
        println(rawJson)
    }

    /**
     * Error handling example.
     */
    suspend fun errorHandling() {
        val client = SubstitutionClient()

        try {
            // This will throw an exception due to invalid URL
            client.setEndpointUrl("https://invalid-url.com")
        } catch (e: IllegalArgumentException) {
            println("Error: ${e.message}")
        }

        try {
            // This will throw an exception due to invalid class symbol
            client.setClassSymbol("X")
        } catch (e: IllegalArgumentException) {
            println("Error: ${e.message}")
        }

        // Set valid configuration
        client.setEndpointUrl("https://jecnarozvrh.jzitnik.dev/versioned/v1")
        client.setClassSymbol("E3")

        try {
            val substitutions = client.getSubstitutions()
            println("Successfully fetched ${substitutions?.size} substitutions")
        } catch (e: Exception) {
            println("Failed to fetch substitutions: ${e.message}")
        }
    }

    /**
     * Parse daily substitutions for a specific class.
     */
    suspend fun parseDailySubstitutions() {
        val client = SubstitutionClient()
        client.setEndpointUrl("https://jecnarozvrh.jzitnik.dev/versioned/v1")
        client.setClassSymbol("C4b")

        val dailySchedules = client.getDailySubstitutions()

        println("Daily substitutions for class:")
        dailySchedules?.forEach { day ->
            println("\nDate: ${day.date} ${if (day.isPriprava) "(Příprava)" else ""}")
            day.classSubs.forEach {
                it.value.forEach {
                    println(it.toString())
                }
            }
        }
    }

    // --- Test wrappers to run the suspend example methods in JUnit ---

    @Test
    fun testBasicUsage() = runBlocking {
        basicUsage()
    }

    @Test
    fun testGetAbsences() = runBlocking {
        getAbsences()
    }

    @Test
    fun testGetStatus() = runBlocking {
        getStatus()
    }

    @Test
    fun testGetCompleteSchedule() = runBlocking {
        getCompleteSchedule()
    }

    @Test
    fun testGetRawData() = runBlocking {
        getRawData()
    }

    @Test
    fun testErrorHandling() = runBlocking {
        errorHandling()
    }

    @Test
    fun testParseDailySubstitutions() = runBlocking {
        parseDailySubstitutions()
    }
}