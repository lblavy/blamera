package dev.blamera.vision

import android.graphics.Bitmap

sealed interface AnalysisResult {
    data class Success(val detections: List<VisionDetection>) : AnalysisResult
    data object Failed : AnalysisResult
}

fun interface VisionAnalyzer {
    suspend fun analyze(bitmap: Bitmap): AnalysisResult
}
