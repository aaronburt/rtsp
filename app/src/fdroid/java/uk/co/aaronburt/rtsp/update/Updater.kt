package uk.co.aaronburt.rtsp.update

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object Updater {
    fun start(application: Application) = Unit
}

@Composable
fun UpdatePrompt() = Unit

@Composable
fun UpdateSettingsSection(modifier: Modifier = Modifier) = Unit
