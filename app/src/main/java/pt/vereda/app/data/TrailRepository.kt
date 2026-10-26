package pt.vereda.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.vereda.core.catalog.Catalog
import pt.vereda.core.catalog.CatalogParser
import pt.vereda.core.catalog.GpxImport
import pt.vereda.core.catalog.StatusParser
import pt.vereda.core.catalog.StatusSnapshot
import pt.vereda.core.catalog.TrailEntry
import pt.vereda.core.geo.GpxParseException
import pt.vereda.core.geo.GpxParser
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/** Live status as the app knows it: the last good snapshot plus how the last refresh went. */
data class StatusLoad(
    val snapshot: StatusSnapshot = StatusSnapshot.EMPTY,
    val refreshing: Boolean = false,
    val lastError: String? = null,
)

sealed interface ImportResult {
    data class Imported(val entry: TrailEntry) : ImportResult
    data object NoTrack : ImportResult
    data object NotGpx : ImportResult
    data object TooBig : ImportResult
}

/**
 * The trails the app shows: the catalog bundled in the APK (OpenStreetMap, ODbL), the official
 * status published by the status job, and GPX files the user imported. Imports never leave the phone.
 */
class TrailRepository(private val context: Context, private val scope: CoroutineScope) {

    private val _catalog = MutableStateFlow<Catalog?>(null)
    /** Null while the bundled catalog is being read. */
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    private val _status = MutableStateFlow(StatusLoad())
    val status: StateFlow<StatusLoad> = _status.asStateFlow()

    private val _imports = MutableStateFlow<List<TrailEntry>>(emptyList())
    val imports: StateFlow<List<TrailEntry>> = _imports.asStateFlow()

    private val statusCache = File(context.filesDir, "status.json")
    private val importsDir = File(context.filesDir, "imports")
    private val refreshLock = Mutex()
    @Volatile private var lastSuccess: Instant? = null

    fun start() {
        scope.launch(Dispatchers.IO) {
            _catalog.value = runCatching {
                context.assets.open(CATALOG_ASSET).bufferedReader().use { CatalogParser.parse(it.readText()) }
            }.getOrElse { CatalogParser.parse("{}") } // still lists the 42 official trails
        }
        scope.launch(Dispatchers.IO) {
            runCatching { StatusParser.parse(statusCache.readText()) }.getOrNull()?.let { cached ->
                _status.update { it.copy(snapshot = cached) }
            }
            refreshStatus()
        }
        scope.launch(Dispatchers.IO) { _imports.value = readImports() }
    }

    /** Downloads the latest status.json; on failure the last good copy stays (freshness rule applies). */
    suspend fun refreshStatus() {
        if (!refreshLock.tryLock()) return
        try {
            _status.update { it.copy(refreshing = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = download(STATUS_URL)
                    val snapshot = StatusParser.parse(text)
                    statusCache.writeText(text)
                    snapshot
                }
            }
            if (result.isSuccess) lastSuccess = Instant.now()
            _status.update { current ->
                result.fold(
                    onSuccess = { StatusLoad(snapshot = it, refreshing = false, lastError = null) },
                    onFailure = { current.copy(refreshing = false, lastError = it.message ?: it.javaClass.simpleName) },
                )
            }
        } finally {
            refreshLock.unlock()
        }
    }

    /** Refreshes when the last good download is older than [maxAge] (on app resume). */
    suspend fun refreshStatusIfOlderThan(maxAge: java.time.Duration) {
        val last = lastSuccess
        if (last == null || java.time.Duration.between(last, Instant.now()) > maxAge) refreshStatus()
    }

    /** Copies the picked file into app storage and adds it to "My imports". */
    suspend fun importGpx(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
            if (buffer.size > MAX_IMPORT_BYTES) return@withContext ImportResult.TooBig
            buffer
        } ?: return@withContext ImportResult.NotGpx

        val document = try {
            GpxParser.parse(bytes.inputStream())
        } catch (e: GpxParseException) {
            return@withContext ImportResult.NotGpx
        }
        val id = GpxImport.ID_PREFIX + UUID.randomUUID().toString()
        val fallbackName = GpxImport.nameFromFile(displayName)
        val entry = GpxImport.toEntry(id, document, fallbackName) ?: return@withContext ImportResult.NoTrack

        importsDir.mkdirs()
        File(importsDir, "$id.gpx").writeBytes(bytes)
        File(importsDir, "$id.name").writeText(fallbackName)
        _imports.update { listOf(entry) + it }
        ImportResult.Imported(entry)
    }

    suspend fun deleteImport(id: String) = withContext(Dispatchers.IO) {
        File(importsDir, "$id.gpx").delete()
        File(importsDir, "$id.name").delete()
        _imports.update { list -> list.filterNot { it.id == id } }
    }

    private fun readImports(): List<TrailEntry> {
        val files = importsDir.listFiles { f -> f.name.endsWith(".gpx") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.mapNotNull { file ->
            val id = file.name.removeSuffix(".gpx")
            val fallback = File(importsDir, "$id.name").takeIf { it.exists() }?.readText() ?: GpxImport.nameFromFile(null)
            runCatching { file.inputStream().use { GpxImport.toEntry(id, GpxParser.parse(it), fallback) } }.getOrNull()
        }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        const val CATALOG_ASSET = "catalog.json"
        const val STATUS_URL = "https://raw.githubusercontent.com/Psy8891/Madeira-Trails-/status/status.json"
        const val OFFICIAL_LIST_URL = "https://www.visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/"
        private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

        /** "now" for the freshness rule, in one place so screens agree. */
        fun now(): Instant = Instant.now()
    }
}
