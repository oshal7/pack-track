package com.packtrack.app.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.packtrack.app.model.Rider
import com.packtrack.app.model.RideRoom
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "Firestore not available", e)
        null
    }

    private fun ridesCollection() = db?.collection("rides")
    private fun rideDoc(pin: String) = ridesCollection()?.document(pin)
    private fun ridersCollection(pin: String) = rideDoc(pin)?.collection("riders")

    val isAvailable: Boolean get() = db != null

    suspend fun createRoom(room: RideRoom): Boolean {
        return try {
            rideDoc(room.pin)?.set(
                mapOf(
                    "destinationName" to room.destinationName,
                    "destinationLat" to room.destinationLat,
                    "destinationLng" to room.destinationLng,
                    "routeGeojson" to room.routeGeojson,
                    "createdAt" to room.createdAt
                )
            )?.await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "createRoom failed", e)
            false
        }
    }

    suspend fun getRoom(pin: String): RideRoom? {
        return try {
            val snap = rideDoc(pin)?.get()?.await() ?: return null
            if (!snap.exists()) return null
            RideRoom(
                pin = pin,
                destinationName = snap.getString("destinationName") ?: "",
                destinationLat = snap.getDouble("destinationLat") ?: 0.0,
                destinationLng = snap.getDouble("destinationLng") ?: 0.0,
                routeGeojson = snap.getString("routeGeojson") ?: "",
                createdAt = snap.getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "getRoom failed", e)
            null
        }
    }

    suspend fun updateRiderLocation(
        pin: String, riderId: String, name: String,
        lat: Double, lng: Double, status: String
    ) {
        try {
            ridersCollection(pin)?.document(riderId)?.set(
                mapOf(
                    "name" to name,
                    "latitude" to lat,
                    "longitude" to lng,
                    "status" to status,
                    "lastUpdate" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )?.await()
        } catch (e: Exception) {
            Log.e(TAG, "updateRiderLocation failed", e)
        }
    }

    suspend fun sendNudge(pin: String, riderId: String, nudgeType: String) {
        try {
            ridersCollection(pin)?.document(riderId)?.update("nudge", nudgeType)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "sendNudge failed", e)
        }
    }

    fun observeRiders(pin: String): Flow<List<Rider>> = callbackFlow {
        val reg: ListenerRegistration? = ridersCollection(pin)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val riders = snapshot.documents.mapNotNull { doc ->
                    try {
                        Rider(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            status = doc.getString("status") ?: Rider.STATUS_ACTIVE,
                            lastUpdate = doc.getLong("lastUpdate") ?: 0L,
                            nudge = doc.getString("nudge") ?: ""
                        )
                    } catch (e: Exception) { null }
                }
                trySend(riders)
            }
        awaitClose { reg?.remove() }
    }

    companion object {
        private const val TAG = "FirestoreRepo"
    }
}
