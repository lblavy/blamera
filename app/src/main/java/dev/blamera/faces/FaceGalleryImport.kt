package dev.blamera.faces

import android.content.Context
import android.net.Uri
import dev.blamera.chatgpt.storage.AtomicSessionFile
import dev.blamera.chatgpt.storage.EncryptedSessionStore
import dev.blamera.chatgpt.storage.SessionCipher
import dev.blamera.chatgpt.storage.SessionKeyProvider
import dev.blamera.chatgpt.storage.TrialSessionStore
import dev.blamera.chatgpt.storage.androidSessionKeys
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope

internal fun createFaceGallerySettings(
    context: Context,
    scope: CoroutineScope
): FaceGallerySettings {
    val application = context.applicationContext
    val approved = application.assets.open("faces/profile-v1.json").bufferedReader().use {
        it.readText()
    }
    return FaceGallerySettings(scope, faceGalleryStore(application), FaceGalleryDecoder(approved)) {
        try {
            readFaceGallery(application.assets.open("faces/bootstrap.json"))
        } catch (_: FileNotFoundException) {
            null
        }
    }
}

internal fun faceGalleryStore(
    context: Context,
    keys: SessionKeyProvider = androidSessionKeys(FACE_GALLERY_DOMAIN)
): TrialSessionStore = EncryptedSessionStore(
    AtomicSessionFile(File(context.applicationContext.noBackupFilesDir, FACE_GALLERY_FILENAME)),
    SessionCipher(FACE_GALLERY_DOMAIN, keys)
)

internal fun readFaceGallery(context: Context, uri: Uri): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException()
    return readFaceGallery(input)
}

internal fun readFaceGallery(input: InputStream): ByteArray = input.use {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    var count = it.read(buffer)
    while (count != -1) {
        if (output.size() + count > MAX_FACE_GALLERY_BYTES) throw FaceGalleryException()
        output.write(buffer, 0, count)
        count = it.read(buffer)
    }
    output.toByteArray()
}

internal const val FACE_GALLERY_FILENAME = "face-gallery-v1.bin"
private const val FACE_GALLERY_DOMAIN = "dev.blamera.faces.gallery.v1"
private const val READ_BUFFER_BYTES = 4096
