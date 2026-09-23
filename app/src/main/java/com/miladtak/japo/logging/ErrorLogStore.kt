package com.miladtak.japo.logging

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ErrorLogStore(context: Context) {
    private val file = context.getFileStreamPath("error_log.json")

    @Synchronized
    fun add(component: String, message: String, throwable: Throwable? = null) {
        val logs = read().toMutableList()
        logs.add(ErrorLog(System.currentTimeMillis(), component, message, throwable?.stackTraceToString()))
        while (logs.size > 200) logs.removeAt(0)
        write(logs)
    }

    @Synchronized
    fun read(): List<ErrorLog> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(ErrorLog(
                        o.getLong("time"),
                        o.getString("component"),
                        o.getString("message"),
                        o.optString("stackTrace").ifBlank { null }
                    ))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun clear() { if (file.exists()) file.delete() }

    private fun write(logs: List<ErrorLog>) {
        val array = JSONArray()
        logs.forEach {
            array.put(JSONObject().apply {
                put("time", it.time)
                put("component", it.component)
                put("message", it.message)
                put("stackTrace", it.stackTrace ?: "")
            })
        }
        file.writeText(array.toString())
    }
}
