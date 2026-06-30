package com.packtrack.app.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("packtrack_prefs", Context.MODE_PRIVATE)

    var riderName: String
        get() = prefs.getString(KEY_RIDER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RIDER_NAME, value).apply()

    val riderId: String
        get() {
            var id = prefs.getString(KEY_RIDER_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_RIDER_ID, id).apply()
            }
            return id
        }

    var currentPin: String
        get() = prefs.getString(KEY_CURRENT_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_PIN, value).apply()

    var isRideLeader: Boolean
        get() = prefs.getBoolean(KEY_IS_LEADER, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LEADER, value).apply()

    companion object {
        private const val KEY_RIDER_NAME = "rider_name"
        private const val KEY_RIDER_ID = "rider_id"
        private const val KEY_CURRENT_PIN = "current_pin"
        private const val KEY_IS_LEADER = "is_leader"
    }
}
