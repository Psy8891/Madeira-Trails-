package pt.vereda.app.ui.map

import android.content.Context
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import pt.vereda.core.catalog.LineStyle
import pt.vereda.core.catalog.PlaceKind
import pt.vereda.core.maps.TopoStyle

/** Sources and layers Vereda adds on top of the base map (spec: Map style, Phase 0.6). */
object MapLayers {
    const val TRAILS_SOURCE = "vereda-trails"
    const val SELECTED_SOURCE = "vereda-selected"
    const val PLACES_SOURCE = "vereda-places"
    const val MARKERS_SOURCE = "vereda-markers"
    const val ROUTE_SOURCE = "vereda-route"
    const val PLAN_SOURCE = "vereda-plan"

    private const val TRAILS_SOLID = "vereda-trails-solid"
    private const val TRAILS_DASHED = "vereda-trails-dashed"
    private const val SELECTED_CASING = "vereda-selected-casing"
    private const val SELECTED_LINE = "vereda-selected-line"
    private const val APPROACH_LINE = "vereda-approach"
    private const val MARKERS = "vereda-markers"
    private const val PLACE_HALO = "vereda-place-halo"
    private const val ROUTE_CASING = "vereda-route-casing"
    private const val ROUTE_LINE = "vereda-route-line"
    private const val PLAN_POINTS = "vereda-plan-points"

    /** One layer per zoom band, so each kind of place appears at its own zoom (PlaceKind.minZoom). */
    private val placeBands: List<Pair<String, Double>> =
        PlaceKind.entries.map { it.minZoom }.distinct().sorted().map { "vereda-places-$it" to it }

    /** Layers a tap can pick a trail from. */
    val TAPPABLE_TRAILS = arrayOf(TRAILS_SOLID, TRAILS_DASHED)

    /** Layers a tap can pick a place from. */
    val TAPPABLE_PLACES: Array<String> = placeBands.map { it.first }.toTypedArray()

    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /** Fonts for Vereda's own labels: the offline style ships its own (TopoStyle). */
    data class Fonts(val regular: String, val bold: String) {
        companion object {
            val ONLINE = Fonts("Noto Sans Regular", "Noto Sans Bold")
        }
    }

