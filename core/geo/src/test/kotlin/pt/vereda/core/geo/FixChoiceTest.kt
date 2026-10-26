package pt.vereda.core.geo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixChoiceTest {

    private fun gps(t: Long, acc: Double? = 5.0) = FixChoice.Fix(t, acc, fromGps = true)
    private fun network(t: Long, acc: Double? = 300.0) = FixChoice.Fix(t, acc, fromGps = false)

    @Test
    fun `the first fix is always taken`() {
        assertTrue(FixChoice.better(null, network(0)))
    }

    @Test
    fun `an older fix never replaces a newer one`() {
        assertFalse(FixChoice.better(gps(10_000), gps(9_000)))
    }

    @Test
    fun `a rough network fix doesn't replace a fresh GPS fix`() {
        assertFalse(FixChoice.better(gps(10_000), network(12_000)))
    }

    @Test
    fun `a network fix replaces GPS once GPS has gone quiet`() {
        assertTrue(FixChoice.better(gps(10_000), network(30_000)))
    }

    @Test
    fun `a more accurate network fix is taken even after fresh GPS`() {
        assertTrue(FixChoice.better(gps(10_000, acc = 50.0), network(11_000, acc = 20.0)))
    }

    @Test
    fun `newer GPS always replaces`() {
        assertTrue(FixChoice.better(network(10_000), gps(10_500, acc = 40.0)))
        assertTrue(FixChoice.better(gps(10_000), gps(11_000)))
    }
}
