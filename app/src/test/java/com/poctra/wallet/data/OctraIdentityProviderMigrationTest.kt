package com.poctra.wallet.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OctraIdentityProviderMigrationTest {
    @Test
    fun keepsContractProviders() {
        assertFalse(
            OctraIdentityProviderRepository.isLegacyNativeCircleProvider(
                type = "contract",
                id = "identity-devnet-contract",
                address = "oct6wwc1bPHF4ZMnBjDGYWbfqKgymwxNcqqqYocutYFbQUY",
            ),
        )
    }

    @Test
    fun removesLegacyNativeCircleProviders() {
        assertTrue(
            OctraIdentityProviderRepository.isLegacyNativeCircleProvider(
                type = "native_circle",
            ),
        )
        assertTrue(
            OctraIdentityProviderRepository.isLegacyNativeCircleProvider(
                type = "",
                id = "identity-devnet-native-ons-circle",
            ),
        )
    }
}
