package com.packtrack.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.packtrack.app.R
import com.packtrack.app.data.local.AppDatabase
import com.packtrack.app.data.local.GpsCacheEntry
import com.packtrack.app.model.Rider
import com.packtrack.app.repository.FirestoreRepository
import com.packtrack.app.ui.ride.RideActivity
import com.packtrack.app.util.PrefsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationForegroundService : LifecycleService() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var prefs: PrefsManager
    private lateinit var repo: FirestoreRepository
    private lateinit var db: AppDatabase
    private var wakeLock: PowerManager.WakeLock? = null
    private var syncJob: Job? = null

    private var lastLat = 0.0
    private var lastLng = 0.0
    private var haltedSinceMs = 0L
    private var currentStatus = Rider.STATUS_ACTIVE

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onNewLocation(location.latitude, location.longitude)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = PrefsManager(this)
        repo = FirestoreRepository()
        db = AppDatabase.getInstance(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
        startSyncLoop()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
                .build()
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    private fun onNewLocation(lat: Double, lng: Double) {
        val pin = prefs.currentPin
        val riderId = prefs.riderId

        updateHaltStatus(lat, lng)

        lifecycleScope.launch {
            db.gpsCacheDao().insert(GpsCacheEntry(latitude = lat, longitude = lng, timestamp = System.currentTimeMillis()))
            if (repo.isAvailable && pin.isNotBlank()) {
                repo.updateRiderLocation(pin, riderId, prefs.riderName, lat, lng, currentStatus)
                flushCache()
            }
        }
        lastLat = lat
        lastLng = lng
    }

    private fun updateHaltStatus(lat: Double, lng: Double) {
        val moved = if (lastLat == 0.0 && lastLng == 0.0) true
        else distanceMeters(lastLat, lastLng, lat, lng) > HALT_MOVEMENT_THRESHOLD_M

        if (moved) {
            haltedSinceMs = 0L
            currentStatus = Rider.STATUS_ACTIVE
        } else {
            if (haltedSinceMs == 0L) haltedSinceMs = System.currentTimeMillis()
            if (System.currentTimeMillis() - haltedSinceMs >= HALT_THRESHOLD_MS) {
                currentStatus = Rider.STATUS_HALTED
            }
        }
    }

    private suspend fun flushCache() {
        val unsync = db.gpsCacheDao().getUnsynced()
        if (unsync.isEmpty()) return
        val pin = prefs.currentPin
        val riderId = prefs.riderId
        val last = unsync.last()
        repo.updateRiderLocation(pin, riderId, prefs.riderName, last.latitude, last.longitude, currentStatus)
        db.gpsCacheDao().markSynced(unsync.map { it.id })
        db.gpsCacheDao().pruneOld(System.currentTimeMillis() - 3_600_000L)
    }

    private fun startSyncLoop() {
        syncJob = lifecycleScope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                if (repo.isAvailable) flushCache()
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PackTrack:LocationWakeLock")
        wakeLock?.acquire(TEN_HOURS_MS)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "PackTrack Location", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracks your location for the pack"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, RideActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PackTrack Active")
            .setContentText("Sharing your location with the pack")
            .setSmallIcon(R.drawable.ic_motorcycle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2).let { it * it }
        return 6371000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        syncJob?.cancel()
        wakeLock?.release()
    }

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "packtrack_location"
        private const val NOTIF_ID = 1001
        private const val INTERVAL_MS = 10_000L
        private const val HALT_THRESHOLD_MS = 90_000L
        private const val HALT_MOVEMENT_THRESHOLD_M = 20.0
        private const val TEN_HOURS_MS = 10 * 60 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }
}
