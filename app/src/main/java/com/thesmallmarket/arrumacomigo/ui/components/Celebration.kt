package com.thesmallmarket.arrumacomigo.ui.components

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Vibração de comemoração ao concluir uma tarefa: "tum-tum" firme, em amplitude máxima. */
fun Context.vibrateCelebration() {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }
    if (vibrator == null || !vibrator.hasVibrator()) return
    val timings = longArrayOf(0, 120, 80, 200)
    val effect = if (vibrator.hasAmplitudeControl()) {
        VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255), -1)
    } else {
        VibrationEffect.createWaveform(timings, -1)
    }
    vibrator.vibrate(effect)
}

/** "Plim" de sucesso: toca o som de notificação padrão do aparelho. */
fun Context.playSuccessChime() {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(this, uri)?.play()
    }
}

/**
 * Barramento simples para comemorar de qualquer tela: o overlay global (na raiz do app)
 * escuta e dispara confete em tela cheia + vibração + plim.
 */
object CelebrationBus {
    private val _bursts = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val bursts: SharedFlow<Unit> = _bursts

    fun celebrate() {
        _bursts.tryEmit(Unit)
    }
}

/** Overlay global de comemoração — colocar por cima de TUDO na raiz do app. */
@Composable
fun CelebrationOverlay(modifier: Modifier = Modifier) {
    var trigger by remember { mutableStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        CelebrationBus.bursts.collect {
            trigger++
            context.vibrateCelebration()
            context.playSuccessChime()
        }
    }
    ConfettiBurst(trigger = trigger, modifier = modifier)
}

private val confettiColors = listOf(
    Color(0xFF6C4DDB), // roxo
    Color(0xFFE5739D), // rosa
    Color(0xFFFFC857), // amarelo
    Color(0xFF6FCF97), // verde
    Color(0xFF56CCF2), // azul
    Color(0xFFF2994A), // laranja
)

private class ConfettiParticle(random: Random) {
    val angle = random.nextFloat() * 2f * Math.PI.toFloat()
    val speed = 0.35f + random.nextFloat() * 0.65f
    val size = 8f + random.nextFloat() * 14f
    val color = confettiColors[random.nextInt(confettiColors.size)]
    val rotationSpeed = (random.nextFloat() - 0.5f) * 1080f
    val isCircle = random.nextBoolean()
    val drift = (random.nextFloat() - 0.5f) * 0.3f
}

/**
 * Explosão de confetes a partir do centro. Dispara sempre que [trigger] muda para um
 * valor > 0; desenha por ~1,4s e some. Colocar por cima do conteúdo, em fillMaxSize.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger <= 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val particles = remember(trigger) { List(90) { ConfettiParticle(Random(trigger * 1000 + it)) } }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1_400, easing = LinearEasing))
    }
    val t = progress.value
    if (t <= 0f || t >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val maxRadius = size.minDimension * 0.55f
        val gravity = size.height * 0.35f
        val alpha = (1f - t).coerceIn(0f, 1f)
        particles.forEach { p ->
            val distance = p.speed * t * maxRadius
            val x = center.x + cos(p.angle) * distance + p.drift * size.width * t
            val y = center.y + sin(p.angle) * distance + gravity * t * t
            rotate(degrees = p.rotationSpeed * t, pivot = Offset(x, y)) {
                if (p.isCircle) {
                    drawCircle(color = p.color, radius = p.size / 2f, center = Offset(x, y), alpha = alpha)
                } else {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                        size = Size(p.size, p.size / 2f),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}
