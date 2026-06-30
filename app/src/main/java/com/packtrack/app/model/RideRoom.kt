package com.packtrack.app.model

data class RideRoom(
    val pin: String = "",
    val destinationName: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val routeGeojson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
