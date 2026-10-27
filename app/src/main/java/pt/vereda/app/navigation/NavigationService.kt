package pt.vereda.app.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import pt.vereda.app.MainActivity
import pt.vereda.app.R
import pt.vereda.app.VeredaApplication
import pt.vereda.app.data.Language
import pt.vereda.app.ui.map.durationMinutesText
import pt.vereda.core.geo.Fix
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.route.GpsPolicy
import pt.vereda.core.route.NavEvent
import pt.vereda.core.route.Navigator
import pt.vereda.core.route.Turn

/**
 * Follows a walk with the screen off or the app in the background (spec: Phase 1.5): one GPS
 * request at the adaptive interval, the navigator, voice and vibration, and an ongoing
 * notification with the progress.
 */
class NavigationService : Service() {

    private lateinit var session: NavigationSession
    private var navigator: Navigator? = null
    private var feedback: Feedback? = null
    private var intervalMs = 0L
    private var lastNotifyMs = 0L
    private var lastNotifyKey = ""
    private var batteryWarned = false
    /** After arrival the map keeps showing the finished walk until it is closed. */
    private var arrived = false
    private var lastBatteryCheckMs = 0L
    private val walked = ArrayList<GeoPoint>()
    private val manager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val listener = LocationListenerCompat { onLocation(it) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        session = (application as VeredaApplication).navigation
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val route = session.pending
        if (route == null || !hasLocationPermission()) {
            finish()
            return START_NOT_STICKY
        }
        session.pending = null
        createChannel()
        val nav = Navigator(route)
        navigator = nav
        walked.clear()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(nav),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (feedback == null) feedback = Feedback(this)
        feedback?.say(getString(R.string.nav_started_spoken, route.name))
        requestUpdates(GpsPolicy.NORMAL_MS)
        return START_NOT_STICKY
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked before starting
    private fun requestUpdates(ms: Long) {
        if (ms == intervalMs) return
        intervalMs = ms
        LocationManagerCompat.removeUpdates(manager, listener)
        val request = LocationRequestCompat.Builder(ms)
            .setMinUpdateIntervalMillis(ms)
            .setMinUpdateDistanceMeters(GpsPolicy.MIN_DISTANCE_M)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()
        runCatching {
            LocationManagerCompat.requestLocationUpdates(manager, LocationManager.GPS_PROVIDER, request, listener, Looper.getMainLooper())
        }
    }

    private fun onLocation(location: Location) {
        val nav = navigator ?: return
        val now = System.currentTimeMillis()
        // Fix time from the elapsed-realtime clock, so a wrong phone clock can't make fixes look old.
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        val fix = Fix(
            lat = location.latitude,
            lon = location.longitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else 99.0,
            timeMs = now - ageMs.coerceAtLeast(0),
            ele = if (location.hasAltitude()) location.altitude else null,
        )
        val events = nav.onFix(fix, now)
        val state = nav.state
        if (fix.accuracyM <= 30 && !state.offRoute) walked += GeoPoint(fix.lat, fix.lon)
        events.forEach { feedback?.announce(it) }
        val current = session.active.value
        session.update(
            ActiveNavigation(
                nav.route,
                state,
                walked.toList(),
                current?.startedAtMs ?: now,
            ),
        )
        checkBattery(now)
        notifyIfChanged(nav, events.isNotEmpty())
        if (state.arrived) {
            // Done: GPS off, the notification stays (no longer ongoing) until dismissed.
            LocationManagerCompat.removeUpdates(manager, listener)
            arrived = true
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(nav))
            stopSelf()
        } else {
            requestUpdates(GpsPolicy.intervalMs(state, now))
        }
    }

    private fun checkBattery(now: Long) {
        if (batteryWarned || now - lastBatteryCheckMs < 60_000) return
        lastBatteryCheckMs = now
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        if (level * 100 / scale <= LOW_BATTERY_PERCENT) {
            batteryWarned = true
            feedback?.say(getString(R.string.nav_low_battery))
            notifyIfChanged(navigator ?: return, force = true)
        }
    }

    private fun notifyIfChanged(nav: Navigator, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val s = nav.state
        val key = "${s.offRoute}|${s.wrongWay}|${s.arrived}|${s.nextCue?.alongM}"
        if (!force && key == lastNotifyKey && now - lastNotifyMs < NOTIFY_EVERY_MS) return
        lastNotifyKey = key
        lastNotifyMs = now
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(nav))
    }

    private fun notification(nav: Navigator): Notification {
        val s = nav.state
        val route = nav.route
        val title = if (s.arrived) {
            getString(R.string.nav_arrived_title, route.name)
        } else {
            getString(R.string.nav_notification_title, route.name, CueText.distance(s.remainingM), durationMinutesText(this, s.minutesLeft))
        }
        val cue = s.nextCue
        val text = when {
            s.arrived -> getString(R.string.cue_arrive)
            s.offRoute -> getString(R.string.nav_off_route, CueText.distance(s.offRouteM))
            s.wrongWay -> getString(R.string.nav_wrong_way)
            cue != null && cue.turn != Turn.ARRIVE && s.toNextCueM != null ->
                "${CueText.arrow(cue.turn)} " + getString(R.string.cue_in_distance, CueText.distance(s.toNextCueM!!), CueText.turn(this, cue))
            else -> getString(R.string.nav_follow_line, CueText.distance(s.remainingM))
        } + if (batteryWarned) " · " + getString(R.string.nav_low_battery_short) else ""
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, NavigationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val progress = if (route.lengthM > 0) ((s.progressM / route.lengthM) * 100).toInt().coerceIn(0, 100) else 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, progress, false)
            .setOngoing(!s.arrived)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.nav_stop), stop)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.nav_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.nav_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun finish() {
        arrived = false
        LocationManagerCompat.removeUpdates(manager, listener)
        intervalMs = 0
        navigator = null
        feedback?.shutdown()
        feedback = null
        if (::session.isInitialized) session.update(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        LocationManagerCompat.removeUpdates(manager, listener)
        // Let the arrival announcement finish before the voice goes.
        val voice = feedback
        feedback = null
        if (voice != null) android.os.Handler(Looper.getMainLooper()).postDelayed({ voice.shutdown() }, 4_000)
        if (::session.isInitialized && !arrived) session.update(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "pt.vereda.app.navigation.STOP"
        private const val CHANNEL_ID = "navigation"
        const val NOTIFICATION_ID = 42
        private const val NOTIFY_EVERY_MS = 10_000L
        private const val LOW_BATTERY_PERCENT = 20
    }
}
