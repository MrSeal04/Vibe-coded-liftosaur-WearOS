package dev.fquo.liftwear.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorEnvelope(val error: ErrorBodyDto? = null)

@Serializable
data class ErrorBodyDto(
    val code: String? = null,
    val message: String? = null,
    val details: List<ErrorDetailDto> = emptyList(),
)

@Serializable
data class ErrorDetailDto(
    val line: Int? = null,
    val offset: Int? = null,
    val message: String? = null,
)
