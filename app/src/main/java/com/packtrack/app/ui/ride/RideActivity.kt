package com.packtrack.app.ui.ride

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.packtrack.app.R
import com.packtrack.app.databinding.ActivityRideBinding
import com.packtrack.app.model.Rider
import com.packtrack.app.repository.FirestoreRepository
import com.packtrack.app.service.LocationForegroundService
import com.packtrack.app.ui.nudge.NudgeActivity
import com.packtrack.app.util.OsrmUtils
import com.packtrack.app.util.PrefsManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class RideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRideBinding
    private lateinit var prefs: PrefsManager
    private val repo = FirestoreRepository()

    private var destLat = 0.0
    private var destLng = 0.0
    private var pin = ""
    private var routeGeoJson = ""
    private var mapReady = false

    private lateinit var adapter: RiderAdapter
    private lateinit var bottomSheet: BottomSheetBehavior<View>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        pin = intent.getStringExtra(EXTRA_PIN) ?: prefs.currentPin
        destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, 0.0)
        destLng = intent.getDoubleExtra(EXTRA_DEST_LNG, 0.0)
        val destName = intent.getStringExtra(EXTRA_DEST_NAME) ?: "Destination"
        routeGeoJson = intent.getStringExtra(EXTRA_ROUTE_GEOJSON) ?: ""

        binding.tvDestinationName.text = destName.uppercase()
        binding.tvPin.text = "PIN: $pin"

        setupBottomSheet()
        setupWebMap()

        adapter = RiderAdapter(prefs.riderId)
        binding.rvRiders.adapter = adapter

        binding.fabNudge.setOnClickListener {
            startActivity(Intent(this, NudgeActivity::class.java))
        }
        binding.btnEndRide.setOnClickListener { endRide() }

        LocationForegroundService.start(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebMap() {
        binding.webMap.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        // WebViewClient must be set BEFORE loadUrl — for file:// assets the page can
        // finish loading before onCreate sets it, causing onPageFinished to be missed.
        binding.webMap.webViewClient = WebViewClient()
        binding.webMap.addJavascriptInterface(MapBridge(), "Android")
        binding.webMap.loadUrl("file:///android_asset/map.html")
    }

    private fun setupBottomSheet() {
        bottomSheet = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheet.peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek)
        bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun observeRiders() {
        if (!repo.isAvailable || pin.isBlank()) {
            binding.tvPackCount.text = "OFFLINE MODE"
            return
        }

        lifecycleScope.launch {
            repo.observeRiders(pin)
                .catch { Log.e(TAG, "Observe riders failed", it) }
                .collect { riders ->
                    val withDist = riders.map { rider ->
                        rider.copy(
                            distanceToDestinationKm = if (destLat != 0.0)
                                OsrmUtils.distanceKm(rider.latitude, rider.longitude, destLat, destLng)
                            else 0.0
                        )
                    }.sortedBy { it.distanceToDestinationKm }

                    adapter.submitList(withDist)
                    binding.tvPackCount.text = "${riders.size} RIDERS"

                    if (mapReady) updateRidersOnMap(riders)

                    val emergency = riders.firstOrNull {
                        it.nudge == Rider.NUDGE_EMERGENCY && it.id != prefs.riderId
                    }
                    if (emergency != null) {
                        Toast.makeText(this@RideActivity, "SOS from ${emergency.name}!", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun updateRidersOnMap(riders: List<Rider>) {
        val arr = JSONArray()
        riders.filter { it.latitude != 0.0 }.forEach { rider ->
            val obj = JSONObject()
            obj.put("id", rider.id)
            obj.put("name", rider.name)
            obj.put("lat", rider.latitude)
            obj.put("lng", rider.longitude)
            obj.put("status", rider.status)
            obj.put("isMe", rider.id == prefs.riderId)
            arr.put(obj)
        }
        val json = arr.toString().replace("'", "\\'")
        binding.webMap.evaluateJavascript("updateRiders('$json');", null)
    }

    private fun endRide() {
        LocationForegroundService.stop(this)
        finish()
    }

    inner class MapBridge {
        @JavascriptInterface
        fun onMapReady() {
            // This fires from JS after Leaflet initialises — the correct point to push
            // destination, route, and start streaming rider positions.
            runOnUiThread {
                mapReady = true
                if (destLat != 0.0) {
                    binding.webMap.evaluateJavascript("setDestination($destLat, $destLng);", null)
                }
                if (routeGeoJson.isNotBlank()) {
                    val escaped = routeGeoJson.replace("'", "\\'")
                    binding.webMap.evaluateJavascript("drawRoute('$escaped');", null)
                }
                observeRiders()
            }
        }
    }

    companion object {
        const val EXTRA_PIN = "extra_pin"
        const val EXTRA_DEST_NAME = "extra_dest_name"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LNG = "extra_dest_lng"
        const val EXTRA_ROUTE_GEOJSON = "extra_route_geojson"
        private const val TAG = "RideActivity"
    }
}
