package com.hightemp.proxy_switcher.ui.screens

data class ProxyFormValidationResult(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val errorMessage: String?
) {
    val isValid: Boolean = errorMessage == null
}

object ProxyFormValidator {
    fun validate(
        hostInput: String,
        portInput: String,
        usernameInput: String,
        passwordInput: String
    ): ProxyFormValidationResult {
        val host = hostInput.trim()
        val portText = portInput.trim()
        val username = usernameInput.trim().ifBlank { null }
        val password = if (username == null) null else passwordInput

        val error = when {
            host.isBlank() -> "Host is required"
            host.any { it.isWhitespace() } -> "Host must not contain whitespace"
            portText.isBlank() -> "Port is required"
            portText.toIntOrNull() == null -> "Port must be a number"
            portText.toInt() !in 1..65535 -> "Port must be between 1 and 65535"
            username == null && passwordInput.isNotEmpty() -> "Username is required when password is set"
            else -> null
        }

        return ProxyFormValidationResult(
            host = host,
            port = portText.toIntOrNull() ?: 0,
            username = username,
            password = password,
            errorMessage = error
        )
    }
}
