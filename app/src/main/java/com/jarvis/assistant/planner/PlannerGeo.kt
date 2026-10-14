package com.jarvis.assistant.planner

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object PlannerGeo {
    private const val TAG = "PlannerGeo"
    private const val RADIUS_METERS = 150f

    fun hasLocationPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission(ctx)
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingFor(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, PlannerReceiver::class.java)
        i.action = PlannerReceiver.ACTION_PLACE
        i.data = Uri.parse("planner://place/$id")
        i.putExtra(PlannerReceiver.EXTRA_ID, id)
        // The system adds extras (entering/leaving) to this intent, so it has to be mutable.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(ctx, ("place" + id).hashCode(), i, flags)
    }

    @SuppressLint("MissingPermission")
    fun register(ctx: Context, item: PlannerItem): Boolean {
        if (!hasLocationPermission(ctx)) return false
        val place = PlannerStore.places(ctx).firstOrNull { it.name.equals(item.extra1, ignoreCase = true) } ?: return false
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.addProximityAlert(place.lat, place.lon, RADIUS_METERS, -1L, pendingFor(ctx, item.id))
            true
        } catch (e: Exception) {
            Log.e(TAG, "addProximityAlert failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun unregister(ctx: Context, id: String) {
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeProximityAlert(pendingFor(ctx, id))
        } catch (e: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun lastLocation(ctx: Context): Location? {
        if (!hasLocationPermission(ctx)) return null
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                val l = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
                if (l != null && (best == null || l.time > best.time)) best = l
            }
            best
        } catch (e: Exception) {
            null
        }
    }
}
