package com.poctra.wallet.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmDAppRpcPolicyTest {
    @Test
    fun allowsReadOnlyRpcMethodsUsedByDApps() {
        assertTrue(EvmDAppRpcPolicy.canForward("eth_call"))
        assertTrue(EvmDAppRpcPolicy.canForward("eth_estimateGas"))
        assertTrue(EvmDAppRpcPolicy.canForward("eth_getTransactionReceipt"))
        assertTrue(EvmDAppRpcPolicy.canForward("eth_feeHistory"))
    }

    @Test
    fun blocksSigningBroadcastAndSubscriptionMethods() {
        assertFalse(EvmDAppRpcPolicy.canForward("web3_clientVersion"))
        assertFalse(EvmDAppRpcPolicy.canForward("eth_sendRawTransaction"))
        assertFalse(EvmDAppRpcPolicy.canForward("eth_sendTransaction"))
        assertFalse(EvmDAppRpcPolicy.canForward("eth_sign"))
        assertFalse(EvmDAppRpcPolicy.canForward("personal_sign"))
        assertFalse(EvmDAppRpcPolicy.canForward("eth_subscribe"))
        assertFalse(EvmDAppRpcPolicy.canForward("wallet_addEthereumChain"))
    }
}
