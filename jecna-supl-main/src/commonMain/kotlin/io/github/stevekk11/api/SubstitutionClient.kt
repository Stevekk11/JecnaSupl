package io.github.stevekk11.api

import io.github.stevekk11.dtos.DailySchedule
import io.github.stevekk11.dtos.LabeledTeacherAbsences
import io.github.stevekk11.dtos.ScheduleWithAbsences
import io.github.stevekk11.dtos.SubstitutedLesson
import io.github.stevekk11.dtos.SubstitutionStatus
import io.github.stevekk11.parser.SubstitutionParser
import java.time.LocalDate

/**
 * Client for fetching and parsing substitution data from provided endpoint.
 */
class SubstitutionClient
{
    private var baseUrl = ""
    private var classSymbol = ""

    /**
     * Set the endpoint URL for the substitution API.
     * @param url The API endpoint URL (must contain 'jecnarozvrh')
     */
    fun setEndpointUrl(url: String)
    {
        if (!url.contains("jecnarozvrh"))
        {
            throw IllegalArgumentException("Invalid substitution endpoint URL. Must contain 'jecnarozvrh'")
        }
        baseUrl = url
    }

    /**
     * Set the class symbol to filter substitutions.
     * @param symbol Class symbol (e.g., 'C2b', 'A4', 'E2', 'C3a')
     */
    fun setClassSymbol(symbol: String)
    {
        if (symbol.isBlank() || symbol.length < 2)
        {
            throw IllegalArgumentException("Invalid class symbol. Examples of valid symbols: 'C2b, A4, E2, C3a'")
        }
        classSymbol = symbol
    }

    /**
     * Get teacher absences for all days in the schedule.
     * @return List of teacher absences labeled by date
     */
    suspend fun getTeacherAbsences(): List<LabeledTeacherAbsences>?
    {
        if (getSubstitutionsStatus().isOffline == true) return null
        val json = Fetcher.fetchJsonFromApi(baseUrl)
        val schedule = SubstitutionParser.parseCompleteSchedule(json)

        return schedule.dailySchedules.map { day ->
            LabeledTeacherAbsences(
                date = day.date,
                absences = day.absences
            )
        }
    }

    /**
     * Get teacher absences for a specific date.
     * @param date The date as a LocalDate object
     * @return Teacher absences for the specified date, or null if not found
     */
    suspend fun getTeacherAbsences(date: LocalDate): LabeledTeacherAbsences? {
        if (getSubstitutionsStatus().isOffline == true) return null
        val json = Fetcher.fetchJsonFromApi(baseUrl)
        val schedule = SubstitutionParser.parseCompleteSchedule(json)

        return schedule.dailySchedules.find { it.date == date.toString() }?.let { day ->
            LabeledTeacherAbsences(
                date = day.date,
                absences = day.absences
            )
        }
    }

    /**
     * Get an example substitution from text for testing the parser.
     */
    fun getExampleSubstitution(text: String) : String {
        return SubstitutionParser.parseExampleSubstitutionText(text).toString()
    }

    /**
     * Get raw JSON data from the substitution API.
     * @return Raw JSON string
     */
    suspend fun getRawSubstitutionData(): String?
    {
        if (getSubstitutionsStatus().isOffline == true) return null
        return Fetcher.fetchJsonFromApi(baseUrl)
    }

    /**
     * Get substitutions for the configured class symbol.
     * @return List of substituted lessons for the class across all days - unlabeled days
     */
    suspend fun getSubstitutions(): List<SubstitutedLesson>?
    {
        if (classSymbol.isBlank())
        {
            throw IllegalArgumentException("Class symbol must not be empty.")
        }
        if (getSubstitutionsStatus().isOffline == true) return null
        val json = Fetcher.fetchJsonFromApi(baseUrl)
        val schedule = SubstitutionParser.parseCompleteSchedule(json)

        // Filter and flatten lessons for the specified class
        val lessons = mutableListOf<SubstitutedLesson>()
        schedule.dailySchedules.forEach { day ->
            day.classSubs[classSymbol]?.let { dayLessons ->
                lessons.addAll(dayLessons)
            }
        }

        return lessons
    }

    /**
     * Get the status information from the substitution API.
     * @return Status object with last updated time and update schedule
     */
    suspend fun getSubstitutionsStatus(): SubstitutionStatus {
        try
        {
            val json = Fetcher.fetchJsonFromApi(baseUrl)
            val status = SubstitutionParser.parseStatus(json)
            return status
        } catch (_: Exception) {
            return SubstitutionStatus("n/a", 0, true)
        }
    }

    /**
     * Get the complete parsed schedule with all data.
     * @return Complete schedule with daily schedules and status
     */
    suspend fun getCompleteSchedule(): ScheduleWithAbsences?
    {
        if (getSubstitutionsStatus().isOffline == true) return null
        val json = Fetcher.fetchJsonFromApi(baseUrl)
        return SubstitutionParser.parseCompleteSchedule(json)
    }

    /**
     * Parse daily substitutions for the configured class symbol.
     * @return List of daily schedules for the specified class
     */
    suspend fun getDailySubstitutions(): List<DailySchedule>?
    {
        if (classSymbol.isBlank())
        {
            throw IllegalArgumentException("Class symbol must not be empty.")
        }
        if (getSubstitutionsStatus().isOffline == true) return null
        val json = Fetcher.fetchJsonFromApi(baseUrl)
        val schedule = SubstitutionParser.parseCompleteSchedule(json)
        return schedule.dailySchedules.map { day ->
            val filteredSubs = day.classSubs.filterKeys { it == classSymbol }
            day.copy(classSubs = filteredSubs)
        }.filter { it.classSubs.isNotEmpty() }
    }
}