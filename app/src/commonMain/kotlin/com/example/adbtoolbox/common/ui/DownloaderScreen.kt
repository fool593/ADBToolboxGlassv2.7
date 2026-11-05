package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.Downloader
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * v3.2 多线程下载器（玻璃 UI 独立页面）。
 *
 * - 直链分块多线程高速下载（Range 分段，最多 8 线程）；
 * - 断点续传：中断后再次点「开始下载」自动从断点继续（.part/.dlm 存应用私有缓存，不污染公共目录）；
 * - 完成后自动保存到手机内部存储 Download/ADBToolbox 文件夹（API 29+ MediaStore 免权限，
 *   API 24-28 写公共 Download 目录）；
 * - 速度实时计算，进度条 + 已下载/总大小 + 剩余时间。
 */
@Composable
fun DownloaderScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var threads by remember { mutableStateOf(4) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("") }
    var speed by remember { mutableLongStateOf(0L) }
    var eta by remember { mutableLongStateOf(0L) }
    var lastDone by remember { mutableLongStateOf(0L) }
    var lastTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    // 速度/剩余时间：每 500ms 采样一次，只在下载中运行
    LaunchedEffect(running) {
        speed = 0L
        eta = 0L
        lastDone = 0L
        lastTime = 0L
        while (running) {
            val now = System.currentTimeMillis()
            if (lastTime > 0L) {
                val dt = (now - lastTime).coerceAtLeast(1L)
                val s = ((done - lastDone) * 1000L / dt).coerceAtLeast(0L)
                speed = s
                eta = if (s > 0L && total > done) (total - done) / s else 0L
            }
            lastDone = done
            lastTime = now
            delay(500)
        }
    }

    fun start() {
        if (running) return
        if (url.isBlank()) {
            status = AppStrings.get("dl_url_required")
            return
        }
        // API 24-28：先请求存储权限（MainActivity 弹系统权限框），授予后用户再次点开始
        if (Downloader.needsLegacyStoragePermission()) {
            com.example.adbtoolbox.common.AppCache.requestStoragePermissionTrigger.value++
            return
        }
        running = true
        status = ""
        done = 0L
        total = 0L
        scope.launch {
            val r = Downloader.downloadToDownloads(url, fileName, threads) { d, t ->
                done = d
                total = t
            }
            status = r.message
            running = false
        }
    }

    fun oneDec(x: Float): String {
        val r = (x * 10).roundToInt()
        return "${r / 10}.${kotlin.math.abs(r % 10)}"
    }

    fun fmt(v: Long): String = when {
        v >= 1024L * 1024 * 1024 -> oneDec(v / 1024f / 1024f / 1024f) + " GB"
        v >= 1024L * 1024 -> oneDec(v / 1024f / 1024f) + " MB"
        v >= 1024 -> (v / 1024).toString() + " KB"
        else -> "$v B"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier.height(36f.dp),
                tint = Color.Unspecified
            ) {
                BasicText("← " + AppStrings.get("home"), Modifier.padding(horizontal = 12f.dp), style = TextStyle(contentColor, 13f.sp))
            }
            Spacer(Modifier.width(12f.dp))
            BasicText(AppStrings.get("dl_title"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        }
        Spacer(Modifier.height(6f.dp))
        BasicText(AppStrings.get("dl_hint"), style = TextStyle(contentColor.copy(alpha = 0.6f), 13f.sp))
        Spacer(Modifier.height(16f.dp))

        // ---------------- 新建任务卡 ----------------
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("dl_new_task"), style = TextStyle(contentColor, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(12f.dp))

                // URL 输入
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(contentColor, 14f.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = if (contentColor == Color.White) 0.06f else 0.08f))
                        .border(1.dp, contentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12f.dp, vertical = 12f.dp)
                )
                Spacer(Modifier.height(10f.dp))

                // 文件名输入
                BasicTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    textStyle = TextStyle(contentColor, 14f.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = if (contentColor == Color.White) 0.06f else 0.08f))
                        .border(1.dp, contentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12f.dp, vertical = 12f.dp)
                )
                Spacer(Modifier.height(8f.dp))
                BasicText(AppStrings.get("dl_name_placeholder"), style = TextStyle(contentColor.copy(alpha = 0.45f), 11f.sp))
                Spacer(Modifier.height(12f.dp))

                // 线程选择
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(AppStrings.get("dl_threads") + "（1-8）", modifier = Modifier.weight(1f), style = TextStyle(contentColor, 13f.sp))
                    LiquidButton(
                        onClick = { threads = (threads - 1).coerceAtLeast(1) },
                        backdrop = backdrop,
                        modifier = Modifier.size(34f.dp),
                        tint = Color.Unspecified
                    ) { BasicText("−", style = TextStyle(contentColor, 16f.sp)) }
                    BasicText(threads.toString(), Modifier.padding(horizontal = 14f.dp), style = TextStyle(AppTheme.accent, 15f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
                    LiquidButton(
                        onClick = { threads = (threads + 1).coerceAtMost(8) },
                        backdrop = backdrop,
                        modifier = Modifier.size(34f.dp),
                        tint = Color.Unspecified
                    ) { BasicText("＋", style = TextStyle(contentColor, 16f.sp)) }
                }
                Spacer(Modifier.height(14f.dp))

                // 开始按钮
                LiquidButton(
                    onClick = ::start,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(46f.dp),
                    tint = if (running) Color(0xFF8E8E93) else AppTheme.accent
                ) {
                    BasicText(
                        if (running) AppStrings.get("dl_downloading") + "…" else AppStrings.get("dl_start"),
                        style = TextStyle(Color.White, 15f.sp, androidx.compose.ui.text.font.FontWeight.Medium)
                    )
                }
                Spacer(Modifier.height(10f.dp))
                BasicText(AppStrings.get("dl_auto_save"), style = TextStyle(contentColor.copy(alpha = 0.55f), 11f.sp))
            }
        }

        Spacer(Modifier.height(16f.dp))

        // ---------------- 任务状态卡 ----------------
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("dl_tasks"), style = TextStyle(contentColor, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(12f.dp))

                val frac = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                // 进度条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8f.dp)
                        .clip(RoundedCornerShape(4f.dp))
                        .background(Color.White.copy(alpha = if (contentColor == Color.White) 0.12f else 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(8f.dp)
                            .background(AppTheme.accent, RoundedCornerShape(4f.dp))
                    )
                }
                Spacer(Modifier.height(10f.dp))

                // 进度文字
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(
                        if (total > 0L) fmt(done) + " / " + fmt(total) + "  (" + (frac * 100).toInt() + "%)" else AppStrings.get("dl_unknown"),
                        style = TextStyle(contentColor, 13f.sp)
                    )
                    if (running && speed > 0L) {
                        BasicText(fmt(speed) + "/s", style = TextStyle(AppTheme.accent, 13f.sp))
                    }
                }
                if (running && eta > 0L) {
                    Spacer(Modifier.height(4f.dp))
                    BasicText(AppStrings.get("dl_eta") + " " + fmt(eta), style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(10f.dp))
                    val ok = status.startsWith("已保存") || status.startsWith("OK") || status.contains("saved") || status.startsWith("Saved")
                    BasicText(
                        status,
                        style = TextStyle(if (ok) Color(0xFF34C759) else Color(0xFFFF9500), 13f.sp)
                    )
                }
                if (url.isBlank() && status.isEmpty()) {
                    Spacer(Modifier.height(10f.dp))
                    BasicText(AppStrings.get("dl_empty"), style = TextStyle(contentColor.copy(alpha = 0.5f), 12f.sp))
                }
            }
        }

        Spacer(Modifier.height(16f.dp))
        BasicText(
            "· " + AppStrings.get("dl_download_folder") + "：Download/ADBToolbox",
            Modifier.padding(horizontal = 4f.dp),
            style = TextStyle(contentColor.copy(alpha = 0.5f), 11f.sp)
        )
        Spacer(Modifier.height(16f.dp))
    }
}
