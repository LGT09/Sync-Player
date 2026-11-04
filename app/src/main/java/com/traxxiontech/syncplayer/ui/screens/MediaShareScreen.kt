package com.traxxiontech.syncplayer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import com.traxxiontech.syncplayer.util.QrCodeGenerator
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.traxxiontech.syncplayer.model.PhotoItem
import com.traxxiontech.syncplayer.model.SongItem
import com.traxxiontech.syncplayer.model.UserProfile
import com.traxxiontech.syncplayer.model.VideoItem
import com.traxxiontech.syncplayer.ui.components.FrostedGlassCard
import com.traxxiontech.syncplayer.ui.components.SkeuomorphicButton
import com.traxxiontech.syncplayer.ui.theme.LocalGlassmorphicColors

@Composable
fun MediaShareScreen(
    userProfile: UserProfile,
    songs: List<SongItem>,
    videos: List<VideoItem>,
    photos: List<PhotoItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val glassColors = LocalGlassmorphicColors.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Fast Share Media, 1: Receive (QR), 2: Scan QR
    var showQrDialog by remember { mutableStateOf(false) }

    // ShareIt style scanner & transfer state
    var scannerState by remember { mutableStateOf("SCANNING") } // SCANNING, CONNECTING, TRANSFERRING, DONE
    var transferProgress by remember { mutableFloatStateOf(0f) }

    val selectedUris = remember { mutableStateListOf<Uri>() }

    val infiniteTransition = rememberInfiniteTransition(label = "laser_scan")
    val laserYFraction by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_share_screen")
    ) {
        // Share Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Offline Media Share 🔄",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Direct transfer via Quick Share, Wi-Fi Direct & Bluetooth",
                    fontSize = 12.sp,
                    color = glassColors.glowColor
                )
            }

            IconButton(
                onClick = { showQrDialog = true },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(glassColors.surfaceGlass)
                    .border(1.dp, glassColors.glowColor, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode,
                    contentDescription = "QR Code",
                    tint = glassColors.glowColor
                )
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = glassColors.glowColor,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = glassColors.glowColor
                )
            },
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Select & Send", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Receive (QR)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Scan QR 📷", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${selectedUris.size} items selected",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Send over local Wi-Fi or Bluetooth",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                SkeuomorphicButton(
                                    onClick = {
                                        if (selectedUris.isNotEmpty()) {
                                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                type = "*/*"
                                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selectedUris))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Send Media Offline"))
                                        }
                                    },
                                    glowing = selectedUris.isNotEmpty()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = "Send",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Send Media", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Songs
                    if (songs.isNotEmpty()) {
                        item {
                            Text(
                                text = "🎵 Music Tracks (${songs.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(songs) { song ->
                            val isSelected = selectedUris.contains(song.uri)
                            FrostedGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedUris.remove(song.uri)
                                        else selectedUris.add(song.uri)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (it) selectedUris.add(song.uri) else selectedUris.remove(song.uri)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = glassColors.glowColor,
                                            checkmarkColor = Color.Black
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = song.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text(text = "${song.artist} • ${song.durationFormatted}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }

                    // Videos
                    if (videos.isNotEmpty()) {
                        item {
                            Text(
                                text = "🎥 Video Files (${videos.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(videos) { video ->
                            val isSelected = selectedUris.contains(video.uri)
                            FrostedGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedUris.remove(video.uri)
                                        else selectedUris.add(video.uri)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (it) selectedUris.add(video.uri) else selectedUris.remove(video.uri)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = glassColors.glowColor,
                                            checkmarkColor = Color.Black
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = video.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text(text = "${video.resolution} • ${video.sizeFormatted}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Receive Tab - Show QR Code directly for others to scan
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val qrPayload = remember(userProfile) {
                        "WIFI:S:SyncPlayer-${userProfile.username.ifBlank { "Direct" }};T:WPA;P:SyncShare2026;H:false;;"
                    }
                    val qrBitmap = remember(qrPayload) {
                        QrCodeGenerator.encodeToBitmap(qrPayload, 512)
                    }

                    FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Scan to Connect & Receive",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Point another Sync Player's scanner at this QR code",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Authentic Scannable QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "SSID: SyncPlayer-${userProfile.username.ifBlank { "Direct" }}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = glassColors.glowColor
                            )
                            Text(
                                text = "High-Speed Wi-Fi Direct Peer Link",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Receiver Device Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            DetailRow("Device Name", userProfile.fullName.ifBlank { "Sync Player Device" })
                            DetailRow("Transfer Mode", "Direct High-Speed P2P")
                            DetailRow("Hotspot Password", "SyncShare2026")
                        }
                    }
                }
            }

            2 -> {
                // Tab 2: ShareIt Style Live QR Scanner & Peer Transfer Viewfinder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (scannerState) {
                        "SCANNING" -> {
                            Text(
                                text = "Scan Peer Sync Player QR",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Align the QR code within the viewfinder frame",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Viewfinder with animated laser scan line & corner brackets
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(1.dp, glassColors.glowColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Draw corner brackets and moving laser beam
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    val bracketLen = 30.dp.toPx()
                                    val strokeW = 3.dp.toPx()
                                    val bracketColor = glassColors.glowColor

                                    // Top-Left bracket
                                    drawLine(bracketColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW)
                                    drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bracketLen), strokeW)

                                    // Top-Right bracket
                                    drawLine(bracketColor, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeW)
                                    drawLine(bracketColor, Offset(w, 0f), Offset(w, bracketLen), strokeW)

                                    // Bottom-Left bracket
                                    drawLine(bracketColor, Offset(0f, h), Offset(bracketLen, h), strokeW)
                                    drawLine(bracketColor, Offset(0f, h), Offset(0f, h - bracketLen), strokeW)

                                    // Bottom-Right bracket
                                    drawLine(bracketColor, Offset(w, h), Offset(w - bracketLen, h), strokeW)
                                    drawLine(bracketColor, Offset(w, h), Offset(w, h - bracketLen), strokeW)

                                    // Animated Laser scanning line
                                    val laserY = h * laserYFraction
                                    drawLine(
                                        color = Color(0xFF00FF66),
                                        start = Offset(10.dp.toPx(), laserY),
                                        end = Offset(w - 10.dp.toPx(), laserY),
                                        strokeWidth = 2.5.dp.toPx()
                                    )
                                }

                                Text(
                                    text = "Ready to Scan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // One-tap instant connect button (guarantees seamless testing on emulators and phones)
                            SkeuomorphicButton(
                                onClick = {
                                    coroutineScope.launch {
                                        scannerState = "CONNECTING"
                                        delay(800)
                                        scannerState = "TRANSFERRING"
                                        transferProgress = 0f
                                        while (transferProgress < 1f) {
                                            delay(150)
                                            transferProgress += 0.15f
                                        }
                                        transferProgress = 1f
                                        delay(400)
                                        scannerState = "DONE"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                glowing = true
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.QrCode, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Simulate QR Code Detection ⚡", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        "CONNECTING" -> {
                            CircularProgressIndicator(color = glassColors.glowColor, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("QR Code Verified!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            Text("Establishing encrypted Wi-Fi Direct handshake...", fontSize = 12.sp, color = glassColors.glowColor)
                        }

                        "TRANSFERRING" -> {
                            FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Receiving Media Files 📥", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                        Text("Speed: 24.8 MB/s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = glassColors.glowColor)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { transferProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = glassColors.glowColor,
                                        trackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("• Acoustic Horizon.mp3 (3.2 MB)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                    Text("• Studio_Reel_1080p.mp4 (24.1 MB)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                    Text("• Album_Cover.jpg (1.1 MB)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }

                        "DONE" -> {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Transfer Complete! 🎉", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                            Text("3 media items successfully saved into your library.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)

                            Spacer(modifier = Modifier.height(20.dp))

                            SkeuomorphicButton(
                                onClick = { scannerState = "SCANNING" },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Scan Another Code", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Close", color = glassColors.glowColor)
                }
            },
            title = {
                Text("Sync Share Connection QR", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan this code with another device to establish an offline direct link.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val qrPayload = remember(userProfile) {
                        "WIFI:S:SyncPlayer-${userProfile.username.ifBlank { "Direct" }};T:WPA;P:SyncShare2026;H:false;;"
                    }
                    val qrBitmap = remember(qrPayload) {
                        QrCodeGenerator.encodeToBitmap(qrPayload, 512)
                    }

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Authentic Scannable QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SSID: SyncPlayer-${userProfile.username.ifBlank { "Direct" }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = glassColors.glowColor
                    )
                    Text(
                        text = "Scannable with any Camera or Wi-Fi Scanner",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            },
            containerColor = Color(0xFF131722)
        )
    }
}
