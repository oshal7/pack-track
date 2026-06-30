package com.packtrack.app.ui.nudge

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.packtrack.app.databinding.ActivityNudgeBinding
import com.packtrack.app.model.Rider
import com.packtrack.app.repository.FirestoreRepository
import com.packtrack.app.util.PrefsManager
import kotlinx.coroutines.launch

class NudgeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNudgeBinding
    private lateinit var prefs: PrefsManager
    private val repo = FirestoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNudgeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        binding.btnEmergency.setOnClickListener { sendNudge(Rider.NUDGE_EMERGENCY) }
        binding.btnRegroup.setOnClickListener { sendNudge(Rider.NUDGE_REGROUP) }
        binding.btnAck.setOnClickListener { sendNudge(Rider.NUDGE_ACK) }
        binding.btnClose.setOnClickListener { finish() }
    }

    private fun sendNudge(type: String) {
        val pin = prefs.currentPin
        val riderId = prefs.riderId

        if (!repo.isAvailable || pin.isBlank()) {
            Toast.makeText(this, "Nudge sent locally (Firebase offline)", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            repo.sendNudge(pin, riderId, type)
            val label = when (type) {
                Rider.NUDGE_EMERGENCY -> "EMERGENCY STOP sent to pack"
                Rider.NUDGE_REGROUP -> "REGROUP signal sent to pack"
                Rider.NUDGE_ACK -> "ACKNOWLEDGED sent to pack"
                else -> "Nudge sent"
            }
            Toast.makeText(this@NudgeActivity, label, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
