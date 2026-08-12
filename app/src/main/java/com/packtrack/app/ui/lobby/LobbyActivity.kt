package com.packtrack.app.ui.lobby

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.packtrack.app.databinding.ActivityLobbyBinding
import com.packtrack.app.databinding.ItemSearchResultBinding
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
    private var destinationName = ""
    private var routeGeojson = ""

    private val searchAdapter = SearchResultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        binding.tvRiderGreeting.text = "WELCOME, ${prefs.riderName.uppercase()}"

        binding.btnCreateRide.setOnClickListener { switchMode(MODE_CREATE) }
        binding.btnJoinRide.setOnClickListener { switchMode(MODE_JOIN) }

        binding.btnSearchDest.setOnClickListener { searchDestination() }
        binding.etDestination.setOnEditorActionListener { _, _, _ ->
            searchDestination(); true
        }

        // Search results RecyclerView (we'll populate layoutSearchResults dynamically)
        searchAdapter.onSelect = { result -> selectDestination(result) }

        binding.tvChangeDest.setOnClickListener { resetDestination() }
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
            resetDestination()
        }
    }

    private fun searchDestination() {
        val query = binding.etDestination.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a destination name", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide any previous results / selected state
        binding.layoutSearchResults.visibility = View.GONE
        binding.layoutDestSelected.visibility = View.GONE
        binding.btnLaunchRide.isEnabled = false

        binding.btnSearchDest.isEnabled = false
        binding.layoutSearchLoading.visibility = View.VISIBLE
        binding.tvSearchStatus.text = "Searching..."

        lifecycleScope.launch {
            val results = OsrmUtils.geocode(query)

            binding.layoutSearchLoading.visibility = View.GONE
            binding.btnSearchDest.isEnabled = true

            if (results.isEmpty()) {
                Toast.makeText(
                    this@LobbyActivity,
                    "No results found — try a different search",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            showSearchResults(results)
        }
    }

    private fun showSearchResults(results: List<OsrmUtils.GeoResult>) {
        binding.layoutSearchResults.removeAllViews()

        results.forEachIndexed { index, result ->
            // Divider between items
            if (index > 0) {
                val divider = View(this).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(16.dp, 0, 16.dp, 0) }
                    setBackgroundColor(Color.parseColor("#333333"))
                }
                binding.layoutSearchResults.addView(divider)
            }

            val itemBinding = ItemSearchResultBinding.inflate(
                LayoutInflater.from(this), binding.layoutSearchResults, false
            )
            val parts = result.displayName.split(",")
            itemBinding.tvResultPrimary.text = parts.firstOrNull()?.trim() ?: result.displayName
            val secondary = parts.drop(1).take(2).joinToString(", ").trim()
            if (secondary.isNotBlank()) {
                itemBinding.tvResultSecondary.text = secondary
                itemBinding.tvResultSecondary.visibility = View.VISIBLE
            }
            itemBinding.root.setOnClickListener { selectDestination(result) }
            binding.layoutSearchResults.addView(itemBinding.root)
        }

        binding.layoutSearchResults.visibility = View.VISIBLE
    }

    private fun selectDestination(result: OsrmUtils.GeoResult) {
        destinationLat = result.lat
        destinationLng = result.lng

        // Build a concise display name: "City, State/Country"
        val parts = result.displayName.split(",")
        destinationName = parts.take(2).joinToString(", ").trim()
        if (destinationName.isBlank()) destinationName = result.displayName

        binding.layoutSearchResults.visibility = View.GONE
        binding.tvSelectedDest.text = result.displayName
        binding.layoutDestSelected.visibility = View.VISIBLE
        binding.btnLaunchRide.isEnabled = true
    }

    private fun resetDestination() {
        destinationLat = 0.0
        destinationLng = 0.0
        destinationName = ""
        routeGeojson = ""
        binding.layoutSearchResults.visibility = View.GONE
        binding.layoutDestSelected.visibility = View.GONE
        binding.btnLaunchRide.isEnabled = false
        binding.etDestination.text?.clear()
        binding.etDestination.requestFocus()
    }

    private fun launchRide() {
        if (destinationLat == 0.0) {
            Toast.makeText(this, "Pick a destination first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLaunchRide.isEnabled = false
        binding.layoutSearchLoading.visibility = View.VISIBLE
        binding.tvSearchStatus.text = "Creating room..."

        lifecycleScope.launch {
            val room = RideRoom(
                pin = generatedPin,
                destinationName = destinationName.ifBlank {
                    binding.etDestination.text.toString().trim()
                },
                destinationLat = destinationLat,
                destinationLng = destinationLng,
                routeGeojson = routeGeojson
            )

            if (repo.isAvailable) {
                val ok = repo.createRoom(room)
                if (!ok) {
                    binding.layoutSearchLoading.visibility = View.GONE
                    binding.btnLaunchRide.isEnabled = true
                    Toast.makeText(
                        this@LobbyActivity,
                        "Failed to create room — check Firebase setup",
                        Toast.LENGTH_LONG
                    ).show()
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
        binding.layoutJoinLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            val room = if (repo.isAvailable) {
                repo.getRoom(pin)
            } else {
                RideRoom(pin = pin, destinationName = "Unknown", destinationLat = 0.0, destinationLng = 0.0)
            }

            binding.layoutJoinLoading.visibility = View.GONE
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
        startActivity(Intent(this, RideActivity::class.java).apply {
            putExtra(RideActivity.EXTRA_PIN, room.pin)
            putExtra(RideActivity.EXTRA_DEST_NAME, room.destinationName)
            putExtra(RideActivity.EXTRA_DEST_LAT, room.destinationLat)
            putExtra(RideActivity.EXTRA_DEST_LNG, room.destinationLng)
            putExtra(RideActivity.EXTRA_ROUTE_GEOJSON, room.routeGeojson)
        })
    }

    private fun generatePin(): String = Random.nextInt(1000, 9999).toString()

    // Extension for dp→px
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    // ── Adapter ─────────────────────────────────────────────────────────────

    private class SearchResultAdapter : RecyclerView.Adapter<SearchResultAdapter.VH>() {
        var items = emptyList<OsrmUtils.GeoResult>()
        var onSelect: ((OsrmUtils.GeoResult) -> Unit)? = null

        class VH(val b: ItemSearchResultBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = items[position]
            val parts = r.displayName.split(",")
            holder.b.tvResultPrimary.text = parts.firstOrNull()?.trim() ?: r.displayName
            val secondary = parts.drop(1).take(2).joinToString(", ").trim()
            if (secondary.isNotBlank()) {
                holder.b.tvResultSecondary.text = secondary
                holder.b.tvResultSecondary.visibility = View.VISIBLE
            } else {
                holder.b.tvResultSecondary.visibility = View.GONE
            }
            holder.itemView.setOnClickListener { onSelect?.invoke(r) }
        }
    }

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_CREATE = 1
        private const val MODE_JOIN = 2
    }
}
