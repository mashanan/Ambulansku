package com.example.tugasakhirfinal

import android.content.Context
import java.util.UUID

// ID unik per instalasi app (bukan per akun) - dipakai untuk menegakkan "1 akun cuma boleh
// aktif di 1 device". Sengaja disimpan di SharedPreferences TERPISAH dari cache profil
// (user_cache/driver_preferences) supaya TIDAK ikut terhapus saat logout - identitas device
// harus tetap sama walau akun yang login di device ini berganti-ganti.
object DeviceSession {
    private const val PREFS_NAME = "device_identity"
    private const val KEY_SESSION_ID = "installation_session_id"

    fun getSessionId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_SESSION_ID, null)
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SESSION_ID, newId).apply()
        return newId
    }
}
