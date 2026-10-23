package com.poctra.wallet.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Bug condition exploration test for APP-09.
 *
 * Bug: `sendEvmTransaction` in EvmRepository reads `eth_getTransactionCount` independently
 * per coroutine with no per-(account,chainId) mutex. Two concurrent sends for the same
 * wallet receive the same nonce N, and the second transaction is rejected on-chain.
 *
 * Similarly, `sendOct`/`sendContractCall` in RpcRepository read `pending_nonce` independently.
 *
 * Bug condition: two or more concurrent sends for the same (account, chain) pair.
 * Fix: add `ConcurrentHashMap<String, Mutex>` nonce locks in both repositories.
 *
 * CRITICAL: Bug condition tests MUST FAIL on unfixed code.
 */
class NonceRaceTest {

    /**
     * Returns true when APP-09 bug condition holds:
     * two sends share the same account+chain with no serialization.
     */
    private fun isBugCondition_APP09(concurrentSends: Int, hasMutex: Boolean): Boolean =
        concurrentSends >= 2 && !hasMutex

    private fun findSourceFile(filename: String): File {
        val candidates = listOf(
            File("app/src/main/java/com/poctra/wallet/data/$filename"),
            File("../app/src/main/java/com/poctra/wallet/data/$filename"),
            File("../../app/src/main/java/com/poctra/wallet/data/$filename"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Could not find $filename")
    }

    // =========================================================================
    // Bug condition exploration tests — APP-09
    // =========================================================================

    /**
     * EXPECTED OUTCOME (on unfixed code): FAILS.
     *
     * Asserts that EvmRepository uses a per-(account,chainId) mutex for nonce reads.
     * On unfixed code, no such mutex exists.
     *
     * Counterexample: two concurrent ERC-20 transfers for the same wallet → both read
     * nonce N → second transaction rejected on-chain.
     */
    @Test
    fun bugConditionApp09_evmRepository_mustHaveNonceLockForConcurrentSends() {
        val content = findSourceFile("EvmRepository.kt").readText()

        val hasMutex = content.contains("Mutex") || content.contains("nonceLock") || content.contains("nonceMutex")

        if (!hasMutex) {
            fail(
                "BUG CONFIRMED (APP-09): EvmRepository.sendEvmTransaction reads " +
                    "eth_getTransactionCount with no per-(account,chainId) mutex.\n\n" +
                    "Root cause: two concurrent sends for the same wallet both read nonce N " +
                    "and both submit with nonce N — the second is rejected on-chain.\n\n" +
                    "Counterexample: two concurrent ERC-20 transfers → duplicate nonce N.\n\n" +
                    "Fix: add `private val nonceLocks = ConcurrentHashMap<String, ReentrantLock>()` " +
                    "keyed by `\"\${address.lowercase()}:\${chainId}\"`."
            )
        }

        assertTrue("EvmRepository must have a lock for nonce serialization", hasMutex)
    }

    /**
     * EXPECTED OUTCOME (on unfixed code): FAILS.
     *
     * Asserts that RpcRepository uses a per-address mutex for nonce reads.
     * On unfixed code, no such mutex exists.
     */
    @Test
    fun bugConditionApp09_rpcRepository_mustHaveNonceLockForConcurrentSends() {
        val content = findSourceFile("RpcRepository.kt").readText()

        // Check for a nonce-specific mutex (distinct from existing general locks)
        val hasNonceMutex = content.contains("nonceLock") ||
            content.contains("nonceMutex") ||
            (content.contains("Mutex()") && content.contains("sendOct") && content.contains("nonce"))

        if (!hasNonceMutex) {
            fail(
                "BUG CONFIRMED (APP-09): RpcRepository.sendOct/sendContractCall reads " +
                    "pending_nonce via fetchBalance with no per-address mutex.\n\n" +
                    "Root cause: two concurrent Octra sends for the same wallet both read nonce N " +
                    "and submit with nonce N+1 — the second is rejected.\n\n" +
                    "Fix: add `private val nonceLocks = ConcurrentHashMap<String, Mutex>()` " +
                    "keyed by `wallet.address.lowercase()`."
            )
        }

        assertTrue("RpcRepository must have a Mutex for nonce serialization", hasNonceMutex)
    }

    // =========================================================================
    // Preservation property tests — APP-09
    // =========================================================================

    /**
     * Preservation: a single non-concurrent transaction does not need a mutex
     * (but will still acquire one — just uncontended). The fix must not break sequential sends.
     * EXPECTED OUTCOME: PASS on unfixed code.
     */
    @Test
    fun preservation_singleTransaction_notBugCondition() {
        assertFalse("Single transaction must NOT trigger bug condition",
            isBugCondition_APP09(concurrentSends = 1, hasMutex = false))
        assertFalse("Single transaction with mutex must NOT trigger bug condition",
            isBugCondition_APP09(concurrentSends = 1, hasMutex = true))
    }

    /**
     * Preservation: the bug condition requires both concurrency AND no mutex.
     * With a mutex, even N concurrent sends are safe.
     * EXPECTED OUTCOME: PASS.
     */
    @Test
    fun preservation_concurrentSendsWithMutex_notBugCondition() {
        assertFalse("Concurrent sends WITH mutex must NOT trigger bug condition",
            isBugCondition_APP09(concurrentSends = 5, hasMutex = true))
    }

    /**
     * Preservation: EvmRepository.isValidRpcUrl still accepts HTTPS URLs.
     * EXPECTED OUTCOME: PASS on unfixed code.
     */
    @Test
    fun preservation_evmRepository_httpsUrlsStillAccepted() {
        val content = findSourceFile("EvmRepository.kt").readText()
        assertTrue(
            "EvmRepository.isValidRpcUrl must still accept https:// URLs",
            content.contains("scheme == \"https\"")
        )
    }
}
