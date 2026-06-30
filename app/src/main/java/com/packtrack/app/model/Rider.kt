package com.packtrack.app.model

data class Rider(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = STATUS_ACTIVE,
    val lastUpdate: Long = 0L,
    val distanceToDestinationKm: Double = 0.0,
    val nudge: String = ""
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_HALTED = "halted"
        const val STATUS_EMERGENCY = "emergency"
        const val STATUS_REGROUP = "regroup"

        const val NUDGE_EMERGENCY = "emergency"
        const val NUDGE_REGROUP = "regroup"
        const val NUDGE_ACK = "acknowledged"
    }
}
