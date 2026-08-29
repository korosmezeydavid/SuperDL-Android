package com.nitaplay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nitaplay.player.PlayerUiState
import com.nitaplay.ui.screens.EqualizerScreen
import com.nitaplay.ui.screens.LibraryScreen
import com.nitaplay.ui.screens.NowPlayingScreen
import com.nitaplay.ui.screens.SettingsScreen
import com.nitaplay.ui.theme.themeOf

@Composable
fun NitaPlayAppRoot(
    ui: UiState,
    playback: PlayerUiState,
    vm: NitaPlayViewModel,
    onPickCloudFolder: () -> Unit
) {
    val theme = themeOf(ui.themeId)
    val animMs = if (ui.animationsEnabled) 280 else 0
    val bgTop by animateColorAsState(theme.backgroundTop, tween(animMs), label = "bgTop")
    val bgBottom by animateColorAsState(theme.backgroundBottom, tween(animMs), label = "bgBottom")
    val accent by animateColorAsState(theme.accent, tween(animMs), label = "accent")
    val text by animateColorAsState(theme.text, tween(animMs), label = "text")
    val muted by animateColorAsState(theme.textMuted, tween(animMs), label = "muted")
    val card by animateColorAsState(theme.card, tween(animMs), label = "card")

    val colors = theme.copy(
        backgroundTop = bgTop,
        backgroundBottom = bgBottom,
        accent = accent,
        text = text,
        textMuted = muted,
        card = card
    )

    var tab by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.backgroundBrush())
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = colors.card.copy(alpha = 0.92f),
                    contentColor = colors.text
                ) {
                    val items = listOf(
                        Triple("Most szól", Icons.Default.PlayCircle, 0),
                        Triple("Könyvtár", Icons.Default.LibraryMusic, 1),
                        Triple("Hangszín", Icons.Default.Equalizer, 2),
                        Triple("Beállítások", Icons.Default.Settings, 3)
                    )
                    items.forEach { (label, icon, idx) ->
                        NavigationBarItem(
                            selected = tab == idx,
                            onClick = { tab = idx },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.accent,
                                unselectedIconColor = colors.textMuted,
                                unselectedTextColor = colors.textMuted,
                                indicatorColor = colors.accent.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> NowPlayingScreen(
                        colors = colors,
                        ui = ui,
                        playback = playback,
                        vm = vm,
                        animations = ui.animationsEnabled
                    )
                    1 -> LibraryScreen(
                        colors = colors,
                        ui = ui,
                        vm = vm,
                        onPickCloudFolder = onPickCloudFolder
                    )
                    2 -> EqualizerScreen(
                        colors = colors,
                        ui = ui,
                        playback = playback,
                        vm = vm
                    )
                    3 -> SettingsScreen(
                        colors = colors,
                        ui = ui,
                        vm = vm,
                        onPickCloudFolder = onPickCloudFolder
                    )
                }
            }
        }
    }

    ui.resumeOffer?.let { (track, posMs) ->
        val mins = (posMs / 1000 / 60).toInt()
        val secs = ((posMs / 1000) % 60).toInt()
        AlertDialog(
            onDismissRequest = { vm.dismissResume() },
            title = { Text("Folytatás") },
            text = {
                Text("Folytatás: ${track.title} — %d:%02d-től".format(mins, secs))
            },
            confirmButton = {
                TextButton(onClick = { vm.acceptResume() }) { Text("Folytatás") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissResume() }) { Text("Nem") }
            }
        )
    }
}
