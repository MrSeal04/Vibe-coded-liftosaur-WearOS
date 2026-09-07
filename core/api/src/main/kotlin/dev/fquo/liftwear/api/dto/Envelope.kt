package dev.fquo.liftwear.api.dto

import kotlinx.serialization.Serializable

/** Every successful Liftosaur v1 response is wrapped in a single `data` object. */
@Serializable
data class Envelope<T>(val data: T)
