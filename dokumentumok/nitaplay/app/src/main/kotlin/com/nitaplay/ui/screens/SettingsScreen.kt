package com.nitaplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitaplay.data.AppPreferences
import com.nitaplay.ui.NitaPlayViewModel
import com.nitaplay.ui.UiState
import com.nitaplay.ui.theme.AppThemeId
import com.nitaplay.ui.theme.NitaThemeColors
import com.nitaplay.ui.theme.themeOf

@Composable
fun SettingsScreen(
    colors: NitaThemeColors,
    ui: UiState,
    vm: NitaPlayViewModel,
    onPickCloudFolder: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Beállítások", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionTitle("Téma", colors)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppThemeId.entries.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { id ->
                        ThemeCard(
                            id = id,
                            selected = ui.themeId == id,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setTheme(id) }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Megjelenés", colors)
        SettingSwitch(
            title = "A borító színei",
            subtitle = "A hangsúlyszín a szóló szám borítójából (ha van)",
            checked = ui.useArtworkColors,
            colors = colors,
            onChecked = { vm.setArtworkColors(it) }
        )
        SettingSwitch(
            title = "Animációk",
            subtitle = "Finom átmenetek és lüktető lejátszás-gomb",
            checked = ui.animationsEnabled,
            colors = colors,
            onChecked = { vm.setAnimations(it) }
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Tekerés lépésköze", colors)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppPreferences.SEEK_STEPS.forEach { sec ->
                FilterChip(
                    selected = ui.seekStepSec == sec,
                    onClick = { vm.setSeekStep(sec) },
                    label = {
                        Text(
                            when (sec) {
                                30 -> "30 mp (hangoskönyv)"
                                else -> "$sec mp"
                            },
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.copy(alpha = 0.3f),
                        selectedLabelColor = colors.accent,
                        containerColor = colors.card.copy(alpha = 0.45f),
                        labelColor = colors.textMuted
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Alvó időzítő alapértelmezés", colors)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppPreferences.SLEEP_OPTIONS.forEach { m ->
                FilterChip(
                    selected = ui.defaultSleepMinutes == m,
                    onClick = { vm.setDefaultSleep(m) },
                    label = { Text(if (m == 0) "Nincs" else "$m perc", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.copy(alpha = 0.3f),
                        selectedLabelColor = colors.accent,
                        containerColor = colors.card.copy(alpha = 0.45f),
                        labelColor = colors.textMuted
                    )
                )
            }
        }
        Text(
            "A Most szól képernyőn az időzítő gombbal indítható.",
            color = colors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Felhős mappa", colors)
        Spacer(Modifier.height(6.dp))
        Text("Aktuális: ${ui.cloudFolderName}", color = colors.text, fontSize = 14.sp)
        Row {
            TextButton(onClick = onPickCloudFolder) {
                Text(
                    if (ui.hasCloudFolder) "Mappa cseréje" else "Mappa választása",
                    color = colors.accent
                )
            }
            if (ui.hasCloudFolder) {
                TextButton(onClick = { vm.clearCloudFolder() }) {
                    Text("Törlés", color = colors.secondary)
                }
            }
        }
        Text(
            "Google Drive, OneDrive, Dropbox vagy a telefon tárhelye. " +
                "Egyes Android-verziókon a Drive nem enged mappát választani — " +
                "ilyenkor OneDrive/Dropbox megbízhatóbb.",
            color = colors.textMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "NitaPlay 1.0 — szép zene, lila hangulat.",
            color = colors.textMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SectionTitle(text: String, colors: NitaThemeColors) {
    Text(text, color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: NitaThemeColors,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontWeight = FontWeight.Medium)
            Text(subtitle, color = colors.textMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accent,
                checkedTrackColor = colors.accent.copy(alpha = 0.4f),
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.card
            )
        )
    }
}

@Composable
private fun ThemeCard(
    id: AppThemeId,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val t = themeOf(id)
    Box(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(t.backgroundTop, t.backgroundBottom)))
            .then(
                if (selected) Modifier.border(2.dp, t.accent, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Text(id.labelHu(), color = t.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(t.accent))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(t.secondary))
            }
        }
    }
}
