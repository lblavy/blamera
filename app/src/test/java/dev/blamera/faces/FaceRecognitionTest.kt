package dev.blamera.faces

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun faceVector(vararg values: Double): List<Double> =
    List(512) { values.getOrElse(it) { 0.0 } }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceRecognitionTest {
    @Test fun decoderRetainsValidatedPackagePolicyAndKnownFacesUseIt() {
        val gallery = faceDecoder().decode(faceFixture().toByteArray())
        assertEquals(.3, gallery.policy.reject, 0.0)
        assertEquals(.5, gallery.policy.accept, 0.0)
        assertEquals(.05, gallery.policy.margin, 0.0)
        val decision = recognizeFace(faceVector(1.0), gallery)
        assertEquals(FaceIdentityStatus.RECOGNIZED, decision.status)
        assertEquals("Personne synthétique A", decision.name)
        assertEquals(1.0, decision.score, 0.0)
        assertEquals(0.0, decision.second)
    }

    @Test fun unknownUncertainAndTiedFacesNeverReceiveAName() {
        val gallery = gallery()
        assertDecision(FaceIdentityStatus.UNKNOWN, faceVector(0.0, 0.0, 1.0), gallery)
        assertDecision(FaceIdentityStatus.UNCERTAIN, faceVector(.4, 0.0, sqrt(.84)), gallery)
        assertDecision(FaceIdentityStatus.UNCERTAIN, faceVector(sqrt(.5), sqrt(.5)), gallery)
    }

    @Test fun absoluteAndMarginBoundariesApplyWithOneOrSeveralPeople() {
        val source = gallery(FacePolicy(.25, .5, .125))
        val single = FaceGalleryDocument(source.json, source.identities.take(1), source.policy)
        assertDecision(FaceIdentityStatus.UNCERTAIN, faceVector(.25, sqrt(.9375)), single)
        val accepted = recognizeFace(faceVector(.5, sqrt(.75)), single)
        assertEquals(FaceIdentityStatus.RECOGNIZED, accepted.status)
        assertNull(accepted.second)
        assertDecision(FaceIdentityStatus.UNKNOWN, faceVector(0.0, 0.0, 1.0), single)
        assertEquals(
            FaceIdentityStatus.RECOGNIZED,
            recognizeFace(faceVector(.5, .375, sqrt(.609375)), source).status
        )
        assertDecision(
            FaceIdentityStatus.UNCERTAIN,
            faceVector(.5, .375, sqrt(.609375)),
            gallery(FacePolicy(.25, .5, .1251))
        )
    }

    @Test fun malformedEmbeddingsPoliciesAndEmptyGalleriesAreRejected() {
        for (vector in listOf(
            listOf(1.0),
            faceVector(),
            faceVector(Double.NaN),
            faceVector(Double.POSITIVE_INFINITY)
        )) {
            assertThrows(IllegalArgumentException::class.java) { recognizeFace(vector, gallery()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            recognizeFace(faceVector(1.0), gallery(FacePolicy(.6, .5, .05)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            recognizeFace(
                faceVector(1.0),
                FaceGalleryDocument("", emptyList(), FacePolicy(.3, .5, .05))
            )
        }
    }

    private fun gallery(policy: FacePolicy = FacePolicy(.3, .5, .05)): FaceGalleryDocument {
        val parsed = faceDecoder().decode(faceFixture().toByteArray())
        return FaceGalleryDocument(parsed.json, parsed.identities, policy)
    }

    private fun assertDecision(
        status: FaceIdentityStatus,
        vector: List<Double>,
        gallery: FaceGalleryDocument
    ) {
        val decision = recognizeFace(vector, gallery)
        assertEquals(status, decision.status)
        assertNull(decision.name)
    }
}
