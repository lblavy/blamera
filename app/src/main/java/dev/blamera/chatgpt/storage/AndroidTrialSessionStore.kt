package dev.blamera.chatgpt.storage

import android.content.Context
import java.io.File

internal class AndroidTrialSessionStore(
    context: Context,
    keys: SessionKeyProvider = AndroidSessionKeyProvider()
) : TrialSessionStore by EncryptedSessionStore(
    AtomicSessionFile(File(context.applicationContext.noBackupFilesDir, SESSION_FILENAME)),
    SessionCipher(keys)
)

internal const val SESSION_FILENAME = "chatgpt-session-v1.bin"
