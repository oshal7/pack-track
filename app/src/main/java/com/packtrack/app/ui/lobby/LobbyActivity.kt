package com.packtrack.app.ui.lobby

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.packtrack.app.databinding.ActivityLobbyBinding
import com.packtrack.app.model.RideRoom
import com.packtrack.app.repository.FirestoreRepository
import com.packtrack.app.ui.ride.RideActivity
import com.packtrack.app.util.OsrmUtils
import com.packtrack.app.util.PrefsManager
import kotlinx.coroutines.launch
import kotlin.random.Random

class LobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLobbyBinding
    private lateinit var prefs: PrefsManager
    private val repo = FirestoreRepository()

    private var mode = MODE_NONE
    private var generatedPin = ""
    private var destinationLat = 0.0
    private var destinationLng = 0.0
    private var routeGeojson = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        binding.tvRiderGreeting.text = "WELCOME, ${prefs.riderName.uppercase()}"

        binding.btnCreateRide.setOnClickListener { switchMode(MODE_CREATE) }
        binding.btnJoinRide.setOnClickListener { switchMode(MODE_JOIN) }

        binding.btnSearchDest.setOnClickListener { searchDestination() }
        binding.btnLaunchRide.setOnClickListener { launchRide() }
        binding.btnJoinConfirm.setOnClickListener { joinRide() }
    }

    private fun switchMode(newMode: Int) {
        mode = newMode
        binding.panelCreate.visibility = if (newMode == MODE_CREATE) View.VISIBLE else View.GONE
        binding.panelJoin.visibility = if (newMode == MODE_JOIN) View.VISIBLE else View.GONE

        if (newMode == MODE_CREATE) {
            generatedPin = generatePin()
            binding.tvGeneratedPin.text = generatedPin
            binding.btnLaunchRide.isEnabled = false
        }
    }

    private fun searchDestination() {
        val query = binding.etDestination.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a destination", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSearchDest.isEnabled = false
        binding.progressCreate.visibility = View.VISIBLE

        lifecycleScope.launch {
            val results = OsrmUtils.geocode(query)
            binding.progressCreate.visibility = View.GONE
            binding.btnSearchDest.isEnabled = true

            if (results.isEmpty()) {
                Toast.makeText(this@LobbyActivity, "Destination not found. Try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            destinationLat = results[0].lat
            destinationLng = results[0].lng

            binding.tvDestFound.text = "Destination set: ${String.format("%.4f", destinationLat)}, ${String.format("%.4f", destinationLng)}"
            binding.tvDestFound.visibility = View.VISIBLE
            binding.btnLaunchRide.isEnabled = true
        }
    }

    private fun launchRide() {
        if (destinationLat == 0.0 && destinationLng == 0.0) {
            Toast.makeText(this, "Search for a destination first", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnLaunchRide.isEnabled = false
        binding.progressCreate.visibility = View.VISIBLE

        lifecycleScope.launch {
            val destName = binding.etDestination.text.toString().trim()

            val room = RideRoom(
                pin = generatedPin,
                destinationName = destName,
                destinationLat = destinationLat,
                destinationLng = destinationLng,
                routeGeojson = routeGeojson
            )

            if (repo.isAvailable) {
                val ok = repo.createRoom(room)
                if (!ok) {
                    Toast.makeText(this@LobbyActivity, "Failed to create room. Check Firebase setup.", Toast.LENGTH_LONG).show()
                    binding.progressCreate.visibility = View.GONE
                    binding.btnLaunchRide.isEnabled = true
                    return@launch
                }
            }

            prefs.currentPin = generatedPin
            prefs.isRideLeader = true

            startRide(room)
        }
    }

    private fun joinRide() {
        val pin = binding.etJoinPin.text.toString().trim()
        if (pin.length != 4) {
            Toast.makeText(this, "Enter the 4-digit PIN", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnJoinConfirm.isEnabled = false
        binding.progressJoin.visibility = View.VISIBLE

        lifecycleScope.launch {
            val room = if (repo.isAvailable) {
                repo.getRoom(pin)
            } else {
                RideRoom(pin = pin, destinationName = "Unknown", destinationLat = 0.0, destinationLng = 0.0)
            }

            binding.progressJoin.visibility = View.GONE
            binding.btnJoinConfirm.isEnabled = true

            if (room == null) {
                Toast.makeText(this@LobbyActivity, "Room $pin not found", Toast.LENGTH_SHORT).show()
                return@launch
            }

            prefs.currentPin = pin
            prefs.isRideLeader = false

            startRide(room)
        }
    }

    private fun startRide(room: RideRoom) {
        val intent = Intent(this, RideActivity::class.java).apply {
            putExtra(RideActivity.EXTRA_PIN, room.pin)
            putExtra(RideActivity.EXTRA_DEST_NAME, room.destinationName)
            putExtra(RideActivity.EXTRA_DEST_LAT, room.destinationLat)
            putExtra(RideActivity.EXTRA_DEST_LNG, room.destinationLng)
            putExtra(RideActivity.EXTRA_ROUTE_GEOJSON, room.routeGeojson)
        }
        startActivity(intent)
    }

    private fun generatePin(): String = Random.nextInt(1000, 9999).toString()

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_CREATE = 1
        private const val MODE_JOIN = 2
    }
}
