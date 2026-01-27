package io.github.stevekk11.dtos

import kotlinx.serialization.Serializable

@Serializable
data class SubstitutedLesson(
    val hour: Int,
    val group: String?,
    val subject: String?,
    val room: String?,
    val substitutingTeacher: String?,
    val missingTeacher: String?,
    val isDropped: Boolean = false,
    val isJoined: Boolean = false,
    val isSeparated: Boolean = false,
    val roomChanged: Boolean = false,
    val isShifted: Boolean = false,
    val shiftTarget: String? = null,
    val note: String? = null,
    val originalText: String? = null,
)