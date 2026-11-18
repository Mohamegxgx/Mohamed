package com.example.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat

fun interface NetworkConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidNetworkConnectivityChecker(private val context: Context) : NetworkConnectivityChecker {
    override fun isOnline(): Boolean {
        val appContext = context.applicationContext
        val hasPerm = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return true

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return try {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: SecurityException) {
            true
        }
    }
}
