package com.poctra.wallet.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RpcEndpointTest {
    @Test
    fun `appends rpc path to an Octra base URL`() {
        assertEquals(
            "https://gateway.example/octra-devnet/rpc",
            octraRpcEndpoint("https://gateway.example/octra-devnet"),
        )
    }

    @Test
    fun `preserves a complete custom RPC endpoint`() {
        assertEquals(
            "https://custom.example/rpc",
            octraRpcEndpoint("https://custom.example/rpc/"),
        )
    }

    @Test
    fun `preserves the Octrascan devnet RPC option`() {
        assertEquals(
            RpcRepository.DefaultDevnetUrl,
            octraRpcEndpoint(RpcRepository.DefaultDevnetUrl),
        )
    }
}
