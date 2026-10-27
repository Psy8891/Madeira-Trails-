package pt.vereda.core.maps

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapsTest {

    private val hash = "a".repeat(64)
    private val manifestJson = """
        {"schema": 1, "packages": [{
          "id": "madeira", "name": "Madeira and Porto Santo", "version": "2026.10.27", "minApp": 1,
          "files": [
            {"role": "base", "url": "https://github.com/x/releases/download/maps-2026.10.27/madeira-base.pmtiles", "bytes": 1000, "sha256": "$hash"},
            {"role": "contours", "url": "https://github.com/x/releases/download/maps-2026.10.27/madeira-contours.pmtiles", "bytes": 500, "sha256": "$hash"},
            {"role": "dem", "url": "https://github.com/x/releases/download/maps-2026.10.27/madeira-dem.pmtiles", "bytes": 250, "sha256": "$hash"}
          ]}], "extra": true}
    """.trimIndent()

    @Test
    fun `the manifest is read with sizes and file names`() {
        val m = MapManifest.parse(manifestJson)
        val p = m.find("madeira")!!
        assertEquals(1750, p.totalBytes)
        assertEquals("madeira-dem.pmtiles", p.file("dem")!!.fileName)
        assertNull(m.find("azores"))
        assertEquals(m, MapManifest.parse(MapManifest.encode(m)))
    }

    @Test
    fun `broken or newer manifests are refused`() {
        assertFailsWith<IllegalArgumentException> { MapManifest.parse(manifestJson.replace("\"schema\": 1", "\"schema\": 2")) }
        assertFailsWith<IllegalArgumentException> { MapManifest.parse(manifestJson.replace("https://github.com/x/releases/download/maps-2026.10.27/madeira-base", "http://evil/base")) }
        assertFailsWith<IllegalArgumentException> { MapManifest.parse(manifestJson.replaceFirst(hash, "nope")) }
    }

    @Test
    fun `status says installed, and offers a newer version this app can read`() {
        val p = MapManifest.parse(manifestJson).find("madeira")!!
        assertEquals(PackageStatus.NotInstalled, Packages.status(null, p, 1))
        val old = InstalledPackage("madeira", "2026.09.30", mapOf("base" to "b", "contours" to "c", "dem" to "d"))
        val status = Packages.status(old, p, appVersion = 1)
        assertIs<PackageStatus.Installed>(status)
        assertEquals(p, status.update)
        assertNull((Packages.status(old, p.copy(minApp = 9), appVersion = 1) as PackageStatus.Installed).update)
        assertNull((Packages.status(old.copy(version = "2026.10.27"), p, 1) as PackageStatus.Installed).update)
        assertTrue(Packages.complete(old))
        assertEquals(old, Packages.parseInstalled(Packages.encodeInstalled(old)))
        assertNull(Packages.parseInstalled("{broken"))
        assertNull(Packages.parseInstalled(null))
        assertFalse(Packages.complete(old.copy(files = mapOf("base" to "b"))))
    }

    @Test
    fun `downloads resume from the bytes already on the phone`() {
        assertEquals(0, Resume.startAt(0, 1000))
        assertEquals(400, Resume.startAt(400, 1000))
        assertEquals(0, Resume.startAt(1000, 1000))
        assertEquals(0, Resume.startAt(1200, 1000))
        assertTrue(Resume.accepted(200, 0))
        assertTrue(Resume.accepted(206, 400))
        assertFalse(Resume.accepted(200, 400)) // the server ignored the range: start again
        assertEquals(40, Resume.percent(400, 1000))
        assertEquals(0, Resume.percent(5, 0))
    }

    @Test
    fun `sha256 matches a known value`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".byteInputStream()),
        )
    }

    @Test
    fun `the topo style points at the package files and uses bundled fonts`() {
        val text = TopoStyle.json("/data/files/maps/base.pmtiles", "/data/files/maps/contours.pmtiles", "/data/files/maps/dem.pmtiles")
        assertFalse("{{" in text)
        val style = Json.parseToJsonElement(text).jsonObject
        val sources = style["sources"]!!.jsonObject
        assertEquals("pmtiles://file:///data/files/maps/base.pmtiles", sources["base"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals("terrarium", sources["dem"]!!.jsonObject["encoding"]!!.jsonPrimitive.content)
        val layers = style["layers"]!!.jsonArray.map { it as JsonObject }
        val ids = layers.map { it["id"]!!.jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size, "layer ids are unique")
        assertTrue(TopoStyle.HILLSHADE_LAYER in ids)
        assertTrue(ids.containsAll(TopoStyle.CONTOUR_LAYERS))
        val fonts = layers.mapNotNull { it["layout"]?.jsonObject?.get("text-font") }.flatMap { it.jsonArray }.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf(TopoStyle.FONT_REGULAR, TopoStyle.FONT_BOLD, TopoStyle.FONT_ITALIC), fonts)
        // Hillshade sits under the contours, which sit under the roads.
        assertTrue(ids.indexOf("hillshade") < ids.indexOf("contour-index"))
        assertTrue(ids.indexOf("contour-index") < ids.indexOf("road-regional"))
        assertFailsWith<IllegalArgumentException> { TopoStyle.json("relative", "/a", "/b") }
    }

    @Test
    fun `the overview style has only the base map`() {
        val style = Json.parseToJsonElement(TopoStyle.json("/data/files/maps/overview.pmtiles", null, null)).jsonObject
        assertEquals(setOf("base"), style["sources"]!!.jsonObject.keys)
        val layers = style["layers"]!!.jsonArray.map { it as JsonObject }
        assertTrue(layers.none { it["source"]?.jsonPrimitive?.content in setOf("contours", "dem") })
        assertTrue(layers.any { it["id"]!!.jsonPrimitive.content == "background" })
        assertTrue(layers.any { it["id"]!!.jsonPrimitive.content == "road-regional" })
    }
}
