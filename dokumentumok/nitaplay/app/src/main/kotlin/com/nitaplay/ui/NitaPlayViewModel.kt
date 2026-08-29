package com.nitaplay.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nitaplay.NitaPlayApp
import com.nitaplay.data.AppPreferences
import com.nitaplay.data.CloudMusicStore
import com.nitaplay.data.LibraryTab
import com.nitaplay.data.MusicSource
import com.nitaplay.data.PlayMode
import com.nitaplay.data.Track
import com.nitaplay.player.PlayerUiState
import com.nitaplay.ui.theme.AppThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class UiState(
    val themeId: AppThemeId = AppThemeId.PURPLE_NIGHT,
    val useArtworkColors: Boolean = false,
    val animationsEnabled: Boolean = true,
    val seekStepSec: Int = 10,
    val source: MusicSource = MusicSource.PHONE,
    val libraryTab: LibraryTab = LibraryTab.TRACKS,
    val searchQuery: String = "",
    val tracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val playlists: List<com.nitaplay.data.Playlist> = emptyList(),
    val loading: Boolean = false,
    val cloudFolderName: String = "nincs kiválasztva",
    val hasCloudFolder: Boolean = false,
    val resumeOffer: Pair<Track, Long>? = null,
    val eqProfile: String = AppPreferences.EQ_OFF,
    val eqBands: FloatArray = FloatArray(5) { 0f },
    val useCustomEq: Boolean = false,
    val defaultSleepMinutes: Int = 30,
    val selectedArtist: String? = null,
    val selectedAlbum: String? = null,
    val selectedPlaylistId: String? = null
)

class NitaPlayViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app as NitaPlayApp
    private val prefs = appCtx.prefs
    private val repo = appCtx.musicRepository
    private val favorites = appCtx.favoritesStore
    private val playlists = appCtx.playlistStore
    val player = appCtx.playerController

    private val _ui = MutableStateFlow(
        UiState(
            themeId = prefs.themeId,
            useArtworkColors = prefs.useArtworkColors,
            animationsEnabled = prefs.animationsEnabled,
            seekStepSec = prefs.seekStepSec,
            source = prefs.musicSource,
            eqProfile = prefs.eqProfile,
            eqBands = prefs.eqBands,
            useCustomEq = prefs.useCustomEq,
            defaultSleepMinutes = prefs.defaultSleepMinutes
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _playback = MutableStateFlow(PlayerUiState())
    val playback: StateFlow<PlayerUiState> = _playback.asStateFlow()

    init {
        player.bind()
        viewModelScope.launch {
            // Poll service state
            while (true) {
                val svcState = player.playbackState
                _playback.value = svcState.value
                kotlinx.coroutines.delay(300)
            }
        }
        viewModelScope.launch {
            combine(repo.phoneTracks, repo.cloudTracks, repo.loading) { phone, cloud, loading ->
                Triple(phone, cloud, loading)
            }.collect { (phone, cloud, loading) ->
                refreshTracks(phone, cloud, loading)
            }
        }
        refreshMeta()
        // A médiatár betöltését a MainActivity indítja az engedély megadása után,
        // így itt nem töltünk feleslegesen (elkerüli a korábbi dupla/hármas betöltést).
        // A felhős mappát viszont egyszer előtöltjük, hogy a "Folytatás" ajánlat
        // felhős számnál is működjön, és a Felhő fül gyorsan kész legyen.
        if (CloudMusicStore.hasFolder(getApplication())) {
            viewModelScope.launch { repo.loadCloud() }
        }
    }

    private var resumeChecked = false

    private fun refreshTracks(phone: List<Track>, cloud: List<Track>, loading: Boolean) {
        val source = _ui.value.source
        val tracks = if (source == MusicSource.PHONE) phone else cloud
        _ui.value = _ui.value.copy(
            tracks = tracks,
            filteredTracks = applyFilter(tracks, _ui.value),
            loading = loading
        )
        maybeOfferResume(phone, cloud)
    }

    private fun maybeOfferResume(phone: List<Track>, cloud: List<Track>) {
        if (resumeChecked) return
        val id = prefs.getLastTrackId()
        val pos = prefs.getLastPositionMs()
        if (id < 0 || pos < 3000L) {
            resumeChecked = true
            return
        }
        val track = phone.find { it.id == id } ?: cloud.find { it.id == id } ?: return
        resumeChecked = true
        _ui.value = _ui.value.copy(resumeOffer = track to pos)
    }

    private fun applyFilter(tracks: List<Track>, state: UiState): List<Track> {
        var list = tracks
        when (state.libraryTab) {
            LibraryTab.FAVORITES -> list = list.filter { it.id in state.favoriteIds }
            LibraryTab.ARTISTS -> {
                state.selectedArtist?.let { a ->
                    list = list.filter { it.artist.ifBlank { "Ismeretlen" } == a }
                }
            }
            LibraryTab.ALBUMS -> {
                state.selectedAlbum?.let { a ->
                    list = list.filter { it.album.ifBlank { "Ismeretlen album" } == a }
                }
            }
            LibraryTab.PLAYLISTS -> {
                state.selectedPlaylistId?.let { pid ->
                    val ids = state.playlists.find { it.id == pid }?.trackIds?.toSet() ?: emptySet()
                    list = list.filter { it.id in ids }
                }
            }
            LibraryTab.TRACKS -> Unit
        }
        val q = state.searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
        return list
    }

    private fun refreshMeta() {
        val ctx = getApplication<Application>()
        _ui.value = _ui.value.copy(
            favoriteIds = favorites.getIds(),
            playlists = playlists.getAll(),
            cloudFolderName = CloudMusicStore.folderName(ctx),
            hasCloudFolder = CloudMusicStore.hasFolder(ctx)
        )
    }

    fun loadCurrentSource() {
        viewModelScope.launch {
            when (_ui.value.source) {
                MusicSource.PHONE -> repo.loadPhone()
                MusicSource.CLOUD -> repo.loadCloud()
            }
        }
    }

    fun setSource(source: MusicSource) {
        prefs.musicSource = source
        _ui.value = _ui.value.copy(source = source, selectedArtist = null, selectedAlbum = null)
        loadCurrentSource()
    }

    fun setLibraryTab(tab: LibraryTab) {
        _ui.value = _ui.value.copy(
            libraryTab = tab,
            selectedArtist = null,
            selectedAlbum = null,
            selectedPlaylistId = null
        ).let { it.copy(filteredTracks = applyFilter(it.tracks, it)) }
    }

    fun setSearch(q: String) {
        _ui.value = _ui.value.copy(searchQuery = q).let {
            it.copy(filteredTracks = applyFilter(it.tracks, it))
        }
    }

    fun selectArtist(name: String?) {
        _ui.value = _ui.value.copy(selectedArtist = name).let {
            it.copy(filteredTracks = applyFilter(it.tracks, it))
        }
    }

    fun selectAlbum(name: String?) {
        _ui.value = _ui.value.copy(selectedAlbum = name).let {
            it.copy(filteredTracks = applyFilter(it.tracks, it))
        }
    }

    fun selectPlaylist(id: String?) {
        _ui.value = _ui.value.copy(selectedPlaylistId = id).let {
            it.copy(filteredTracks = applyFilter(it.tracks, it))
        }
    }

    fun playTrack(track: Track, fromList: List<Track> = _ui.value.filteredTracks) {
        val list = if (fromList.isEmpty()) listOf(track) else fromList
        val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.play(list, idx)
        _ui.value = _ui.value.copy(resumeOffer = null)
    }

    fun playAll(list: List<Track>) {
        if (list.isEmpty()) return
        player.play(list, 0)
    }

    fun toggleFavorite(id: Long) {
        favorites.toggle(id)
        refreshMeta()
        _ui.value = _ui.value.copy(filteredTracks = applyFilter(_ui.value.tracks, _ui.value))
    }

    fun createPlaylist(name: String) {
        playlists.create(name)
        refreshMeta()
    }

    fun addToPlaylist(playlistId: String, trackId: Long) {
        playlists.addTrack(playlistId, trackId)
        refreshMeta()
    }

    fun deletePlaylist(id: String) {
        playlists.delete(id)
        refreshMeta()
    }

    fun setTheme(id: AppThemeId) {
        prefs.themeId = id
        _ui.value = _ui.value.copy(themeId = id)
    }

    fun setArtworkColors(on: Boolean) {
        prefs.useArtworkColors = on
        _ui.value = _ui.value.copy(useArtworkColors = on)
    }

    fun setAnimations(on: Boolean) {
        prefs.animationsEnabled = on
        _ui.value = _ui.value.copy(animationsEnabled = on)
    }

    fun setSeekStep(sec: Int) {
        prefs.seekStepSec = sec
        _ui.value = _ui.value.copy(seekStepSec = sec)
    }

    fun cyclePlayMode() {
        val next = (_playback.value.playMode).next()
        player.setPlayMode(next)
    }

    fun setEqProfile(name: String) {
        prefs.eqProfile = name
        prefs.useCustomEq = false
        player.applyEqProfile(name)
        _ui.value = _ui.value.copy(eqProfile = name, useCustomEq = false)
    }

    fun setEqBand(index: Int, db: Float) {
        val bands = _ui.value.eqBands.copyOf()
        if (index in bands.indices) bands[index] = db
        prefs.eqBands = bands
        prefs.useCustomEq = true
        player.applyEqBands(bands)
        _ui.value = _ui.value.copy(eqBands = bands, useCustomEq = true)
    }

    fun setSleep(minutes: Int) {
        if (minutes <= 0) player.cancelSleepTimer()
        else player.setSleepTimer(minutes)
    }

    fun onCloudFolderPicked(uri: Uri) {
        val ctx = getApplication<Application>()
        CloudMusicStore.saveFolder(ctx, uri)
        refreshMeta()
        if (_ui.value.source == MusicSource.CLOUD) loadCurrentSource()
    }

    fun clearCloudFolder() {
        CloudMusicStore.clearFolder(getApplication())
        refreshMeta()
        if (_ui.value.source == MusicSource.CLOUD) loadCurrentSource()
    }

    fun setDefaultSleep(minutes: Int) {
        prefs.defaultSleepMinutes = minutes
        _ui.value = _ui.value.copy(defaultSleepMinutes = minutes)
    }

    fun dismissResume() {
        _ui.value = _ui.value.copy(resumeOffer = null)
    }

    fun acceptResume() {
        val offer = _ui.value.resumeOffer ?: return
        val all = repo.tracksFor(offer.first.source)
        val list = if (all.any { it.id == offer.first.id }) all else listOf(offer.first)
        player.play(list, list.indexOfFirst { it.id == offer.first.id }.coerceAtLeast(0))
        _ui.value = _ui.value.copy(resumeOffer = null)
    }

    fun artists(): List<String> =
        _ui.value.tracks.map { it.artist.ifBlank { "Ismeretlen" } }.distinct().sorted()

    fun albums(): List<String> =
        _ui.value.tracks.map { it.album.ifBlank { "Ismeretlen album" } }.distinct().sorted()

    override fun onCleared() {
        // keep service running
        super.onCleared()
    }
}
