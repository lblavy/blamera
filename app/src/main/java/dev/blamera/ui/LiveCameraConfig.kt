package dev.blamera.ui

import androidx.camera.view.PreviewView
import androidx.camera.view.transform.OutputTransform
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
internal data class LiveCameraConfig(
    val analyzer: VisionAnalyzer,
    val target: (PreviewView) -> OutputTransform? = { it.outputTransform }
) {
    companion object {
        val UNAVAILABLE = LiveCameraConfig(VisionAnalyzer { AnalysisResult.Failed })
    }
}
