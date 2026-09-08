package dev.fquo.liftwear.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LiftWearApplication
        setContent { LiftWearApp(app.container, app.nodes, BuildConfig.VERSION_NAME) }
    }
}
