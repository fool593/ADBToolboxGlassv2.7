package com.example.adbtoolbox.common.ui.coloros17

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * ColorOS 17 开启动画独立控件体系（第 5 套独立 UI 的控件层）。
 * 全部为全新实现：渐变大圆角按钮 / 发光功能面板 / 步骤指示点 / 中央光球。
 * 按压反馈只走 graphicsLayer 缩放（不触发布局），保证 60fps 顺滑。
 */

// ---------------- 主按钮（ColorOS 引导的大圆渐变按钮） ----------------
@Composable
fun Co17Button(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = Co17.Accent,
    sub: String? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.955f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "co17btn"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(Co17.ButtonCorner.dp))
            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.72f))))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 26.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text,
                style = TextStyle(Color.White, 16.sp, FontWeight.Bold, letterSpacing = 1.sp)
            )
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(sub, style = TextStyle(Color.White.copy(alpha = 0.82f), 11.sp))
            }
        }
    }
}

// ---------------- 次级按钮（描边） ----------------
@Composable
fun Co17GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "co17ghost"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(Co17.ButtonCorner.dp))
            .background(Co17.Surface.copy(alpha = 0.55f))
            .border(1.dp, Co17.Accent.copy(alpha = 0.55f), RoundedCornerShape(Co17.ButtonCorner.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(Co17.On, 13.sp, letterSpacing = 0.5.sp))
    }
}

// ---------------- 功能面板（发光入口行） ----------------
@Composable
fun Co17Panel(
    title: String,
    desc: String,
    icon: String,
    modifier: Modifier = Modifier,
    accent: Color = Co17.Accent,
    trailing: String? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "co17panel"
    )
    val density = LocalDensity.current
    val iconCx = with(density) { 19.dp.toPx() }
    val iconCy = with(density) { 14.dp.toPx() }
    val iconR = with(density) { 26.dp.toPx() }
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(Co17.Corner.dp))
            .background(Co17.Surface.copy(alpha = 0.62f))
            .border(0.5.dp, Co17.Accent.copy(alpha = 0.28f), RoundedCornerShape(Co17.Corner.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标容器：圆形渐变底 + 首字符
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.12f)),
                        center = Offset(iconCx, iconCy),
                        radius = iconR
                    )
                )
                .border(0.5.dp, accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            BasicText(icon, style = TextStyle(accent, 16.sp, FontWeight.Bold))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(Co17.On, 14.sp, FontWeight.Medium))
            Spacer(Modifier.height(2.dp))
            BasicText(desc, style = TextStyle(Co17.Sub, 11.sp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            BasicText(trailing, style = TextStyle(Co17.Accent, 11.sp, FontWeight.Medium))
        }
        Spacer(Modifier.width(4.dp))
        BasicText("›", style = TextStyle(Co17.Sub, 18.sp))
    }
}

// ---------------- 步骤指示点（4 步，当前步发光放大） ----------------
@Composable
fun Co17StepDots(total: Int, active: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            val isActive = i == active
            val isPassed = i < active
            Box(
                Modifier
                    .size(if (isActive) 11.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> Co17.Accent
                            isPassed -> Co17.Accent.copy(alpha = 0.45f)
                            else -> Co17.Line
                        }
                    )
            )
        }
    }
}

// ---------------- 中央光球（呼吸 + 旋转弧光；delay 驱动 ≈30fps，软件渲染也顺滑） ----------------
@Composable
fun Co17Orb(modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            t = System.currentTimeMillis()
            kotlinx.coroutines.delay(33)
        }
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension / 2f
        val tSec = t / 1000f
        val pulse = 1f + 0.045f * sin(tSec * 1.35f)

        // 外圈光晕（3 层同心圆，零 Brush 分配）
        drawCircle(Co17.Accent.copy(alpha = 0.05f), base * 1.75f * pulse, c)
        drawCircle(Co17.Accent.copy(alpha = 0.05f), base * 1.45f * pulse, c)
        drawCircle(Co17.Accent.copy(alpha = 0.045f), base * 1.2f * pulse, c)

        // 主环（青）
        drawCircle(
            color = Co17.Accent.copy(alpha = 0.85f),
            radius = base * pulse,
            center = c,
            style = Stroke(width = base * 0.10f)
        )
        // 旋转弧光（紫）
        drawArc(
            color = Co17.AccentAlt.copy(alpha = 0.95f),
            startAngle = (tSec * 42f) % 360f,
            sweepAngle = 78f,
            useCenter = false,
            topLeft = Offset(c.x - base * pulse, c.y - base * pulse),
            size = Size(base * pulse * 2f, base * pulse * 2f),
            style = Stroke(width = base * 0.10f, cap = StrokeCap.Round)
        )
        // 内芯
        drawCircle(Co17.Accent.copy(alpha = 0.10f), base * 0.52f * pulse, c)
        drawCircle(Color.White.copy(alpha = 0.07f), base * 0.30f, c)
    }
}
