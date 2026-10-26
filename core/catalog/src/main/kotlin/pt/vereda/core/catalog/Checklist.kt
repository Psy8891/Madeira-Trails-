package pt.vereda.core.catalog

/** One thing to check before a walk (spec: Safety, trail-specific checklist). */
enum class ChecklistItem {
    STATUS, BOOKING, WEATHER, TORCH, WATER, LAYERS, FOOTWEAR, PHONE, TELL_SOMEONE, OFFLINE_MAP, START_EARLIER
}

/** What is known about the walk ahead; null when unknown. */
data class WalkFacts(
    val official: Boolean,
    val levada: Boolean,
    val tunnels: Int?,
    val minutes: Int?,
    val highestM: Double?,
    val daylightLeftMinutes: Int?,
    val warnings: Boolean,
    val offlineMap: Boolean,
)

object Checklists {
    /** Above this the weather turns quickly: cold, wind and cloud on the peaks. */
    const val HIGH_M = 1200.0
    /** Daylight to spare after the estimated time. */
    const val MARGIN_MIN = 60

    fun items(f: WalkFacts): List<ChecklistItem> = buildList {
        if (f.official) {
            add(ChecklistItem.STATUS)
            add(ChecklistItem.BOOKING)
        }
        add(ChecklistItem.WEATHER)
        val longWalk = (f.minutes ?: 0) >= 180
        val lateStart = f.daylightLeftMinutes != null && f.minutes != null && f.minutes + MARGIN_MIN > f.daylightLeftMinutes
        if (lateStart) add(ChecklistItem.START_EARLIER)
        if ((f.tunnels ?: 0) > 0 || f.levada || lateStart || longWalk) add(ChecklistItem.TORCH)
        add(ChecklistItem.WATER)
        if ((f.highestM ?: 0.0) >= HIGH_M || f.warnings || longWalk) add(ChecklistItem.LAYERS)
        add(ChecklistItem.FOOTWEAR)
        add(ChecklistItem.PHONE)
        add(ChecklistItem.TELL_SOMEONE)
        if (!f.offlineMap) add(ChecklistItem.OFFLINE_MAP)
    }
}
