package com.jarvis.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ProductInfo(val name: String, val brand: String?, val category: String?, val raw: String)

/**
 * Looks up a scanned barcode against a free public product database.
 * Barcode *reading* (ML Kit) is fully offline; this lookup step is the one
 * part of the whole app that genuinely needs internet, since no phone can
 * locally store a database of every product ever manufactured.
 */
class ProductLookupService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Runs on a background thread - call from a coroutine or executor, not the main thread. */
    fun lookupBarcode(code: String): ProductInfo? {
        if (!isOnline()) return null
        return try {
            // Free trial tier, no API key needed for light/personal use.
            // Swap this for a paid provider (or your own key) if you scan a lot.
            val request = Request.Builder()
                .url("https://api.upcitemdb.com/prod/trial/lookup?upc=$code")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return null
                if (items.length() == 0) return null
                val item = items.getJSONObject(0)
                ProductInfo(
                    name = item.optString("title", "Unknown product"),
                    brand = item.optString("brand", null),
                    category = item.optString("category", null),
                    raw = body
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
