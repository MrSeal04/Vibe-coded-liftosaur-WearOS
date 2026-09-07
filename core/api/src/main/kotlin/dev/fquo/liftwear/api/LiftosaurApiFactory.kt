package dev.fquo.liftwear.api

import dev.fquo.liftwear.api.internal.AuthInterceptor
import dev.fquo.liftwear.api.internal.ErrorInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object LiftosaurApiFactory {

    const val BASE_URL = "https://www.liftosaur.com/api/v1/"

    /**
     * Lenient by design. An added field upstream must not brick the watch mid-workout,
     * and a null where a number was expected must coerce to the default rather than throw.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    /**
     * @param apiKeyProvider returns the stored key, or null when unpaired.
     * @param deviceIdProvider a stable per-install id for X-Liftosaur-Device-Id.
     * @param clientName value for X-Liftosaur-Client, e.g. "liftwear/0.1.0".
     */
    fun create(
        apiKeyProvider: () -> String?,
        deviceIdProvider: () -> String,
        clientName: String,
        baseUrl: String = BASE_URL,
        extraInterceptors: List<okhttp3.Interceptor> = emptyList(),
    ): LiftosaurApi {
        val client = OkHttpClient.Builder()
            // A watch on a flaky gym connection should fail fast so the outbox can
            // queue and retry, rather than hanging on a dead socket.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(AuthInterceptor(apiKeyProvider, deviceIdProvider, clientName))
            .addInterceptor(ErrorInterceptor(json))
            .apply { extraInterceptors.forEach { addInterceptor(it) } }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LiftosaurApi::class.java)
    }
}
