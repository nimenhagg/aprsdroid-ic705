package org.aprsdroid.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.SymbolView

@Composable
fun SymbolBadge(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    val bitmap: Bitmap = remember { SymbolView.getSingleton(context) }
    val symbolSize = remember(bitmap) { bitmap.width / 16 }

    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val strokeColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cornerRadius = CornerRadius(w * 0.28f, h * 0.28f)

        // Draw squircle badge background & border
        drawRoundRect(
            color = bgColor,
            size = Size(w, h),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = strokeColor,
            size = Size(w, h),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.dp.toPx())
        )

        // Calculate source rect in sprite sheet
        val page = if (symbol.isNotEmpty() && symbol[0] == '/') 0 else 1
        val index = if (symbol.length > 1) symbol[1].code - 33 else 0
        val altOffset = page * symbolSize * 6
        val srcY = (index / 16) * symbolSize + altOffset
        val srcX = (index % 16) * symbolSize

        val pad = (w * 0.12f).toInt()
        val destSize = (w - pad * 2).toInt()

        val imageBitmap = bitmap.asImageBitmap()

        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(symbolSize, symbolSize),
            dstOffset = IntOffset(pad, pad),
            dstSize = IntSize(destSize, destSize)
        )

        // Draw overlay character if present
        if (symbol.isNotEmpty() && symbol[0] != '/' && symbol[0] != '\\') {
            val overlayIndex = symbol[0].code - 33
            val overlayY = (overlayIndex / 16) * symbolSize + (2 * symbolSize * 6)
            val overlayX = (overlayIndex % 16) * symbolSize

            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(overlayX, overlayY),
                srcSize = IntSize(symbolSize, symbolSize),
                dstOffset = IntOffset(pad, pad),
                dstSize = IntSize(destSize, destSize)
            )
        }
    }
}
