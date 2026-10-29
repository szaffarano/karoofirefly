package io.github.derstrassi.karoofirefly.ui.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a @Preview on a frame matching the Hammerhead Karoo 2/3 screen:
 * a 3.2" 480x800 portrait panel (~290 ppi, nearest standard bucket is xhdpi/320).
 *
 * Use this instead of a bare @Preview so UI is previewed at the real target size.
 */
@Preview(
    showBackground = true,
    device = "spec:width=480px,height=800px,dpi=320",
    name = "Karoo 3",
)
annotation class KarooPreview
