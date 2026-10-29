package io.github.derstrassi.karoofirefly.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.derstrassi.karoofirefly.ui.preview.KarooPreview
import io.github.derstrassi.karoofirefly.ui.preview.PreviewData

@KarooPreview
@Composable
fun SettingsScreenEmptyPreview() {
    MaterialTheme {
        SettingsScreen(
            settings = PreviewData.emptySettings,
            onSave = {},
        )
    }
}

@KarooPreview
@Composable
fun SettingsScreenWithLightsPreview() {
    MaterialTheme {
        SettingsScreen(
            settings = PreviewData.populatedSettings,
            discoveredLights = PreviewData.discoveredLights,
            currentLux = 250f,
            sunriseTime = PreviewData.sunrise,
            sunsetTime = PreviewData.sunset,
            onSave = {},
        )
    }
}

@KarooPreview
@Composable
fun SettingsScreenNightPreview() {
    MaterialTheme {
        SettingsScreen(
            settings = PreviewData.populatedSettings,
            discoveredLights = PreviewData.discoveredLights,
            currentLux = 8f,
            sunriseTime = PreviewData.sunrise,
            sunsetTime = PreviewData.sunset,
            onSave = {},
        )
    }
}
