package com.nitaplay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitaplay.data.AppPreferences
import com.nitaplay.data.MusicSource
import com.nitaplay.data.PlayMode
import com.nitaplay.player.PlayerUiState
import com.nitaplay.ui.NitaPlayViewModel
import com.nitaplay.ui.UiState
import com.nitaplay.ui.theme.NitaThemeColors
import com.nitaplay.ui.theme.moodEmoji

@Composable
fun NowPlayingScreen(
    colors: NitaThemeColors,
    ui: UiState,
    playback: PlayerUiState,
    vm: NitaPlayViewModel,
    animations: Boolean
) {
    val track = playback.track
    val isFav = track?.let { ui.favoriteIds.contains(it.id) } == true
    var sleepMenu by remember { mutableStateOf(false) }

    val pulse = if (animations && playback.isPlaying) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            1f, 1.08f,
            infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulseV"
        ).value
    } else 1f

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Source badge
        Text(
            text = when (track?.source ?: ui.source) {
                MusicSource.PHONE -> "telefon"
                MusicSource.CLOUD -> "felhő"
            },
            color = colors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .background(colors.card.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Artwork / mood
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .scale(pulse)
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        listOf(colors.accent.copy(alpha = 0.35f), colors.card)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = track?.id to track?.title,
                transitionSpec = {
                    if (animations) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "art"
            ) {
                Text(
                    text = moodEmoji(colors.id),
                    fontSize = 88.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = track?.title ?: "Nincs lejátszás",
            color = colors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = track?.displayArtist() ?: "Válassz zenét a könyvtárból",
            color = colors.textMuted,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (playback.error != null) {
            Text(
                text = playback.error!!,
                color = colors.secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Seek bar
        val dur = playback.durationMs.coerceAtLeast(0)
        val pos = playback.positionMs.coerceIn(0, if (dur > 0) dur else Int.MAX_VALUE)
        Slider(
            value = if (dur > 0) pos.toFloat() / dur else 0f,
            onValueChange = { f -> if (dur > 0) vm.player.seekTo((f * dur).toInt()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.textMuted.copy(alpha = 0.3f)
            )
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(pos), color = colors.textMuted, fontSize = 12.sp)
            Text(
                if (dur > 0) "-${formatMs(dur - pos)}" else "--:--",
                color = colors.textMuted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // Main controls
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { vm.player.previous() },
                modifier = Modifier.semantics { contentDescription = "Előző szám" }
            ) {
                Icon(Icons.Default.SkipPrevious, null, tint = colors.text, modifier = Modifier.size(40.dp))
            }

            Box(
                Modifier
                    .size(76.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { vm.player.togglePlayPause() },
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = if (playback.isPlaying) "Szünet" else "Lejátszás"
                        }
                ) {
                    val isCatPause = colors.id.name == "CAT" && !playback.isPlaying
                    if (isCatPause) {
                        Text("😴", fontSize = 32.sp)
                    } else {
                        Icon(
                            if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (colors.isDark) colors.backgroundBottom else Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = { vm.player.next() },
                modifier = Modifier.semantics { contentDescription = "Következő szám" }
            ) {
                Icon(Icons.Default.SkipNext, null, tint = colors.text, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // Tekerés a beállított lépésközzel (10 mp zene / 30 mp hangoskönyv)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { vm.player.seekBy(-ui.seekStepSec) },
                enabled = track != null,
                modifier = Modifier.semantics {
                    contentDescription = "Vissza ${ui.seekStepSec} másodperc"
                }
            ) {
                Icon(Icons.Default.FastRewind, null, tint = colors.text, modifier = Modifier.size(20.dp))
                Text(" ${ui.seekStepSec}s", color = colors.textMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.size(24.dp))
            TextButton(
                onClick = { vm.player.seekBy(ui.seekStepSec) },
                enabled = track != null,
                modifier = Modifier.semantics {
                    contentDescription = "Előre ${ui.seekStepSec} másodperc"
                }
            ) {
                Text("${ui.seekStepSec}s ", color = colors.textMuted, fontSize = 12.sp)
                Icon(Icons.Default.FastForward, null, tint = colors.text, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // Secondary row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { vm.cyclePlayMode() },
                modifier = Modifier.semantics { contentDescription = "Lejátszási mód: ${playback.playMode.labelHu()}" }
            ) {
                Icon(
                    when (playback.playMode) {
                        PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                        PlayMode.REPEAT_ALL -> Icons.Default.Repeat
                        PlayMode.SEQUENTIAL -> Icons.AutoMirrored.Filled.QueueMusic
                    },
                    contentDescription = null,
                    tint = colors.accent
                )
            }

            IconButton(
                onClick = { track?.let { vm.toggleFavorite(it.id) } },
                enabled = track != null,
                modifier = Modifier.semantics { contentDescription = "Kedvenc" }
            ) {
                Icon(
                    if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    null,
                    tint = if (isFav) colors.secondary else colors.textMuted
                )
            }

            Box {
                IconButton(
                    onClick = { sleepMenu = true },
                    modifier = Modifier.semantics { contentDescription = "Alvó időzítő" }
                ) {
                    Icon(
                        Icons.Default.Timer,
                        null,
                        tint = if (playback.sleepRemainingSec > 0) colors.accent else colors.textMuted
                    )
                }
                DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    AppPreferences.SLEEP_OPTIONS.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (m == 0) "Kikapcsolás"
                                    else "$m perc"
                                )
                            },
                            onClick = {
                                vm.setSleep(m)
                                sleepMenu = false
                            }
                        )
                    }
                }
            }

            Text(
                text = playback.playMode.labelHu(),
                color = colors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        if (playback.sleepRemainingSec > 0) {
            val m = playback.sleepRemainingSec / 60
            val s = playback.sleepRemainingSec % 60
            Text(
                "Alvó időzítő: %d:%02d".format(m, s),
                color = colors.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
