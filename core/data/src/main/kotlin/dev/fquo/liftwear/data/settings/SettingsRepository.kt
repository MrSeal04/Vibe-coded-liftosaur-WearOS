package dev.fquo.liftwear.data.settings

import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurApi
import dev.fquo.liftwear.api.apiCall
import dev.fquo.liftwear.api.dto.SettingsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Account settings: units and the default rest timers.
 *
 * `GET /settings` doubles as the key-validation call - it is the cheapest endpoint that
 * distinguishes a bad key (401) from a lapsed subscription (403).
 */
class SettingsRepository(private val api: LiftosaurApi) {

    private val _settings = MutableStateFlow<SettingsDto?>(null)
    val settings: StateFlow<SettingsDto?> = _settings.asStateFlow()

    suspend fun refresh(): ApiResult<SettingsDto> =
        apiCall { api.getSettings().data }.also { if (it is ApiResult.Ok) _settings.value = it.value }

    /** "kg" or "lb"; falls back to the account default before the first fetch lands. */
    val units: String get() = _settings.value?.units ?: "lb"
}
