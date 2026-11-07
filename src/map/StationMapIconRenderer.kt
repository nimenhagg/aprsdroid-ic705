package org.aprsdroid.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import org.aprsdroid.app.MapModes
import java.util.Base64
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared utility for generating APRS station map callsign badge bitmaps and safe image IDs
 * across MapLibre renderers (both EmbeddedMapScreen and MapAct).
 */
object StationMapIconRenderer {

    fun encodeImageId(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    fun createLabeledStationBitmap(
        context: Context,
        call: String,
        symbol: String,
        iconSize: Int
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val textWidth = ceil(textPaint.measureText(call)).toInt()
        val textHeight = ceil(textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent).toInt()
        val textBaseline = -textPaint.fontMetrics.ascent
        val padding = (3f * density).roundToInt()
        val cornerRadius = 3f * density
        val spacing = (2f * density).roundToInt()

        val symbolBitmap = MapModes.symbol2bitmap(symbol, iconSize)
        val bitmapWidth = max(iconSize, textWidth + padding * 2)
        val bitmapHeight = iconSize + spacing + textHeight + padding * 2
        val bitmap = createBitmap(bitmapWidth, bitmapHeight).apply {
            this.density = context.resources.displayMetrics.densityDpi
        }
        val canvas = Canvas(bitmap)
        val symbolLeft = ((bitmapWidth - iconSize) / 2f).coerceAtLeast(0f)
        canvas.drawBitmap(symbolBitmap, symbolLeft, 0f, null)

        val backgroundRect = RectF(
            ((bitmapWidth - (textWidth + padding * 2)) / 2f).coerceAtLeast(0f),
            iconSize.toFloat() + spacing,
            ((bitmapWidth + (textWidth + padding * 2)) / 2f).coerceAtMost(bitmapWidth.toFloat()),
            bitmapHeight.toFloat()
        )
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = density
        }
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, borderPaint)
        canvas.drawText(call, backgroundRect.left + padding, backgroundRect.top + padding + textBaseline, textPaint)
        return bitmap
    }
}
