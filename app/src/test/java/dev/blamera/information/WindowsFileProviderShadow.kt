package dev.blamera.information

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.xmlpull.v1.XmlPullParser

/** AndroidX compares canonical descendants with '/', whereas the JVM host uses '\\'.
 * Only URI generation is adapted; ContentResolver still opens the real expiring provider.
 */
@Implements(FileProvider::class)
class WindowsFileProviderShadow {
    companion object {
        @JvmStatic
        @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            val provider = requireNotNull(
                context.packageManager.resolveContentProvider(
                    authority,
                    PackageManager.GET_META_DATA
                )
            )
            val resource = provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS")
            context.resources.getXml(resource).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG ||
                        parser.name != "cache-path"
                    ) {
                        continue
                    }
                    val name = parser.getAttributeValue(null, "name")
                    val path = parser.getAttributeValue(null, "path")
                    val root = File(context.cacheDir, path).canonicalFile.toPath()
                    val target = file.canonicalFile.toPath()
                    if (target != root && target.startsWith(root)) {
                        return Uri.Builder().scheme("content").authority(authority)
                            .appendPath(name)
                            .apply { root.relativize(target).forEach { appendPath(it.toString()) } }
                            .build()
                    }
                }
            }
            throw IllegalArgumentException("File is outside configured provider roots")
        }
    }
}
