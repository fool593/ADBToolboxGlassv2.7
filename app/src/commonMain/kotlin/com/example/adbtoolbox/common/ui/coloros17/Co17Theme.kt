package com.example.adbtoolbox.common.ui.coloros17

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * v3.2 ColorOS 17 开启动画独立 UI —— 第 5 套独立 UI 控件体系。
 *
 * 与液态玻璃 UI、科技风 TechRoot、首次设置向导、开屏动画完全独立的视觉语言：
 * 深空蓝黑底 + 极光青/蓝/紫动态光效 + 扫描线 + 大圆角发光控件，
 * 模拟 ColorOS 17 首次开机引导（OOBE）第 4 步「开始使用」的科技风界面。
 *
 * 本文件只定义颜色/排版 token，不依赖任何其它 UI 体系。
 */
object Co17 {

    // ---------------- 颜色 token ----------------
    /** 背景基色（深空蓝黑） */
    val Bg = Color(0xFF04070F)
    /** 面板底色（半透明深蓝） */
    val Surface = Color(0xFF0C1526)
    /** 主强调色（极光青） */
    val Accent = Color(0xFF00E5FF)
    /** 次强调色（极光紫） */
    val AccentAlt = Color(0xFF7B6CFF)
    /** 成功/完成色（荧光绿） */
    val Ok = Color(0xFF00FF9C)
    /** 主文字色 */
    val On = Color(0xFFEAF6FF)
    /** 次级文字色 */
    val Sub = Color(0xFF8FA9C0)
    /** 描边/分隔线 */
    val Line = Color(0xFF1E3A55)

    // ---------------- 排版 token ----------------
    val Mono = FontFamily.Monospace
    val Sans = FontFamily.Default

    /** 大标题（欢迎语） */
    val TitleSize = 30
    /** 副标题/说明 */
    val BodySize = 15
    /** 小注 */
    val CaptionSize = 11

    val TitleWeight = FontWeight.Bold
    val BodyWeight = FontWeight.Normal

    /** 圆角（主按钮/面板） */
    const val Corner = 24
    /** 主按钮圆角（ColorOS 引导的大圆按钮风格） */
    const val ButtonCorner = 28
}
