package com.yaris.hvfan.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yaris.hvfan.MainActivity
import com.yaris.hvfan.R
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.ble.BleManager
import com.yaris.hvfan.data.AppPreferences
import com.yaris.hvfan.obd.ObdController
import kotlinx.coroutines.*

class FanControlForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "yaris_hv_fan_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.yaris.hvfan.START"
        const val ACTION_STOP = "com.yaris.hvfan.STOP"

        var isRunning: Boolean = false
            private set
    }

    inner class LocalBinder : Binder() {
        val service: FanControlForegroundService get() = this@FanControlForegroundService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    lateinit var bleManager: BleManager
        private set
    lateinit var obdController: ObdController
        private set
    lateinit var appPreferences: AppPreferences
        private set

    @SuppressLint("WakelockTimeout")
    private fun updateWakeLock(isConnected: Boolean) {
        if (isConnected) {
            if (wakeLock == null || wakeLock?.isHeld != true) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YarisHvFan::CpuWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                    Log.i("HvFanService", "CPU WakeLock acquisito: protezione termica batteria HV attiva in background")
                }
            }
        } else {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i("HvFanService", "CPU WakeLock rilasciato (auto disconnessa, risparmio batteria smartphone)")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(this)
        bleManager = BleManager(this)
        obdController = ObdController(bleManager, serviceScope, appPreferences)
        obdController.setTargetThreshold(appPreferences.targetTempThreshold)
        obdController.setForcedFan(appPreferences.forcedFanSpeed == 6)

        createNotificationChannel()
        obdController.startController()
        observeStateForNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                isRunning = true
                startAsForeground()
                // Auto connect to saved MAC if available
                val savedMac = appPreferences.savedMacAddress
                if (!savedMac.isNullOrBlank() && bleManager.connectionState.value is BleConnectionState.Disconnected) {
                    val transport = try {
                        com.yaris.hvfan.ble.BluetoothTransportType.valueOf(appPreferences.savedTransportType)
                    } catch (e: Exception) {
                        com.yaris.hvfan.ble.BluetoothTransportType.AUTO
                    }
                    bleManager.connect(savedMac, transport)
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification("Inizializzazione servizio...", "Ricerca adattatore OBD...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, FanControlForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnetti", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun observeStateForNotification() {
        serviceScope.launch {
            bleManager.connectionState.collect { connState ->
                updateWakeLock(connState is BleConnectionState.Ready || connState is BleConnectionState.Connected)
                updateNotification()
            }
        }
        serviceScope.launch {
            obdController.liveState.collect {
                updateNotification()
            }
        }
    }

    private fun updateNotification() {
        val state = obdController.liveState.value
        val connState = bleManager.connectionState.value
        val title: String
        val content: String

        when (connState) {
            is BleConnectionState.Ready -> {
                if (!state.hasEcuCommunication) {
                    title = "Toyota Yaris: In attesa centralina"
                    content = "Dongle connesso. Accendi la vettura (spia READY) per visualizzare i dati"
                } else {
                    val fanText = if (state.batteryStatus.isFanForced) "Ventola MAX (Lvl 6)" else "Ventola Auto (Lvl ${state.batteryStatus.fanSpeedLevel})"
                    val tempText = "Temp HV: ${String.format(java.util.Locale.US, "%.1f", state.batteryStatus.maxTemp)}°C"
                    title = "Toyota Yaris: $fanText"
                    content = "$tempText | Target: ${state.targetThreshold}°C"
                }
            }
            is BleConnectionState.Connected -> {
                title = "Toyota Yaris: Connesso a OBD"
                content = "Inizializzazione protocollo CAN Denso..."
            }
            is BleConnectionState.Connecting -> {
                title = "Toyota Yaris: Connessione in corso..."
                content = "Collegamento a ${connState.deviceName}..."
            }
            is BleConnectionState.Reconnecting -> {
                title = "Toyota Yaris: In attesa vettura"
                content = "Riconnessione automatica a ${connState.deviceName} (tentativo #${connState.attempt})..."
            }
            is BleConnectionState.Scanning -> {
                title = "Toyota Yaris: Scansione BLE..."
                content = "Ricerca dispositivi nei paraggi..."
            }
            else -> {
                title = "Toyota Yaris Fan Controller"
                content = "Disconnesso"
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun stopService() {
        isRunning = false
        updateWakeLock(false)
        wakeLock = null
        bleManager.disconnect()
        obdController.stopLoop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopService()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        updateWakeLock(false)
        wakeLock = null
        bleManager.cleanup()
        obdController.stopLoop()
        serviceScope.cancel()
    }
}
