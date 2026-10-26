package pt.vereda.core.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 position in decimal degrees, with optional elevation in metres. */
data class GeoPoint(val lat: Double, val lon: Double, val ele: Double? = null)

object Geo {

    /** Mean Earth radius (IUGG), metres. */
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Metres per degree of latitude on the mean sphere. */
    val METRES_PER_DEGREE: Double = EARTH_RADIUS_M * PI / 180.0

    /** Great-circle (haversine) distance in metres. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double = distanceM(a.lat, a.lon, b.lat, b.lon)

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)
        val h = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Length of a polyline in metres. */
    fun lengthM(points: List<GeoPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }

    /** The point [metres] north (negative: south) of [p]. Good enough for short offsets. */
    fun offsetNorth(p: GeoPoint, metres: Double): GeoPoint =
        p.copy(lat = p.lat + metres / METRES_PER_DEGREE)

    /** The point [metres] east (negative: west) of [p]. Good enough for short offsets. */
    fun offsetEast(p: GeoPoint, metres: Double): GeoPoint =
        p.copy(lon = p.lon + metres / (METRES_PER_DEGREE * cos(Math.toRadians(p.lat))))
}
