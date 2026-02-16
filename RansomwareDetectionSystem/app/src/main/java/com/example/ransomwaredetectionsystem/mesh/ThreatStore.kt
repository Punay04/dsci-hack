package com.example.ransomwaredetectionsystem.mesh

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ThreatStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("meshnet_store", Context.MODE_PRIVATE)

    private val key = "threat_signatures"

    fun save(signature: ThreatSignature) {
        val list = getAll().toMutableList()
        list.add(signature)

        val jsonArray = JSONArray()

        list.forEach {
            val obj = JSONObject()
            obj.put("eventType", it.eventType)
            obj.put("riskScore", it.riskScore)
            obj.put("source", it.source)
            obj.put("timeBucket", it.timeBucket)
            obj.put("severity", it.severity.name)
            obj.put("version", it.version)
            jsonArray.put(obj)
        }

        prefs.edit().putString(key, jsonArray.toString()).apply()

        Log.d("MeshNet", "Signature saved. Total count: ${list.size}")
    }

    fun getAll(): List<ThreatSignature> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(json)

        val result = mutableListOf<ThreatSignature>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            result.add(
                ThreatSignature(
                    eventType = obj.getString("eventType"),
                    riskScore = obj.getInt("riskScore"),
                    source = obj.getString("source"),
                    timeBucket = obj.getLong("timeBucket"),
                    severity = ThreatSignature.Severity.valueOf(
                        obj.getString("severity")
                    ),
                    version = obj.getInt("version")
                )
            )
        }

        return result
    }

    fun clear() {
        prefs.edit().remove(key).apply()
        Log.i("MeshNet", "ThreatStore cleared")
    }
}
