package dev.blamera.vision

import android.content.Context
import dev.blamera.BuildConfig
import java.io.File

private val photoNames = PhotoLabelCatalog(
    ModelAsset(
        BuildConfig.PHOTO_LABELS_ASSET,
        BuildConfig.PHOTO_LABELS_BYTES,
        BuildConfig.PHOTO_LABELS_SHA256
    ),
    BuildConfig.PHOTO_CATEGORY_COUNT
)
private val frenchPhotoNames = FrenchPhotoLabels(
    photoNames,
    ModelAsset(
        BuildConfig.PHOTO_FRENCH_ASSET,
        BuildConfig.PHOTO_FRENCH_BYTES,
        BuildConfig.PHOTO_FRENCH_SHA256
    ),
    BuildConfig.PHOTO_CATEGORY_COUNT
)

fun createPhotoAnalyzer(context: Context): VisionAnalyzer {
    val application = context.applicationContext
    val asset =
        ModelAsset(
            BuildConfig.PHOTO_MODEL_ASSET,
            BuildConfig.PHOTO_MODEL_BYTES,
            BuildConfig.PHOTO_MODEL_SHA256
        )
    val cache = ModelFileCache(File(application.cacheDir, "photo-model"), application.assets::open)
    val runner = OrtPhotoRunner {
        frenchPhotoNames.load(application.assets::open)
        cache.prepare(asset).file
    }
    return OnnxImageAnalyzer(runner::run, ::logVisionTiming, CompactPhotoDecoder()::decode)
}

@Suppress("UNUSED_PARAMETER")
fun createPhotoCategoryLabels(context: Context): (Int) -> String = frenchPhotoNames::label
