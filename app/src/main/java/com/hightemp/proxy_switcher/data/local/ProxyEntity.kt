package com.hightemp.proxy_switcher.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProxyType {
    HTTP,
    HTTPS,
    SOCKS5
}

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val type: ProxyType,
    val username: String? = null,
    val password: String? = null,
    val label: String? = null,
    val isEnabled: Boolean = true
)