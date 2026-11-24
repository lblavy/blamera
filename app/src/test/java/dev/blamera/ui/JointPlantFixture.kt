package dev.blamera.ui

import dev.blamera.botany.PlantAnalysis
import dev.blamera.botany.PlantHttp
import dev.blamera.botany.PlantHttpResponse
import dev.blamera.botany.PlantIdentifier
import dev.blamera.botany.PlantSettings
import dev.blamera.chatgpt.storage.TrialSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class UiPlantStore : TrialSessionStore {
    var value: String? = null
    override suspend fun load() = value
    override suspend fun save(value: String) {
        this.value = value
    }
    override suspend fun clear() {
        value = null
    }
}

internal class JointPlantFixture(configured: Boolean = true) : AutoCloseable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val store = UiPlantStore()
    val settings = PlantSettings(scope, store)
    var requests = 0
    var response: suspend () -> PlantHttpResponse = { plantResponse() }
    val plants = PlantAnalysis(
        scope,
        settings,
        PlantIdentifier(
            PlantHttp { _, _ ->
                requests++
                response()
            },
            quotaDispatcher = Dispatchers.Unconfined
        )
    ) { byteArrayOf(1, 2, 3) }
    init {
        if (configured) settings.save("BLAMERA-FAKE")
    }
    override fun close() {
        plants.close()
        scope.cancel()
    }
}

internal fun plantResponse() = PlantHttpResponse(
    200,
    """{"results":[{"score":0.8,"species":{"scientificNameWithoutAuthor":"Rosa canina","commonNames":["Églantier"]}},{"score":0.15,"species":{"scientificNameWithoutAuthor":"Rosa rubiginosa","commonNames":["Rosier rouillé"]}}]}"""
)
