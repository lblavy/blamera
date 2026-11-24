package dev.blamera.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.blamera.R
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.faces.FacePhotoState
import dev.blamera.information.ObjectInformationTools
import dev.blamera.vision.AnalysisResult

@Composable
internal fun PhotoResults(
    session: CaptureSession,
    labels: (Int) -> String,
    actions: PhotoActions,
    information: ObjectInformationTools,
    chatGpt: ChatGptTrialViewModel? = null
) {
    val objectTools = PhotoInformation(information, chatGpt)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoImage(session, Modifier.weight(1f).fillMaxHeight())
                PhotoPanel(
                    session,
                    labels,
                    actions,
                    objectTools,
                    Modifier.width(300.dp).fillMaxHeight()
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoImage(session, Modifier.weight(1f).fillMaxWidth())
                PhotoPanel(
                    session,
                    labels,
                    actions,
                    objectTools,
                    Modifier.fillMaxWidth().heightIn(max = 320.dp)
                )
            }
        }
    }
}

@Composable
private fun PhotoImage(session: CaptureSession, modifier: Modifier) {
    val photo = session.photo ?: return
    val interaction = session.interaction ?: return
    val detections = (session.result as? AnalysisResult.Success)?.detections.orEmpty()
    ExplorablePhoto(
        photo.bitmap,
        PhotoOverlay(detections, session.selected, session.running, interaction::selectHits),
        interaction,
        modifier,
        faces = (session.recognition?.state as? FacePhotoState.Detected)?.faces.orEmpty()
    )
}

@Composable
private fun PhotoPanel(
    session: CaptureSession,
    labels: (Int) -> String,
    actions: PhotoActions,
    information: PhotoInformation,
    modifier: Modifier
) {
    val detections = (session.result as? AnalysisResult.Success)?.detections.orEmpty()
    Surface(tonalElevation = 4.dp, modifier = modifier) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            session.recognition?.let { FacePhotoFeedback(it.state) }
            val selection = detections.getOrNull(session.selected ?: -1)
            if (selection == null) {
                Text(photoStatus(session, labels), style = MaterialTheme.typography.bodySmall)
            }
            if (session.hits.size > 1) {
                Text(
                    "Objets superposés à cet endroit",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    session.hits.forEach { index ->
                        val detection = detections.getOrNull(index) ?: return@forEach
                        FilterChip(
                            selected = session.selected == index,
                            onClick = { session.interaction?.select(index) },
                            label = { Text("${labels(detection.classId)} · ${index + 1}") }
                        )
                    }
                }
            }
            ObjectInformationCard(
                session,
                information.tools,
                information.chatGpt,
                actions.region,
                selection?.let { labels(it.classId) } ?: "Cadrage visible"
            )
        }
    }
}

internal data class PhotoActions(
    val retry: () -> Unit,
    val region: () -> Unit,
    val original: () -> Unit
)

private data class PhotoInformation(
    val tools: ObjectInformationTools,
    val chatGpt: ChatGptTrialViewModel?
)

private fun photoStatus(session: CaptureSession, labels: (Int) -> String): String = when {
    session.loading -> "Chargement de l’image…"
    session.running -> "Analyse en cours…"
    session.result == AnalysisResult.Failed ->
        "Analyse impossible. Réessayez ou choisissez une autre image."
    else -> detectionStatus(session, labels)
}

private fun detectionStatus(session: CaptureSession, labels: (Int) -> String): String {
    val detections =
        (session.result as? AnalysisResult.Success)?.detections
            ?: return "Analyse interrompue. Vous pouvez la relancer."
    val selected = session.selected?.let(detections::getOrNull)
    return when {
        selected != null -> labels(selected.classId)
        detections.isEmpty() -> "Aucun objet reconnu à ce seuil."
        else -> "${detections.size} objets détectés. Touchez un objet pour le sélectionner."
    }
}
