package com.packtrack.app.util

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.*

object OsrmUtils {

    private val client = OkHttpClient()

    data class RouteResult(
        val geojson: String,
        val distanceMeters: Double,
        val durationSeconds: Double
    )

    data class GeoResult(val lat: Double, val lng: Double, val displayName: String)

    suspend fun fetchRoute(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double
    ): RouteResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "$fromLng,$fromLat;$toLng,$toLat" +
                    "?overview=full&geometries=geojson"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JsonParser.parseString(body).asJsonObject
            val routes = json.getAsJsonArray("routes")
            if (routes.size() == 0) return@withContext null

            val route = routes[0].asJsonObject
            val distance = route.get("distance").asDouble
            val duration = route.get("duration").asDouble
            val geometry = route.getAsJsonObject("geometry").toString()

            RouteResult(geometry, distance, duration)
        } catch (e: Exception) {
            Log.e("OsrmUtils", "Route fetch failed", e)
            null
        }
    }

    suspend fun geocode(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&addressdetails=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PackTrackApp/1.0 (android)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val arr = JsonParser.parseString(body).asJsonArray
            arr.map { el ->
                val obj = el.asJsonObject
                GeoResult(
                    lat = obj.get("lat").asDouble,
                    lng = obj.get("lon").asDouble,
                    displayName = obj.get("display_name").asString
                )
            }
        } catch (e: Exception) {
            Log.e("OsrmUtils", "Geocode failed", e)
            emptyList()
        }
    }

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
