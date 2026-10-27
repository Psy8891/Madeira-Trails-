package pt.vereda.core.maps

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the offline map packages are (spec: *Package manifest*, Phase 1). */
@Serializable
data class MapManifest(
    val schema: Int = 1,
    val packages: List<MapPackage> = emptyList(),
) {
    fun find(id: String): MapPackage? = packages.firstOrNull { it.id == id }

    companion object {
        const val SCHEMA = 1
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** Parses manifest.json; refuses a newer schema the app can't read. */
        fun parse(text: String): MapManifest {
            val manifest = json.decodeFromString(serializer(), text)
            require(manifest.schema <= SCHEMA) { "Manifest schema ${manifest.schema} is newer than this app" }
            manifest.packages.forEach { p ->
                require(p.files.isNotEmpty()) { "Package ${p.id} has no files" }
                p.files.forEach { f ->
                    require(f.url.startsWith("https://")) { "File ${f.role} of ${p.id} is not https" }
                    require(f.sha256.matches(Regex("[0-9a-f]{64}"))) { "File ${f.role} of ${p.id} has no SHA-256" }
                    require(f.bytes > 0) { "File ${f.role} of ${p.id} is empty" }
                }
            }
            return manifest
        }

        fun encode(manifest: MapManifest): String = json.encodeToString(serializer(), manifest)
    }
}

@Serializable
data class MapPackage(
    val id: String,
    val name: String = id,
    /** Date-based, sortable: "2026.10.27". */
    val version: String,
    val minApp: Int = 1,
    val files: List<MapFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.bytes }
    fun file(role: String): MapFile? = files.firstOrNull { it.role == role }
}

@Serializable
data class MapFile(
    /** "base", "contours" or "dem". */
    val role: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
) {
    /** The file name on the phone: the last part of the URL. */
    val fileName: String get() = url.substringAfterLast('/').substringBefore('?')
}

/** A package on the phone, as recorded after every file passed its checksum. */
@Serializable
data class InstalledPackage(
    val id: String,
    val version: String,
    /** role → file name inside the maps folder. */
    val files: Map<String, String>,
)

sealed interface PackageStatus {
    data object NotInstalled : PackageStatus
    data class Installed(val version: String, val update: MapPackage?) : PackageStatus
}

object Packages {
    const val MADEIRA = "madeira"
    val ROLES = listOf("base", "contours", "dem")
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeInstalled(p: InstalledPackage): String = json.encodeToString(InstalledPackage.serializer(), p)

    /** The record written after install, or null when missing or unreadable. */
    fun parseInstalled(text: String?): InstalledPackage? =
        text?.let { runCatching { json.decodeFromString(InstalledPackage.serializer(), it) }.getOrNull() }

    /** Installed, and whether the manifest (when known) has a newer version for this app. */
    fun status(installed: InstalledPackage?, available: MapPackage?, appVersion: Int): PackageStatus {
        if (installed == null) return PackageStatus.NotInstalled
        val newer = available?.takeIf { it.version > installed.version && it.minApp <= appVersion }
        return PackageStatus.Installed(installed.version, newer)
    }

    /** True when every role the style needs is on the phone. */
    fun complete(installed: InstalledPackage?): Boolean = installed != null && ROLES.all { it in installed.files }
}

/** Resuming an interrupted download with an HTTP Range request. */
object Resume {
    /** Where to start: the bytes already on disk, unless they are none, all or too many. */
    fun startAt(partBytes: Long, expectedBytes: Long): Long =
        if (partBytes in 1 until expectedBytes) partBytes else 0L

    /** Whether the server's answer continues the file from [start]. */
    fun accepted(responseCode: Int, start: Long): Boolean = when {
        start == 0L -> responseCode == 200
        else -> responseCode == 206
    }

    /** Percent done, 0–100. */
    fun percent(done: Long, total: Long): Int = if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)
}
