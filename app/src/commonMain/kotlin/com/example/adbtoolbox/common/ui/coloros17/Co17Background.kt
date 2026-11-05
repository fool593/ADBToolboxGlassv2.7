package com.example.adbtoolbox.common.ui.coloros17

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.adbtoolbox.common.AppCache
import kotlin.math.cos
import kotlin.math.sin

/**
 * ColorOS 17 开启动画动态背景 —— 丝滑关键：
 *
 * 1. 单个 Canvas + withFrameNanos 帧驱动；每帧只写一个 Long 状态（重组仅限本叶子 Canvas），
 *    粒子/光晕全部在 DrawScope 内用解析式运动（位置 = 初始 + 速度×时间 + 正弦振荡），无逐帧积分状态；
 * 2. 粒子数据存普通数组（remember 缓存，非 Compose 状态），每帧只读不写；
 * 3. 光晕用 3 层半透明同心圆叠加（零 Brush 分配，避免每帧 GC）；
 * 4. 粒子数随减负模式自适应（崩溃后降档），低端机也不掉帧。
 */
@Composable
fun Co17DynamicBackground(modifier: Modifier = Modifier) {
    val particleCount = if (AppCache.reducedMode.value) 10 else 16
    val particles = remember {
        // 确定性散列伪随机（[0,1)），避免每次重组粒子乱跳；Long 中间量防 Int 溢出
        fun rnd(i: Int, salt: Int): Float {
            var h = i.toLong() * 374761393L + salt.toLong() * 668265263L
            h = (h xor (h shr 13)) * 1274126177L
            h = h xor (h shr 16)
            return (h and 0x7FFFFFFFL).toFloat() / 0x7FFFFFFFL.toFloat()
        }
        Array(particleCount) { i ->
            Particle(
                x0 = rnd(i, 1) * 1200f - 100f,
                y0 = rnd(i, 2) * 2400f - 200f,
                vx = (rnd(i, 3) * 0.4f + 0.05f) * (if (i % 2 == 0) 1f else -1f) * 14f,
                vy = (rnd(i, 4) * 0.3f + 0.02f) * 10f,
                amp = rnd(i, 5) * 46f + 8f,
                freq = rnd(i, 6) * 0.55f + 0.12f,
                phase = rnd(i, 7) * 6.28f,
                r = rnd(i, 8) * 2.6f + 0.9f,
                colorIndex = i % 3,
                alpha = rnd(i, 9) * 0.30f + 0.10f
            )
        }
    }
    val colors = listOf(Co17.Accent, Color(0xFF4D9FFF), Co17.AccentAlt)

    // 渲染时钟（毫秒）。delay 驱动 ≈ 30fps：不跟 vsync 空转，软件渲染/低端机上零额外调度开销；
    // 写普通 Long 状态：只重组本 Canvas 叶子，粒子数组等大对象不受影响。
    var t by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            t = System.currentTimeMillis()
            kotlinx.coroutines.delay(33)
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val tSec = t / 1000f

        // ---- 光晕 1（左上，青）：2 层同心圆叠加，中心缓慢漂移（半径收敛，软件渲染光栅化面积更小）
        val gx1 = w * 0.18f + sin(tSec * 0.11f) * w * 0.05f
        val gy1 = h * 0.22f + cos(tSec * 0.09f) * h * 0.04f
        drawCircle(Co17.Accent.copy(alpha = 0.06f), radius = w * 0.36f, center = Offset(gx1, gy1))
        drawCircle(Co17.Accent.copy(alpha = 0.045f), radius = w * 0.20f, center = Offset(gx1, gy1))

        // ---- 光晕 2（右下，紫）
        val gx2 = w * 0.82f + sin(tSec * 0.08f + 2.1f) * w * 0.05f
        val gy2 = h * 0.78f + cos(tSec * 0.10f + 1.2f) * h * 0.05f
        drawCircle(Co17.AccentAlt.copy(alpha = 0.055f), radius = w * 0.34f, center = Offset(gx2, gy2))
        drawCircle(Co17.AccentAlt.copy(alpha = 0.040f), radius = w * 0.18f, center = Offset(gx2, gy2))

        // ---- 粒子（解析式运动 + 回绕）
        val margin = 24f
        for (p in particles) {
            var x = p.x0 + p.vx * tSec + sin(tSec * p.freq + p.phase) * p.amp
            var y = p.y0 + p.vy * tSec + cos(tSec * p.freq * 0.7f + p.phase) * p.amp * 0.6f
            val spanX = w + margin * 2
            val spanY = h + margin * 2
            x = ((x % spanX) + spanX) % spanX - margin
            y = ((y % spanY) + spanY) % spanY - margin
            drawCircle(
                color = colors[p.colorIndex].copy(alpha = p.alpha),
                radius = p.r,
                center = Offset(x, y)
            )
        }

        // ---- 扫描线（静态，稀疏化：16dp 间隔，低透明度）
        val step = 16.dp.toPx()
        var yy = step * 0.5f
        while (yy < h) {
            drawLine(
                color = Co17.Accent.copy(alpha = 0.03f),
                start = Offset(0f, yy),
                end = Offset(w, yy),
                strokeWidth = 1f
            )
            yy += step
        }
    }
}

/** 粒子数据：普通类（非 Compose 状态），remember 缓存，每帧只读。 */
private class Particle(
    val x0: Float, val y0: Float,
    val vx: Float, val vy: Float,
    val amp: Float, val freq: Float, val phase: Float,
    val r: Float, val colorIndex: Int, val alpha: Float
)
