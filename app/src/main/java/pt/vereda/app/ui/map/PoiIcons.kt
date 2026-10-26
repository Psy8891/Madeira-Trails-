package pt.vereda.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import pt.vereda.core.catalog.PlaceKind

/**
 * Map icons drawn in code (no image assets): a coloured disc with a white glyph for each kind of
 * place, and the start / end / road markers for a picked trail. Colours follow
 * docs/design/README.md: water in levada blue, outdoors in laurel, services in slate.
 */
internal object PoiIcons {

    const val START = "marker-start"
    const val END = "marker-end"
    const val ROAD = "marker-road"
    const val PARKING_MARKER = "marker-parking"
    const val PLACE = "marker-place"
    const val STOP = "marker-stop"

    fun imageName(kind: PlaceKind) = "poi-${kind.key}"

    fun all(context: Context): Map<String, Bitmap> {
        val density = context.resources.displayMetrics.density
        val icons = PlaceKind.entries.associate { imageName(it) to poi(it, density) }.toMutableMap()
        icons[START] = start(density)
        icons[END] = end(density)
        icons[ROAD] = road(density)
        icons[PARKING_MARKER] = disc(density, 26f, "#1673B5") { size -> text(size, "P", 0.62f) }
        icons[PLACE] = disc(density, 26f, "#D81B60") { size -> ring(size) }
        // A dark ring with a white centre; the stop number is drawn on it as map text.
        icons[STOP] = disc(density, 24f, "#17201B") { s -> drawCircle(s * 0.5f, s * 0.5f, s * 0.34f, glyph) }
        return icons
    }

    private fun colour(kind: PlaceKind): String = when (kind) {
        PlaceKind.PEAK -> "#6B4F3A"
        PlaceKind.VIEWPOINT, PlaceKind.TRAILHEAD -> "#1F5F3F"
        PlaceKind.WATERFALL, PlaceKind.SPRING, PlaceKind.WATER -> "#1A8FD6"
        PlaceKind.SHELTER, PlaceKind.HUT, PlaceKind.PICNIC, PlaceKind.CAMP -> "#8A5300"
        PlaceKind.CAVE, PlaceKind.TUNNEL -> "#4E5A53"
        PlaceKind.PARKING -> "#1673B5"
        PlaceKind.TOILETS, PlaceKind.BUS_STOP -> "#5B6B9A"
    }

