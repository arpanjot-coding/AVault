/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.recording

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.IntentCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import com.kitsumed.shizucallrecorder.IShellService
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.CallLogEntry
import com.kitsumed.shizucallrecorder.data.call.CallLogRepository
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.data.recording.RecordingFailure
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuConnectionManager
import com.kitsumed.shizucallrecorder.system.storage.HierarchicalStorageManager
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * RecordingForegroundService is the long-running service that manage the audio-recording logic.
 */
class RecordingForegroundService : Service() {
    companion object {
        // -- Intent action for controlling and initializing the service lifecycle. --

        /** Intent action sent to this service to initialize the service and immediately start a new recording session. */
        const val ACTION_START_RECORDING = "com.kitsumed.shizucallrecorder.START_RECORDING"

        /** Intent action sent to this service to start a new recording session, triggered manually by the user (via notification action or quick settings tile). */
        const val ACTION_MANUAL_START = "com.kitsumed.shizucallrecorder.MANUAL_START"

        /** Intent action sent to this service to put the service in a standby state, waiting for the call to be answered before starting the recording session. */
        const val ACTION_STANDBY = "com.kitsumed.shizucallrecorder.STANDBY"

        /** Intent action sent to this service to pause the current recording session. */
        const val ACTION_PAUSE_RECORDING = "com.kitsumed.shizucallrecorder.PAUSE_RECORDING"

        /** Intent action sent to this service to resume the current recording session. */
        const val ACTION_RESUME_RECORDING = "com.kitsumed.shizucallrecorder.RESUME_RECORDING"

        /** Intent action sent to this service to stop the current recording session and stop the foreground service. */
        const val ACTION_STOP_RECORDING = "com.kitsumed.shizucallrecorder.STOP_RECORDING"

        /** Intent action sent to this service when the user dismisses the ongoing notification. */
        const val ACTION_NOTIFICATION_DISMISSED = "com.kitsumed.shizucallrecorder.NOTIFICATION_DISMISSED"
    }

    // ── Dependencies ──────────────────────────────────────────────────────────
    private lateinit var shizukuManager: ShizukuConnectionManager

    private lateinit var appPreferences: AppPreferences

    private lateinit var recordingPlanResolver: RecordingPlanResolver
    private lateinit var notificationHelper: RecordingNotificationHelper

    private lateinit var overlayController: RecordingOverlayController

    /** IPC stub to the privileged ShellService running in the shell process. */
    private var shellService: IShellService? = null

    /** Scope for service lifecycle operations (binding, etc.) */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var sessionStartTime: Long = 0L

    // ── Recording session state ────────────────────────────────────────────────────────

    /* The current state of the service. */
    private val _serviceState = MutableStateFlow<RecordingServiceState>(RecordingServiceState.Standby(null))

    /* Exposes the current state of the recording service (read-only) for external observation. */
    val serviceState = _serviceState.asStateFlow()

    // ── Service lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationHelper = RecordingNotificationHelper(this)
        notificationHelper.createNotificationChannels()
        overlayController = RecordingOverlayController(this)

        appPreferences = AppPreferences(this)
        recordingPlanResolver = RecordingPlanResolver(this)

        serviceScope.launch(Dispatchers.Main.immediate) {
            var oldState: RecordingServiceState = _serviceState.value
            _serviceState.collect { newState ->
                if (oldState != newState) {
                    updateNotification()
                    notificationHelper.handleStateChangeToasts(oldState, newState)
                    overlayController.showOverlay(newState)
                    oldState = newState
                }
            }
        }

        shizukuManager = ShizukuConnectionManager(this) {
            AppLogger.w("Received callback from ShizukuConnectionManager: Shizuku disconnected unexpectedly. Stopping recording service...")
            if (_serviceState.value.isRecordingActive) {
                notificationHelper.showErrorNotification(getString(R.string.recording_error_shizuku_disconnected_unexpectedly))
                stopRecordingSessionAndService()
            }
        }

        AppLogger.d("RecordingForegroundService initialized")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val state = _serviceState.value

        var currentMeta = state.metadata

        if (intent != null) {
            val newMetadata = IntentCompat.getParcelableExtra(
                intent,
                EnrichedCallData.EXTRA_METADATA,
                EnrichedCallData::class.java
            )
            if (newMetadata != null) {
                currentMeta = newMetadata
            }
        }

        updateNotification()

