package dev.blamera.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.blamera.R
import dev.blamera.faces.FaceGallerySettings
import dev.blamera.faces.readFaceGallery

@Composable
internal fun rememberFaceGalleryPicker(settings: FaceGallerySettings?): () -> Unit {
    val context = LocalContext.current.applicationContext
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) settings?.import { readFaceGallery(context, uri) }
    }
    return { picker.launch(arrayOf("*/*")) }
}

@Composable
internal fun FaceGallerySettingsContent(settings: FaceGallerySettings, choose: () -> Unit) {
    val names = settings.gallery?.identities.orEmpty().map { it.name }
    Column {
        Text("Galerie faciale", style = MaterialTheme.typography.titleMedium)
        Text(
            when (names.size) {
                0 -> "Aucune personne importée."
                1 -> "1 personne importée"
                else -> "${names.size} personnes importées"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (names.isNotEmpty()) {
            Text(
                names.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(choose, enabled = !settings.busy) {
            Icon(painterResource(R.drawable.ic_import), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (names.isEmpty()) "Importer une galerie" else "Remplacer la galerie")
        }
        if (settings.busy) {
            Text(
                "Chargement de la galerie…",
                style = MaterialTheme.typography.bodySmall
            )
        }
        settings.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
    }
}
