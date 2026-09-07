package dev.fquo.liftwear.api.dto

import kotlinx.serialization.Serializable

/**
 * History is NOT structured JSON. Each record carries only an id and a blob of
 * Liftoscript "Workouts format" text. See docs/api-findings.md; parsing lives in
 * [dev.fquo.liftwear.api.history.WorkoutTextParser].
 */
@Serializable
data class HistoryPageDto(
    val records: List<HistoryRecordDto> = emptyList(),
    val hasMore: Boolean = false,
    /** The id of the last record on this page; feed back as `cursor`. */
    val nextCursor: Long? = null,
)

@Serializable
data class HistoryRecordDto(
    /** Unix milliseconds; doubles as the record's identity. */
    val id: Long,
    val text: String = "",
)
