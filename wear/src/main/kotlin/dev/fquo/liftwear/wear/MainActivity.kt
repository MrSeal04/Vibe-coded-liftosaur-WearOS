package dev.fquo.liftwear.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiftWearApp() }
    }
}

@Composable
fun LiftWearApp() {
    MaterialTheme {
        AppScaffold {
            ScreenScaffold {
                Text("LiftWear")
            }
        }
    }
}
