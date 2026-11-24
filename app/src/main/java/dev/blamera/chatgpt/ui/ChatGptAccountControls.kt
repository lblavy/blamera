package dev.blamera.chatgpt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blamera.R
import dev.blamera.chatgpt.trialReasoningEfforts
import dev.blamera.ui.PhotoActionButton
import dev.blamera.ui.markdown.ChatGptMarkdown

@Composable
internal fun ChatGptAccountControls(
    model: ChatGptTrialViewModel,
    onBrowser: (String) -> Unit,
    onUsage: () -> Unit
) {
    val state = model.state
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ChatGPT", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            ChatGptAccountMenu(model, onUsage)
        }
        if (state.account?.planUsageEnabled != true) {
            if (state.restoreAvailable) {
                PhotoActionButton("Retrouver ma connexion", R.drawable.ic_message, {
                    model.restore()
                }, enabled = !state.busy)
            } else {
                PhotoActionButton("Continuer avec ChatGPT", R.drawable.ic_message, {
                    model.connect(onBrowser)
                }, enabled = !state.busy)
            }
        }
        TrialSelectors(model)
        val noCatalog = state.account != null && state.models.isEmpty()
        val showStatus = noCatalog || state.status == "Essai annulé."
        if (!state.busy && showStatus) Text(state.status)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ChatGptAccountMenu(model: ChatGptTrialViewModel, onUsage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val state = model.state
    Box {
        IconButton(onClick = {
            open = true
        }) { Icon(painterResource(R.drawable.ic_more), "Compte ChatGPT") }
        DropdownMenu(open, { open = false }) {
            state.account?.let { account ->
                DropdownMenuItem(text = {
                    Text(account.displayName)
                }, onClick = {}, enabled = false)
            }
            DropdownMenuItem(text = { Text("Limites ChatGPT") }, onClick = {
                open = false
                onUsage()
            })
            if (state.account != null || state.restoreAvailable) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (state.account ==
                                null
                            ) {
                                "Oublier la connexion enregistrée"
                            } else {
                                "Se déconnecter"
                            }
                        )
                    },
                    enabled = !state.busy,
                    onClick = {
                        open = false
                        model.disconnect()
                    }
                )
            }
        }
    }
}

@Composable
private fun TrialSelectors(model: ChatGptTrialViewModel) {
    val state = model.state
    if (state.models.isEmpty()) return
    val efforts =
        listOf(null to "Par défaut") +
            trialReasoningEfforts(checkNotNull(state.selectedModel)).map {
                it to reasoningLabel(it)
            }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrialChoice(
            ChoiceSpec(
                "Modèle",
                state.selectedModel,
                state.models.map {
                    it.slug to it.displayName
                }
            ) { it?.let(model::selectModel) },
            Modifier.weight(1f).testTag("model-selector"),
            !state.busy
        )
        TrialChoice(
            ChoiceSpec(
                "Réflexion",
                state.selectedReasoningEffort,
                efforts,
                model::selectReasoningEffort
            ),
            Modifier.weight(1f).testTag("reasoning-selector"),
            !state.busy
        )
    }
}

private data class ChoiceSpec(
    val label: String,
    val selected: String?,
    val options: List<Pair<String?, String>>,
    val onSelect: (String?) -> Unit
)

@Composable
private fun TrialChoice(choice: ChoiceSpec, modifier: Modifier, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val selected = choice.options.firstOrNull { it.first == choice.selected }?.second.orEmpty()
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription =
                    "${choice.label} : $selected"
            }
        ) {
            Column(Modifier.weight(1f)) {
                Text(choice.label, style = MaterialTheme.typography.labelSmall)
                Text(
                    selected,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(16.dp))
        }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 280.dp)) {
            choice.options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    open = false
                    choice.onSelect(value)
                })
            }
        }
    }
}

internal fun reasoningLabel(effort: String): String = when (effort) {
    "none" -> "Aucune"
    "low" -> "Faible"
    "medium" -> "Moyenne"
    "high" -> "Élevée"
    "xhigh" -> "Très élevée"
    "max" -> "Maximale"
    else -> effort
}

@Composable
internal fun ChatGptResponse(state: TrialUiState) {
    state.response?.let { response ->
        Text("Réponse ChatGPT", style = MaterialTheme.typography.titleMedium)
        if (state.responseIncomplete) Text(if (state.busy) "En cours…" else "Réponse partielle")
        state.responseModel?.let { slug ->
            val name = state.models.firstOrNull { it.slug == slug }?.displayName ?: slug
            Text("Modèle utilisé : $name", style = MaterialTheme.typography.labelSmall)
        }
        ChatGptMarkdown(response)
    }
}
