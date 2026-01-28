package io.github.stevekk11.dtos

import kotlinx.serialization.Serializable

@Serializable
data class SubstitutionStatus(
    val lastUpdated: String,
    val currentUpdateSchedule: Int,
    val isOffline: Boolean? = false //If error occurs, "Endpoint not available"
)
