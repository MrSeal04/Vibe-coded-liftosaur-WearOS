package dev.fquo.liftwear.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SettingsDto(
    /** "kg" or "lb". */
    val units: String = "lb",
    val timers: TimersDto = TimersDto(),
)

/**
 * Every field is nullable: the live account returned `superset: null`.
 * Callers should fall back to [DEFAULT_REST_SECONDS] rather than assume a value.
 */
@Serializable
data class TimersDto(
    val warmup: Int? = null,
    val workout: Int? = null,
    val superset: Int? = null,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 180
    }
}
