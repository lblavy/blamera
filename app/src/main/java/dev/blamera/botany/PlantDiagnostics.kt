package dev.blamera.botany

import android.util.Log

// Only closed categories and booleans cross this boundary; no server or image content.
internal fun writePlantOutcome(identified: Boolean) {
    Log.i(PLANT_DIAGNOSTIC_TAG, if (identified) "IDENTIFICATION_PUBLISHED" else "NO_PLANT")
}

internal fun writePlantFailure(failure: PlantFailure) {
    Log.i(PLANT_DIAGNOSTIC_TAG, "FAILURE_${failure.name}")
}

internal fun writePlantPrompt(hasHint: Boolean) {
    Log.i(
        PLANT_DIAGNOSTIC_TAG,
        if (hasHint) "PROMPT_PREPARED_WITH_HINT" else "PROMPT_PREPARED_WITHOUT_HINT"
    )
}

private const val PLANT_DIAGNOSTIC_TAG = "BlameraPlantNet"
