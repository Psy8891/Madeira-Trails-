package pt.vereda.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.vereda.app.BuildConfig
import pt.vereda.core.maps.InstalledPackage
import pt.vereda.core.maps.MapFile
import pt.vereda.core.maps.MapManifest
import pt.vereda.core.maps.MapPackage
import pt.vereda.core.maps.Packages
import pt.vereda.core.maps.Resume
import pt.vereda.core.maps.Sha256
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/** Where the manifest stands. */
sealed interface ManifestState {
    data object Loading : ManifestState
    data class Ready(val manifest: MapManifest) : ManifestState
    data class Failed(val message: String) : ManifestState
}

/** The package download (spec: Phase 1, one-tap packages). */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val doneBytes: Long, val totalBytes: Long, val verifying: Boolean = false) : DownloadState
    data class Paused(val doneBytes: Long, val totalBytes: Long) : DownloadState
    data object WaitingForWifi : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Downloads, checks and keeps the offline map package in the app's private files
 * (files/maps/<version>/). Resumes with HTTP Range requests; a file is only used after its
 * SHA-256 matches the manifest.
 */
class MapPackages(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: Settings,
) {
    private val root = File(context.filesDir, "maps")
    private val record = File(root, "installed.json")

    private val _manifest = MutableStateFlow<ManifestState>(ManifestState.Loading)
    val manifest: StateFlow<ManifestState> = _manifest.asStateFlow()

    private val _installed = MutableStateFlow<InstalledPackage?>(null)
    val installed: StateFlow<InstalledPackage?> = _installed.asStateFlow()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private var job: Job? = null

    /** Reads the small install record right away (so the map opens on the right style), then the manifest. */
    fun start() {
        _installed.value = runCatching {
            Packages.parseInstalled(record.takeIf { it.exists() }?.readText())
                ?.takeIf { p -> p.files.values.all { File(versionDir(p.version), it).exists() } }
        }.getOrNull()
        scope.launch { refreshManifest() }
    }

    /** Absolute paths of the installed files by role, or null when nothing complete is installed. */
    fun installedPaths(p: InstalledPackage? = _installed.value): Map<String, String>? {
        if (!Packages.complete(p)) return null
        return p!!.files.mapValues { (_, name) -> File(versionDir(p.version), name).absolutePath }
    }

    suspend fun refreshManifest() {
        if (_manifest.value !is ManifestState.Ready) _manifest.value = ManifestState.Loading
        _manifest.value = withContext(Dispatchers.IO) {
            runCatching { ManifestState.Ready(MapManifest.parse(fetchText(MANIFEST_URL))) }
                .getOrElse { e ->
                    (_manifest.value as? ManifestState.Ready) ?: ManifestState.Failed(e.message ?: e.javaClass.simpleName)
                }
        }
    }

    fun refreshManifestAsync() {
        scope.launch { refreshManifest() }
    }

    /** Download (or update to) [pkg]; resumes whatever is already on the phone. */
    fun download(pkg: MapPackage) {
        if (job?.isActive == true) return
        if (settings.wifiOnly.value && isMetered()) {
            _download.value = DownloadState.WaitingForWifi
            return
        }
        job = scope.launch(Dispatchers.IO) {
            val dir = versionDir(pkg.version).apply { mkdirs() }
            try {
                var doneBefore = 0L
                for (file in pkg.files) {
                    val target = File(dir, file.fileName)
                    if (!target.exists()) fetchFile(file, target, doneBefore, pkg.totalBytes)
                    doneBefore += file.bytes
                }
                val installed = InstalledPackage(pkg.id, pkg.version, pkg.files.associate { it.role to it.fileName })
                record.parentFile?.mkdirs()
                val tmp = File(root, "installed.json.tmp")
                tmp.writeText(Packages.encodeInstalled(installed))
                if (!tmp.renameTo(record)) throw IOException("Couldn't save the map record")
                val previous = _installed.value
                _installed.value = installed
                _download.value = DownloadState.Idle
                // Remove older versions only once the new one is in use.
                root.listFiles { f -> f.isDirectory && f.name != pkg.version }?.forEach { it.deleteRecursively() }
                if (previous != null && previous.version != pkg.version) Log.i(TAG, "Updated maps ${previous.version} → ${pkg.version}")
            } catch (e: CancellationException) {
                val done = partBytes(pkg)
                _download.value = DownloadState.Paused(done, pkg.totalBytes)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Map download failed", e)
                _download.value = DownloadState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun pause() {
        job?.cancel()
    }

    /** Delete the installed package and any partial download. */
    fun delete() {
        job?.cancel()
        scope.launch(Dispatchers.IO) {
            record.delete()
            root.listFiles()?.forEach { it.deleteRecursively() }
            _installed.value = null
            _download.value = DownloadState.Idle
        }
    }

    private fun versionDir(version: String) = File(root, version)

    private fun partBytes(pkg: MapPackage): Long {
        val dir = versionDir(pkg.version)
        return pkg.files.sumOf { f ->
            val done = File(dir, f.fileName)
            if (done.exists()) f.bytes else File(dir, f.fileName + ".part").length()
        }
    }

    private suspend fun fetchFile(file: MapFile, target: File, doneBefore: Long, total: Long) {
        val part = File(target.path + ".part")
        val start = Resume.startAt(part.length(), file.bytes)
        if (start == 0L) part.delete()
        val connection = URL(file.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Vereda/${BuildConfig.VERSION_NAME} (Android)")
            if (start > 0) connection.setRequestProperty("Range", "bytes=$start-")
            val code = connection.responseCode
            val append = when {
                Resume.accepted(code, start) -> start > 0
                code == HttpURLConnection.HTTP_OK -> false // the server ignored the range: start over
                else -> throw IOException("HTTP $code for ${file.fileName}")
            }
            var written = if (append) start else 0L
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(128 * 1024)
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        written += n
                        if (written - lastReport > 256 * 1024) {
                            lastReport = written
                            _download.value = DownloadState.Running(doneBefore + written, total)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (part.length() != file.bytes) throw IOException("${file.fileName} is incomplete, try again")
        _download.value = DownloadState.Running(doneBefore + file.bytes, total, verifying = true)
        if (Sha256.of(part) != file.sha256) {
            part.delete()
            throw IOException("${file.fileName} failed its checksum, try again")
        }
        if (!part.renameTo(target)) throw IOException("Couldn't save ${file.fileName}")
    }

    private fun isMetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MANIFEST_URL = "https://github.com/Psy8891/Madeira-Trails-/releases/download/maps-latest/manifest.json"
        /** Bumped when the app can no longer read an older package format (manifest minApp). */
        const val APP_MAPS_VERSION = 1
        private const val TAG = "MapPackages"
    }
}