    fun install(context: Context, style: Style, fonts: Fonts = Fonts.ONLINE) {
        if (style.getSource(TRAILS_SOURCE) != null) return
        hideBasePeakLabels(style)
        PoiIcons.all(context).forEach { (name, bitmap) -> style.addImage(name, bitmap) }

        style.addSource(GeoJsonSource(TRAILS_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(SELECTED_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(PLACES_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(MARKERS_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(ROUTE_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(PLAN_SOURCE, EMPTY))

        val width = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(9, 1.2f),
            Expression.stop(13, 2.5f),
            Expression.stop(16, 4f),
        )
        val color = Expression.toColor(Expression.get("color"))

        style.addLayer(
            LineLayer(TRAILS_DASHED, TRAILS_SOURCE)
                .withFilter(Expression.eq(Expression.get("dashed"), true))
                .withProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(width),
                    PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
        style.addLayer(
            LineLayer(TRAILS_SOLID, TRAILS_SOURCE)
                .withFilter(Expression.eq(Expression.get("dashed"), false))
                .withProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(width),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )

        // Points of interest under the picked trail, so the highlight stays readable.
        placeBands.forEach { (layerId, minZoom) ->
            val isPeakBand = PlaceKind.entries.filter { it.minZoom == minZoom }.all { it == PlaceKind.PEAK }
            val label = if (isPeakBand) {
                Expression.get("label")
            } else {
                // Names of other places only once zoomed in, so peaks keep the room.
                Expression.step(Expression.zoom(), Expression.literal(""), Expression.stop(14, Expression.get("label")))
            }
            style.addLayer(
                SymbolLayer(layerId, PLACES_SOURCE)
                    .withFilter(Expression.eq(Expression.get("minZoom"), minZoom))
                    .withProperties(
                        PropertyFactory.iconImage(Expression.concat(Expression.literal("poi-"), Expression.get("kind"))),
                        PropertyFactory.iconAllowOverlap(false),
                        PropertyFactory.iconPadding(1f),
                        PropertyFactory.symbolSortKey(Expression.get("rank")),
                        PropertyFactory.textField(label),
                        PropertyFactory.textSize(11.5f),
                        PropertyFactory.textFont(arrayOf(fonts.regular)),
                        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                        PropertyFactory.textOffset(arrayOf(0f, 0.9f)),
                        PropertyFactory.textOptional(true),
                        PropertyFactory.textMaxWidth(8f),
                        PropertyFactory.textColor("#17201B"),
                        PropertyFactory.textHaloColor("#FFFFFF"),
                        PropertyFactory.textHaloWidth(1.6f),
                    ),
            )
            style.getLayer(layerId)?.minZoom = minZoom.toFloat()
        }

        style.addLayer(
            LineLayer(SELECTED_CASING, SELECTED_SOURCE).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(SELECTED_LINE, SELECTED_SOURCE).withProperties(
                PropertyFactory.lineColor(LineStyle.SELECTED_COLOR),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        // The walk from the road to the start: dotted, in ink, so it doesn't read as the trail.
        style.addLayer(
            LineLayer(APPROACH_LINE, MARKERS_SOURCE)
                .withFilter(Expression.eq(Expression.get("role"), "approach"))
                .withProperties(
                    PropertyFactory.lineColor("#17201B"),
                    PropertyFactory.lineWidth(3.5f),
                    PropertyFactory.lineDasharray(arrayOf(0.1f, 2f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
        style.addLayer(
            CircleLayer(PLACE_HALO, MARKERS_SOURCE)
                .withFilter(Expression.eq(Expression.get("role"), "place"))
                .withProperties(
                    PropertyFactory.circleRadius(16f),
                    PropertyFactory.circleColor(LineStyle.SELECTED_COLOR),
                    PropertyFactory.circleOpacity(0.25f),
                ),
        )
        style.addLayer(
            SymbolLayer(MARKERS, MARKERS_SOURCE)
                .withFilter(Expression.neq(Expression.get("role"), "approach"))
                .withProperties(
                    PropertyFactory.iconImage(
                        Expression.match(
                            Expression.get("role"),
                            Expression.literal(PoiIcons.PLACE),
                            Expression.stop("start", PoiIcons.START),
                            Expression.stop("end", PoiIcons.END),
                            Expression.stop("road", PoiIcons.ROAD),
                            Expression.stop("road-other", PoiIcons.ROAD),
                            Expression.stop("parking", PoiIcons.PARKING_MARKER),
                        ),
                    ),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconOpacity(
                        Expression.match(Expression.get("role"), Expression.literal(1f), Expression.stop("road-other", 0.55f)),
                    ),
                    PropertyFactory.iconSize(
                        Expression.match(Expression.get("role"), Expression.literal(1f), Expression.stop("road-other", 0.8f)),
                    ),
                    PropertyFactory.symbolSortKey(
                        Expression.match(
                            Expression.get("role"),
                            Expression.literal(0),
                            Expression.stop("start", 4),
                            Expression.stop("end", 3),
                            Expression.stop("road", 2),
                            Expression.stop("parking", 1),
                        ),
                    ),
                ),
        )
        installRoute(style, fonts)
    }

    /** The planned route (Phase 0.7) and its start, stops and end, above everything else. */
    private fun installRoute(style: Style, fonts: Fonts) {
        style.addLayer(
            LineLayer(ROUTE_CASING, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(ROUTE_LINE, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor(LineStyle.SELECTED_COLOR),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            SymbolLayer(PLAN_POINTS, PLAN_SOURCE).withProperties(
                PropertyFactory.iconImage(
                    Expression.match(
                        Expression.get("role"),
                        Expression.literal(PoiIcons.STOP),
                        Expression.stop("start", PoiIcons.START),
                        Expression.stop("end", PoiIcons.END),
                    ),
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf(fonts.bold)),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor("#17201B"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            ),
        )
    }

    /** Hillshade and contours of the offline style (Layers sheet); the online style has neither. */
    fun applyTerrain(style: Style, hillshade: Boolean, strength: Float, contours: Boolean) {
        fun visible(on: Boolean) = PropertyFactory.visibility(if (on) Property.VISIBLE else Property.NONE)
        style.getLayer(TopoStyle.HILLSHADE_LAYER)?.setProperties(
            visible(hillshade && strength > 0f),
            PropertyFactory.hillshadeExaggeration(strength.coerceIn(0f, 1f)),
        )
        TopoStyle.CONTOUR_LAYERS.forEach { style.getLayer(it)?.setProperties(visible(contours)) }
    }

    /**
     * The online base map labels peaks with OpenStreetMap's short names ("Grande"); ours carry the
     * full name and height, so the base map's own peak labels are hidden to avoid doubles.
     */
    private fun hideBasePeakLabels(style: Style) {
        style.layers.forEach { layer ->
            val symbol = layer as? SymbolLayer ?: return@forEach
            if (symbol.sourceLayer == "mountain_peak") {
                symbol.setProperties(PropertyFactory.visibility(Property.NONE))
            }
        }
    }

    fun setGeoJsonAll(style: Style, vararg pairs: Pair<String, String>) = pairs.forEach { (id, json) -> setGeoJson(style, id, json) }

    fun setGeoJson(style: Style, sourceId: String, json: String) {
        if (!style.isFullyLoaded) return
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(json)
    }
}
