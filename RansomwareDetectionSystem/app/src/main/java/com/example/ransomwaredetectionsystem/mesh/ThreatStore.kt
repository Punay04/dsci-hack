package com.example.ransomwaredetectionsystem.mesh

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ThreatStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("mesh_threat_store", Context.MODE_PRIVATE)

    private val lock = ReentrantLock()
    private val maxEntries = 500

    fun save(signature: ThreatSignature) {
        lock.withLock {
            try {
                val all = getAll().toMutableList()

                if (all.size >= maxEntries) {
                    Log.w("MeshNet", "ThreatStore full. Removing oldest entry.")
                    all.removeAt(0)
                }

                all.add(signature)

                val jsonArray = JSONArray()
                all.forEach { jsonArray.put(it.toJson()) }

                prefs.edit()
                    .putString("signatures", jsonArray.toString())
                    .apply()

                Log.d("MeshNet", "Signature saved. Total count: ${all.size}")
            } catch (e: Exception) {
                Log.e("MeshNet", "Error saving signature", e)
            }
        }
    }

    fun getAll(): List<ThreatSignature> {
        lock.withLock {
            val raw = prefs.getString("signatures", null) ?: return emptyList()

            return try {
                val array = JSONArray(raw)
                List(array.length()) { index ->
                    ThreatSignature.fromJson(array.getJSONObject(index))
                }
            } catch (e: Exception) {
                Log.e("MeshNet", "Error parsing stored signatures", e)
                emptyList()
            }
        }
    }

    fun clear() {
        lock.withLock {
            prefs.edit().remove("signatures").apply()
            Log.d("MeshNet", "ThreatStore cleared")
        }
    }
}
