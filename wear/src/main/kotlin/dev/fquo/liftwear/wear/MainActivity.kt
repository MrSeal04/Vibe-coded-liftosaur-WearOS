package dev.fquo.liftwear.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.fquo.liftwear.wear.debuglog.eventLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LiftWearApplication
        app.debugLog.log(AREA, if (savedInstanceState == null) "activity created" else "activity recreated")
        setContent { LiftWearApp(app.container, app.nodes, app.session, BuildConfig.VERSION_NAME) }
    }

    /** singleTask: a Tile, chip or complication tap on a running app lands here, not in a second copy. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        eventLog().log(AREA, "brought to the front (intent flags 0x${Integer.toHexString(intent.flags)})")
    }

    override fun onStart() {
        super.onStart()
        eventLog().log(AREA, "on screen")
    }

    override fun onStop() {
        eventLog().log(AREA, "off screen")
        super.onStop()
    }

    private companion object {
        const val AREA = "App"
    }
}
