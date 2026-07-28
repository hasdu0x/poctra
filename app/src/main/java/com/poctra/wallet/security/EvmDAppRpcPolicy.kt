package com.poctra.wallet.security

object EvmDAppRpcPolicy {
    private val readOnlyMethods = setOf(
        "eth_blockNumber",
        "eth_call",
        "eth_estimateGas",
        "eth_feeHistory",
        "eth_gasPrice",
        "eth_getBalance",
        "eth_getBlockByHash",
        "eth_getBlockByNumber",
        "eth_getBlockTransactionCountByHash",
        "eth_getBlockTransactionCountByNumber",
        "eth_getCode",
        "eth_getLogs",
        "eth_getProof",
        "eth_getStorageAt",
        "eth_getTransactionByBlockHashAndIndex",
        "eth_getTransactionByBlockNumberAndIndex",
        "eth_getTransactionByHash",
        "eth_getTransactionCount",
        "eth_getTransactionReceipt",
        "eth_maxPriorityFeePerGas",
        "eth_syncing",
        "net_listening",
        "net_peerCount",
        "web3_sha3",
    )

    fun canForward(method: String): Boolean = method in readOnlyMethods
}
