package com.poctra.wallet.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActiveRpcEndpointTest {
    @Test
    fun `request before active network is configured fails closed`() {
        val endpoint = ActiveRpcEndpoint("Test")

        val error = assertThrows(IllegalStateException::class.java) {
            endpoint.requireUrl()
        }

        assertEquals(
            "Test RPC is not configured. Select an active network in Settings and try again.",
            error.message,
        )
    }

    @Test
    fun `configured endpoint is normalized and returned`() {
        val endpoint = ActiveRpcEndpoint("Test")

        endpoint.configure("  https://rpc.example.org///  ")

        assertEquals("https://rpc.example.org", endpoint.requireUrl())
    }

    @Test
    fun `changing active endpoint replaces the previous request target`() {
        val endpoint = ActiveRpcEndpoint("Test")
        endpoint.configure("https://first.example")

        endpoint.configure("https://second.example/rpc")

        assertEquals("https://second.example/rpc", endpoint.requireUrl())
    }

    @Test
    fun `blank endpoint cannot silently fall back`() {
        val endpoint = ActiveRpcEndpoint("Test")

        assertThrows(IllegalArgumentException::class.java) {
            endpoint.configure("   ")
        }
    }

    @Test
    fun `non HTTP endpoint is rejected`() {
        val endpoint = ActiveRpcEndpoint("Test")

        assertThrows(IllegalArgumentException::class.java) {
            endpoint.configure("file:///tmp/rpc")
        }
    }
}
