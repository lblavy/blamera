package dev.blamera.benchmark

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BenchmarkScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun explicitReplayNeverRunsModelAndCanBeCancelledByTheUser() {
        var runs = 0
        var replays = 0
        val waiting = CompletableDeferred<Unit>()
        val work = object : BenchmarkWork {
            override suspend fun run(progress: suspend (String) -> Unit): BenchmarkResult {
                runs++
                return BenchmarkResult(emptyList())
            }
            override suspend fun replay(progress: suspend (String) -> Unit) {
                replays++
                progress("Réémission des fragments")
                waiting.await()
            }
        }
        compose.setContent { BenchmarkScreen(work, {}) }
        assertEquals(0, replays)
        compose.onNodeWithText("Réémettre le dernier résultat").performClick()
        compose.waitUntil { replays == 1 }
        compose.onNodeWithText("Démarrer la comparaison").assertIsNotEnabled()
        compose.onNodeWithText("Réémission des fragments").assertExists()
        assertEquals(0, runs)
        compose.onNodeWithText("Annuler").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Réémettre le dernier résultat").assertIsEnabled()
        waiting.complete(Unit)
        compose.onNodeWithText("Réémettre le dernier résultat").performClick()
        compose.onNodeWithText(
            "Dernier rapport réémis. Aucun calcul supplémentaire."
        ).assertExists()
        assertEquals(0, runs)
    }

    @Test fun idleExplicitStartProgressPauseCancellationAndRetry() {
        var calls = 0
        val waiting = CompletableDeferred<Unit>()
        val work =
            BenchmarkWork { progress ->
                calls++
                progress("Mesure en cours")
                waiting.await()
                BenchmarkResult(emptyList())
            }
        compose.setContent { BenchmarkScreen(work, {}) }
        compose.onNodeWithText("Prêt. Aucun calcul démarré.").assertExists()
        assertEquals(0, calls)
        compose.onNodeWithText("Annuler").assertIsNotEnabled()
        compose.onNodeWithText("Démarrer la comparaison").performClick()
        compose.waitUntil { calls == 1 }
        compose.onNodeWithText("Mesure en cours").assertExists()
        compose.onNodeWithText("Démarrer la comparaison").assertIsNotEnabled()
        compose.onNodeWithText("Annuler").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Comparaison annulée. Aucun résultat complet.").assertExists()
        compose.onNodeWithText("Démarrer la comparaison").performClick()
        compose.waitUntil { calls == 2 }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Comparaison annulée. Aucun résultat complet.").assertExists()
        compose.onNodeWithText("Démarrer la comparaison").assertIsEnabled()
    }

    @Test fun errorRetriesAndCompletedReportExplainsScopeAndBack() {
        var fail = true
        var back = 0
        val model = BenchmarkModel("baseline", "a", "h", 1, 80)
        val stats = benchmarkStats(listOf(1_000_000L, 3_000_000L))
        val blocks =
            listOf(
                BenchmarkBlock(
                    model, "nasa", "h", true,
                    BenchmarkSample(
                        4,
                        2,
                        1
                    ),
                    stats, stats, 42, null
                ),
                BenchmarkBlock(
                    model, "synthetic", "h", false,
                    BenchmarkSample(
                        4,
                        2,
                        0
                    ),
                    stats, stats, null, 43
                )
            )
        val result = BenchmarkResult(blocks, listOf(BenchmarkModelTiming("baseline", 1, 2, 4, 5)))
        val work = BenchmarkWork {
            if (fail) throw IOException("test")
            result
        }
        compose.setContent { BenchmarkScreen(work, { back++ }) }
        compose.onNodeWithText("Démarrer la comparaison").performClick()
        compose.onNodeWithText("Comparaison impossible.", substring = true).assertExists()
        fail = false
        compose.onNodeWithText("Démarrer la comparaison").performClick()
        compose.onNodeWithText("Comparaison terminée", substring = true).assertExists()
        compose.onAllNodesWithText("N=2 ; médiane 2.00 ms", substring = true).assertCountEquals(2)
        compose.onNodeWithText("Ordre fixe baseline", substring = true).assertExists()
        compose.onNodeWithText("Retour à Blamera").performScrollTo().performClick()
        assertEquals(1, back)
    }
}
