package dev.fquo.liftwear.datalayer

/**
 * Shared contract between :mobile and :wear.
 *
 * The Data Layer only carries data between apps that share an applicationId,
 * versionCode and signing certificate - so both app modules must keep those aligned.
 */
object DataLayerContract {
    /** DataItem path carrying the Liftosaur API key from phone to watch. */
    const val PATH_CREDENTIALS = "/liftwear/credentials"

    /** MessageClient path the watch uses to acknowledge receipt, so the phone can delete the item. */
    const val PATH_CREDENTIALS_ACK = "/liftwear/credentials/ack"

    /** Capability advertised by the phone app so the watch can find it. */
    const val CAPABILITY_PHONE_APP = "liftwear_phone_app"

    const val KEY_API_KEY = "api_key"
    const val KEY_ISSUED_AT = "issued_at"
}
