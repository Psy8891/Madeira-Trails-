package pt.vereda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.vereda.app.ui.VeredaApp
import pt.vereda.app.ui.theme.VeredaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeredaTheme {
                VeredaApp()
            }
        }
    }
}
