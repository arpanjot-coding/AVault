/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kitsumed.shizucallrecorder.data.call.CallLogEntry
import com.kitsumed.shizucallrecorder.data.call.CallLogRepository
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuConnectionManager
import com.kitsumed.shizucallrecorder.system.openShizukuManager
import com.kitsumed.shizucallrecorder.system.storage.HierarchicalStorageManager
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.kitsumed.shizucallrecorder.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsDashboardScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPreferences = remember { com.kitsumed.shizucallrecorder.data.AppPreferences(context) }
    var isAutoDeleteEnabled by remember { mutableStateOf(appPreferences.isAutoDeleteEnabled()) }

    val repository = remember { CallLogRepository.getInstance(context) }
    val allCalls by repository.entriesFlow.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == com.kitsumed.shizucallrecorder.data.AppPreferences.Key.AUTO_DELETE_ENABLED.id) {
                isAutoDeleteEnabled = appPreferences.isAutoDeleteEnabled()
            }
        }
        appPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            appPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(isAutoDeleteEnabled) {
        if (!isAutoDeleteEnabled && selectedFilter == "Saved") {
            selectedFilter = "All"
        }
    }

    var deleteTargetEntry by remember { mutableStateOf<CallLogEntry?>(null) }
    var currentlyPlayingEntry by remember { mutableStateOf<CallLogEntry?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(0) }

    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
            com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.clearTempPlayback(context)
        }
    }

    // Playback tracking loop
    LaunchedEffect(currentlyPlayingEntry, isPlaying) {
        while (isPlaying && currentlyPlayingEntry != null) {
            try {
                currentPositionMs = mediaPlayer.currentPosition
                totalDurationMs = mediaPlayer.duration.coerceAtLeast(1)
            } catch (_: Exception) {}
            delay(250)
        }
    }

    fun playAudio(entry: CallLogEntry) {
        try {
            val file = entry.getEffectiveFile()
            if (!file.exists()) {
                Toast.makeText(context, "Audio file not found on disk", Toast.LENGTH_SHORT).show()
                return
            }
            if (currentlyPlayingEntry?.id == entry.id && isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
                return
            }
            if (currentlyPlayingEntry?.id == entry.id && !isPlaying) {
                mediaPlayer.start()
                isPlaying = true
                return
            }

            mediaPlayer.reset()
            val playbackFile = if (com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.isEncrypted(file) || file.extension == com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.VAULT_EXTENSION) {
                com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.decryptToTempPlaybackFile(context, file) ?: file
            } else {
                file
            }
            try {
                mediaPlayer.setDataSource(playbackFile.absolutePath)
            } catch (_: Exception) {
                val uri = HierarchicalStorageManager.getShareableUri(context, playbackFile)
                mediaPlayer.setDataSource(context, uri)
            }
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
                com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.clearTempPlayback(context)
            }
            mediaPlayer.start()
            currentlyPlayingEntry = entry
            isPlaying = true
            totalDurationMs = mediaPlayer.duration
        } catch (e: Exception) {
            AppLogger.e("Failed to play recording: ${e.message}", e)
            Toast.makeText(context, "Cannot play audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAudio() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()
        } catch (_: Exception) {}
        com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.clearTempPlayback(context)
        currentlyPlayingEntry = null
        isPlaying = false
        currentPositionMs = 0
        totalDurationMs = 0
    }

    fun shareAudio(entry: CallLogEntry) {
        try {
            val file = entry.getEffectiveFile()
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                return
            }
            val fileToShare = if (com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.isEncrypted(file) || file.extension == com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.VAULT_EXTENSION) {
                val shareDir = File(context.cacheDir, "shared_audio").apply { mkdirs() }
                val tempShare = File(shareDir, "${file.nameWithoutExtension}.ogg")
                if (com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.decryptFile(file, tempShare)) tempShare else file
            } else file

            val uri = HierarchicalStorageManager.getShareableUri(context, fileToShare)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Audio File - ${entry.contactName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share file"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Counts for filter chips
    val savedCount = remember(allCalls) { allCalls.count { it.isBookmarked } }
    val phoneCount = remember(allCalls) { allCalls.count { it.appType.equals("Cellular", true) || it.appType.equals("Phone", true) } }
    val whatsAppCount = remember(allCalls) { allCalls.count { it.appType.contains("WhatsApp", true) } }
    val telegramCount = remember(allCalls) { allCalls.count { it.appType.contains("Telegram", true) } }
    val otherCount = remember(allCalls) { allCalls.size - phoneCount - whatsAppCount - telegramCount }

    // Filtered list
    val filteredCalls = remember(allCalls, selectedFilter, searchQuery) {
        allCalls.filter { entry ->
            val matchesFilter = when (selectedFilter) {
                "Saved" -> entry.isBookmarked
                "Phone" -> entry.appType.equals("Cellular", true) || entry.appType.equals("Phone", true)
                "WhatsApp" -> entry.appType.contains("WhatsApp", true)
                "Telegram" -> entry.appType.contains("Telegram", true)
                "VoIP" -> !entry.appType.equals("Cellular", true)
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                entry.contactName.contains(searchQuery, ignoreCase = true) ||
                        entry.phoneNumberOrSource.contains(searchQuery, ignoreCase = true) ||
                        entry.appType.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    // Group calls by date
    val groupedCalls = remember(filteredCalls) {
        filteredCalls.groupBy { it.getDateGroup() }
    }

    val isShizukuReady = remember {
        ShizukuConnectionManager.isAvailable() && ShizukuConnectionManager.hasPermission(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrikDarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                append("AVAULT")
                                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)) {
                                    append("®")
                                }
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 23.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = BrikTextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isShizukuReady) BrikLavender else BrikAmber,
                            modifier = Modifier.clickable { if (!isShizukuReady) context.openShizukuManager() }
                        ) {
                            Text(
                                text = if (isShizukuReady) "ROOTLESS ACTIVE" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = if (isShizukuReady) BrikOnLavender else BrikOnAmber,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = BrikTextPrimary
                            )
                        }
                        if (appPreferences.isAppLockEnabled()) {
                            IconButton(onClick = { com.kitsumed.shizucallrecorder.system.security.AppLockManager.lock() }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = BrikTextPrimary
                                )
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = BrikTextPrimary
                            )
                        }
                    }
                }

                // Expandable Search Bar
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, number, or app...", color = BrikTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrikLavender) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = BrikTextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrikLavender,
                            unfocusedBorderColor = BrikBorder,
                            focusedContainerColor = BrikDarkCard,
                            unfocusedContainerColor = BrikDarkCard
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )
                }
            }
        },
        bottomBar = {
            // Audio Player Strip when playing
            AnimatedVisibility(
                visible = currentlyPlayingEntry != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                currentlyPlayingEntry?.let { entry ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.contactName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.appType} • ${formatMs(currentPositionMs)} / ${formatMs(totalDurationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalIconButton(onClick = { playAudio(entry) }) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play"
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { stopAudio() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close player")
                                    }
                                }
                            }

                            if (totalDurationMs > 0) {
                                Slider(
                                    value = currentPositionMs.toFloat(),
                                    onValueChange = { newPos ->
                                        currentPositionMs = newPos.toInt()
                                        try {
                                            mediaPlayer.seekTo(newPos.toInt())
                                        } catch (_: Exception) {}
                                    },
                                    valueRange = 0f..totalDurationMs.toFloat(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrikDarkBackground)
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
            ) {
                // Compact Key Metrics Strip (Sleek, minimal, non-intrusive)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = BrikDarkCard,
                            border = BorderStroke(1.dp, BrikBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "SEALED CALLS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = BrikTextSecondary
                                    )
                                    Text(
                                        text = "${allCalls.size} recordings",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = BrikTextPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(BrikLavenderContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = BrikLavender,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = BrikDarkCard,
                            border = BorderStroke(1.dp, BrikBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "HARDWARE VAULT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = BrikTextSecondary
                                    )
                                    Text(
                                        text = if (isAutoDeleteEnabled) "${appPreferences.getAutoDeleteDays()}d purge" else "AES-256 bound",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = BrikMint
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F2B28)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = BrikMint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Filter Chips Row
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selectedFilter == "All") BrikLavender else BrikDarkCard,
                                border = BorderStroke(1.dp, if (selectedFilter == "All") BrikLavender else BrikBorder),
                                modifier = Modifier.clickable { selectedFilter = "All" }
                            ) {
                                Text(
                                    text = "All (${allCalls.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedFilter == "All") BrikOnLavender else BrikTextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                        if (isAutoDeleteEnabled && savedCount > 0) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (selectedFilter == "Saved") BrikAmber else BrikDarkCard,
                                    border = BorderStroke(1.dp, if (selectedFilter == "Saved") BrikAmber else BrikBorder),
                                    modifier = Modifier.clickable { selectedFilter = "Saved" }
                                ) {
                                    Text(
                                        text = "Saved ($savedCount)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (selectedFilter == "Saved") BrikOnAmber else BrikTextSecondary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        if (phoneCount > 0 || allCalls.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (selectedFilter == "Phone") BrikLavender else BrikDarkCard,
                                    border = BorderStroke(1.dp, if (selectedFilter == "Phone") BrikLavender else BrikBorder),
                                    modifier = Modifier.clickable { selectedFilter = "Phone" }
                                ) {
                                    Text(
                                        text = "Phone ($phoneCount)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (selectedFilter == "Phone") BrikOnLavender else BrikTextSecondary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        if (whatsAppCount > 0 || allCalls.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (selectedFilter == "WhatsApp") BrikLavender else BrikDarkCard,
                                    border = BorderStroke(1.dp, if (selectedFilter == "WhatsApp") BrikLavender else BrikBorder),
                                    modifier = Modifier.clickable { selectedFilter = "WhatsApp" }
                                ) {
                                    Text(
                                        text = "WhatsApp ($whatsAppCount)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (selectedFilter == "WhatsApp") BrikOnLavender else BrikTextSecondary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Calls List or Empty Placeholder
                if (filteredCalls.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = BrikDarkCard,
                            border = BorderStroke(1.dp, BrikBorder),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(54.dp),
                                    tint = BrikLavender
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching calls found" else "No conversations saved yet",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = BrikTextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty())
                                        "Try searching with another name or number"
                                    else
                                        "Incoming and outgoing calls will automatically appear here once sealed in vault.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrikTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    groupedCalls.forEach { (dateGroup, callsInGroup) ->
                        item {
                            Text(
                                text = dateGroup,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = BrikTextSecondary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                            )
                        }

                        items(callsInGroup) { entry ->
                            CallLogRow(
                                entry = entry,
                                isPlaying = currentlyPlayingEntry?.id == entry.id && isPlaying,
                                showBookmark = isAutoDeleteEnabled,
                                onPlay = { playAudio(entry) },
                                onShare = { shareAudio(entry) },
                                onDelete = { deleteTargetEntry = entry },
                                onToggleBookmark = {
                                    val isNowSaved = repository.toggleBookmark(entry.id)
                                    if (isNowSaved) {
                                        Toast.makeText(context, "Conversation saved forever (immune to auto-cleanup)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    deleteTargetEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTargetEntry = null },
            title = { Text("Delete entry?") },
            text = { Text("Are you sure you want to delete this entry with ${entry.contactName}? The audio file will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (currentlyPlayingEntry?.id == entry.id) {
                            stopAudio()
                        }
                        repository.delete(entry.id)
                        deleteTargetEntry = null
                        Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CallLogRow(
    entry: CallLogEntry,
    isPlaying: Boolean,
    showBookmark: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isPlaying) BrikDarkCardElevated else BrikDarkCard,
        border = BorderStroke(1.dp, if (isPlaying) BrikLavender else BrikBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Initial with Lavender Theme
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(BrikLavenderContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.getAvatarInitial(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BrikLavender
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Contact Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.contactName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    color = BrikTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val appLabel = when {
                        entry.appType.contains("WhatsApp", true) -> "WhatsApp"
                        entry.appType.contains("Telegram", true) -> "Telegram"
                        else -> "Cellular"
                    }
                    val appColor = when {
                        entry.appType.contains("WhatsApp", true) -> Color(0xFF25D366)
                        entry.appType.contains("Telegram", true) -> Color(0xFF00B0FF)
                        else -> BrikMint
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = appColor, fontWeight = FontWeight.SemiBold)) {
                                append(if (entry.isIncoming()) "↙ $appLabel" else "↗ $appLabel")
                            }
                            withStyle(SpanStyle(color = BrikTextSub)) {
                                append(" • ")
                            }
                            withStyle(SpanStyle(color = BrikTextSecondary)) {
                                append(entry.getFormattedTime())
                            }
                            withStyle(SpanStyle(color = BrikTextSub)) {
                                append(" • ")
                            }
                            withStyle(SpanStyle(color = BrikTextSecondary)) {
                                append(entry.getFormattedDuration())
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (showBookmark) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (entry.isBookmarked) "Saved forever" else "Bookmark conversation",
                        tint = if (entry.isBookmarked) BrikAmber else BrikTextSub,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // High-Contrast Circular Play Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) BrikLavender else BrikDarkCardElevated)
                    .border(1.dp, if (isPlaying) BrikLavender else BrikBorder, CircleShape)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isPlaying) BrikOnLavender else BrikTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Options Overflow Menu
            Box {
                IconButton(
                    onClick = { isMenuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = BrikTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share Audio File") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onShare()
                        }
                    )
                    if (showBookmark) {
                        DropdownMenuItem(
                            text = { Text(if (entry.isBookmarked) "Remove Bookmark" else "Save Permanently") },
                            leadingIcon = { Icon(if (entry.isBookmarked) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, contentDescription = null) },
                            onClick = {
                                isMenuExpanded = false
                                onToggleBookmark()
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            isMenuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
