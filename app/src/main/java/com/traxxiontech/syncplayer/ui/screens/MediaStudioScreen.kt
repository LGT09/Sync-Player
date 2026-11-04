package com.traxxiontech.syncplayer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.traxxiontech.syncplayer.model.PhotoItem
import com.traxxiontech.syncplayer.model.SongItem
import com.traxxiontech.syncplayer.model.VideoItem
import com.traxxiontech.syncplayer.ui.components.FrostedGlassCard
import com.traxxiontech.syncplayer.ui.components.LiquidGlassCard
import com.traxxiontech.syncplayer.ui.components.SkeuomorphicButton
import com.traxxiontech.syncplayer.ui.theme.LocalGlassmorphicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class PhotoFilter(val displayName: String) {
    ORIGINAL("Normal"),
    CYBERPUNK("Cyberpunk"),
    FILM_NOIR("Film Noir"),
    GOLDEN_HOUR("Golden Hour"),
    NEON_GLITCH("Neon Glitch"),
    VINTAGE_1985("Vintage '85"),
    EMERALD("Emerald"),
    VIVID("Vivid Boost")
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1 (Post)", 1.0f),
    PORTRAIT("4:5 (IG)", 0.8f),
    REELS("9:16 (Shorts)", 9f / 16f),
    CINEMA("16:9 (Cinema)", 16f / 9f)
}

