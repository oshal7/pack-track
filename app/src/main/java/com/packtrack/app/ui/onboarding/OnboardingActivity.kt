package com.packtrack.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.packtrack.app.databinding.ActivityOnboardingBinding
import com.packtrack.app.ui.lobby.LobbyActivity
import com.packtrack.app.util.PrefsManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        val savedName = prefs.riderName
        if (savedName.isNotBlank()) {
            binding.etRiderName.setText(savedName)
        }

        binding.btnStart.setOnClickListener {
            val name = binding.etRiderName.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(this, "Enter your rider name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.riderName = name
            requestPermissionsAndContinue()
        }
    }

    private fun requestPermissionsAndContinue() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            goToLobby()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), RC_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERMISSIONS) {
            val locationGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (locationGranted) {
                goToLobby()
            } else {
                Toast.makeText(this, "Location permission required for PackTrack", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToLobby() {
        startActivity(Intent(this, LobbyActivity::class.java))
    }

    companion object {
        private const val RC_PERMISSIONS = 100
    }
}
