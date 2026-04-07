package com.hightemp.proxy_switcher.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyFormValidatorTest {
    @Test
    fun validate_acceptsBlankPasswordWhenUsernameIsSet() {
        val result = ProxyFormValidator.validate(
            hostInput = " proxy.example.com ",
            portInput = "1080",
            usernameInput = "alice",
            passwordInput = ""
        )

        assertTrue(result.isValid)
        assertEquals("proxy.example.com", result.host)
        assertEquals(1080, result.port)
        assertEquals("alice", result.username)
        assertEquals("", result.password)
    }

    @Test
    fun validate_rejectsInvalidPortInsteadOfDefaulting() {
        val result = ProxyFormValidator.validate(
            hostInput = "proxy.example.com",
            portInput = "not-a-port",
            usernameInput = "",
            passwordInput = ""
        )

        assertFalse(result.isValid)
        assertEquals("Port must be a number", result.errorMessage)
    }

    @Test
    fun validate_rejectsPasswordWithoutUsername() {
        val result = ProxyFormValidator.validate(
            hostInput = "proxy.example.com",
            portInput = "8080",
            usernameInput = "",
            passwordInput = "secret"
        )

        assertFalse(result.isValid)
        assertEquals("Username is required when password is set", result.errorMessage)
        assertNull(result.username)
        assertNull(result.password)
    }
}
