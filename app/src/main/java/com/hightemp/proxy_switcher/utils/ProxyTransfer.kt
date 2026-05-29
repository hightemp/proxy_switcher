package com.hightemp.proxy_switcher.utils

import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.local.ProxyType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes/deserializes proxy lists to a plain JSON text format.
 * The text can be copied to chat or saved to a file, and re-imported later.
 */
object ProxyTransfer {

    /** Pretty-printed JSON array representing the given proxies. */
    fun export(proxies: List<ProxyEntity>): String {
        val array = JSONArray()
        proxies.forEach { proxy ->
            val obj = JSONObject()
            obj.put("host", proxy.host)
            obj.put("port", proxy.port)
            obj.put("type", proxy.type.name)
            proxy.username?.let { obj.put("username", it) }
            proxy.password?.let { obj.put("password", it) }
            proxy.label?.let { obj.put("label", it) }
            obj.put("isEnabled", proxy.isEnabled)
            array.put(obj)
        }
        return array.toString(2)
    }

    data class ImportResult(
        val proxies: List<ProxyEntity>,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean get() = errorMessage == null
    }

    /** Parses a JSON array of proxies. New entities always get a fresh id (id = 0). */
    fun import(text: String): ImportResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ImportResult(emptyList(), "Input is empty")
        }
        return try {
            val array = JSONArray(trimmed)
            val result = mutableListOf<ProxyEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val host = obj.optString("host").trim()
                val port = obj.optInt("port", -1)
                if (host.isEmpty() || port !in 1..65535) continue
                val type = runCatching {
                    ProxyType.valueOf(obj.optString("type", "HTTP").trim().uppercase())
                }.getOrDefault(ProxyType.HTTP)
                result.add(
                    ProxyEntity(
                        host = host,
                        port = port,
                        type = type,
                        username = obj.optString("username").ifBlank { null },
                        password = obj.optString("password").ifBlank { null },
                        label = obj.optString("label").ifBlank { null },
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
            if (result.isEmpty()) {
                ImportResult(emptyList(), "No valid proxies found")
            } else {
                ImportResult(result)
            }
        } catch (e: Exception) {
            ImportResult(emptyList(), "Invalid format: ${e.message}")
        }
    }
}
