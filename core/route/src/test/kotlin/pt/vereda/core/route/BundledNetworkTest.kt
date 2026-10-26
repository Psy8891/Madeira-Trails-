package pt.vereda.core.route

import org.junit.jupiter.api.Assumptions.assumeTrue
import pt.vereda.core.geo.GeoPoint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Routes on the real network the APK ships (written by data/scripts/build_network.py). */
class BundledNetworkTest {

    private val file = File("../../app/src/main/assets/network.bin")

    private val router: Router by lazy {
        assumeTrue(file.exists(), "No bundled network yet")
        Router(NetworkReader.read(file.readBytes()))
    }

    private val bocaDaCorrida = GeoPoint(32.710971, -16.986848)
    private val picoGrande = GeoPoint(32.737149, -16.987857)
    private val achadaDoTeixeira = GeoPoint(32.764879, -16.921084)
    private val picoRuivo = GeoPoint(32.758884, -16.94276)
    private val picoDoAreeiro = GeoPoint(32.734869, -16.928645)

    private fun route(vararg points: GeoPoint): PlannedRoute {
        val outcome = router.route(points.toList())
        assertIs<RouteOutcome.Found>(outcome, "no route: $outcome")
        return outcome.route
    }

    @Test
    fun `Boca da Corrida to Pico Grande follows PR 12 and the Pico Grande path`() {
        val r = route(bocaDaCorrida, picoGrande)
        assertTrue(r.summary.distanceM in 4000.0..6000.0, "distance ${r.summary.distanceM}")
        assertTrue("Caminho Real da Encumeada" in r.summary.mainNames, "names ${r.summary.mainNames}")
        assertTrue("PR 12" in r.summary.officialTrails)
        assertTrue(r.summary.roadM < 300)
    }

    @Test
    fun `Pico do Areeiro to Pico Ruivo uses PR 1, and Achada do Teixeira is the short way`() {
        val classic = route(picoDoAreeiro, picoRuivo)
        assertTrue(classic.summary.distanceM in 5000.0..7500.0, "distance ${classic.summary.distanceM}")
        assertTrue("PR 1" in classic.summary.officialTrails, "trails ${classic.summary.officialTrails}")
        val short = route(achadaDoTeixeira, picoRuivo)
        assertTrue(short.summary.distanceM in 2300.0..3300.0, "distance ${short.summary.distanceM}")
    }

    @Test
    fun `a route with a stop is planned quickly`() {
        router.route(listOf(bocaDaCorrida, picoGrande)) // warm up the snapping grid
        val start = System.nanoTime()
        val r = route(picoDoAreeiro, picoRuivo, achadaDoTeixeira)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 1500, "took $ms ms")
        assertTrue(r.points.size > 50)
    }
}
