package dev.blamera.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Composable
internal fun BenchmarkScreen(work: BenchmarkWork, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(work) { BenchmarkController(scope, work) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event ==
                Lifecycle.Event.ON_PAUSE
            ) {
                controller.cancel()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.close()
        }
    }
    val view = LocalView.current
    DisposableEffect(view, controller.busy) {
        val previous = view.keepScreenOn
        view.keepScreenOn = controller.busy
        onDispose { view.keepScreenOn = previous }
    }
    MaterialTheme {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(
                rememberScrollState()
            ).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Comparer les modèles", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Diagnostic local CPU : quatre modèles, quatorze images publiques. " +
                    "Le modèle habituel reste " +
                    "inchangé. Une comparaison peut prendre quelques minutes."
            )
            Button(onClick = controller::start, enabled = !controller.busy) {
                Text("Démarrer la comparaison")
            }
            Button(onClick = controller::cancel, enabled = controller.busy) { Text("Annuler") }
            Button(onClick = controller::replay, enabled = !controller.busy) {
                Text("Réémettre le dernier résultat")
            }
            BenchmarkStatus(controller.state)
            Button(onClick = onBack) { Text("Retour à Blamera") }
        }
    }
}

@Composable
private fun BenchmarkStatus(state: BenchmarkState) {
    when (state) {
        BenchmarkState.Idle -> Text("Prêt. Aucun calcul démarré.")
        is BenchmarkState.Running -> Text(state.message)
        BenchmarkState.Stopping -> Text("Annulation : attente de la fin du calcul natif en cours…")
        BenchmarkState.Cancelled -> Text("Comparaison annulée. Aucun résultat complet.")
        BenchmarkState.Failed -> Text(
            "Comparaison impossible. Les modèles ou les ressources locales ne sont pas " +
                "disponibles. Réessayez ou revenez à Blamera."
        )
        BenchmarkState.Reemitted -> Text("Dernier rapport réémis. Aucun calcul supplémentaire.")
        is BenchmarkState.Complete -> BenchmarkResults(state.result)
    }
}

@Composable
private fun BenchmarkResults(result: BenchmarkResult) {
    Text("Comparaison terminée — CPU, 2 threads intra / 1 inter.")
    Text(
        "session.run inclut inférence + NMS. Prétraitement et création du tenseur exclus ; " +
            "copie Java et masques/contours chronométrés séparément. Aucun FPS caméra déduit."
    )
    result.models.forEach { model ->
        Text(
            "${model.model} : lecture + SHA ${ms(
                model.readHashNanos.toDouble()
            )} ms ; session ${ms(model.sessionNanos.toDouble())} ms."
        )
    }
    result.blocks.forEach { block ->
        Text(
            "${block.model.id} / ${block.fixture} : premier appel de cette fixture ${ms(
                block.first.runNanos.toDouble()
            )} ms ; session neuve : ${block.sessionFirst}."
        )
        Text(
            "N=${block.runs.count} ; médiane ${ms(
                block.runs.medianNanos
            )} ms ; min ${ms(
                block.runs.minNanos.toDouble()
            )} ; max ${ms(
                block.runs.maxNanos.toDouble()
            )}. Copie Java médiane ${ms(block.copies.medianNanos)} ms."
        )
        Text(
            "PSS processus échantillonné avant / après bloc (session ouverte), KiB : " +
                "${block.pssBefore ?: "non mesuré"} / ${block.pssAfter ?: "non mesuré"}. Ce " +
                "n’est pas un pic."
        )
    }
    Text(
        "Ordre fixe baseline → Small → Large → Xlarge ; cache et état thermique " +
            "non contrôlés. Ces " +
            "résultats publics permettent une comparaison PC ; aucun classement général " +
            "de qualité n’est déduit de ces quatorze images."
    )
}
private fun ms(nanos: Double): String = String.format(Locale.ROOT, "%.2f", nanos / NANOS_PER_MILLI)
private const val NANOS_PER_MILLI = 1_000_000
