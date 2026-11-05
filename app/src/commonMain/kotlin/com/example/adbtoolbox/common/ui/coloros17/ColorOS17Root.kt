package com.example.adbtoolbox.common.ui.coloros17

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppSettings
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.GlassEffectPersistence
import com.example.adbtoolbox.common.theme.AppTheme

/**
 * v3.2 ColorOS 17 开启动画 —— 第 5 套独立 UI（主界面）。
 *
 * 模拟 ColorOS 17 首次开机引导（OOBE）第 4 步「开始使用」的科技风页面：
 * 深空蓝黑 + 极光粒子/光晕 + 中央呼吸光球 + 扫描线；顶部步骤指示（第 4 步高亮），
 * 中央欢迎语，功能入口面板全部真实生效（语言/主题即时切换并落盘，
 * 下载器/设备信息跳转真实页面），底部大圆角「开始使用」主按钮。
 *
 * 全部控件来自本包（Co17Theme / Co17Controls / Co17Background），
 * 不依赖玻璃 UI 与科技风 UI 的任何控件 —— 真正全新的独立 UI 控件体系。
 */
@Composable
fun ColorOS17Root(
    onExit: () -> Unit,
    onLaunch: () -> Unit,
    onOpenDownloader: () -> Unit,
    onOpenDeviceInfo: () -> Unit
) {
    val save: () -> Unit = { try { GlassEffectPersistence.saveAll() } catch (_: Exception) {} }
    var showSecureNote by remember { mutableStateOf(false) }

    fun cycleLanguage() {
        AppSettings.language = when (AppSettings.language) {
            "zh" -> "en"
            "en" -> "hi"
            else -> "zh"
        }
        save()
    }

    fun cycleTheme() {
        val next = when (AppTheme.id) {
            AppTheme.CLASSIC -> AppTheme.NATIONAL_DAY
            AppTheme.NATIONAL_DAY -> AppTheme.HUAWEI
            else -> AppTheme.CLASSIC
        }
        AppTheme.apply(next, persist = true)
    }

    val langLabel = when (AppSettings.language) {
        "zh" -> "中文"
        "en" -> "EN"
        else -> "हिन्दी"
    }
    val themeLabel = when (AppTheme.id) {
        AppTheme.CLASSIC -> AppStrings.get("theme_classic")
        AppTheme.NATIONAL_DAY -> AppStrings.get("theme_national_day")
        else -> AppStrings.get("theme_huawei")
    }

    Box(Modifier.fillMaxSize().background(Co17.Bg)) {
        Co17DynamicBackground(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------------- 顶栏：步骤指示 + 语言快捷 ----------------
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Co17StepDots(total = 4, active = 3)
                Spacer(Modifier.weight(1f))
                BasicText(
                    AppStrings.get("co17_step4"),
                    style = TextStyle(Co17.Sub, 12.sp, letterSpacing = 1.sp)
                )
                Spacer(Modifier.width(14.dp))
                Co17GhostButton(langLabel, Modifier, onClick = ::cycleLanguage)
            }

            Spacer(Modifier.height(34.dp))

            // ---------------- 中央：光球 + 欢迎语 ----------------
            Co17Orb(Modifier.size(128.dp))
            Spacer(Modifier.height(26.dp))
            BasicText(
                AppStrings.get("co17_welcome"),
                style = TextStyle(Co17.On, 30.sp, FontWeight.Bold, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                AppStrings.get("co17_ready"),
                style = TextStyle(Co17.Sub, 15.sp, textAlign = TextAlign.Center)
            )

            Spacer(Modifier.height(30.dp))

            // ---------------- 功能入口面板（真实功能） ----------------
            Co17Panel(
                title = AppStrings.get("co17_lang"),
                desc = AppStrings.get("tech_lang"),
                icon = "言",
                trailing = langLabel,
                onClick = ::cycleLanguage
            )
            Spacer(Modifier.height(10.dp))
            Co17Panel(
                title = AppStrings.get("co17_theme"),
                desc = AppStrings.get("theme_hint"),
                icon = "色",
                accent = Color(0xFF7B6CFF),
                trailing = themeLabel,
                onClick = ::cycleTheme
            )
            Spacer(Modifier.height(10.dp))
            Co17Panel(
                title = AppStrings.get("co17_secure"),
                desc = AppStrings.get("co17_secure_desc"),
                icon = "盾",
                accent = Color(0xFF00FF9C),
                onClick = { showSecureNote = !showSecureNote }
            )
            if (showSecureNote) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Co17.Surface.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    BasicText(
                        AppStrings.get("restore_remind_perf"),
                        style = TextStyle(Co17.Sub, 12.sp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Co17Panel(
                title = AppStrings.get("co17_about"),
                desc = AppStrings.get("co17_about_desc"),
                icon = "机",
                accent = Color(0xFF4D9FFF),
                onClick = onOpenDeviceInfo
            )
            Spacer(Modifier.height(10.dp))
            Co17Panel(
                title = AppStrings.get("co17_dl"),
                desc = AppStrings.get("co17_dl_desc"),
                icon = "↓",
                accent = Color(0xFFFFB74D),
                onClick = onOpenDownloader
            )

            Spacer(Modifier.height(32.dp))

            // ---------------- 底部：主按钮 + 退出 ----------------
            Co17Button(
                text = AppStrings.get("co17_launch"),
                sub = "ADB TOOLBOX // v3.2",
                modifier = Modifier.fillMaxWidth(),
                onClick = onLaunch
            )
            Spacer(Modifier.height(12.dp))
            Co17GhostButton(
                AppStrings.get("co17_preview_exit"),
                Modifier,
                onClick = onExit
            )
            Spacer(Modifier.height(16.dp))
            BasicText(
                AppStrings.get("co17_tip"),
                style = TextStyle(Co17.Sub.copy(alpha = 0.7f), 10.sp, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
