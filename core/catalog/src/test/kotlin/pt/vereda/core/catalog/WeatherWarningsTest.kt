package pt.vereda.core.catalog

import pt.vereda.core.model.Island
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherWarningsTest {
    private val json = """
        [
          {"text":"","awarenessTypeName":"Precipitação","idAreaAviso":"MRM","startTime":"2026-10-27T06:00:00","awarenessLevelID":"yellow","endTime":"2026-10-27T18:00:00"},
          {"text":"Vento forte","awarenessTypeName":"Vento","idAreaAviso":"MCN","startTime":"2026-10-27T09:00:00","awarenessLevelID":"orange","endTime":"2026-10-28T00:00:00"},
          {"text":"","awarenessTypeName":"Agitação Marítima","idAreaAviso":"MPS","startTime":"2026-10-27T00:00:00","awarenessLevelID":"yellow","endTime":"2026-10-27T12:00:00"},
          {"text":"","awarenessTypeName":"Nevoeiro","idAreaAviso":"MCS","startTime":"2026-10-26T00:00:00","awarenessLevelID":"yellow","endTime":"2026-10-26T12:00:00"},
          {"text":"","awarenessTypeName":"Trovoada","idAreaAviso":"MRM","startTime":"2026-10-27T00:00:00","awarenessLevelID":"green","endTime":"2026-10-28T00:00:00"},
          {"text":"","awarenessTypeName":"Precipitação","idAreaAviso":"LSB","startTime":"2026-10-27T00:00:00","awarenessLevelID":"red","endTime":"2026-10-28T00:00:00"},
          {"text":"","awarenessTypeName":"Vento","idAreaAviso":"MRM","startTime":"broken","awarenessLevelID":"red","endTime":"2026-10-28T00:00:00"}
        ]
    """.trimIndent()

    private val now = Instant.parse("2026-10-27T08:00:00Z")

    @Test
    fun `only the archipelago's areas are read`() {
        val all = WeatherWarnings.parse(json)
        assertEquals(5, all.size) // mainland Lisbon and the broken row are skipped
        assertEquals(WarningKind.RAIN, all[0].kind)
        assertEquals(WarningArea.MOUNTAINS, all[0].area)
        assertEquals(Instant.parse("2026-10-27T06:00:00Z"), all[0].start) // Portuguese winter time is UTC
    }

    @Test
    fun `Madeira shows yellow and worse, still to come, worst first`() {
        val madeira = WeatherWarnings.relevant(WeatherWarnings.parse(json), Island.MADEIRA, now)
        assertEquals(listOf(WarningLevel.ORANGE, WarningLevel.YELLOW), madeira.map { it.level })
        assertEquals(listOf(WarningKind.WIND, WarningKind.RAIN), madeira.map { it.kind })
        val portoSanto = WeatherWarnings.relevant(WeatherWarnings.parse(json), Island.PORTO_SANTO, now)
        assertEquals(listOf(WarningKind.SEA), portoSanto.map { it.kind })
    }

    @Test
    fun `kinds and levels are read from IPMA's names`() {
        assertEquals(WarningKind.THUNDER, WarningKind.of("Trovoada"))
        assertEquals(WarningKind.HOT, WarningKind.of("Tempo Quente"))
        assertEquals(WarningKind.OTHER, WarningKind.of("Algo novo"))
        assertEquals(WarningLevel.RED, WarningLevel.of("RED"))
        assertEquals(WarningLevel.GREEN, WarningLevel.of(null))
    }
}
