package pt.vereda.app

import android.app.Application
import org.maplibre.android.MapLibre

class VeredaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialised once before any MapView is created.
        MapLibre.getInstance(this)
    }
}
