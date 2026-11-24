package dev.blamera.vision

import android.content.Context
import dev.blamera.BuildConfig

fun createLiveAnalyzer(context: Context): VisionAnalyzer {
    val assets = context.applicationContext.assets
    val runner = OrtModelRunner({
        assets.open(BuildConfig.VISION_MODEL_ASSET).use { it.readBytes() }
    }, BuildConfig.VISION_MODEL_SHA256)
    return OnnxImageAnalyzer(runner::run, ::logVisionTiming)
}