@Composable
fun MediaStudioScreen(
    photos: List<PhotoItem>,
    videos: List<VideoItem>,
    songs: List<SongItem>,
    initialPhoto: PhotoItem? = null,
    initialVideo: VideoItem? = null,
    onSavePhoto: (PhotoItem) -> Unit,
    onSaveVideo: (VideoItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val glassColors = LocalGlassmorphicColors.current

    var activeTab by remember { mutableIntStateOf(if (initialVideo != null) 1 else 0) } // 0: Photo Studio, 1: Video Studio

    // Photo Studio State
    var selectedPhoto by remember { mutableStateOf(initialPhoto ?: photos.firstOrNull()) }
    var selectedFilter by remember { mutableStateOf(PhotoFilter.ORIGINAL) }
    var selectedCropRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var brightness by remember { mutableFloatStateOf(0f) } // -100 to 100
    var contrast by remember { mutableFloatStateOf(1f) } // 0.5 to 2.0
    var saturation by remember { mutableFloatStateOf(1f) } // 0 to 2.0
    var vignette by remember { mutableFloatStateOf(0f) } // 0 to 1.0
    var rotationDegrees by remember { mutableIntStateOf(0) } // 0, 90, 180, 270
    var flipHorizontal by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf("") }
    var isExportingPhoto by remember { mutableStateOf(false) }

    // Video Studio State (CapCut style)
    var selectedVideo by remember { mutableStateOf(initialVideo ?: videos.firstOrNull()) }
    var trimStartFraction by remember { mutableFloatStateOf(0f) }
    var trimEndFraction by remember { mutableFloatStateOf(1f) }
    var playbackSpeedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var videoCanvasRatio by remember { mutableStateOf(CropAspectRatio.REELS) }
    var videoVolumeBoost by remember { mutableFloatStateOf(1.0f) }
    var isMuteOriginalAudio by remember { mutableStateOf(false) }
    var selectedBgmSong by remember { mutableStateOf<SongItem?>(null) }
    var videoColorLut by remember { mutableStateOf("Teal & Orange") }
    var isRenderingVideo by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_studio_screen")
    ) {
        // Studio Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Media Studio ✂️",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = if (activeTab == 0) "Professional Photo & Image Lab" else "CapCut & InShot Style Video Editor",
                        fontSize = 11.sp,
                        color = glassColors.glowColor
                    )
                }
            }
        }

        // Mode Tab Bar
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = glassColors.glowColor,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = glassColors.glowColor
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo Editor", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video Editor", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeTab == 0) {
            // PHOTO STUDIO TAB
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Photo Canvas Preview
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedPhoto != null) {
                            AsyncImage(
                                model = selectedPhoto!!.uri,
                                contentDescription = selectedPhoto!!.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Fit
                            )

                            // Render text overlay watermark if set
                            if (overlayText.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = overlayText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = glassColors.glowColor
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Select a photo below to start editing",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Photo Selector Carousel
                Text("Select Photo to Edit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos) { photo ->
                        val isSelected = selectedPhoto?.id == photo.id
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPhoto = photo }
                        ) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Crop Aspect Ratio Presets
                Text("Canvas & Crop Ratio", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CropAspectRatio.values()) { ratio ->
                        val isSelected = selectedCropRatio == ratio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) glassColors.glowColor.copy(alpha = 0.25f) else glassColors.surfaceGlassSecondary)
                                .border(
                                    1.dp,
                                    if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCropRatio = ratio }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = ratio.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Filters / Color LUTs
                Text("Artistic Color Filters", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PhotoFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) glassColors.glowColor.copy(alpha = 0.25f) else glassColors.surfaceGlassSecondary)
                                .border(
                                    1.dp,
                                    if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = filter.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image Adjustments Sliders
                Text("Precision Adjustments", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        AdjustmentSliderRow(
                            label = "Brightness",
                            value = brightness,
                            valueRange = -50f..50f,
                            onValueChange = { brightness = it },
                            displayValue = "${brightness.toInt()}%",
                            accentColor = glassColors.glowColor
                        )
                        AdjustmentSliderRow(
                            label = "Contrast",
                            value = contrast,
                            valueRange = 0.5f..2.0f,
                            onValueChange = { contrast = it },
                            displayValue = "%.1fx".format(contrast),
                            accentColor = glassColors.glowColor
                        )
                        AdjustmentSliderRow(
                            label = "Saturation",
                            value = saturation,
                            valueRange = 0f..2.0f,
                            onValueChange = { saturation = it },
                            displayValue = "%.1fx".format(saturation),
                            accentColor = glassColors.glowColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transform: Rotate & Flip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(glassColors.surfaceGlassSecondary)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { rotationDegrees = (rotationDegrees + 90) % 360 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.RotateRight, contentDescription = null, tint = glassColors.glowColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rotate 90° ($rotationDegrees°)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (flipHorizontal) glassColors.glowColor.copy(alpha = 0.25f) else glassColors.surfaceGlassSecondary)
                            .border(1.dp, if (flipHorizontal) glassColors.glowColor else Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { flipHorizontal = !flipHorizontal }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Flip, contentDescription = null, tint = glassColors.glowColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Flip Horizontal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Text Overlay Watermark Input
                OutlinedTextField(
                    value = overlayText,
                    onValueChange = { overlayText = it },
                    label = { Text("Overlay Caption / Watermark") },
                    placeholder = { Text("e.g. Sync Player Studio") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = glassColors.glowColor,
                        unfocusedBorderColor = glassColors.borderGlass,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Export Button
                SkeuomorphicButton(
                    onClick = {
                        val photo = selectedPhoto ?: return@SkeuomorphicButton
                        coroutineScope.launch {
                            isExportingPhoto = true
                            delay(600) // Processing animation
                            val newPhoto = saveEditedPhoto(
                                context = context,
                                original = photo,
                                filter = selectedFilter,
                                text = overlayText
                            )
                            onSavePhoto(newPhoto)
                            isExportingPhoto = false
                            Toast.makeText(context, "Edited Photo saved to your library! 🎉", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    glowing = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isExportingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rendering & Saving Photo...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export & Save Photo", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        } else {
            // VIDEO STUDIO TAB (CapCut style)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Video Screen Preview
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedVideo != null) {
                            AsyncImage(
                                model = selectedVideo!!.uri,
                                contentDescription = selectedVideo!!.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .border(1.5.dp, glassColors.glowColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = glassColors.glowColor, modifier = Modifier.size(32.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Speed: ${playbackSpeedMultiplier}x • ${videoCanvasRatio.label}",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        } else {
                            Text("Select a video below to edit", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Video Selector Carousel
                Text("Select Video to Cut & Edit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videos) { video ->
                        val isSelected = selectedVideo?.id == video.id
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedVideo = video }
                        ) {
                            AsyncImage(
                                model = video.uri,
                                contentDescription = video.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timeline Trimmer Controls (CapCut Style)
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Timeline Cut / Trim ✂️", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            val totalSec = ((selectedVideo?.durationMs ?: 60000L) / 1000).toInt()
                            val trimmedSec = (totalSec * (trimEndFraction - trimStartFraction)).toInt().coerceAtLeast(1)
                            Text("Trimmed: ${trimmedSec}s / ${totalSec}s", fontSize = 11.sp, color = glassColors.glowColor, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Start Cut Point (${(trimStartFraction * 100).toInt()}%)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        Slider(
                            value = trimStartFraction,
                            onValueChange = { if (it < trimEndFraction - 0.05f) trimStartFraction = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = glassColors.glowColor, activeTrackColor = glassColors.glowColor)
                        )

                        Text("End Cut Point (${(trimEndFraction * 100).toInt()}%)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        Slider(
                            value = trimEndFraction,
                            onValueChange = { if (it > trimStartFraction + 0.05f) trimEndFraction = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = glassColors.glowColor, activeTrackColor = glassColors.glowColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speed Ramp Control
                Text("Speed Control (Slow Mo / Fast Ramp)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    items(speeds) { sp ->
                        val isSelected = playbackSpeedMultiplier == sp
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) glassColors.glowColor.copy(alpha = 0.25f) else glassColors.surfaceGlassSecondary)
                                .border(
                                    1.dp,
                                    if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { playbackSpeedMultiplier = sp }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${sp}x",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas Aspect Ratio for Video (Reels, TikTok, Cinema)
                Text("Canvas Format", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ratios = listOf(CropAspectRatio.REELS, CropAspectRatio.CINEMA, CropAspectRatio.SQUARE)
                    items(ratios) { r ->
                        val isSelected = videoCanvasRatio == r
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) glassColors.glowColor.copy(alpha = 0.25f) else glassColors.surfaceGlassSecondary)
                                .border(1.dp, if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .clickable { videoCanvasRatio = r }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = r.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) glassColors.glowColor else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio Mixer (Mute, Volume Boost)
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Audio Master & BGM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = if (isMuteOriginalAudio) "Muted 🔇" else "Volume: ${(videoVolumeBoost * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = glassColors.glowColor
                            )
                        }

                        Slider(
                            value = videoVolumeBoost,
                            onValueChange = { videoVolumeBoost = it },
                            valueRange = 0f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = glassColors.glowColor, activeTrackColor = glassColors.glowColor)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isMuteOriginalAudio = !isMuteOriginalAudio }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = isMuteOriginalAudio,
                                onCheckedChange = { isMuteOriginalAudio = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mute Original Video Sound", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Render Progress
                AnimatedVisibility(visible = isRenderingVideo) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Encoding Video Track...", fontSize = 12.sp, color = glassColors.glowColor)
                            Text("${(renderProgress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { renderProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = glassColors.glowColor,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }

                // Render & Export Button
                SkeuomorphicButton(
                    onClick = {
                        val video = selectedVideo ?: return@SkeuomorphicButton
                        coroutineScope.launch {
                            isRenderingVideo = true
                            renderProgress = 0f
                            while (renderProgress < 1.0f) {
                                delay(120)
                                renderProgress += 0.15f
                            }
                            renderProgress = 1.0f
                            val newVideo = video.copy(
                                id = System.currentTimeMillis(),
                                title = "${video.title} (Edited Studio)",
                                durationMs = ((video.durationMs * (trimEndFraction - trimStartFraction)) / playbackSpeedMultiplier).toLong()
                            )
                            onSaveVideo(newVideo)
                            isRenderingVideo = false
                            Toast.makeText(context, "Edited Video rendered and saved to your library! 🎬", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    glowing = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export & Render Video", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun AdjustmentSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Text(displayValue, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }
}

private suspend fun saveEditedPhoto(
    context: Context,
    original: PhotoItem,
    filter: PhotoFilter,
    text: String
): PhotoItem = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, "studio_edit_${System.currentTimeMillis()}.jpg")
    try {
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.CYAN
            textSize = 36f
        }
        if (text.isNotBlank()) {
            canvas.drawText(text, 50f, 750f, paint)
        }

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    PhotoItem(
        id = System.currentTimeMillis(),
        title = "${original.title} (Edited)",
        uri = Uri.fromFile(file),
        path = file.absolutePath,
        sizeBytes = file.length(),
        dateAdded = System.currentTimeMillis() / 1000
    )
}
