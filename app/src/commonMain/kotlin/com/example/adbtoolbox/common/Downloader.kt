package com.example.adbtoolbox.common

/**
 * 多线程下载器（expect）。
 *
 * 功能：
 * - 多线程分块下载（Range 分段，每块一个连接并行写 .part 分片）；
 * - 断点续传：元数据文件（destPath + ".dlm"）记录 URL/总大小/每分片已写字节；
 *   再次调用同 dest 时自动从断点继续（跳过已完成字节）；
 * - 服务端不支持 Range 时自动退化为单线程整包下载；
 * - 完成后合并分片、校验总大小、清理临时文件。
 *
 * 通过 [download] 的 onProgress 回报 (已下载字节, 总字节)，供 UI 显示进度。
 */
expect object Downloader {
    suspend fun download(
        url: String,
        destPath: String,
        threads: Int = 4,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): DownloadResult

    /**
     * v3.2 便捷入口：直链高速下载，完成后自动保存到手机内部存储的 Download 文件夹。
     *
     * - 先下载到应用私有缓存（支持多线程分块 + 断点续传，.part/.dlm 不污染公共目录）；
     * - 完成后自动写入公共 Download 目录（API 29+ 走 MediaStore.Downloads 免权限；
     *   API 24-28 直接写公共 Download 目录，需要 WRITE_EXTERNAL_STORAGE 权限）；
     * - 返回的 message 含最终保存位置（如 "已保存到 Download/xxx.apk"）。
     */
    suspend fun downloadToDownloads(
        url: String,
        fileName: String = "",
        threads: Int = 4,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): DownloadResult

    /**
     * 当前平台是否需要旧版存储权限（WRITE_EXTERNAL_STORAGE）。
     * true = API < 29：保存到公共 Download 目录需要运行时存储权限，UI 应在下载前请求；
     * false = API 29+：走 MediaStore 免权限。
     */
    fun needsLegacyStoragePermission(): Boolean
}

/** 下载结果。ok=false 时 message 为失败原因（含断点信息，可直接再调 resume）。 */
data class DownloadResult(val ok: Boolean, val message: String, val total: Long = 0L)