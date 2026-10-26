package pt.vereda.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.vereda.app.data.Language
import pt.vereda.app.ui.VeredaApp
import pt.vereda.app.ui.theme.VeredaTheme

class MainActivity : ComponentActivity() {
    // Android 8–12 only: apply the language picked in Me → Language (Phase 0.9).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.wrap(newBase))
    }

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
