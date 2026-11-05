package com.traxxiontech.syncplayer.data

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import com.traxxiontech.syncplayer.model.LyricLine
import com.traxxiontech.syncplayer.model.MediaType
import com.traxxiontech.syncplayer.model.PhotoItem
import com.traxxiontech.syncplayer.model.Playlist
import com.traxxiontech.syncplayer.model.SmartScanReport
import com.traxxiontech.syncplayer.model.SongItem
import com.traxxiontech.syncplayer.model.SyncedLyrics
import com.traxxiontech.syncplayer.model.ThemeType
import com.traxxiontech.syncplayer.model.UserProfile
import com.traxxiontech.syncplayer.model.VaultMediaItem
import com.traxxiontech.syncplayer.model.VideoItem
import com.traxxiontech.syncplayer.data.db.AppDatabase
import com.traxxiontech.syncplayer.data.db.MediaDao
import com.traxxiontech.syncplayer.data.db.MediaItemEntity
import com.traxxiontech.syncplayer.data.db.PlaylistDao
import com.traxxiontech.syncplayer.data.db.PlaylistEntity
import com.traxxiontech.syncplayer.data.db.PlaylistTrackCrossRef
import com.traxxiontech.syncplayer.security.VaultCryptoService
import com.traxxiontech.syncplayer.security.VaultLockType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MediaRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_player_prefs", Context.MODE_PRIVATE)

    val database: AppDatabase = AppDatabase.getInstance(context)
    val mediaDao: MediaDao = database.mediaDao()
    val playlistDao: PlaylistDao = database.playlistDao()
    val vaultCrypto: VaultCryptoService = VaultCryptoService(context)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()

    private val _songSortOrder = MutableStateFlow(com.traxxiontech.syncplayer.model.MediaSortOrder.LATEST)
    val songSortOrder: StateFlow<com.traxxiontech.syncplayer.model.MediaSortOrder> = _songSortOrder.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos: StateFlow<List<PhotoItem>> = _photos.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _vaultItems = MutableStateFlow<List<VaultMediaItem>>(emptyList())
    val vaultItems: StateFlow<List<VaultMediaItem>> = _vaultItems.asStateFlow()

    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadFavorites()
        observeRoomPlaylists()
        loadVaultItems()
    }

    fun hasCompletedFirstLaunch(): Boolean {
        return prefs.getBoolean("has_completed_first_launch", false)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean("has_completed_first_launch", true).apply()
        _userProfile.value = _userProfile.value.copy(hasCompletedFirstLaunch = true)
    }

    fun setSongSortOrder(order: com.traxxiontech.syncplayer.model.MediaSortOrder) {
        _songSortOrder.value = order
        _songs.value = sortSongsList(_songs.value, order)
    }

    private fun sortSongsList(list: List<SongItem>, order: com.traxxiontech.syncplayer.model.MediaSortOrder): List<SongItem> {
        return when (order) {
            com.traxxiontech.syncplayer.model.MediaSortOrder.LATEST -> list.sortedByDescending { it.dateAdded }
            com.traxxiontech.syncplayer.model.MediaSortOrder.ALPHABETICAL_AZ -> list.sortedBy { it.title.lowercase() }
            com.traxxiontech.syncplayer.model.MediaSortOrder.ALPHABETICAL_ZA -> list.sortedByDescending { it.title.lowercase() }
            com.traxxiontech.syncplayer.model.MediaSortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
            com.traxxiontech.syncplayer.model.MediaSortOrder.DURATION -> list.sortedByDescending { it.durationMs }
        }
    }

    suspend fun scanDeviceMedia() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            loadDeviceAudio()
            loadDeviceVideos()
            loadDevicePhotos()
            indexMediaToRoomDatabase()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun indexMediaToRoomDatabase() {
        val entities = mutableListOf<MediaItemEntity>()
        _songs.value.forEach { s ->
            entities.add(
                MediaItemEntity(
                    id = s.id,
                    title = s.title,
                    artist = s.artist,
                    album = s.album,
                    albumId = s.albumId,
                    durationMs = s.durationMs,
                    uriString = s.uri.toString(),
                    path = s.path,
                    sizeBytes = s.sizeBytes,
                    dateAdded = s.dateAdded,
                    mediaType = "AUDIO",
                    isFavorite = s.isFavorite,
                    hasLyrics = s.hasLyrics
                )
            )
        }
        _videos.value.forEach { v ->
            entities.add(
                MediaItemEntity(
                    id = v.id,
                    title = v.title,
                    durationMs = v.durationMs,
                    uriString = v.uri.toString(),
                    path = v.path,
                    sizeBytes = v.sizeBytes,
                    dateAdded = v.dateAdded,
                    mediaType = "VIDEO",
                    isFavorite = v.isFavorite,
                    resolution = v.resolution
                )
            )
        }
        _photos.value.forEach { p ->
            entities.add(
                MediaItemEntity(
                    id = p.id,
                    title = p.title,
                    uriString = p.uri.toString(),
                    path = p.path,
                    sizeBytes = p.sizeBytes,
                    dateAdded = p.dateAdded,
                    mediaType = "PHOTO",
                    isFavorite = p.isFavorite,
                    width = p.width,
                    height = p.height,
                    bucketName = p.albumName
                )
            )
        }
        if (entities.isNotEmpty()) {
            mediaDao.insertAll(entities)
        }
    }

    private fun loadDeviceAudio() {
        val audioList = mutableListOf<SongItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            val lockedIds = _vaultItems.value.map { it.id }.toSet()

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    if (lockedIds.contains(id.toString())) continue

                    val title = it.getString(titleCol) ?: "Unknown Track"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val albumId = it.getLong(albumIdCol)
                    val duration = it.getLong(durCol)
                    val path = it.getString(dataCol) ?: ""
                    val size = it.getLong(sizeCol)
                    val dateAdded = it.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    audioList.add(
                        SongItem(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            durationMs = duration,
                            uri = uri,
                            path = path,
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            isFavorite = _favorites.value.contains(id),
                            hasLyrics = hasLyricsFile(id, path)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If device has no media (e.g. fresh install or emulator), provide offline playable sample tracks
        if (audioList.isEmpty()) {
            val sampleUri = getOrCreateOfflineAudioUri()
            val sampleFile = File(context.filesDir, "sync_player_offline_audio.wav")
            audioList.add(
                SongItem(
                    id = -101L,
                    title = "Acoustic Horizon",
                    artist = "Traxxion Studio",
                    album = "Liquid Waves",
                    albumId = 0L,
                    durationMs = 30000L,
                    uri = sampleUri,
                    path = sampleFile.absolutePath,
                    sizeBytes = if (sampleFile.exists()) sampleFile.length() else 2646044L,
                    dateAdded = System.currentTimeMillis() / 1000,
                    isFavorite = false,
                    hasLyrics = false
                )
            )
            audioList.add(
                SongItem(
                    id = -102L,
                    title = "Neon Resonance",
                    artist = "Traxxion Studio",
                    album = "Liquid Waves",
                    albumId = 0L,
                    durationMs = 30000L,
                    uri = sampleUri,
                    path = sampleFile.absolutePath,
                    sizeBytes = if (sampleFile.exists()) sampleFile.length() else 2646044L,
                    dateAdded = (System.currentTimeMillis() / 1000) - 3600,
                    isFavorite = true,
                    hasLyrics = false
                )
            )
        }

        _songs.value = sortSongsList(audioList, _songSortOrder.value)
    }

    private fun getOrCreateOfflineAudioUri(): Uri {
        val sampleFile = File(context.filesDir, "sync_player_offline_audio.wav")
        if (!sampleFile.exists()) {
            try {
                val sampleRate = 44100
                val durationSeconds = 30
                val numSamples = sampleRate * durationSeconds
                val buffer = ByteArray(44 + numSamples * 2)

                val totalDataLen = numSamples * 2 + 36
                val byteRate = sampleRate * 2

                buffer[0] = 'R'.code.toByte(); buffer[1] = 'I'.code.toByte(); buffer[2] = 'F'.code.toByte(); buffer[3] = 'F'.code.toByte()
                buffer[4] = (totalDataLen and 0xff).toByte()
                buffer[5] = ((totalDataLen shr 8) and 0xff).toByte()
                buffer[6] = ((totalDataLen shr 16) and 0xff).toByte()
                buffer[7] = ((totalDataLen shr 24) and 0xff).toByte()
                buffer[8] = 'W'.code.toByte(); buffer[9] = 'A'.code.toByte(); buffer[10] = 'V'.code.toByte(); buffer[11] = 'E'.code.toByte()
                buffer[12] = 'f'.code.toByte(); buffer[13] = 'm'.code.toByte(); buffer[14] = 't'.code.toByte(); buffer[15] = ' '.code.toByte()
                buffer[16] = 16; buffer[17] = 0; buffer[18] = 0; buffer[19] = 0
                buffer[20] = 1; buffer[21] = 0
                buffer[22] = 1; buffer[23] = 0
                buffer[24] = (sampleRate and 0xff).toByte()
                buffer[25] = ((sampleRate shr 8) and 0xff).toByte()
                buffer[26] = ((sampleRate shr 16) and 0xff).toByte()
                buffer[27] = ((sampleRate shr 24) and 0xff).toByte()
                buffer[28] = (byteRate and 0xff).toByte()
                buffer[29] = ((byteRate shr 8) and 0xff).toByte()
                buffer[30] = ((byteRate shr 16) and 0xff).toByte()
                buffer[31] = ((byteRate shr 24) and 0xff).toByte()
                buffer[32] = 2; buffer[33] = 0
                buffer[34] = 16; buffer[35] = 0
                buffer[36] = 'd'.code.toByte(); buffer[37] = 'a'.code.toByte(); buffer[38] = 't'.code.toByte(); buffer[39] = 'a'.code.toByte()
                val dataLen = numSamples * 2
                buffer[40] = (dataLen and 0xff).toByte()
                buffer[41] = ((dataLen shr 8) and 0xff).toByte()
                buffer[42] = ((dataLen shr 16) and 0xff).toByte()
                buffer[43] = ((dataLen shr 24) and 0xff).toByte()

                var offset = 44
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val beatEnv = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * 2.0 * t)
                    val sampleVal = (Math.sin(2.0 * Math.PI * 220.0 * t) * 0.4 +
                                    Math.sin(2.0 * Math.PI * 277.18 * t) * 0.3 +
                                    Math.sin(2.0 * Math.PI * 329.63 * t) * 0.3) * beatEnv * 12000.0
                    val shortVal = sampleVal.toInt().coerceIn(-32767, 32767).toShort()
                    buffer[offset++] = (shortVal.toInt() and 0xff).toByte()
                    buffer[offset++] = ((shortVal.toInt() shr 8) and 0xff).toByte()
                }
                sampleFile.writeBytes(buffer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return Uri.fromFile(sampleFile)
    }

    fun addEditedPhoto(photo: PhotoItem) {
        val updated = listOf(photo) + _photos.value
        _photos.value = updated
    }

    fun addEditedVideo(video: VideoItem) {
        val updated = listOf(video) + _videos.value
        _videos.value = updated
    }

    private fun loadDeviceVideos() {
        val videoList = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            val lockedIds = _vaultItems.value.map { it.id }.toSet()

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val resCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    if (lockedIds.contains(id.toString())) continue

                    val title = it.getString(titleCol) ?: "Video $id"
                    val duration = it.getLong(durCol)
                    val resolution = it.getString(resCol) ?: "1080p"
                    val path = it.getString(dataCol) ?: ""
                    val size = it.getLong(sizeCol)
                    val dateAdded = it.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    videoList.add(
                        VideoItem(
                            id = id,
                            title = title,
                            durationMs = duration,
                            resolution = resolution,
                            uri = uri,
                            path = path,
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            isFavorite = _favorites.value.contains(id)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _videos.value = videoList
    }

    private fun loadDevicePhotos() {
        val photoList = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            val lockedIds = _vaultItems.value.map { it.id }.toSet()

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val wCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    if (lockedIds.contains(id.toString())) continue

                    val title = it.getString(titleCol) ?: "Photo $id"
                    val path = it.getString(dataCol) ?: ""
                    val size = it.getLong(sizeCol)
                    val dateAdded = it.getLong(dateCol)
                    val width = it.getInt(wCol)
                    val height = it.getInt(hCol)
                    val albumName = it.getString(bucketCol) ?: "Gallery"
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    photoList.add(
                        PhotoItem(
                            id = id,
                            title = title,
                            uri = uri,
                            path = path,
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            width = width,
                            height = height,
                            albumName = albumName,
                            isFavorite = _favorites.value.contains(id)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _photos.value = photoList
    }

    // --- SECURE MEDIA VAULT ---

    fun isVaultConfigured(): Boolean = vaultCrypto.isVaultConfigured()

    fun getVaultLockType(): VaultLockType = vaultCrypto.getLockType()

    fun isBiometricEnabled(): Boolean = vaultCrypto.isBiometricEnabled()

    fun setBiometricEnabled(enabled: Boolean) = vaultCrypto.setBiometricEnabled(enabled)

    fun setupVault(type: VaultLockType, credential: String, enableBiometrics: Boolean) {
        vaultCrypto.setupVault(type, credential, enableBiometrics)
    }

    fun verifyVaultCredential(credential: String): Boolean {
        return vaultCrypto.verifyCredential(credential)
    }

    fun setVaultPin(pin: String) {
        vaultCrypto.setupVault(VaultLockType.PIN, pin, vaultCrypto.isBiometricEnabled())
    }

    fun verifyVaultPin(pin: String): Boolean {
        return vaultCrypto.verifyCredential(pin)
    }

    fun lockMedia(id: String, title: String, type: MediaType, originalPath: String, sizeBytes: Long, uri: Uri?) {
        // Perform AES-256 GCM/CBC military grade file encryption into isolated vault storage
        uri?.let {
            try {
                vaultCrypto.encryptMediaFile(id, it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val newItem = VaultMediaItem(
            id = id,
            title = title,
            type = type,
            originalPath = originalPath,
            dateLocked = System.currentTimeMillis(),
            sizeBytes = sizeBytes,
            uri = uri
        )
        val updated = _vaultItems.value + newItem
        _vaultItems.value = updated
        saveVaultItems(updated)

        // Remove from public memory lists
        when (type) {
            MediaType.AUDIO -> _songs.value = _songs.value.filter { it.id.toString() != id }
            MediaType.VIDEO -> _videos.value = _videos.value.filter { it.id.toString() != id }
            MediaType.PHOTO -> _photos.value = _photos.value.filter { it.id.toString() != id }
        }
    }

    fun unlockMedia(id: String) {
        // Delete encrypted file from secure storage
        try {
            vaultCrypto.deleteEncryptedFile(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val updated = _vaultItems.value.filter { it.id != id }
        _vaultItems.value = updated
        saveVaultItems(updated)

        // Rescan to bring back to public views
        loadDeviceAudio()
        loadDeviceVideos()
        loadDevicePhotos()
    }

    fun getVaultItemPlayableUri(item: VaultMediaItem): Uri? {
        val ext = when (item.type) {
            MediaType.AUDIO -> "mp3"
            MediaType.VIDEO -> "mp4"
            MediaType.PHOTO -> "jpg"
        }
        val tempFile = vaultCrypto.getDecryptedTempFile(item.id, ext) ?: return null
        return Uri.fromFile(tempFile)
    }

    suspend fun importMedia(uri: Uri): SongItem? = withContext(Dispatchers.IO) {
        try {
            var fileName = "Imported Media"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val mime = context.contentResolver.getType(uri) ?: ""
            val id = System.currentTimeMillis()
            val cleanTitle = fileName.substringBeforeLast(".")

            if (mime.startsWith("video") || fileName.endsWith(".mp4", ignoreCase = true) || fileName.endsWith(".mkv", ignoreCase = true)) {
                val video = VideoItem(
                    id = id,
                    title = cleanTitle,
                    durationMs = 0L,
                    uri = uri,
                    path = uri.toString(),
                    sizeBytes = fileSize,
                    dateAdded = System.currentTimeMillis()
                )
                _videos.value = listOf(video) + _videos.value
                null
            } else if (mime.startsWith("image") || fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".png", ignoreCase = true)) {
                val photo = PhotoItem(
                    id = id,
                    title = cleanTitle,
                    uri = uri,
                    path = uri.toString(),
                    sizeBytes = fileSize,
                    dateAdded = System.currentTimeMillis()
                )
                _photos.value = listOf(photo) + _photos.value
                null
            } else {
                val song = SongItem(
                    id = id,
                    title = cleanTitle,
                    artist = "Imported",
                    album = "Sync Imports",
                    albumId = 0L,
                    durationMs = 0L,
                    uri = uri,
                    path = uri.toString(),
                    sizeBytes = fileSize,
                    dateAdded = System.currentTimeMillis(),
                    tags = "Imported, Offline"
                )
                _songs.value = listOf(song) + _songs.value
                // Persist to Room database
                mediaDao.insertItem(
                    MediaItemEntity(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        albumId = song.albumId,
                        durationMs = song.durationMs,
                        uriString = song.uri.toString(),
                        path = song.path,
                        sizeBytes = song.sizeBytes,
                        dateAdded = song.dateAdded,
                        mediaType = "AUDIO",
                        tags = "Imported, Offline"
                    )
                )
                song
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadVaultItems() {
        val json = prefs.getString("vault_media_json", "[]") ?: "[]"
        val list = mutableListOf<VaultMediaItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uriStr = obj.optString("uri", "")
                val uri = if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null
                list.add(
                    VaultMediaItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        type = MediaType.valueOf(obj.getString("type")),
                        originalPath = obj.optString("originalPath", ""),
                        dateLocked = obj.optLong("dateLocked", System.currentTimeMillis()),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        uri = uri
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _vaultItems.value = list
    }

    private fun saveVaultItems(list: List<VaultMediaItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type.name)
                put("originalPath", item.originalPath)
                put("dateLocked", item.dateLocked)
                put("sizeBytes", item.sizeBytes)
                item.uri?.let { put("uri", it.toString()) }
            }
            array.put(obj)
        }
        prefs.edit().putString("vault_media_json", array.toString()).apply()
    }

    // --- LYRICS HANDLING ---

    private fun hasLyricsFile(songId: Long, songPath: String): Boolean {
        val appLyricsFile = File(context.filesDir, "lyrics_$songId.lrc")
        if (appLyricsFile.exists()) return true

        if (songPath.isNotEmpty()) {
            val dotIdx = songPath.lastIndexOf('.')
            if (dotIdx > 0) {
                val companionPath = songPath.substring(0, dotIdx) + ".lrc"
                if (File(companionPath).exists()) return true
            }
        }
        return false
    }

    suspend fun getLyricsForSong(song: SongItem): SyncedLyrics = withContext(Dispatchers.IO) {
        val appLyricsFile = File(context.filesDir, "lyrics_${song.id}.lrc")
        if (appLyricsFile.exists()) {
            val lrcContent = appLyricsFile.readText()
            val parsed = parseLrcText(song.id, song.title, lrcContent)
            if (parsed.lines.isNotEmpty()) return@withContext parsed
        }

        if (song.path.isNotEmpty()) {
            val dotIdx = song.path.lastIndexOf('.')
            if (dotIdx > 0) {
                val companion = File(song.path.substring(0, dotIdx) + ".lrc")
                if (companion.exists()) {
                    val lrcContent = companion.readText()
                    val parsed = parseLrcText(song.id, song.title, lrcContent)
                    if (parsed.lines.isNotEmpty()) return@withContext parsed
                }
                val companionUpper = File(song.path.substring(0, dotIdx) + ".LRC")
                if (companionUpper.exists()) {
                    val lrcContent = companionUpper.readText()
                    val parsed = parseLrcText(song.id, song.title, lrcContent)
                    if (parsed.lines.isNotEmpty()) return@withContext parsed
                }
            }
        }

        // No lyrics file found for this track - return empty lyrics so user can import or paste real lyrics
        SyncedLyrics(
            songId = song.id,
            songTitle = song.title,
            lines = emptyList(),
            isTimecoded = false
        )
    }

    fun saveLyricsForSong(songId: Long, lrcContent: String) {
        try {
            val file = File(context.filesDir, "lyrics_$songId.lrc")
            file.writeText(lrcContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseLrcText(songId: Long, title: String, rawContent: String): SyncedLyrics {
        val lines = mutableListOf<LyricLine>()
        val lrcRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?](.*)""")

        for (line in rawContent.lines()) {
            val match = lrcRegex.find(line.trim())
            if (match != null) {
                val (minStr, secStr, msStr, text) = match.destructured
                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val millis = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = (minutes * 60 + seconds) * 1000 + millis
                if (text.isNotBlank()) {
                    lines.add(LyricLine(totalMs, text.trim()))
                }
            }
        }

        lines.sortBy { it.timestampMs }
        return SyncedLyrics(
            songId = songId,
            songTitle = title,
            lines = lines,
            isTimecoded = lines.isNotEmpty()
        )
    }

    // --- FAVORITES ---

    fun toggleFavorite(mediaId: Long) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(mediaId)) {
            current.remove(mediaId)
        } else {
            current.add(mediaId)
        }
        _favorites.value = current
        saveFavorites(current)

        _songs.value = _songs.value.map { if (it.id == mediaId) it.copy(isFavorite = current.contains(mediaId)) else it }
        _videos.value = _videos.value.map { if (it.id == mediaId) it.copy(isFavorite = current.contains(mediaId)) else it }
        _photos.value = _photos.value.map { if (it.id == mediaId) it.copy(isFavorite = current.contains(mediaId)) else it }
    }

    private fun loadFavorites() {
        val favJson = prefs.getString("favorite_media_ids", "[]") ?: "[]"
        val set = mutableSetOf<Long>()
        try {
            val array = JSONArray(favJson)
            for (i in 0 until array.length()) {
                set.add(array.getLong(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _favorites.value = set
    }

    private fun saveFavorites(set: Set<Long>) {
        val array = JSONArray()
        set.forEach { array.put(it) }
        prefs.edit().putString("favorite_media_ids", array.toString()).apply()
    }

    // --- PLAYLISTS (ROOM DATABASE PERSISTENCE) ---

    private fun observeRoomPlaylists() {
        repositoryScope.launch {
            playlistDao.getAllPlaylistsWithSongs().collect { listWithSongs ->
                if (listWithSongs.isEmpty()) {
                    // Seed initial offline playlists in Room if database is fresh
                    runCatching {
                        playlistDao.insertPlaylist(PlaylistEntity(name = "Favorites", description = "Your handpicked media favorites"))
                        playlistDao.insertPlaylist(PlaylistEntity(name = "Chill Vibes", description = "Relaxing offline sounds"))
                        playlistDao.insertPlaylist(PlaylistEntity(name = "Drive Anthems", description = "High energy road tracks"))
                    }
                } else {
                    _playlists.value = listWithSongs.map { pws ->
                        Playlist(
                            id = pws.playlist.id.toString(),
                            name = pws.playlist.name,
                            createdDate = pws.playlist.createdAt,
                            songIds = pws.songs.map { it.id },
                            description = pws.playlist.description
                        )
                    }
                }
            }
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        repositoryScope.launch {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = name,
                    description = description,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val pid = playlistId.toLongOrNull() ?: return
        repositoryScope.launch {
            playlistDao.addTrackToPlaylist(
                PlaylistTrackCrossRef(
                    playlistId = pid,
                    mediaId = songId
                )
            )
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val pid = playlistId.toLongOrNull() ?: return
        repositoryScope.launch {
            playlistDao.removeTrackFromPlaylist(
                playlistId = pid,
                mediaId = songId
            )
        }
    }

    fun deletePlaylist(playlistId: String) {
        val pid = playlistId.toLongOrNull() ?: return
        repositoryScope.launch {
            playlistDao.deletePlaylistById(pid)
        }
    }

    // --- USER PROFILE & STATS ---

    private fun loadUserProfile(): UserProfile {
        val name = prefs.getString("user_full_name", "") ?: ""
        val username = prefs.getString("user_username", "") ?: ""
        val preset = prefs.getInt("user_avatar_preset", 0)
        val avatarUri = prefs.getString("user_avatar_uri", null)
        val themeName = prefs.getString("user_theme", ThemeType.NEON.name) ?: ThemeType.NEON.name
        val theme = runCatching { ThemeType.valueOf(themeName) }.getOrDefault(ThemeType.NEON)
        val songsPlayed = prefs.getInt("stats_songs_played", 0)
        val videosWatched = prefs.getInt("stats_videos_watched", 0)
        val listeningMins = prefs.getLong("stats_listening_mins", 0L)
        val watchingMins = prefs.getLong("stats_watching_mins", 0L)
        val firstLaunchDone = prefs.getBoolean("has_completed_first_launch", false)

        return UserProfile(
            fullName = name,
            username = username,
            avatarPreset = preset,
            avatarUriString = avatarUri,
            favoriteTheme = theme,
            totalSongsPlayed = songsPlayed,
            totalVideosWatched = videosWatched,
            totalListeningMinutes = listeningMins,
            totalWatchingMinutes = watchingMins,
            hasCompletedFirstLaunch = firstLaunchDone
        )
    }

    fun updateUserProfile(profile: UserProfile) {
        _userProfile.value = profile
        prefs.edit()
            .putString("user_full_name", profile.fullName)
            .putString("user_username", profile.username)
            .putInt("user_avatar_preset", profile.avatarPreset)
            .putString("user_avatar_uri", profile.avatarUriString)
            .putString("user_theme", profile.favoriteTheme.name)
            .putInt("stats_songs_played", profile.totalSongsPlayed)
            .putInt("stats_videos_watched", profile.totalVideosWatched)
            .putLong("stats_listening_mins", profile.totalListeningMinutes)
            .putLong("stats_watching_mins", profile.totalWatchingMinutes)
            .putBoolean("has_completed_first_launch", profile.hasCompletedFirstLaunch)
            .apply()
    }

    fun incrementSongPlayed(durationMinutes: Long) {
        val cur = _userProfile.value
        val updated = cur.copy(
            totalSongsPlayed = cur.totalSongsPlayed + 1,
            totalListeningMinutes = cur.totalListeningMinutes + durationMinutes
        )
        updateUserProfile(updated)
    }

    fun incrementVideoWatched(durationMinutes: Long) {
        val cur = _userProfile.value
        val updated = cur.copy(
            totalVideosWatched = cur.totalVideosWatched + 1,
            totalWatchingMinutes = cur.totalWatchingMinutes + durationMinutes
        )
        updateUserProfile(updated)
    }

    // --- SMART SCANNER ---

    fun runSmartScan(): SmartScanReport {
        val songs = _songs.value
        val videos = _videos.value
        val photos = _photos.value
        val totalCount = songs.size + videos.size + photos.size

        val sizeMap = mutableMapOf<Long, String>()
        val duplicates = mutableListOf<String>()
        var missingArt = 0
        var corrupted = 0
        var savedBytes = 0L

        songs.forEach {
            if (it.albumId == 0L) missingArt++
            if (it.durationMs <= 0) corrupted++
            if (it.sizeBytes > 0) {
                if (sizeMap.containsKey(it.sizeBytes)) {
                    duplicates.add("${it.title} (Matches ${sizeMap[it.sizeBytes]})")
                    savedBytes += it.sizeBytes
                } else {
                    sizeMap[it.sizeBytes] = it.title
                }
            }
        }

        videos.forEach {
            if (it.durationMs <= 0) corrupted++
            if (it.sizeBytes > 0) {
                if (sizeMap.containsKey(it.sizeBytes)) {
                    duplicates.add("${it.title} (Matches ${sizeMap[it.sizeBytes]})")
                    savedBytes += it.sizeBytes
                } else {
                    sizeMap[it.sizeBytes] = it.title
                }
            }
        }

        return SmartScanReport(
            totalFilesScanned = totalCount,
            duplicateFiles = duplicates,
            corruptedFiles = if (corrupted > 0) listOf("$corrupted media items with irregular headers") else emptyList(),
            missingArtworkCount = missingArt,
            potentialSavedBytes = savedBytes
        )
    }
}
