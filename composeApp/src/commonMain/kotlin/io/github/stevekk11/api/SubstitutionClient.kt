package io.github.stevekk11.api

import io.github.stevekk11.dtos.LabeledTeacherAbsences
import io.github.stevekk11.dtos.SubstitutedLesson
import io.github.stevekk11.dtos.SubstitutionStatus
import io.github.stevekk11.parser.SubstitutionParser
import io.github.stevekk11.parser.ScheduleWithAbsences

/**
 * Client for fetching and parsing substitution data from SPŠE Ječná API.
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
    suspend fun getTeacherAbsences(): List<LabeledTeacherAbsences> {
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
     * Get raw JSON data from the substitution API.
     * @return Raw JSON string
     */
    suspend fun getRawSubstitutionData(): String {
        return Fetcher.fetchJsonFromApi(baseUrl)
    }

    /**
     * Get substitutions for the configured class symbol.
     * @return List of substituted lessons for the class across all days
     */
    suspend fun getSubstitutions(): List<SubstitutedLesson> {
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
        val json = Fetcher.fetchJsonFromApi(baseUrl)
        val schedule = SubstitutionParser.parseCompleteSchedule(json)
        return schedule.status
    }

    /**
     * Get the complete parsed schedule with all data.
     * @return Complete schedule with daily schedules and status
     */
    suspend fun getCompleteSchedule(): ScheduleWithAbsences {
        val json = Fetcher.fetchJsonFromApi(baseUrl)
        return SubstitutionParser.parseCompleteSchedule(json)
    }
}