package io.github.derstrassi.karoofirefly.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.derstrassi.karoofirefly.ui.preview.KarooPreview
import io.github.derstrassi.karoofirefly.ui.preview.PreviewData

@KarooPreview
@Composable
fun LightDetailDialogAssignedPreview() {
    MaterialTheme {
        LightDetailDialog(
            light = PreviewData.frontAntLight,
            assignment = PreviewData.frontAntAssignment,
            onUpdateAssignment = {},
            onTestMode = { _, _ -> },
            onDelete = {},
            onDismiss = {},
        )
    }
}

@KarooPreview
@Composable
fun LightDetailDialogUnassignedPreview() {
    MaterialTheme {
        LightDetailDialog(
            light = PreviewData.rearBleLight,
            assignment = null,
            onUpdateAssignment = {},
            onTestMode = { _, _ -> },
            onDelete = {},
            onDismiss = {},
        )
    }
}
