package com.packtrack.app.ui.ride

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

class RideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRideBinding
    private lateinit var prefs: PrefsManager
    private val repo = FirestoreRepository()

    private var map: MapLibreMap? = null
    private var mapReady = false

    private var destLat = 0.0
    private var destLng = 0.0
    private var pin = ""

    private lateinit var adapter: RiderAdapter
    private lateinit var bottomSheet: BottomSheetBehavior<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        pin = intent.getStringExtra(EXTRA_PIN) ?: prefs.currentPin
        destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, 0.0)
        destLng = intent.getDoubleExtra(EXTRA_DEST_LNG, 0.0)
        val destName = intent.getStringExtra(EXTRA_DEST_NAME) ?: "Destination"
        val routeGeoJson = intent.getStringExtra(EXTRA_ROUTE_GEOJSON) ?: ""

        binding.tvDestinationName.text = destName.uppercase()
        binding.tvPin.text = "PIN: $pin"

        setupBottomSheet()
        setupMap(savedInstanceState, routeGeoJson)

        adapter = RiderAdapter(prefs.riderId)
        binding.rvRiders.adapter = adapter

        binding.fabNudge.setOnClickListener {
            startActivity(Intent(this, NudgeActivity::class.java))
        }

        binding.btnEndRide.setOnClickListener { endRide() }

        LocationForegroundService.start(this)
        observeRiders()
    }

    private fun setupBottomSheet() {
        bottomSheet = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheet.peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek)
        bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setupMap(savedInstanceState: Bundle?, routeGeoJson: String) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.setStyle(Style.Builder().fromUri("asset://map_style.json")) { style ->
                mapReady = true
                setupMapLayers(style)
                if (routeGeoJson.isNotBlank()) {
                    drawRoute(style, routeGeoJson)
                }
                if (destLat != 0.0 && destLng != 0.0) {
                    mapLibreMap.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(destLat, destLng))
                                .zoom(10.0)
                                .build()
                        )
                    )
                }
            }
        }
    }

    private fun setupMapLayers(style: Style) {
        style.addSource(GeoJsonSource(SOURCE_RIDERS, FeatureCollection.fromFeatures(emptyList())))
        style.addSource(GeoJsonSource(SOURCE_ROUTE, FeatureCollection.fromFeatures(emptyList())))

        style.addLayer(LineLayer(LAYER_ROUTE, SOURCE_ROUTE).apply {
            setProperties(
                PropertyFactory.lineColor("#D9383A"),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.85f)
            )
        })

        style.addLayer(CircleLayer(LAYER_RIDERS, SOURCE_RIDERS).apply {
            setProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor("#D9383A"),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF")
            )
        })
    }

    private fun drawRoute(style: Style, geojson: String) {
        try {
            val source = style.getSource(SOURCE_ROUTE) as? GeoJsonSource
            val feature = Feature.fromJson("{\"type\":\"Feature\",\"geometry\":$geojson,\"properties\":{}}")
            source?.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
        } catch (e: Exception) {
            Log.e(TAG, "Draw route failed", e)
        }
    }

    private fun updateRidersOnMap(riders: List<Rider>) {
        val style = map?.style ?: return
        val source = style.getSource(SOURCE_RIDERS) as? GeoJsonSource ?: return
        val features = riders.filter { it.latitude != 0.0 }.map { rider ->
            Feature.fromGeometry(Point.fromLngLat(rider.longitude, rider.latitude))
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
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
                    updateRidersOnMap(riders)

                    // Alert if any emergency nudge
                    val emergency = riders.firstOrNull { it.nudge == Rider.NUDGE_EMERGENCY && it.id != prefs.riderId }
                    if (emergency != null) {
                        Toast.makeText(this@RideActivity, "SOS from ${emergency.name}!", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun endRide() {
        LocationForegroundService.stop(this)
        finish()
    }

    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    companion object {
        const val EXTRA_PIN = "extra_pin"
        const val EXTRA_DEST_NAME = "extra_dest_name"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LNG = "extra_dest_lng"
        const val EXTRA_ROUTE_GEOJSON = "extra_route_geojson"

        private const val TAG = "RideActivity"
        private const val SOURCE_RIDERS = "riders-source"
        private const val SOURCE_ROUTE = "route-source"
        private const val LAYER_RIDERS = "riders-layer"
        private const val LAYER_ROUTE = "route-layer"
    }
}
