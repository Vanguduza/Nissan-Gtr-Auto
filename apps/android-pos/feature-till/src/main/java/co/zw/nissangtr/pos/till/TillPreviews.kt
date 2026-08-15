package co.zw.nissangtr.pos.till

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import co.zw.nissangtr.pos.api.FakePosClient
import co.zw.nissangtr.ui.theme.GtrTheme

@Preview(
    name = "Till_Expanded_1280x800",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true,
    backgroundColor = 0xFF12151C,
)
@Composable
fun Till_Expanded_1280x800() {
    GtrTheme(darkTheme = true) {
        TillScreen(
            state = tillUiStateFromFake(
                fake = FakePosClient().fakeTillState(),
                layoutMode = TillLayoutMode.Expanded,
            ),
            clockText = "14:32",
        )
    }
}

@Preview(
    name = "Till_Compact_412x915",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    backgroundColor = 0xFF12151C,
)
@Composable
fun Till_Compact_412x915() {
    GtrTheme(darkTheme = true) {
        TillScreen(
            state = tillUiStateFromFake(
                fake = FakePosClient().fakeTillState(),
                layoutMode = TillLayoutMode.Compact,
            ),
            clockText = "14:32",
        )
    }
}