    private fun poi(kind: PlaceKind, density: Float): Bitmap {
        val dp = if (kind == PlaceKind.PEAK) 18f else 20f
        return disc(density, dp, colour(kind)) { size ->
            when (kind) {
                PlaceKind.PEAK -> triangle(size)
                PlaceKind.VIEWPOINT -> eye(size)
                PlaceKind.WATERFALL -> waves(size)
                PlaceKind.SPRING, PlaceKind.WATER -> drop(size)
                PlaceKind.SHELTER, PlaceKind.HUT -> house(size)
                PlaceKind.PICNIC -> table(size)
                PlaceKind.CAMP -> tent(size)
                PlaceKind.CAVE, PlaceKind.TUNNEL -> arch(size)
                PlaceKind.TRAILHEAD -> flag(size)
                PlaceKind.TOILETS -> text(size, "WC", 0.42f)
                PlaceKind.PARKING -> text(size, "P", 0.62f)
                PlaceKind.BUS_STOP -> text(size, "BUS", 0.32f)
            }
        }
    }

    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }

    /** A disc of [dp] with a white border, and [draw] on top in white. */
    private fun disc(density: Float, dp: Float, fill: String, draw: Canvas.(Float) -> Unit): Bitmap {
        val size = (dp * density).toInt().coerceAtLeast(8)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val half = size / 2f
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        canvas.drawCircle(half, half, half, border)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.toColorInt() }
        canvas.drawCircle(half, half, half - size * 0.09f, body)
        glyph.style = Paint.Style.FILL
        glyph.strokeWidth = size * 0.08f
        canvas.draw(size.toFloat())
        return bitmap
    }

    private fun Canvas.triangle(s: Float) = drawPath(
        Path().apply {
            moveTo(s * 0.5f, s * 0.26f)
            lineTo(s * 0.76f, s * 0.70f)
            lineTo(s * 0.24f, s * 0.70f)
            close()
        },
        glyph,
    )

    private fun Canvas.eye(s: Float) {
        glyph.style = Paint.Style.STROKE
        drawOval(RectF(s * 0.24f, s * 0.36f, s * 0.76f, s * 0.64f), glyph)
        glyph.style = Paint.Style.FILL
        drawCircle(s * 0.5f, s * 0.5f, s * 0.08f, glyph)
    }

    private fun Canvas.waves(s: Float) {
        glyph.style = Paint.Style.STROKE
        for (x in listOf(0.38f, 0.5f, 0.62f)) {
            drawLine(s * x, s * 0.27f, s * x, s * 0.73f, glyph)
        }
    }

    private fun Canvas.drop(s: Float) = drawPath(
        Path().apply {
            moveTo(s * 0.5f, s * 0.24f)
            cubicTo(s * 0.62f, s * 0.42f, s * 0.70f, s * 0.50f, s * 0.70f, s * 0.58f)
            cubicTo(s * 0.70f, s * 0.70f, s * 0.61f, s * 0.77f, s * 0.5f, s * 0.77f)
            cubicTo(s * 0.39f, s * 0.77f, s * 0.30f, s * 0.70f, s * 0.30f, s * 0.58f)
            cubicTo(s * 0.30f, s * 0.50f, s * 0.38f, s * 0.42f, s * 0.5f, s * 0.24f)
            close()
        },
        glyph,
    )

    private fun Canvas.house(s: Float) = drawPath(
        Path().apply {
            moveTo(s * 0.5f, s * 0.25f)
            lineTo(s * 0.76f, s * 0.48f)
            lineTo(s * 0.68f, s * 0.48f)
            lineTo(s * 0.68f, s * 0.74f)
            lineTo(s * 0.32f, s * 0.74f)
            lineTo(s * 0.32f, s * 0.48f)
            lineTo(s * 0.24f, s * 0.48f)
            close()
        },
        glyph,
    )

    private fun Canvas.table(s: Float) {
        drawRect(s * 0.26f, s * 0.38f, s * 0.74f, s * 0.46f, glyph)
        drawRect(s * 0.33f, s * 0.46f, s * 0.39f, s * 0.72f, glyph)
        drawRect(s * 0.61f, s * 0.46f, s * 0.67f, s * 0.72f, glyph)
    }

    private fun Canvas.tent(s: Float) = drawPath(
        Path().apply {
            moveTo(s * 0.5f, s * 0.26f)
            lineTo(s * 0.77f, s * 0.72f)
            lineTo(s * 0.56f, s * 0.72f)
            lineTo(s * 0.5f, s * 0.56f)
            lineTo(s * 0.44f, s * 0.72f)
            lineTo(s * 0.23f, s * 0.72f)
            close()
        },
        glyph,
    )

    private fun Canvas.arch(s: Float) {
        drawPath(
            Path().apply {
                moveTo(s * 0.26f, s * 0.72f)
                lineTo(s * 0.26f, s * 0.50f)
                arcTo(RectF(s * 0.26f, s * 0.26f, s * 0.74f, s * 0.74f), 180f, 180f)
                lineTo(s * 0.74f, s * 0.72f)
                close()
            },
            glyph,
        )
    }

    private fun Canvas.flag(s: Float) {
        drawRect(s * 0.32f, s * 0.24f, s * 0.38f, s * 0.76f, glyph)
        drawPath(
            Path().apply {
                moveTo(s * 0.38f, s * 0.26f)
                lineTo(s * 0.72f, s * 0.37f)
                lineTo(s * 0.38f, s * 0.50f)
                close()
            },
            glyph,
        )
    }

    private fun Canvas.text(s: Float, text: String, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = s * scale
        }
        val y = s / 2f - (paint.descent() + paint.ascent()) / 2f
        drawText(text, s / 2f, y, paint)
    }

    private fun Canvas.ring(s: Float) {
        drawCircle(s * 0.5f, s * 0.5f, s * 0.16f, glyph)
    }

    /** Start: a green disc with a white "play" arrow (walk this way). */
    private fun start(density: Float) = disc(density, 28f, "#2E9E5B") { s ->
        drawPath(
            Path().apply {
                moveTo(s * 0.40f, s * 0.30f)
                lineTo(s * 0.72f, s * 0.50f)
                lineTo(s * 0.40f, s * 0.70f)
                close()
            },
            glyph,
        )
    }

    /** End: a chequered flag on a dark disc. */
    private fun end(density: Float) = disc(density, 28f, "#17201B") { s ->
        drawRect(s * 0.28f, s * 0.24f, s * 0.33f, s * 0.78f, glyph)
        val left = s * 0.33f
        val top = s * 0.26f
        val cell = s * 0.10f
        for (row in 0 until 3) {
            for (col in 0 until 4) {
                if ((row + col) % 2 == 0) {
                    drawRect(left + col * cell, top + row * cell, left + (col + 1) * cell, top + (row + 1) * cell, glyph)
                }
            }
        }
    }

    /** Where the walk from the road starts: a slate disc with a road glyph. */
    private fun road(density: Float) = disc(density, 24f, "#4E5A53") { s ->
        drawPath(
            Path().apply {
                moveTo(s * 0.40f, s * 0.26f)
                lineTo(s * 0.60f, s * 0.26f)
                lineTo(s * 0.72f, s * 0.74f)
                lineTo(s * 0.28f, s * 0.74f)
                close()
            },
            glyph,
        )
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#4E5A53".toColorInt() }
        drawRect(s * 0.48f, s * 0.32f, s * 0.52f, s * 0.42f, dash)
        drawRect(s * 0.48f, s * 0.50f, s * 0.52f, s * 0.62f, dash)
    }
}
