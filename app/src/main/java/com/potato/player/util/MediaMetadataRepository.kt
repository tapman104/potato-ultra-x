package com.potato.player.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaMetadataRepository {

    suspend fun resolveTitle(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) return@withContext name
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaMetadataRepository", "Failed to query title for $uri", e)
            }
        }
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: uri.toString().substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "Video"
    }

    suspend fun resolveFileName(context: Context, videoUriString: String): String = withContext(Dispatchers.IO) {
        val decoded = Uri.decode(videoUriString)
        val parsedUri = Uri.parse(decoded)

        if (parsedUri.scheme == "content") {
            try {
                context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) return@withContext name
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaMetadataRepository", "Failed to query file name for $videoUriString", e)
            }
        }

        parsedUri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: decoded.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "Video"
    }

    suspend fun resolveSubtitlePath(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            return@withContext uri.path
        }
        if (uri.scheme == "content") {
            try {
                var fileName = "external_sub"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            cursor.getString(nameIdx)?.let { fileName = it }
                        }
                    }
                }
                val cacheFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext cacheFile.absolutePath
            } catch (e: Exception) {
                Log.w("MediaMetadataRepository", "Failed to copy subtitle content uri to file", e)
            }
        }
        return@withContext uri.toString()
    }
}
