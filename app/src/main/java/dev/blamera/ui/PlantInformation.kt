package dev.blamera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.blamera.R
import dev.blamera.botany.PlantAnalysis
import dev.blamera.botany.PlantCandidate
import dev.blamera.botany.PlantSettings

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PlantSettingsEditor(settings: PlantSettings) {
    // A new dialog creates an empty editor; the stored key is never copied back into the field.
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Pl@ntNet", style = MaterialTheme.typography.titleMedium)
        Text(if (settings.configured) "Clé configurée" else "Aucune clé configurée")
        Text(
            "Les photos analysées sont envoyées à Pl@ntNet.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            input,
            { input = it },
            Modifier.fillMaxWidth().testTag("plantnet-key"),
            label = { Text("Clé API Pl@ntNet") },
            singleLine = true,
            enabled = !settings.busy,
            visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                PlantKeyVisibilityButton(visible, !settings.busy) { visible = !visible }
            },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password
            )
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = {
                    settings.save(input)
                    input = ""
                    visible = false
                },
                enabled =
                !settings.busy && input.isNotEmpty()
            ) {
                Text(if (settings.configured) "Remplacer la clé" else "Enregistrer la clé")
            }
            TextButton(
                onClick = {
                    settings.remove()
                    input = ""
                    visible = false
                },
                enabled = !settings.busy && settings.configured
            ) {
                Text("Supprimer")
            }
        }
        settings.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PlantKeyVisibilityButton(visible: Boolean, enabled: Boolean, toggle: () -> Unit) {
    IconButton(onClick = toggle, enabled = enabled) {
        Icon(
            painterResource(
                if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            ),
            if (visible) "Masquer la clé" else "Afficher la clé"
        )
    }
}

@Composable
internal fun PlantResultCard(plants: PlantAnalysis?) {
    val snapshot = plants?.snapshot ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painterResource(R.drawable.powered_by_plantnet),
                "Pl@ntNet",
                Modifier.width(88.dp).height(24.dp)
            )
            Text(
                "Plante de l’image analysée",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
        }
        if (snapshot.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp))
                Text(
                    snapshot.status,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { plants.cancel() }) { Text("Annuler Pl@ntNet") }
            }
        } else {
            val candidates = snapshot.result?.candidates.orEmpty()
            if (candidates.isEmpty()) {
                Text(snapshot.status, style = MaterialTheme.typography.bodySmall)
            } else {
                PlantCandidates(candidates)
            }
        }
    }
}

@Composable
private fun PlantCandidates(candidates: List<PlantCandidate>) {
    Text(candidateLabel(candidates.first()), style = MaterialTheme.typography.bodyMedium)
    Text("Hypothèse à vérifier", style = MaterialTheme.typography.labelSmall)
    if (candidates.size > 1) {
        Text("Autres hypothèses pour cette plante", style = MaterialTheme.typography.labelSmall)
        candidates.drop(1).forEach {
            Text(candidateLabel(it), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun candidateLabel(candidate: PlantCandidate): String =
    candidate.commonName?.let { "$it — ${candidate.scientificName}" } ?: candidate.scientificName
