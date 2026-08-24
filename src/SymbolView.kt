package org.aprsdroid.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.color.MaterialColors

class SymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        @Volatile
        private var iconbitmap: Bitmap? = null

        @JvmStatic
        fun getSingleton(context: Context): Bitmap {
            return iconbitmap ?: synchronized(this) {
                iconbitmap ?: BitmapFactory.decodeResource(context.resources, R.drawable.allicons).also {
                    iconbitmap = it
                }
            }
        }
    }

    var symbol: String = "/$"
        private set

    fun setSymbol(newSym: String) {
        symbol = newSym
        invalidate()
    }

    val iconbitmap: Bitmap by lazy { getSingleton(context) }
    val symbolSize: Int by lazy { iconbitmap.width / 16 }

    fun symbol2rect(index: Int, page: Int): Rect {
        val altOffset = page * symbolSize * 6
        val y = (index / 16) * symbolSize + altOffset
        val x = (index % 16) * symbolSize
        return Rect(x, y, x + symbolSize, y + symbolSize)
    }

    fun symbol2rect(sym: String): Rect {
        val page = if (sym.isNotEmpty() && sym[0] == '/') 0 else 1
        val index = if (sym.length > 1) sym[1].code - 33 else 0
        return symbol2rect(index, page)
    }

    fun symbolIsOverlayed(sym: String): Boolean {
        return sym.isNotEmpty() && sym[0] != '/' && sym[0] != '\\'
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1f
    }
    private val drawPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    private val containerRect = RectF()
    private val destRect = Rect()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cornerRadius = w * 0.28f

        containerRect.set(1f, 1f, w - 1f, h - 1f)
        try {
            bgPaint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            strokePaint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
        } catch (_: Throwable) {
            bgPaint.color = 0x22888888.toInt()
            strokePaint.color = 0x33888888.toInt()
        }

        // Draw modern squircle badge background & border
        canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, strokePaint)

        // Draw crisp icon with 12% padding inside badge
        val pad = (w * 0.12f).toInt()
        destRect.set(pad, pad, width - pad, height - pad)

        val srcRect = symbol2rect(symbol)
        canvas.drawBitmap(iconbitmap, srcRect, destRect, drawPaint)

        if (symbolIsOverlayed(symbol)) {
            val overlayRect = symbol2rect(symbol[0].code - 33, 2)
            canvas.drawBitmap(iconbitmap, overlayRect, destRect, drawPaint)
        }
    }
}
