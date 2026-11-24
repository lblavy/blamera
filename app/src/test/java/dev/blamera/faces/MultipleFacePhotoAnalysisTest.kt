package dev.blamera.faces

import android.graphics.Bitmap
import dev.blamera.faces.runtime.FaceBounds
import dev.blamera.faces.runtime.FaceEngineResult
import dev.blamera.faces.runtime.FaceObservation
import dev.blamera.faces.runtime.FaceRgbFrame
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultipleFacePhotoAnalysisTest {
    @Test fun twoKnownPeopleAndAnUnknownKeepTheirOwnSourceBoxesAndNames() = runBlocking {
        val settings = readySettings()
        val faces =
            listOf(
                encoded(10f, faceVector(1.0)),
                encoded(100f, faceVector(0.0, 1.0)),
                encoded(200f, faceVector(0.0, 0.0, 1.0))
            )
        val analysis = analysis(settings, faces)
        val result = settled(analysis)
        assertEquals(listOf(10f, 100f, 200f), result.faces.map { it.bounds.left })
        assertEquals(
            listOf(
                FaceIdentityStatus.RECOGNIZED,
                FaceIdentityStatus.RECOGNIZED,
                FaceIdentityStatus.UNKNOWN
            ),
            result.faces.map {
                it.identity?.status
            }
        )
        assertEquals(
            settings.gallery!!.identities.map {
                it.name
            },
            result.faces.take(2).map { it.identity?.name }
        )
        assertNull(result.faces.last().identity?.name)
        analysis.close()
    }

    @Test fun uncertainAndUnavailableFacesDoNotLoseOtherResultsOrInventNames() = runBlocking {
        val settings = readySettings()
        val analysis = analysis(
            settings,
            listOf(
                encoded(10f, faceVector(1.0)),
                encoded(100f, faceVector(.4, 0.0, sqrt(.84))),
                FaceObservation.Unavailable(FaceBounds(200f, 0f, 240f, 40f))
            )
        )
        val result = settled(analysis)
        assertEquals(FaceIdentityStatus.RECOGNIZED, result.faces[0].identity?.status)
        assertEquals(FaceIdentityStatus.UNCERTAIN, result.faces[1].identity?.status)
        assertNull(result.faces[1].identity?.name)
        assertNull(result.faces[2].identity)
        analysis.close()
    }

    @Test fun independentMatchingAllowsTheSameIdentityMoreThanOnceWithOnePersonRegistered() =
        runBlocking {
            val settings = readySettings()
            val fixture = org.json.JSONObject(faceFixture())
            val people = fixture.getJSONArray("identities")
            people.remove(1)
            settings.import { fixture.toString().toByteArray() }
            while (settings.busy) yield()
            val analysis =
                analysis(
                    settings,
                    listOf(
                        encoded(10f, faceVector(1.0)),
                        encoded(100f, faceVector(1.0)),
                        encoded(200f, faceVector(0.0, 1.0))
                    )
                )
            val result = settled(analysis)
            assertEquals(result.faces[0].identity?.name, result.faces[1].identity?.name)
            assertEquals(FaceIdentityStatus.RECOGNIZED, result.faces[1].identity?.status)
            assertEquals(FaceIdentityStatus.UNKNOWN, result.faces[2].identity?.status)
            analysis.close()
        }

    private suspend fun CoroutineScope.readySettings(): FaceGallerySettings {
        val settings = FaceGallerySettings(this, FaceMemoryStore(faceFixture()), faceDecoder())
        while (settings.busy) yield()
        return settings
    }

    private fun CoroutineScope.analysis(
        settings: FaceGallerySettings,
        faces: List<FaceObservation>
    ): FacePhotoAnalysis =
        FacePhotoAnalysis(this, settings, PhotoFaceEngine { FaceEngineResult.Detected(faces) }) {
            FaceRgbFrame(300, 50, ByteArray(300 * 50 * 3))
        }

    private suspend fun settled(analysis: FacePhotoAnalysis): FacePhotoState.Detected {
        analysis.show(Bitmap.createBitmap(300, 50, Bitmap.Config.ARGB_8888))
        while (analysis.state == FacePhotoState.Running) yield()
        assertTrue(
            "The whole group must be recognized independently",
            analysis.state is FacePhotoState.Detected
        )
        return analysis.state as FacePhotoState.Detected
    }

    private fun encoded(left: Float, vector: List<Double>) = FaceObservation.Encoded(
        FaceBounds(
            left,
            0f,
            left + 40f,
            40f
        ),
        vector
    )
}
