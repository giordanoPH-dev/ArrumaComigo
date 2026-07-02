package com.thesmallmarket.arrumacomigo.ui.theme

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Cores usadas pelas sombras neumórficas, fornecidas pelo tema. */
data class NeumorphicColors(
    val surface: Color,
    val highlight: Color,
    val shadow: Color,
)

val LocalNeumorphicColors = staticCompositionLocalOf {
    NeumorphicColors(LavenderSurface, NeoLightHighlight, NeoLightShadow)
}

/**
 * Aplica o efeito neumórfico: preenche a superfície e desenha sombra dupla
 * (relevo) ou sombra interna (pressionado). A luz vem do canto superior-esquerdo.
 */
@Composable
fun Modifier.neumorphic(
    cornerRadius: Dp = 20.dp,
    pressed: Boolean = false,
    elevation: Dp = 7.dp,
): Modifier {
    val neo = LocalNeumorphicColors.current
    return this
        .drawBehind {
            val cr = cornerRadius.toPx()
            val e = elevation.toPx()
            if (pressed) drawInset(neo, cr, e * 0.7f, e * 1.4f)
            else drawRaised(neo, cr, e, e * 1.7f)
        }
        .clip(RoundedCornerShape(cornerRadius))
}

private fun DrawScope.drawRaised(neo: NeumorphicColors, cr: Float, offset: Float, blur: Float) {
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = neo.surface.toArgb() }
        // Sombra escura no canto inferior-direito.
        paint.setShadowLayer(blur, offset, offset, neo.shadow.toArgb())
        c.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, paint)
        // Brilho no canto superior-esquerdo.
        paint.setShadowLayer(blur, -offset, -offset, neo.highlight.toArgb())
        c.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, paint)
        paint.clearShadowLayer()
    }
}

private fun DrawScope.drawInset(neo: NeumorphicColors, cr: Float, offset: Float, blur: Float) {
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        val w = size.width
        val h = size.height
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = neo.surface.toArgb() }
        c.drawRoundRect(0f, 0f, w, h, cr, cr, fill)

        val clip = Path().apply { addRoundRect(0f, 0f, w, h, cr, cr, Path.Direction.CW) }
        val save = c.save()
        c.clipPath(clip)
        // "Anel" externo cuja sombra projeta para dentro do recorte.
        val ring = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(-w, -h, w * 2, h * 2, Path.Direction.CW)
            addRoundRect(0f, 0f, w, h, cr, cr, Path.Direction.CW)
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Sombra interna escura (cima-esquerda).
        shadowPaint.color = neo.shadow.toArgb()
        shadowPaint.setShadowLayer(blur, offset, offset, neo.shadow.toArgb())
        c.drawPath(ring, shadowPaint)
        // Brilho interno (baixo-direita).
        shadowPaint.color = neo.highlight.toArgb()
        shadowPaint.setShadowLayer(blur, -offset, -offset, neo.highlight.toArgb())
        c.drawPath(ring, shadowPaint)
        c.restoreToCount(save)
    }
}

/** Açúcar sintático: superfície neumórfica com padding interno. */
@Composable
fun Modifier.neumorphicSurface(
    cornerRadius: Dp = 20.dp,
    pressed: Boolean = false,
    elevation: Dp = 7.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
): Modifier = this
    .neumorphic(cornerRadius, pressed, elevation)
    .padding(contentPadding)
