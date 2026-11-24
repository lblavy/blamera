package dev.blamera.chatgpt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.blamera.R
import dev.blamera.ui.PhotoActionButton

@Composable
internal fun ChatGptTrialScreen(
    model: ChatGptTrialViewModel,
    onBrowser: (String) -> Unit,
    onUsage: () -> Unit,
    onBack: () -> Unit
) {
    val state = model.state
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (state.busy) TrialWaitingBanner(state.status) { model.cancel() }
            Column(
                Modifier.weight(
                    1f
                ).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Essai ChatGPT", style = MaterialTheme.typography.headlineMedium)
                PreparedJpegImage(
                    model.imageJpeg,
                    "Image de test synthétique",
                    Modifier.fillMaxWidth().height(200.dp)
                )
                ChatGptAccountControls(model, onBrowser, onUsage)
                PhotoActionButton(
                    "Envoyer l’image de test",
                    R.drawable.ic_message,
                    { model.analyze() },
                    enabled = state.canAnalyze
                )
                ChatGptResponse(state)
                PhotoActionButton("Retour à Blamera", R.drawable.ic_back, onBack)
            }
        }
    }
}

@Composable
internal fun TrialWaitingBanner(status: String, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp))
        Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onCancel) {
            Icon(painterResource(R.drawable.ic_stop), null, Modifier.size(18.dp))
            Text("Annuler")
        }
    }
}
