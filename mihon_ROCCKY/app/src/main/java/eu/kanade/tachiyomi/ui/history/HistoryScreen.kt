package eu.kanade.tachiyomi.ui.history

import androidx.compose.runtime.Composable
import eu.kanade.presentation.util.Screen

class HistoryScreen : Screen() {

    @Composable
    override fun Content() {
        HistoryTab.Content()
    }
}
