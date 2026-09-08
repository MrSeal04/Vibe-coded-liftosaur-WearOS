package dev.fquo.liftwear.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LiftWearApplication).container
        setContent { LiftWearApp(container, BuildConfig.VERSION_NAME) }
    }
}
