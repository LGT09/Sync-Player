package com.traxxiontech.syncplayer.service

import com.traxxiontech.syncplayer.model.SongItem

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlaybackState(
    val currentSong: SongItem? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutesLeft: Int = 0,
    val bassBoostStrength: Short = 0,
    val eqEnabled: Boolean = false
)

open class MusicPlaybackService : MediaPlaybackService()
