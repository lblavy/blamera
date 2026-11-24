package dev.blamera.ui

import dev.blamera.botany.PlantHttpResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlantDiagnosticTest {
    @Test fun onlyClosedOutcomeCategoriesAreLoggedWithoutTheKeyImageOrSpecies() {
        for ((response, expected) in listOf(
            plantResponse() to "IDENTIFICATION_PUBLISHED",
            PlantHttpResponse(404, "PRIVATE-BODY") to "NO_PLANT",
            PlantHttpResponse(401, "PRIVATE-BODY") to "FAILURE_KEY",
            PlantHttpResponse(503, "PRIVATE-BODY") to "FAILURE_SERVICE"
        )) {
            ShadowLog.clear()
            JointPlantFixture().use { fixture ->
                fixture.response = { response }
                fixture.plants.start(capturePhoto())
                assertEquals(listOf(expected), messages())
                assertEquals(1, fixture.requests)
            }
        }
    }

    @Test fun cancelledRepliesNeverEmitAPublishedOutcome() {
        JointPlantFixture().use { fixture ->
            ShadowLog.clear()
            val pending = CompletableDeferred<PlantHttpResponse>()
            fixture.response = { withContext(NonCancellable) { pending.await() } }
            fixture.plants.start(capturePhoto())
            fixture.plants.cancel()
            pending.complete(plantResponse())
            assertEquals(emptyList<String>(), messages())
            fixture.response = { PlantHttpResponse(404, "") }
            fixture.plants.start(capturePhoto())
            assertEquals(listOf("NO_PLANT"), messages())
        }
    }

    private fun messages() = ShadowLog.getLogsForTag("BlameraPlantNet").map { it.msg }
}
