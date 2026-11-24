package dev.blamera.information

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider

class ExpiringSearchImageProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        synchronized(ShareCacheGuard.lock) {
            if (mode != "r") throw SecurityException("Search images are read only")
            val file = ShareImageFiles(
                requireNotNull(context)
            ).liveFile(uri, System.currentTimeMillis())
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

    override fun getType(uri: Uri): String = synchronized(ShareCacheGuard.lock) {
        ShareImageFiles(requireNotNull(context)).liveFile(uri, System.currentTimeMillis())
        "image/jpeg"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor = synchronized(ShareCacheGuard.lock) {
        val file = ShareImageFiles(
            requireNotNull(context)
        ).liveFile(uri, System.currentTimeMillis())
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        MatrixCursor(columns).apply {
            addRow(
                columns.map {
                    when (it) {
                        OpenableColumns.DISPLAY_NAME -> "image.jpg"
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    }
                }
            )
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Search images are read only")
}
