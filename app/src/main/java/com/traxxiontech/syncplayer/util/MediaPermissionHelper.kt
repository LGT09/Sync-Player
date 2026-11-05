package com.traxxiontech.syncplayer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.traxxiontech.syncplayer.ui.theme.LocalGlassmorphicColors

object MediaPermissionHelper {

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.toTypedArray()
    }

    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAudioPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasVideoPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasImagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun MediaPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glassColors = LocalGlassmorphicColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            glassColors.glowColor.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                ),
                RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = glassColors.cardSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glassColors.surfaceGlassSecondary,
                            Color(0xFF101420).copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(glassColors.glowColor.copy(alpha = 0.2f))
                        .border(1.5.dp, glassColors.glowColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PermMedia,
                        contentDescription = "Media Permission",
                        tint = glassColors.glowColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device Media Access",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Grant audio, video & photo permissions to load your full offline library.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("grant_media_permissions_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = glassColors.glowColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable Media Access 🛡️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
