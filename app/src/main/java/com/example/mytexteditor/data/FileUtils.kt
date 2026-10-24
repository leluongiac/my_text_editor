package com.example.mytexteditor.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtils {
    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun saveToFile(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
