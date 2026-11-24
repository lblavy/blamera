package dev.blamera.vision

import android.os.Debug
import android.util.Log
import dev.blamera.BuildConfig

fun logVisionTiming(timing: VisionTiming) {
    val pss = Debug.getPss()
    val memory = if (pss > 0) " pssSampleKiB=$pss" else ""
    Log.i(
        "BlameraVision",
        "provider=CPU modelBytes=${BuildConfig.VISION_MODEL_BYTES} loadMs=${timing.loadMillis} " +
            "inferenceMs=${timing.inferenceMillis} totalMs=${timing.totalMillis}$memory"
    )
}
