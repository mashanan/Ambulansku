package com.example.tugasakhirfinal.Maps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.net.HttpURLConnection
import java.security.MessageDigest

/**
 * Identifies this app to Google Maps Platform web services (e.g. the Directions API) called over
 * plain HTTP. With these headers the API key can be restricted to "Android apps" (package name +
 * SHA-1 signing certificate) in Google Cloud Console, so a copied key is useless elsewhere.
 * The Maps SDK itself (GoogleMap) sends this identity automatically.
 */
object GoogleApiHeaders {

    @Volatile
    private var cachedSha1: String? = null

    fun apply(connection: HttpURLConnection, context: Context) {
        connection.setRequestProperty("X-Android-Package", context.packageName)
        signingSha1(context)?.let { connection.setRequestProperty("X-Android-Cert", it) }
    }

    /** SHA-1 of the app signing certificate, uppercase hex without colons (format Google expects). */
    private fun signingSha1(context: Context): String? {
        cachedSha1?.let { return it }
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return null
            MessageDigest.getInstance("SHA-1").digest(cert)
                .joinToString("") { "%02X".format(it) }
                .also { cachedSha1 = it }
        } catch (e: Exception) {
            null
        }
    }
}
