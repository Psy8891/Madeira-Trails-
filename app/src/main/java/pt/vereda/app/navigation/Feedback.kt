package pt.vereda.app.navigation

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import pt.vereda.app.R
import pt.vereda.core.route.Cue
import pt.vereda.core.route.NavEvent
import pt.vereda.core.route.Turn
import java.util.Locale
import kotlin.math.roundToInt

/** Words for cues, used by the voice, the notification and the screen (spec: Phase 1.5). */
object CueText {
    fun turn(context: Context, cue: Cue): String {
        val verb = context.getString(
            when (cue.turn) {
                Turn.STRAIGHT -> R.string.cue_straight
                Turn.KEEP_LEFT -> R.string.cue_keep_left
                Turn.KEEP_RIGHT -> R.string.cue_keep_right
                Turn.LEFT -> R.string.cue_left
                Turn.RIGHT -> R.string.cue_right
                Turn.SHARP_LEFT -> R.string.cue_sharp_left
                Turn.SHARP_RIGHT -> R.string.cue_sharp_right
                Turn.TURN_AROUND -> R.string.cue_turn_around
                Turn.ARRIVE -> R.string.cue_arrive
            },
        )
        return if (cue.name != null && cue.turn != Turn.TURN_AROUND && cue.turn != Turn.ARRIVE) {
            context.getString(R.string.cue_on_name, verb, cue.name)
        } else verb
    }

    fun distance(metres: Double): String =
        if (metres >= 1000) String.format(Locale.getDefault(), "%.1f km", metres / 1000) else "${(metres / 10).roundToInt() * 10} m"

    /** An arrow for the banner and the notification. */
    fun arrow(turn: Turn): String = when (turn) {
        Turn.STRAIGHT -> "↑"
        Turn.KEEP_LEFT -> "↖"
        Turn.KEEP_RIGHT -> "↗"
        Turn.LEFT, Turn.SHARP_LEFT -> "←"
        Turn.RIGHT, Turn.SHARP_RIGHT -> "→"
        Turn.TURN_AROUND -> "↩"
        Turn.ARRIVE -> "⚑"
    }

    fun spoken(context: Context, event: NavEvent): String? = when (event) {
        is NavEvent.CueAhead -> context.getString(R.string.cue_in_distance, distance(event.inM), turn(context, event.cue))
        is NavEvent.CueNow -> turn(context, event.cue)
        is NavEvent.OffRoute -> context.getString(R.string.nav_off_route_spoken, distance(event.distanceM))
        NavEvent.BackOnRoute -> context.getString(R.string.nav_back_on_route)
        NavEvent.WrongWay -> context.getString(R.string.nav_wrong_way)
        NavEvent.Arrived -> context.getString(R.string.cue_arrive)
    }
}

/** Short spoken lines and vibration patterns; vibration only when no voice is available. */
class Feedback(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var ready = false
    private val waiting = ArrayList<String>()

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val locale = Locale.getDefault()
        val ok = engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) return
        engine.language = locale
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        ready = true
        waiting.forEach { speak(it) }
        waiting.clear()
    }

    fun announce(event: NavEvent) {
        vibrate(
            when (event) {
                is NavEvent.CueAhead -> longArrayOf(0, 120)
                is NavEvent.CueNow -> longArrayOf(0, 120, 120, 120)
                is NavEvent.OffRoute, NavEvent.WrongWay -> longArrayOf(0, 400, 200, 400, 200, 400)
                NavEvent.BackOnRoute -> longArrayOf(0, 80)
                NavEvent.Arrived -> longArrayOf(0, 700)
            },
        )
        CueText.spoken(context, event)?.let(::speak)
    }

    fun say(text: String) = speak(text)

    private fun speak(text: String) {
        val engine = tts ?: return
        if (!ready) {
            if (waiting.size < 3) waiting += text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vereda-${System.nanoTime()}")
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
