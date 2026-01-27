package io.github.stevekk11.dtos

@kotlinx.serialization.Serializable
data class DailySchedule(
    val date: String,
    val isPriprava: Boolean,
    val classSubs: Map<String, List<SubstitutedLesson>>,
    val absences: List<TeacherAbsence>
)

@kotlinx.serialization.Serializable
data class ScheduleWithAbsences(
    val dailySchedules: List<DailySchedule>,
    val status: SubstitutionStatus
)