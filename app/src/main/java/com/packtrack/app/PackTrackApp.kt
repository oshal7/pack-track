package com.packtrack.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import org.maplibre.android.MapLibre

class PackTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.w("PackTrack", "Firebase not configured — real-time sharing disabled until google-services.json is set up")
        }
    }
}
