package dev.fquo.liftwear.data

import android.content.Context
import dev.fquo.liftwear.api.LiftosaurApi
import dev.fquo.liftwear.api.LiftosaurApiFactory
import dev.fquo.liftwear.data.credentials.CredentialStore
import dev.fquo.liftwear.data.credentials.DeviceId
import dev.fquo.liftwear.data.db.LiftWearDatabase
import dev.fquo.liftwear.data.history.HistoryRepository
import dev.fquo.liftwear.data.outbox.OutboxDrainer
import dev.fquo.liftwear.data.settings.SettingsRepository
import dev.fquo.liftwear.data.workout.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Hand-rolled service locator. A dependency-injection framework would earn its keep on a
 * phone app; here there are four objects and one of them is a Retrofit interface.
 */
class LiftWearContainer(context: Context, clientVersion: String) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val credentials = CredentialStore(appContext)

    /**
     * OkHttp's interceptor needs the key synchronously, so the decrypted value is mirrored
     * here from the DataStore flow. Null means unpaired, and [AuthInterceptor] turns that
     * into a typed InvalidApiKey rather than an unauthenticated request.
     */
    @Volatile private var apiKeyCache: String? = null

    private val _pairing = MutableStateFlow(PairingState.Unknown)

    /** Drives the setup-vs-app decision at startup without blocking on DataStore. */
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    val api: LiftosaurApi = LiftosaurApiFactory.create(
        apiKeyProvider = { apiKeyCache },
        deviceIdProvider = { DeviceId.get(appContext) },
        clientName = "liftwear/$clientVersion",
    )

    val database: LiftWearDatabase by lazy { LiftWearDatabase.create(appContext) }

    val workouts by lazy {
        WorkoutRepository(appContext, api, database, LiftosaurApiFactory.json, sharingScope = scope)
    }

    /**
     * Shared with [dev.fquo.liftwear.data.outbox.OutboxWorker] through the Application's
     * [dev.fquo.liftwear.data.outbox.OutboxHost].
     */
    val outboxDrainer by lazy {
        OutboxDrainer(
            api = api,
            outboxDao = database.outboxDao(),
            cacheDao = database.workoutCacheDao(),
            finishResultDao = database.finishResultDao(),
            json = LiftosaurApiFactory.json,
        )
    }

    val history by lazy { HistoryRepository(api, database) }

    val settings = SettingsRepository(api)

    val deviceId: String get() = DeviceId.get(appContext)

    init {
        scope.launch {
            credentials.apiKey.collect { key ->
                apiKeyCache = key
                _pairing.value = if (key == null) PairingState.Unpaired else PairingState.Paired
            }
        }
    }

    suspend fun setApiKey(key: String) = credentials.setApiKey(key.trim())

    suspend fun unpair() = credentials.clear()

    enum class PairingState { Unknown, Unpaired, Paired }
}
