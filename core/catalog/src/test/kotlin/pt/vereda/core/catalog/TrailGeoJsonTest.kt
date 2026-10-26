package pt.vereda.core.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.model.Island
import pt.vereda.core.model.OfficialTrails
import pt.vereda.core.model.StatusState
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSource
import kotlin.test.Test
import kotlin.test.assertEquals

class TrailGeoJsonTest {

    private val line = listOf(GeoPoint(32.71077, -16.98652), GeoPoint(32.7146, -16.993))
    private val pr6 = TrailEntry(OfficialTrails.byId("pr-6")!!, 4300, null, listOf(line, line))
    private val pr7 = TrailEntry(OfficialTrails.byId("pr-7")!!, null, null, emptyList())
    private val quoted = TrailEntry(
        Trail("osm-w1", TrailSource.COMMUNITY, null, "Caminho \"velho\" \\ novo", null, null, null, Island.MADEIRA),
        800, null, listOf(line),
    )

    @Test
    fun `line styles follow the design tokens and the status`() {
        assertEquals(LineStyle.OPEN, LineStyle.of(pr6, StatusState.OPEN))
        assertEquals(LineStyle.CLOSED, LineStyle.of(pr6, StatusState.CLOSED))
        assertEquals(LineStyle.UNKNOWN, LineStyle.of(pr6, StatusState.UNKNOWN))
        assertEquals(LineStyle.COMMUNITY, LineStyle.of(quoted, StatusState.OPEN))
        assertEquals("#C62828", LineStyle.CLOSED.color)
        assertEquals(true, LineStyle.CLOSED.dashed)
        assertEquals(true, LineStyle.COMMUNITY.dashed)
        assertEquals(false, LineStyle.OPEN.dashed)
    }

    @Test
    fun `feature collection is valid GeoJSON with lon-lat order and escaped names`() {
        val text = TrailGeoJson.lines(listOf(pr6, pr7, quoted)) { LineStyle.of(it, StatusState.OPEN) }
        val features = Json.parseToJsonElement(text).jsonObject["features"]!!.jsonArray
        assertEquals(2, features.size) // pr-7 has no line
        val first = features[0].jsonObject
        assertEquals("pr-6", first["properties"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("#2E9E5B", first["properties"]!!.jsonObject["color"]!!.jsonPrimitive.content)
        assertEquals(false, first["properties"]!!.jsonObject["dashed"]!!.jsonPrimitive.boolean)
        val coordinates = first["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(2, coordinates.size)
        val firstPosition = coordinates[0].jsonArray[0].jsonArray
        assertEquals(-16.98652, firstPosition[0].jsonPrimitive.double, 1e-9)
        assertEquals(32.71077, firstPosition[1].jsonPrimitive.double, 1e-9)
        assertEquals("Caminho \"velho\" \\ novo", features[1].jsonObject["properties"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `point collection`() {
        val text = TrailGeoJson.point(GeoPoint(32.7146, -16.993), "Pico Grande")
        val feature = Json.parseToJsonElement(text).jsonObject["features"]!!.jsonArray.single().jsonObject
        assertEquals("Point", feature["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `fixed five decimals match String format`() {
        listOf(-16.98652, 32.7146, 0.0, -0.000001, 0.000005, -17.0, 33.123456789, -0.5).forEach { value ->
            val fast = StringBuilder().apply { appendFixed5(value) }.toString()
            val slow = String.format(java.util.Locale.ROOT, "%.5f", value).let { if (it == "-0.00000") "0.00000" else it }
            assertEquals(slow.toDouble(), fast.toDouble(), 1e-5, "value $value: $fast vs $slow")
        }
        assertEquals("-16.98652", StringBuilder().apply { appendFixed5(-16.98652) }.toString())
        assertEquals("32.71460", StringBuilder().apply { appendFixed5(32.7146) }.toString())
    }
}
