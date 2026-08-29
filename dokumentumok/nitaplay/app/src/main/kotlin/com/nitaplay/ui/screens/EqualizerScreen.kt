package com.nitaplay.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitaplay.data.AppPreferences
import com.nitaplay.player.PlayerUiState
import com.nitaplay.ui.NitaPlayViewModel
import com.nitaplay.ui.UiState
import com.nitaplay.ui.theme.NitaThemeColors

@Composable
fun EqualizerScreen(
    colors: NitaThemeColors,
    ui: UiState,
    playback: PlayerUiState,
    vm: NitaPlayViewModel
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Hangszínszabályzó",
            color = colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (!playback.eqSupported && playback.track != null) {
            Text(
                "Ez a készülék nem támogatja a hangszínszabályzót — a lejátszás ettől még megy.",
                color = colors.textMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card.copy(alpha = 0.5f))
                    .padding(12.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        Text("Gyors profilok", color = colors.textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(AppPreferences.EQ_PROFILES) { profile ->
                val selected = !ui.useCustomEq && ui.eqProfile == profile
                FilterChip(
                    selected = selected,
                    onClick = { vm.setEqProfile(profile) },
                    label = { Text(profile, fontSize = 13.sp) },
                    enabled = playback.eqSupported || playback.track == null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.copy(alpha = 0.3f),
                        selectedLabelColor = colors.accent,
                        containerColor = colors.card.copy(alpha = 0.45f),
                        labelColor = colors.textMuted,
                        disabledContainerColor = colors.card.copy(alpha = 0.2f),
                        disabledLabelColor = colors.textMuted.copy(alpha = 0.4f)
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Curve
        val bands = ui.eqBands
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card.copy(alpha = 0.45f))
                .padding(8.dp)
        ) {
            val path = Path()
            val n = bands.size
            bands.forEachIndexed { i, db ->
                val x = size.width * (i / (n - 1f).coerceAtLeast(1f))
                val y = size.height / 2f - (db / 12f) * (size.height / 2f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = colors.accent,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            // center line
            drawLine(
                colors.textMuted.copy(alpha = 0.3f),
                Offset(0f, size.height / 2f),
                Offset(size.width, size.height / 2f),
                strokeWidth = 1f
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Haladó — 5 sáv (dB)", color = colors.textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        val labels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        bands.forEachIndexed { i, db ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    labels.getOrElse(i) { "Sáv ${i + 1}" },
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(0.28f)
                )
                Slider(
                    value = db,
                    onValueChange = { vm.setEqBand(i, it) },
                    valueRange = -12f..12f,
                    modifier = Modifier.weight(0.55f),
                    enabled = playback.eqSupported || playback.track == null,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.textMuted.copy(alpha = 0.3f),
                        disabledThumbColor = colors.textMuted.copy(alpha = 0.3f),
                        disabledActiveTrackColor = colors.textMuted.copy(alpha = 0.2f)
                    )
                )
                Text(
                    "%+.0f".format(db),
                    color = colors.text,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(0.17f)
                )
            }
        }
    }
}
