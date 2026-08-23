package org.aprsdroid.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ImageView

class SymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

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

    override fun onDraw(canvas: Canvas) {
        val srcRect = symbol2rect(symbol)
        val destRect = Rect(0, 0, width, height)
        val drawPaint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        canvas.drawBitmap(iconbitmap, srcRect, destRect, drawPaint)

        if (symbolIsOverlayed(symbol)) {
            val overlayRect = symbol2rect(symbol[0].code - 33, 2)
            canvas.drawBitmap(iconbitmap, overlayRect, destRect, drawPaint)
        }
    }
}
