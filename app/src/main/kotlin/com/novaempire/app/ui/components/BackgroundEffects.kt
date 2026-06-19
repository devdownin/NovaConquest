package com.novaempire.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint as APaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

// Both composables pre-render to a Bitmap once per unique canvas size, then blit with a single
// drawImage call. The original approach — thousands of drawCircle/drawRect calls inside
// DrawScope every frame — blocked the main thread for ~10-15 ms at startup on a typical phone
// (162 k iterations for NoiseOverlay at step=4, 18 k for HalftoneBackground at spacing=12).

@Composable
fun HalftoneBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.05f),
    dotSize: Float = 2f,
    spacing: Float = 12f
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val bitmap = remember(canvasSize, color, dotSize, spacing) {
        val (w, h) = canvasSize
        if (w <= 0 || h <= 0) return@remember null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = ACanvas(bmp)
        val paint = APaint(APaint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.argb(
                (color.alpha * 255 + 0.5f).toInt(),
                (color.red   * 255 + 0.5f).toInt(),
                (color.green * 255 + 0.5f).toInt(),
                (color.blue  * 255 + 0.5f).toInt()
            )
            style = APaint.Style.FILL
        }
        var y = 0f
        while (y < h) {
            var x = 0f
            while (x < w) {
                canvas.drawCircle(x, y, dotSize, paint)
                x += spacing
            }
            y += spacing
        }
        bmp.asImageBitmap()
    }

    Canvas(modifier = modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        bitmap?.let { drawImage(it) }
    }
}

@Composable
fun NoiseOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.10f   // grain film fort — signature Bilal
) {
    val step = 16
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val bitmap = remember(canvasSize, alpha) {
        val (w, h) = canvasSize
        if (w <= 0 || h <= 0) return@remember null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = ACanvas(bmp)
        val paint = APaint().apply {
            this.color = android.graphics.Color.argb(
                (alpha * 255 + 0.5f).toInt(), 255, 255, 255
            )
            isAntiAlias = false
        }
        val r = Random(42L)
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                if (r.nextFloat() > 0.5f) {
                    canvas.drawRect(
                        x.toFloat(), y.toFloat(),
                        (x + step).toFloat(), (y + step).toFloat(),
                        paint
                    )
                }
            }
        }
        bmp.asImageBitmap()
    }

    Canvas(modifier = modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        bitmap?.let { drawImage(it) }
    }
}

/** Lavis atmosphérique Bilal : grain fort + vignette violet-brun aux bords. */
@Composable
fun InkWashOverlay(modifier: Modifier = Modifier) {
    NoiseOverlay(modifier = modifier, alpha = 0.10f)
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                0.0f to Color(0xFF0D0820).copy(alpha = 0.45f),  // violet profond centre
                0.5f to Color.Transparent,
                1.0f to Color(0xFF1A0F05).copy(alpha = 0.55f)   // brun chaud bords
            )
        )
    )
}
