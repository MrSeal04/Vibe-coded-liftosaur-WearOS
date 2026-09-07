package dev.fquo.liftwear.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProgramListEnvelope(val programs: List<ProgramSummaryDto> = emptyList())

@Serializable
data class ProgramSummaryDto(
    val id: String,
    val name: String = "",
    val isCurrent: Boolean = false,
)
