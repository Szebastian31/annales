package com.annales.journal.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** "Where am I now", as a town name, for a stamp's postmark. Asked only when you tap for it; nothing but the town is kept. */
object Here {
    /** Null without the location permission, with location off, or when the phone can't name the place. */
    @SuppressLint("MissingPermission")
    suspend fun town(context: Context): String? = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return@withContext null
        // a fix from the last 30 minutes is good enough for a town; otherwise ask for a fresh one (up to 10 s)
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 30 * 60_000 }.maxByOrNull { it.time }
        val loc = recent ?: if (Build.VERSION.SDK_INT >= 30) withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { k ->
                runCatching { lm.getCurrentLocation(providers.first(), null, context.mainExecutor) { k.resume(it) } }.onFailure { k.resume(null) }
            }
        } else providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        loc ?: return@withContext null
        @Suppress("DEPRECATION")
        runCatching { Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1) }.getOrNull()
            ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }
}
