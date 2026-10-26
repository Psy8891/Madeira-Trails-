package pt.vereda.app.ui.map

import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import pt.vereda.core.catalog.LineStyle

/** Sources and layers Vereda adds on top of the base map. */
object MapLayers {
    const val TRAILS_SOURCE = "vereda-trails"
    const val SELECTED_SOURCE = "vereda-selected"
    const val PLACE_SOURCE = "vereda-place"

    private const val TRAILS_SOLID = "vereda-trails-solid"
    private const val TRAILS_DASHED = "vereda-trails-dashed"
    private const val SELECTED_CASING = "vereda-selected-casing"
    private const val SELECTED_LINE = "vereda-selected-line"
    private const val PLACE_DOT = "vereda-place-dot"

    /** Layers a tap can pick a trail from. */
    val TAPPABLE = arrayOf(TRAILS_SOLID, TRAILS_DASHED)

    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun install(style: Style) {
        if (style.getSource(TRAILS_SOURCE) != null) return
        style.addSource(GeoJsonSource(TRAILS_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(SELECTED_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(PLACE_SOURCE, EMPTY))

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
        style.addLayer(
            CircleLayer(PLACE_DOT, PLACE_SOURCE).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(LineStyle.SELECTED_COLOR),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }

    fun setGeoJson(style: Style, sourceId: String, json: String) {
        if (!style.isFullyLoaded) return
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(json)
    }
}
