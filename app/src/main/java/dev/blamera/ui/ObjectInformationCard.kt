package dev.blamera.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.blamera.R
import dev.blamera.chatgpt.ui.CHATGPT_USAGE_URL
import dev.blamera.chatgpt.ui.ChatGptAccountControls
import dev.blamera.chatgpt.ui.ChatGptResponse
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.PreparedJpegImage
import dev.blamera.chatgpt.ui.TrialWaitingBanner
import dev.blamera.information.ObjectInformationTools

@Composable
internal fun ObjectInformationCard(
    session: CaptureSession,
    tools: ObjectInformationTools,
    chatGpt: ChatGptTrialViewModel? = null,
    analyze: () -> Unit = {},
    targetLabel: String = "Cadrage visible"
) {
    if (session.photo == null) return
    val state = remember(session) { session.information(tools, chatGpt) }
    ObservePhotoActionLifecycle(state)
    val browser = rememberPhotoBrowser(chatGpt)
    val faceImport = rememberFaceGalleryPicker(session.faces)
    var config by remember { mutableStateOf(false) }
    PhotoTargetHeading(targetLabel) { config = true }
    PhotoTreatmentBar(
        ready = !session.running && !session.loading && session.interaction?.visibleRegion != null,
        busy = state.preparing || state.search?.sending == true || chatGpt?.state?.busy == true,
        analyze = analyze,
        chatGpt = { performChatGptAction(session, state, chatGpt, browser) },
        lens = { session.snapshotTarget()?.let { state.send(it, lens = true) } }
    )
    session.targeting.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    PlantResultCard(session.plants)
    PhotoActionFeedback(state, chatGpt)
    if (config) {
        PhotoConfigDialog(chatGpt, session, faceImport, browser) { config = false }
    }
}

private fun performChatGptAction(
    session: CaptureSession,
    state: PhotoTargetActions,
    model: ChatGptTrialViewModel?,
    browser: (String) -> Unit
) {
    if (model == null) return
    if (model.state.account?.planUsageEnabled != true) {
        if (model.state.restoreAvailable) model.restore() else model.connect(browser)
    } else if (model.state.canAnalyze) {
        session.snapshotTarget()?.let { state.send(it, lens = false) }
    }
}

@Composable
private fun ObservePhotoActionLifecycle(state: PhotoTargetActions) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    DisposableEffect(state, lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                activity?.isChangingConfigurations != true
            ) {
                state.onBackground()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun PhotoChatGptFeedback(model: ChatGptTrialViewModel) {
    val state = model.state
    if (state.busy) TrialWaitingBanner(state.status) { model.cancel() }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val noCatalog = state.account != null && state.models.isEmpty()
    if (state.status == "Essai annulé." || noCatalog) Text(state.status)
}

@Composable
private fun PhotoTargetHeading(label: String, config: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        NavigationIcon("Configuration", R.drawable.ic_settings, config)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhotoTreatmentBar(
    ready: Boolean,
    busy: Boolean,
    analyze: () -> Unit,
    chatGpt: () -> Unit,
    lens: () -> Unit
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TreatmentButton("Analyser", R.drawable.ic_crop, ready && !busy, analyze)
        TreatmentButton("ChatGPT", R.drawable.ic_message, ready && !busy, chatGpt)
        TreatmentButton("Lens", R.drawable.ic_search, ready && !busy, lens)
    }
}

@Composable
private fun TreatmentButton(text: String, icon: Int, enabled: Boolean, click: () -> Unit) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Icon(painterResource(icon), null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    val padding = androidx.compose.foundation.layout.PaddingValues(
        horizontal = 6.dp,
        vertical = 8.dp
    )
    Button(
        click,
        Modifier.heightIn(min = 48.dp),
        enabled = enabled,
        contentPadding = padding,
        content = content
    )
}

@Composable
private fun PhotoActionFeedback(state: PhotoTargetActions, chatGpt: ChatGptTrialViewModel?) {
    if (state.preparing) TrialWaitingBanner("Préparation de l’image…", state::cancel)
    state.search?.let { search ->
        if (search.sending) TrialWaitingBanner("Ouverture de Lens…", search::cancel)
        search.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    chatGpt?.let { PhotoChatGptFeedback(it) }
    state.image?.let {
        PreparedJpegImage(it.jpeg, "Image utilisée", Modifier.fillMaxWidth().height(72.dp))
    }
    chatGpt?.let { ChatGptResponse(it.state) }
}

@Composable
private fun PhotoConfigDialog(
    model: ChatGptTrialViewModel?,
    session: CaptureSession,
    faceImport: () -> Unit,
    browser: (String) -> Unit,
    close: () -> Unit
) {
    // Derive a finite scroll viewport from the host, not from the dialog's measured content.
    val host = LocalConfiguration.current
    val maxBodyHeight = (host.screenHeightDp.dp - 180.dp).coerceAtLeast(96.dp)
    val dialogWidth = (host.screenWidthDp.dp - 48.dp).coerceAtMost(560.dp)
    AlertDialog(
        modifier = Modifier.width(dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = close,
        title = { Text("Configuration") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                session.faces?.let { FaceGallerySettingsContent(it, faceImport) }
                if (model == null) {
                    Text("ChatGPT indisponible.")
                } else {
                    ChatGptAccountControls(model, browser, { browser(CHATGPT_USAGE_URL) })
                }
                session.plants?.let { PlantSettingsEditor(it.settings) }
            }
        },
        confirmButton = { TextButton(close) { Text("Fermer") } }
    )
}

@Composable
private fun rememberPhotoBrowser(model: ChatGptTrialViewModel?): (String) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context, model) {
        { url ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addCategory(
                            Intent.CATEGORY_BROWSABLE
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: ActivityNotFoundException) {
                model?.browserUnavailable()
            } catch (_: SecurityException) {
                model?.browserUnavailable()
            }
        }
    }
}
