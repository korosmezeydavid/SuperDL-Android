package com.superdl.launcher.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFolderPolicyTest {
    private val root = "/storage/emulated/0/music/"
    private val books = listOf("/storage/emulated/0/books/")

    @Test fun musicFolderWinsOverMediaStoreFlags() {
        assertTrue(MusicHelper.isMusicCandidate(
            "${root}album/track.mp3", root, books,
            isMusic = false, isPodcast = true, isAudiobook = true
        ))
    }

    @Test fun booksFolderNeverEntersMusicLibrary() {
        assertFalse(MusicHelper.isMusicCandidate(
            "/storage/emulated/0/books/chapter.mp3", root, books,
            isMusic = true, isPodcast = false, isAudiobook = false
        ))
    }

    @Test fun otherFoldersStillRespectMediaStoreType() {
        assertFalse(MusicHelper.isMusicCandidate(
            "/storage/emulated/0/download/podcast.mp3", root, books,
            isMusic = true, isPodcast = true, isAudiobook = false
        ))
        assertTrue(MusicHelper.isMusicCandidate(
            "/storage/emulated/0/download/song.mp3", root, books,
            isMusic = true, isPodcast = false, isAudiobook = false
        ))
    }
}