        when (action) {
            ACTION_START_RECORDING, ACTION_MANUAL_START -> {
                if (state.isRecordingActive) {
                    AppLogger.w("Start request ignored. A session is already on-going.")
                    return START_NOT_STICKY
                }

                if (currentMeta == null) {
                    AppLogger.e("Start request received without metadata. Cannot start recording session.")
                    notificationHelper.showErrorNotification(getString(R.string.recording_unexpected_error))
                    stopRecordingSessionAndService()
                    return START_NOT_STICKY
                }

                _serviceState.update { RecordingServiceState.Starting(currentMeta) }

                tryStartShizukuServer()

                serviceScope.launch {
                    try {
                        ShizukuConnectionManager.waitForServer()
                        val service = shizukuManager.getShellService()
                        shellService = service
                        startNewRecordingSession(service, currentMeta)
                    } catch (e: SecurityException) {
                        AppLogger.e("Shizuku permission was denied / not granted", e)
                        notificationHelper.showErrorNotification(getString(R.string.recording_shizuku_permission_denied))
                        stopRecordingSessionAndService()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e

                        AppLogger.e("Failed to perform ShellService binding with Shizuku.", e)
                        notificationHelper.showErrorNotification(getString(R.string.recording_shizuku_not_started) + "\nLocalized: " + e.localizedMessage)
                        stopRecordingSessionAndService()
                    } finally {
                        _serviceState.update { currentState ->
                            if (currentState is RecordingServiceState.Starting) {
                                RecordingServiceState.Standby(currentMeta)
                            } else {
                                currentState
                            }
                        }
                    }
                }
            }

            ACTION_STANDBY -> {
                _serviceState.update { RecordingServiceState.Standby(currentMeta) }
                serviceScope.launch {
                    if (!appPreferences.isShizukuStartOnRecordEnabled()) {
                        tryStartShizukuServer()
                    }
                    AppLogger.i("Entered standby for ${currentMeta?.direction} call")
                }
            }

            ACTION_PAUSE_RECORDING -> {
                _serviceState.update { currentState ->
                    if (currentState is RecordingServiceState.Active) {
                        currentState.session.pause()
                        currentState.copy(isPaused = true)
                    } else {
                        currentState
                    }
                }
            }

            ACTION_RESUME_RECORDING -> {
                _serviceState.update { currentState ->
                    if (currentState is RecordingServiceState.Active) {
                        currentState.session.resume()
                        currentState.copy(isPaused = false)
                    } else {
                        currentState
                    }
                }
            }

            ACTION_STOP_RECORDING -> stopRecordingSessionAndService()
            ACTION_NOTIFICATION_DISMISSED -> {
                AppLogger.d("Ongoing foreground service notification dismissed by user (Android 14+), reposting.")
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    private fun tryStartShizukuServer() {
        if (appPreferences.isShizukuAutoManageEnabled()) {
            val authKey = appPreferences.getShizukuAuthKey()
            if (authKey.isNotBlank()) {
                ShizukuConnectionManager.startServer(this, authKey)
            } else {
                notificationHelper.showErrorNotification(getString(R.string.recording_shizuku_auth_key_missing))
                notificationHelper.showToast(getString(R.string.recording_shizuku_auth_key_missing))
                stopRecordingSessionAndService()
            }
        }
    }

    override fun onDestroy() {
        AppLogger.v("RecordingForegroundService is destroying... Ensuring cleanup...")
        serviceScope.cancel()
        overlayController.hideOverlay()
        stopRecordingSessionAndService()
        shizukuManager.unbind()
        if (appPreferences.isShizukuAutoManageEnabled() && !appPreferences.isShizukuKeepAliveEnabled()) {
            ShizukuConnectionManager.stopServer(this, appPreferences.getShizukuAuthKey())
        }
        super.onDestroy()
    }

    // ── Service internal logic ───────────────────────────────────────

    private fun startNewRecordingSession(service: IShellService, metadata: EnrichedCallData) {
        if (_serviceState.value.isRecordingActive) {
            AppLogger.w("startNewRecordingSession() called while a session is already active – ignoring")
            return
        }

        val plan = recordingPlanResolver.resolve(metadata)
        AppLogger.i("Resolved final recording plan: $plan")

        sessionStartTime = System.currentTimeMillis()

        val activeSession = try {
            RecordingSession(
                context = this,
                remoteService = service,
                initializationMetadata = metadata,
                plan = plan,
                listener = object : RecordingSession.SessionListener {
                    override fun onSessionFailed(failure: RecordingFailure) {
                        val userFriendlyMessage = getString(failure.descriptionResId)
                        AppLogger.w("Recording session failure: $failure. Message: $userFriendlyMessage")
                        notificationHelper.showErrorNotification(userFriendlyMessage)
                        stopRecordingSessionAndService()
                    }
                }
            )
        } catch (e: RecordingSession.InitializationException) {
            AppLogger.e(e.message ?: "Empty Message.", "Remote recording engine reported: $e")
            notificationHelper.showErrorNotification(e.userFriendlyMessage)
            stopRecordingSessionAndService()
            return
        }

        _serviceState.update {
            RecordingServiceState.Active(
                session = activeSession,
                isPaused = false,
                metadata = metadata
            )
        }

        AppLogger.i("The recording session has been started successfully. Service state updated to Active.")
    }

    private fun stopRecordingSessionAndService() {
        val activeSession = (_serviceState.value as? RecordingServiceState.Active)?.session

        if (activeSession == null) {
            AppLogger.d("No active session, exiting standby state, removing foreground notification and stopping service.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        AppLogger.i("Stopping active recording session, removing foreground notification and stopping service...")
        activeSession.release()

        // Log completed recording into CallLogRepository
        try {
            val durationSeconds = if (sessionStartTime > 0L) {
                ((System.currentTimeMillis() - sessionStartTime) / 1000L).coerceAtLeast(0L)
            } else 0L

            val recordingFile = activeSession.currentRecordingFile
            val rawPath = recordingFile?.absolutePath ?: (activeSession.currentRecordingUri?.path ?: "")
            val rawFile = recordingFile ?: (if (rawPath.isNotEmpty()) File(rawPath) else null)

            // Automatic hardware-backed AES-256-GCM encryption
            val finalFile = if (rawFile != null && rawFile.exists() && appPreferences.isEncryptionEnabled()) {
                val vaultFile = File(rawFile.parentFile, "${rawFile.nameWithoutExtension}.${com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.VAULT_EXTENSION}")
                if (com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.encryptFile(rawFile, vaultFile)) {
                    rawFile.delete()
                    vaultFile
                } else rawFile
            } else rawFile

            val filePath = finalFile?.absolutePath ?: rawPath
            val fileSize = finalFile?.length() ?: 0L
            val meta = activeSession.initializationMetadata
            val contactName = meta.callerName?.takeIf { it.isNotBlank() }
                ?: meta.getBestNumber().takeIf { it.isNotBlank() }
                ?: "Unknown"
            val appType = HierarchicalStorageManager.resolveAppType(meta)
            val direction = if (meta.direction == CallDirection.OUTGOING) "OUTGOING" else "INCOMING"

            val logEntry = CallLogEntry(
                contactName = contactName,
                phoneNumberOrSource = meta.getBestNumber(),
                appType = appType,
                timestamp = if (sessionStartTime > 0) sessionStartTime else System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                filePath = filePath,
                fileSize = fileSize,
                callDirection = direction,
                audioRoute = "Speaker"
            )
            CallLogRepository.getInstance(applicationContext).add(logEntry)
            AppLogger.i("Recorded call logged to repository: $contactName ($appType, ${durationSeconds}s)")

            if (appPreferences.isAutoDeleteEnabled()) {
                val days = appPreferences.getAutoDeleteDays()
                CallLogRepository.getInstance(applicationContext).purgeExpiredRecordings(days)
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to record call into repository", e)
        }

        if (appPreferences.isPostRecordingFileActionsNotificationEnabled()) {
            activeSession.currentRecordingUri?.let { filePathUri ->
                activeSession.initializationMetadata.let { metadata ->
                    val docFile = DocumentFile.fromSingleUri(applicationContext, filePathUri)
                    if (docFile != null && docFile.exists() && docFile.length() > 0) {
                        AppLogger.d("Showing post-recording notification for user actions.")
                        notificationHelper.showPostCallNotification(
                            filePathUri,
                            metadata
                        )
                    }
                }
            }
        }

        _serviceState.update {
            RecordingServiceState.Standby(null)
        }

        AppLogger.i("Recording session stopped and resources released. Stopping foreground service.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val notification = notificationHelper.getServiceNotification(_serviceState.value)
        startForegroundWithType(notification)
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                RecordingNotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                RecordingNotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
    }
}
