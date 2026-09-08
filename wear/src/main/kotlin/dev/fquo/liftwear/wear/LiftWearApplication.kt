package dev.fquo.liftwear.wear

import android.app.Application
import dev.fquo.liftwear.data.LiftWearContainer

class LiftWearApplication : Application() {

    lateinit var container: LiftWearContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The client header is versioned so that, if the unofficial API contract shifts,
        // breakage is attributable to a build rather than to "some watch app".
        container = LiftWearContainer(this, BuildConfig.VERSION_NAME)
    }
}
