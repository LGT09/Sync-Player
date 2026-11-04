package com.traxxiontech.syncplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.traxxiontech.syncplayer.MainActivity
import com.traxxiontech.syncplayer.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class MediaPlaybackService : MediaSessionService() {

    protected val binder = LocalBinder()
    protected var player: ExoPlayer? = null
    protected var mediaSession: MediaSession? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var currentSongsList: List<SongItem> = emptyList()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var sleepTimer: CountDownTimer? = null

    companion object {
        private val _playbackState = MutableStateFlow(PlaybackState())
        val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        var activeService: MediaPlaybackService? = null
            protected set
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder? {
        // If bound with our local explicit intent or empty action, provide our local binder
        if (intent?.action == null || intent.action == "LOCAL_BIND") {
            return binder
        }
        return super.onBind(intent)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        activeService = this

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        player?.let { p ->
            mediaSession = MediaSession.Builder(this, p)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()

            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = isPlaying,
                        currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                        durationMs = if (p.duration > 0) p.duration else _playbackState.value.durationMs
                    )
                    if (isPlaying) {
                        startProgressTracker()
                    } else {
                        stopProgressTracker()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val duration = if (p.duration > 0) p.duration else _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        durationMs = duration,
                        currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    )
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentIdx = p.currentMediaItemIndex
                    val currentSong = currentSongsList.getOrNull(currentIdx)
                    _playbackState.value = _playbackState.value.copy(
                        currentSong = currentSong,
                        currentPositionMs = 0L,
                        durationMs = if (p.duration > 0) p.duration else (currentSong?.durationMs ?: 0L)
                    )
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    val mode = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.OFF
                    }
                    _playbackState.value = _playbackState.value.copy(repeatMode = mode)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _playbackState.value = _playbackState.value.copy(isShuffle = shuffleModeEnabled)
                }

                override fun onPlayerError(error: PlaybackException) {
                    error.printStackTrace()
                    // Fallback to next track if corrupted or unreadable
                    playNext()
                }
            })

            initAudioFx(p.audioSessionId)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun initAudioFx(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
            _playbackState.value = _playbackState.value.copy(eqEnabled = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setQueue(songs: List<SongItem>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        currentSongsList = songs
        val validIndex = startIndex.coerceIn(0, songs.lastIndex)
        val p = player ?: return

        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.uri)
                        .build()
                )
                .build()
        }

        p.setMediaItems(mediaItems, validIndex, 0L)
        p.prepare()
        p.play()

        _playbackState.value = _playbackState.value.copy(
            currentSong = songs[validIndex],
            isPlaying = true,
            currentPositionMs = 0L,
            durationMs = songs[validIndex].durationMs
        )
        startProgressTracker()
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE || p.mediaItemCount == 0) {
                if (currentSongsList.isNotEmpty()) {
                    setQueue(currentSongsList, 0)
                    return
                }
            }
            p.play()
        }
    }

    fun playNext() {
        val p = player ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            p.play()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && currentSongsList.isNotEmpty()) {
            p.seekTo(0, 0L)
            p.play()
        }
    }

    fun playPrevious() {
        val p = player ?: return
        if (p.currentPosition > 3000L) {
            p.seekTo(0L)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
            p.play()
        } else {
            p.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = positionMs
        )
    }

    fun toggleRepeat() {
        val p = player ?: return
        val nextMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        p.repeatMode = nextMode
    }

    fun toggleShuffle() {
        val p = player ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }

    fun setPlaybackSpeed(speed: Float) {
        val p = player ?: return
        val clamped = speed.coerceIn(0.25f, 3.0f)
        p.playbackParameters = PlaybackParameters(clamped)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerMinutesLeft = 0)
            return
        }

        _playbackState.value = _playbackState.value.copy(sleepTimerMinutesLeft = minutes)
        sleepTimer = object : CountDownTimer(minutes * 60 * 1000L, 60 * 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minsLeft = (millisUntilFinished / 60000L).toInt() + 1
                _playbackState.value = _playbackState.value.copy(sleepTimerMinutesLeft = minsLeft)
            }

            override fun onFinish() {
                _playbackState.value = _playbackState.value.copy(sleepTimerMinutesLeft = 0)
                player?.pause()
            }
        }.start()
    }

    fun setBassBoost(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
            _playbackState.value = _playbackState.value.copy(bassBoostStrength = strength)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getEqualizer(): Equalizer? = equalizer

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    val dur = if (p.duration > 0) p.duration else _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onDestroy() {
        stopProgressTracker()
        sleepTimer?.cancel()
        equalizer?.release()
        bassBoost?.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        if (activeService === this) {
            activeService = null
        }
        super.onDestroy()
    }
}
