package dev.blamera.camera

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import java.util.concurrent.Executors

internal class LiveUseCase(private val processor: LiveCameraAnalysis) :
    ImageAnalysis.Analyzer,
    AutoCloseable {
    val analysis = ImageAnalysis.Builder()
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(
            ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(
                    Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            ).build()
        ).build()
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile var camera: Camera? = null

    @Volatile private var closed = false

    init {
        analysis.setAnalyzer(executor, this)
    }

    override fun analyze(image: ImageProxy) {
        val expected = camera?.cameraInfo?.getSensorRotationDegrees(analysis.targetRotation)
        if (closed || image.imageInfo.rotationDegrees != expected) {
            image.close()
        } else {
            processor.offer(RgbaCameraFrame(image))
        }
    }

    fun rotate(rotation: Int) {
        analysis.targetRotation = rotation
        processor.invalidate()
    }

    override fun close() {
        if (!closed) {
            closed = true
            processor.invalidate()
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }
    private companion object {
        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480
    }
}
