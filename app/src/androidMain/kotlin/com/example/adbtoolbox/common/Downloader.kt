package com.example.adbtoolbox.common

import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 多线程下载器（actual）：HttpURLConnection + Range 分块 + 断点续传。
 *
 * 续传机制：
 * - 元数据文件 `<dest>.dlm`（UTF-8）：第1行 URL，第2行 总大小，第3行 分片数，之后每行 `分片索引=已写字节`；
 * - 分片文件 `<dest>.part<i>`：各分片已下载的字节（续传时 RandomAccessFile 定位到已写偏移继续 Range）；
 * - 再次调用同 dest：读取 .dlm，若 URL 一致则从各分片断点继续；全部完成/单流完成时合并 + 清理。
 */
actual object Downloader {

    private const val TIMEOUT_MS = 20_000
    private const val BUF = 64 * 1024

    actual suspend fun download(
        url: String,
        destPath: String,
        threads: Int,
        onProgress: (done: Long, total: Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {        val dest = File(destPath)
        try {
            val meta = File(destPath + ".dlm")
            val totalKnown = readTotal(meta)
            val head = head(url)
            val serverLen = head.length
            val rangesOk = head.acceptRanges

            // 断点续传：已有元数据且 URL 一致
            val resumeParts: List<Long> = if (meta.exists() && readUrl(meta) == url) readParts(meta, minOf(threads, 8).coerceAtLeast(1)) else emptyList()

            if (serverLen <= 0L && totalKnown <= 0L) {
                return@withContext DownloadResult(false, "无法获取文件大小（Content-Length 缺失）")
            }
            val total = if (serverLen > 0L) serverLen else totalKnown

            if (!rangesOk || threads <= 1 || total <= 0L) {
                // 退化：单线程整包下载（先下载到 .tmp 再改名，可续传）
                val done = singleStream(url, dest, resumeParts.getOrNull(0) ?: 0L, onProgress, total)
                if (done >= total) finishSingle(dest, done, total, onProgress)
                return@withContext DownloadResult(done >= total, if (done >= total) "OK" else "INCOMPLETE (已下载 $done/$total，可断点继续)", total)
            }

            // 多线程分块
            val n = minOf(threads, 8).coerceAtLeast(1)
            val chunk = total / n
            val pool: ExecutorService = Executors.newFixedThreadPool(n)
            val latch = CountDownLatch(n)
            val doneAll = AtomicLong(0L)
            val finished = AtomicLong(0L)
            val error = AtomicLong(-1L)

            fun partFile(i: Int) = File(destPath + ".part$i")
            fun writeMeta(dones: LongArray) {
                try {
                    meta.writeText(
                        buildString {
                            appendLine(url)
                            appendLine(total.toString())
                            appendLine(n.toString())
                            for (i in 0 until n) append("$i=").appendLine(dones[i].toString())
                        }
                    )
                } catch (_: Exception) {
                }
            }

            for (i in 0 until n) {
                val start = i * chunk
                val end = if (i == n - 1) total - 1 else (i + 1) * chunk - 1
                val already = if (resumeParts.size == n) resumeParts[i] else 0L
                pool.execute {
                    try {
                        var written = already
                        var rangeStart = start + written
                        while (rangeStart <= end && error.get() < 0L) {
                            val conn = URL(url).openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.connectTimeout = TIMEOUT_MS
                            conn.readTimeout = TIMEOUT_MS
                            conn.setRequestProperty("Range", "bytes=$rangeStart-$end")
                            conn.setRequestProperty("User-Agent", "ADBToolbox/3.1")
                            val code = conn.responseCode
                            if (code in 200..299) {
                                val input = conn.inputStream
                                val raf = RandomAccessFile(partFile(i), "rw")
                                raf.seek(written)
                                val buf = ByteArray(BUF)
                                var rd: Int
                                try {
                                    while (rangeStart <= end && error.get() < 0L) {
                                        rd = input.read(buf)
                                        if (rd <= 0) break
                                        raf.write(buf, 0, rd)
                                        written += rd
                                        rangeStart += rd
                                        doneAll.addAndGet(rd.toLong())
                                        onProgress(doneAll.get(), total)
                                    }
                                } finally {
                                    raf.close(); input.close()
                                }
                                conn.disconnect()
                                if (rangeStart > end) {
                                    finished.incrementAndGet()
                                    writeMeta(collect(destPath, n, resumeParts, i, written))
                                }
                            } else {
                                if (code == 416) { // 已下载完成该分片
                                    finished.incrementAndGet()
                                    writeMeta(collect(destPath, n, resumeParts, i, end - start + 1))
                                } else {
                                    error.set(code.toLong())
                                }
                                conn.disconnect()
                            }
                        }
                    } catch (e: Exception) {
                        error.compareAndSet(-1L, -2L)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(5, TimeUnit.MINUTES)
            pool.shutdownNow()

            if (error.get() >= 0L && error.get() != -2L) {
                return@withContext DownloadResult(false, "HTTP $error：下载中断（断点已保存，可继续）", total)
            }

            // 合并分片
            val partsOk = (0 until n).all { partFile(it).exists() }
            if (finished.get() == n.toLong() && partsOk) {
                val raf = RandomAccessFile(dest, "rw")
                try {
                    for (i in 0 until n) {
                        val p = File(destPath + ".part$i")
                        val pr = RandomAccessFile(p, "r")
                        try {
                            val len = p.length()
                            val buf = ByteArray(BUF)
                            var rd: Int
                            while (pr.read(buf).also { rd = it } > 0) {
                                raf.write(buf, 0, rd)
                            }
                        } finally {
                            pr.close()
                        }
                        p.delete()
                    }
                } finally {
                    raf.close()
                }
                meta.delete()
                // 清理可能残留的分片元数据
                for (i in 0 until n) File(destPath + ".part$i").delete()
                onProgress(total, total)
                return@withContext DownloadResult(true, "OK（${n} 线程合并完成）", total)
            }
            DownloadResult(false, "INCOMPLETE（已下载 ${doneAll.get()}/$total，可断点继续）", total)
        } catch (e: Exception) {
            DownloadResult(false, "${e.javaClass.simpleName}: ${e.message}（断点已保存，可继续）", 0L)
        }
    }

    private class Head(val length: Long, val acceptRanges: Boolean)

    private fun head(url: String): Head {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "ADBToolbox/3.1")
            val len = conn.contentLengthLong
            val ranges = (conn.getHeaderField("Accept-Ranges")?.lowercase()?.contains("bytes") == true)
            conn.disconnect()
            Head(len, ranges)
        } catch (e: Exception) {
            Head(-1L, false)
        }
    }

    /** 单线程整包下载（写 dest.tmp，支持从已写偏移续传）。 */
    private fun singleStream(url: String, dest: File, already: Long, onProgress: (Long, Long) -> Unit, total: Long): Long {
        var written = already
        val tmp = File(dest.absolutePath + ".tmp")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (written > 0L) conn.setRequestProperty("Range", "bytes=$written-")
            conn.setRequestProperty("User-Agent", "ADBToolbox/3.1")
            val input = conn.inputStream
            val raf = RandomAccessFile(tmp, "rw")
            raf.seek(written)
            val buf = ByteArray(BUF)
            try {
                var rd: Int
                while (input.read(buf).also { rd = it } > 0) {
                    raf.write(buf, 0, rd)
                    written += rd
                    onProgress(written, total)
                }
            } finally {
                raf.close(); input.close(); conn.disconnect()
            }
        } catch (e: Exception) {
            // 中断：保留 tmp，下次续传
        }
        return written
    }

    private fun finishSingle(dest: File, done: Long, total: Long, onProgress: (Long, Long) -> Unit) {
        val tmp = File(dest.absolutePath + ".tmp")
        if (done >= total && tmp.exists()) {
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            onProgress(total, total)
        }
    }

    private fun readUrl(meta: File): String = try { meta.readLines().getOrNull(0) ?: "" } catch (_: Exception) { "" }
    private fun readTotal(meta: File): Long = try { meta.readLines().getOrNull(1)?.toLong() ?: 0L } catch (_: Exception) { 0L }
    private fun readParts(meta: File, n: Int): List<Long> {
        return try {
            val lines = meta.readLines()
            if (lines.size < 3) return emptyList()
            (0 until n).map { i ->
                lines.getOrNull(3 + i)?.substringAfter('=')?.toLong() ?: 0L
            }
        } catch (_: Exception) { emptyList() }
    }

    /** 收集当前分片完成量用于写元数据。 */
    private fun collect(destPath: String, n: Int, resume: List<Long>, cur: Int, curDone: Long): LongArray {
        val arr = LongArray(n)
        for (i in 0 until n) {
            if (i == cur) arr[i] = curDone
            else {
                val p = File(destPath + ".part$i")
                arr[i] = if (resume.size == n && resume[i] > 0L) resume[i] else if (p.exists()) p.length() else 0L
            }
        }
        return arr
    }

    // ------------------------------------------------------------------ v3.2 保存到 Download 目录

    /** 从 URL 提取文件名（取最后路径段，去 query/fragment；带扩展名优先）。 */
    private fun fileNameFromUrl(url: String): String {
        return try {
            val clean = url.substringBefore('?').substringBefore('#')
            val seg = clean.substringAfterLast('/')
            if (seg.isNotBlank() && seg.contains('.') && !seg.endsWith('/')) seg
            else "download_" + kotlin.math.abs(url.hashCode())
        } catch (_: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "txt", "log" -> "text/plain"
        "pdf" -> "application/pdf"
        "gz" -> "application/gzip"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "bin" -> "application/octet-stream"
        "so" -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    actual suspend fun downloadToDownloads(        url: String,
        fileName: String,
        threads: Int,
        onProgress: (done: Long, total: Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val name = fileName.ifBlank { fileNameFromUrl(url) }.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
            // 私有缓存目录作为分块/断点暂存区：.part/.dlm 不污染公共 Download 目录
            val cacheDir = File(appContext.cacheDir, "downloader")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            // 缓存里的目标临时文件：按 URL+文件名 稳定命名，支持断点续传
            val tmpBase = File(cacheDir, "task_" + kotlin.math.abs((url + name).hashCode()) + "_" + name)
            val tmpPath = tmpBase.absolutePath
            // 清理同名旧产物（上一个已完成任务残留），但保留 .part/.dlm 用于断点续传
            val doneTmp = File(tmpPath)
            if (doneTmp.exists() && File(tmpPath + ".dlm").exists().not()) doneTmp.delete()

            // 1) 多线程分块下载到缓存（自动断点续传）
            val r = download(url, tmpPath, threads, onProgress)
            if (!r.ok) return@withContext r

            // 2) 写入公共 Download 目录
            val savedPath = try {
                if (Build.VERSION.SDK_INT >= 29) {
                    saveViaMediaStore(doneTmp, name)
                } else {
                    saveViaLegacyDir(doneTmp, name)
                }
            } catch (e: Exception) {
                return@withContext DownloadResult(false, "下载完成但保存失败：${e.message}", r.total)
            }

            // 3) 清理缓存产物（含分片与元数据）
            try {
                doneTmp.delete()
                File(tmpPath + ".dlm").delete()
                for (i in 0 until 8) File(tmpPath + ".part$i").delete()
            } catch (_: Exception) {
            }
            DownloadResult(true, savedPath, r.total)
        } catch (e: Exception) {
            DownloadResult(false, "${e.javaClass.simpleName}: ${e.message}", 0L)
        }
    }

    /** API 29+：MediaStore.Downloads 事务写入（免权限，系统自动归入 Download 文件夹）。 */
    private fun saveViaMediaStore(src: File, name: String): String {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, guessMime(name))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ADBToolbox")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert 失败")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { ins -> ins.copyTo(out) }
            } ?: throw IllegalStateException("无法打开输出流")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
        return "已保存到 Download/ADBToolbox/$name"
    }

    /** API 24-28：直接写公共 Download 目录（需要 WRITE_EXTERNAL_STORAGE 权限）。 */
    private fun saveViaLegacyDir(src: File, name: String): String {
        val ctx = appContext
        val hasPerm = try {
            ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
        if (!hasPerm) {
            throw IllegalStateException("需要存储权限（WRITE_EXTERNAL_STORAGE），请到设置授予")
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(dir, "ADBToolbox")
        if (!target.exists()) target.mkdirs()
        val dest = File(target, name)
        src.copyTo(dest, overwrite = true)
        return "已保存到 Download/ADBToolbox/$name"
    }

    /** API < 29 时，保存到公共 Download 目录需要 WRITE_EXTERNAL_STORAGE 运行时权限。 */
    actual fun needsLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT < 29
}