package dev.blamera.botany

import android.content.Context
import dev.blamera.chatgpt.storage.AtomicSessionFile
import dev.blamera.chatgpt.storage.EncryptedSessionStore
import dev.blamera.chatgpt.storage.SessionCipher
import dev.blamera.chatgpt.storage.SessionKeyProvider
import dev.blamera.chatgpt.storage.androidSessionKeys
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal fun createPlantAnalysis(
    context: Context,
    scope: CoroutineScope,
    keys: SessionKeyProvider = androidSessionKeys(PLANT_KEY_DOMAIN),
    http: PlantHttp = PlantNetHttp()
): PlantAnalysis {
    val application = context.applicationContext
    val store = EncryptedSessionStore(
        AtomicSessionFile(File(application.noBackupFilesDir, "plantnet-key-v1.bin")),
        SessionCipher(PLANT_KEY_DOMAIN, keys)
    )
    val preferences = application.getSharedPreferences("plantnet-quota", Context.MODE_PRIVATE)
    val quota = PlantQuota(
        read = { preferences.getString("journal", null) },
        write = {
            if (!preferences.edit().putString(
                    "journal",
                    it
                ).commit()
            ) {
                throw PlantException(PlantFailure.STORAGE)
            }
        }
    )
    return PlantAnalysis(scope, PlantSettings(scope, store), PlantIdentifier(http, quota))
}

private const val PLANT_KEY_DOMAIN = "dev.blamera.plantnet.key.v1"
