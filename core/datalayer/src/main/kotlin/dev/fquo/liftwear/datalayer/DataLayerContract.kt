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

    /** Capability advertised by the watch app so the phone can tell it is installed. */
    const val CAPABILITY_WATCH_APP = "liftwear_watch_app"

    /** ChannelClient path the watch streams its debug log down to the phone. */
    const val PATH_DEBUG_LOG = "/liftwear/debug-log"

    /** MessageClient path the phone answers on, carrying how many bytes it saved. */
    const val PATH_DEBUG_LOG_ACK = "/liftwear/debug-log/ack"

    const val KEY_API_KEY = "api_key"
    const val KEY_ISSUED_AT = "issued_at"

    /**
     * How long an offered credential stays acceptable.
     *
     * DataItems persist and replicate, so an item that somehow survives cleanup should not
     * still be able to pair a watch days later. The phone deletes on ack; this is the
     * belt to that braces.
     */
    const val CREDENTIAL_TTL_MILLIS = 10 * 60 * 1000L
}
