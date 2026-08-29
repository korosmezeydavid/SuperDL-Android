package com.nitaplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitaplay.data.LibraryTab
import com.nitaplay.data.MusicSource
import com.nitaplay.data.Track
import com.nitaplay.ui.NitaPlayViewModel
import com.nitaplay.ui.UiState
import com.nitaplay.ui.theme.NitaThemeColors
import com.nitaplay.ui.theme.moodEmoji

@Composable
fun LibraryScreen(
    colors: NitaThemeColors,
    ui: UiState,
    vm: NitaPlayViewModel,
    onPickCloudFolder: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "Könyvtár",
            color = colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        // Source switch
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceChip(
                selected = ui.source == MusicSource.PHONE,
                label = "Telefon",
                icon = Icons.Default.PhoneAndroid,
                colors = colors,
                onClick = { vm.setSource(MusicSource.PHONE) }
            )
            SourceChip(
                selected = ui.source == MusicSource.CLOUD,
                label = "Felhő",
                icon = Icons.Default.Cloud,
                colors = colors,
                onClick = { vm.setSource(MusicSource.CLOUD) }
            )
        }

        if (ui.source == MusicSource.CLOUD) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "mappa: ${ui.cloudFolderName}",
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPickCloudFolder) {
                    Icon(Icons.Default.FolderOpen, null, tint = colors.accent)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (ui.hasCloudFolder) "Mappa cseréje" else "Mappa választása",
                        color = colors.accent
                    )
                }
            }
            Text(
                "A felhős fájlok lejátszáskor töltődnek le — internet és adatforgalom kellhet.",
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = ui.searchQuery,
            onValueChange = { vm.setSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Keresés cím vagy előadó…", color = colors.textMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.textMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.textMuted.copy(alpha = 0.4f),
                cursorColor = colors.accent,
                focusedContainerColor = colors.card.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.card.copy(alpha = 0.35f)
            ),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Tabs
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(LibraryTab.entries.toList(), key = { it.name }) { tab ->
                val label = when (tab) {
                    LibraryTab.TRACKS -> "Számok"
                    LibraryTab.ARTISTS -> "Előadók"
                    LibraryTab.ALBUMS -> "Albumok"
                    LibraryTab.PLAYLISTS -> "Listák"
                    LibraryTab.FAVORITES -> "Kedvencek"
                }
                FilterChip(
                    selected = ui.libraryTab == tab,
                    onClick = { vm.setLibraryTab(tab) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.copy(alpha = 0.25f),
                        selectedLabelColor = colors.accent,
                        containerColor = colors.card.copy(alpha = 0.4f),
                        labelColor = colors.textMuted
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            ui.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (colors.id.name == "DOG") {
                        Text("🐕 csóvál…", color = colors.textMuted, fontSize = 18.sp)
                    } else {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
            }
            ui.libraryTab == LibraryTab.ARTISTS && ui.selectedArtist == null -> {
                val artists = vm.artists()
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(artists, key = { it }) { name ->
                        GroupRow(name, "${ui.tracks.count { (it.artist.ifBlank { "Ismeretlen" }) == name }} szám", colors) {
                            vm.selectArtist(name)
                        }
                    }
                }
            }
            ui.libraryTab == LibraryTab.ALBUMS && ui.selectedAlbum == null -> {
                val albums = vm.albums()
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(albums, key = { it }) { name ->
                        GroupRow(name, "${ui.tracks.count { (it.album.ifBlank { "Ismeretlen album" }) == name }} szám", colors) {
                            vm.selectAlbum(name)
                        }
                    }
                }
            }
            ui.libraryTab == LibraryTab.PLAYLISTS && ui.selectedPlaylistId == null -> {
                var newName by remember { mutableStateOf("") }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Új lista neve", color = colors.textMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.text,
                                unfocusedTextColor = colors.text,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.textMuted.copy(alpha = 0.4f),
                                cursorColor = colors.accent
                            )
                        )
                        TextButton(onClick = {
                            if (newName.isNotBlank()) {
                                vm.createPlaylist(newName)
                                newName = ""
                            }
                        }) { Text("Létrehoz", color = colors.accent) }
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(ui.playlists, key = { it.id }) { p ->
                            GroupRow(p.name, "${p.trackIds.size} szám", colors) {
                                vm.selectPlaylist(p.id)
                            }
                        }
                    }
                }
            }
            ui.filteredTracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (colors.id.name == "CAT") "🐱" else moodEmoji(colors.id),
                            fontSize = 48.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (colors.id.name == "CAT") "Itt még nincs zene."
                            else "Nincs találat.",
                            color = colors.textMuted,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            else -> {
                if (ui.selectedArtist != null || ui.selectedAlbum != null || ui.selectedPlaylistId != null) {
                    TextButton(onClick = {
                        vm.selectArtist(null)
                        vm.selectAlbum(null)
                        vm.selectPlaylist(null)
                    }) {
                        Text("← Vissza", color = colors.accent)
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(ui.filteredTracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            colors = colors,
                            isFavorite = track.id in ui.favoriteIds,
                            showPaw = colors.id.name == "DOG",
                            catPawDivider = colors.id.name == "CAT",
                            onClick = { vm.playTrack(track, ui.filteredTracks) },
                            onFav = { vm.toggleFavorite(track.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: NitaThemeColors,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accent.copy(alpha = 0.3f),
            selectedLabelColor = colors.accent,
            selectedLeadingIconColor = colors.accent,
            containerColor = colors.card.copy(alpha = 0.4f),
            labelColor = colors.textMuted,
            iconColor = colors.textMuted
        )
    )
}

@Composable
private fun GroupRow(title: String, subtitle: String, colors: NitaThemeColors, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    colors: NitaThemeColors,
    isFavorite: Boolean,
    showPaw: Boolean,
    catPawDivider: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card.copy(alpha = 0.5f))
                .clickable(onClick = onClick)
                .semantics { contentDescription = "${track.title}, ${track.displayArtist()}" }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(moodEmoji(colors.id), fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = colors.text,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.displayArtist(),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(track.formatDuration(), color = colors.textMuted, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Kedvenc",
                tint = if (isFavorite) colors.secondary else colors.textMuted.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onFav)
            )
        }
        if (catPawDivider) {
            Text("  🐾  🐾  🐾", color = colors.secondary.copy(alpha = 0.35f), fontSize = 10.sp)
        }
    }
}
