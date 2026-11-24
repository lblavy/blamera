package dev.blamera.faces.runtime

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import dev.blamera.faces.strictFaceJson
import dev.blamera.vision.ModelAsset
import dev.blamera.vision.ModelFileCache
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

internal fun createFaceEngine(context: Context): FaceEngine {
    val application = context.applicationContext
    val qualification = FaceQualification()
    return VerifiedFaceEngine(report = { kind, count, elapsed ->
        Log.i(
            FACE_RUNTIME_LOG_TAG,
            "FACE_ANALYSIS_OK kind=$kind count=$count elapsed_ms=$elapsed pss_kib=${Debug.getPss()}"
        )
    }) {
        val start = SystemClock.elapsedRealtime()
        val memory = Debug.getPss()
        try {
            require(OpenCVLoader.initLocal())
            val paths = withContext(Dispatchers.IO) { preparedFaceModels(application) }
            qualification.openKernel({ OpenCvFaceKernel(paths.paths) }) { current ->
                val cases = loadFaceParityCases(application, paths.profile)
                val proof = verifyFaceParity(current, cases)
                logProof(
                    "FACE_PARITY_OK",
                    proof,
                    SystemClock.elapsedRealtime() - start,
                    Debug.getPss() - memory
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: FaceParityFailure) {
            logParityFailure(
                failed.report,
                SystemClock.elapsedRealtime() - start,
                Debug.getPss() - memory
            )
            throw FaceEngineException()
        } catch (_: IOException) {
            failedRuntime()
        } catch (_: RuntimeException) {
            failedRuntime()
        } catch (_: LinkageError) {
            failedRuntime()
        } catch (_: OutOfMemoryError) {
            failedRuntime()
        }
    }
}

private data class PreparedFaceModels(val profile: String, val paths: FaceModelPaths)

private fun preparedFaceModels(context: Context): PreparedFaceModels {
    val approved =
        strictFaceJson(
            faceAssetBytes(
                context,
                "faces/profile-v1.json",
                MAX_PROFILE_BYTES
            ).toString(Charsets.UTF_8)
        )
    val profile = approved.getJSONObject("profile")
    require(profile.get("dimension") == FACE_EMBEDDING_DIMENSION.toLong())
    val key = profile.getString("key")
    require(PROFILE_HASH.matches(key))
    val models = approved.getJSONObject("pipeline").getJSONArray("models")
    require(models.length() == MODEL_ROLES.size)
    val cache =
        ModelFileCache(File(context.noBackupFilesDir, "face-models/$key"), context.assets::open)
    val paths = (0 until models.length()).associate { index ->
        val item = models.getJSONObject(index)
        val role = item.getString("role")
        val name = item.getString("name")
        require(MODEL_FILENAME.matches(name))
        role to
            cache.prepare(
                ModelAsset("faces/models/$name", item.getLong("size"), item.getString("sha256"))
            ).file
    }
    require(paths.keys == MODEL_ROLES)
    return PreparedFaceModels(
        key,
        FaceModelPaths(
            paths.getValue("detector"),
            paths.getValue("aligner"),
            paths.getValue("encoder")
        )
    )
}

private fun logProof(event: String, proof: FaceParityReport, elapsed: Long, pssDelta: Long) {
    Log.i(
        FACE_RUNTIME_LOG_TAG,
        "$event cases=${proof.cases} bounds_error_px=${proof.boundsError} " +
            "landmarks_error_px=${proof.landmarksError} aligned_rgb_mae=${proof.alignedMae} " +
            "embedding_cosine_min=${proof.embeddingCosineMin} " +
            "prototype_score_delta=${proof.prototypeScoreDelta} " +
            "decision_agreement=${proof.decisionAgreement} cold_ms=$elapsed " +
            "pss_delta_kib=$pssDelta runtime=${Core.getVersionString()}"
    )
}

private fun failedRuntime(): Nothing {
    Log.w(FACE_RUNTIME_LOG_TAG, "FACE_PARITY_FAILED")
    throw FaceEngineException()
}

private fun logParityFailure(proof: FaceParityReport?, elapsed: Long, pssDelta: Long) {
    if (proof == null) {
        Log.w(FACE_RUNTIME_LOG_TAG, "FACE_PARITY_FAILED kind_mismatch=true")
    } else {
        logProof("FACE_PARITY_FAILED", proof, elapsed, pssDelta)
    }
}

internal const val FACE_RUNTIME_LOG_TAG = "BlameraFaces"
private const val MAX_PROFILE_BYTES = 65536
private val MODEL_ROLES = setOf("detector", "aligner", "encoder")
private val MODEL_FILENAME = Regex("[a-zA-Z0-9._-]+\\.onnx")
private val PROFILE_HASH = Regex("[a-f0-9]{64}")
