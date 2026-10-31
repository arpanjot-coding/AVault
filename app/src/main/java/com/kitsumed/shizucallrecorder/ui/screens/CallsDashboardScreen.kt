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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Calls",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val summaryText = if (allCalls.isEmpty()) {
                            "No conversations saved"
                        } else {
                            "${allCalls.size} ${if (allCalls.size == 1) "conversation" else "conversations"} saved"
                        }

                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (appPreferences.isAppLockEnabled()) {
                            IconButton(onClick = { com.kitsumed.shizucallrecorder.system.security.AppLockManager.lock() }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Engine Health Indicator Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { if (!isShizukuReady) context.openShizukuManager() }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isShizukuReady) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isShizukuReady) "Ready • Shizuku Connected" else "Shizuku Not Running (Tap to Start)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isShizukuReady) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
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
                        placeholder = { Text("Search by name, number, or app...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == "All",
                            onClick = { selectedFilter = "All" },
                            label = { Text("All (${allCalls.size})") },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                    if (isAutoDeleteEnabled) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "Saved",
                                onClick = { selectedFilter = "Saved" },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectedFilter == "Saved") MaterialTheme.colorScheme.primary else Color(0xFFF9A825)
                                    )
                                },
                                label = { Text("Saved ($savedCount)") },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                    if (phoneCount > 0 || allCalls.isEmpty()) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "Phone",
                                onClick = { selectedFilter = "Phone" },
                                label = { Text("Phone ($phoneCount)") },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                    if (whatsAppCount > 0 || allCalls.isEmpty()) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "WhatsApp",
                                onClick = { selectedFilter = "WhatsApp" },
                                label = { Text("WhatsApp ($whatsAppCount)") },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                    if (telegramCount > 0) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "Telegram",
                                onClick = { selectedFilter = "Telegram" },
                                label = { Text("Telegram ($telegramCount)") },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
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
        if (filteredCalls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching calls found" else "No conversations saved yet",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty())
                            "Try searching with another contact name or phone number"
                        else
                            "Incoming and outgoing calls will automatically appear here once saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
            ) {
                groupedCalls.forEach { (dateGroup, callsInGroup) ->
                    item {
                        Text(
                            text = dateGroup,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                callsInGroup.forEachIndexed { index, entry ->
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
                                    if (index < callsInGroup.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
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

    val avatarColor = remember(entry.contactName) {
        val colors = listOf(
            Color(0xFF3F51B5), Color(0xFF009688), Color(0xFF673AB7),
            Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFFFF5722)
        )
        val hash = entry.contactName.hashCode()
        colors[Math.abs(hash) % colors.size]
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Initial
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.getAvatarInitial(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Contact Info Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.contactName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Direction Arrow
                Text(
                    text = if (entry.isIncoming()) "↙" else "↗",
                    color = if (entry.isIncoming()) Color(0xFF4CAF50) else Color(0xFF2196F3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))

                // App & Route Badge
                val appLabel = when {
                    entry.appType.contains("WhatsApp", true) -> "WhatsApp"
                    entry.appType.contains("Telegram", true) -> "Telegram"
                    else -> "Cellular"
                }
                val appColor = when {
                    entry.appType.contains("WhatsApp", true) -> Color(0xFF25D366)
                    entry.appType.contains("Telegram", true) -> Color(0xFF00B0FF)
                    else -> Color(0xFF4A90E2)
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = appColor, fontWeight = FontWeight.Medium)) {
                            append(appLabel)
                        }
                        withStyle(SpanStyle(color = Color(0xFF6E7480))) {
                            append(" • ")
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(entry.getFormattedTime())
                        }
                        withStyle(SpanStyle(color = Color(0xFF6E7480))) {
                            append(" • ")
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(entry.getFormattedDuration())
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (showBookmark) {
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (entry.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (entry.isBookmarked) "Saved forever" else "Bookmark conversation",
                    tint = if (entry.isBookmarked) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Circular Play Button matching call_log_final.png
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onPlay() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 3-Dots Menu
        Box {
            IconButton(onClick = { isMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false }
            ) {
                // Header in menu
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(entry.contactName, fontWeight = FontWeight.Bold)
                            Text(
                                "${entry.appType} • ${entry.getFormattedDuration()} • ${entry.getFormattedTime()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {},
                    enabled = false
                )
                HorizontalDivider()
                if (showBookmark) {
                    DropdownMenuItem(
                        text = { Text(if (entry.isBookmarked) "Remove bookmark" else "Bookmark (Keep forever)") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (entry.isBookmarked) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color(0xFFF9A825)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onToggleBookmark()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = {
                        isMenuExpanded = false
                        onPlay()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share audio") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        isMenuExpanded = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
