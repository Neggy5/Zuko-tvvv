package com.zuko.tv.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.util.Locale

object MovieDownload {
    /**
     * Enqueues a system download of [url] into public Movies folder.
     * @return download id, or null if DownloadManager unavailable
     */
    fun enqueue(context: Context, url: String, title: String): Long? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return null
        val safeName = title
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
            .trim()
            .ifBlank { "movie" }
            .take(80)
        val ext = when {
            url.contains(".m3u8", ignoreCase = true) -> "m3u8"
            url.contains(".mkv", ignoreCase = true) -> "mkv"
            else -> "mp4"
        }
        val fileName = "$safeName.$ext"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("ZUKO TV download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setMimeType(
                when (ext.lowercase(Locale.US)) {
                    "m3u8" -> "application/vnd.apple.mpegurl"
                    "mkv" -> "video/x-matroska"
                    else -> "video/mp4"
                }
            )
        try {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "ZukoTV/$fileName")
        } catch (_: Exception) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        return dm.enqueue(request)
    }
}
