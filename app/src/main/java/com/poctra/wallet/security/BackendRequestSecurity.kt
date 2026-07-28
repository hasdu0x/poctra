package com.poctra.wallet.security

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.poctra.wallet.BuildConfig
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class PlayIntegrityStatus {
    Disabled,
    Preparing,
    Ready,
    Unavailable,
}

object PlayIntegrityState {
    @Volatile
    var status: PlayIntegrityStatus = if (BuildConfig.PLAY_CLOUD_PROJECT_NUMBER > 0L) {
        PlayIntegrityStatus.Preparing
    } else {
        PlayIntegrityStatus.Disabled
    }
        private set

    @Volatile
    var lastError: String? = null
        private set

    internal fun update(next: PlayIntegrityStatus, error: Throwable? = null) {
        status = next
        lastError = error?.javaClass?.simpleName
    }
}

class BackendRequestSecurity private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val integrityManager by lazy { IntegrityManagerFactory.createStandard(appContext) }
    private val managedBackendHost = runCatching { URI(BuildConfig.SECURITY_BACKEND_URL).host.orEmpty().lowercase() }
        .getOrDefault("")

    @Volatile
    private var tokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    @Volatile
    private var prepareTask: Task<StandardIntegrityManager.StandardIntegrityTokenProvider>? = null

    fun warmUp() {
        if (!integrityEnabled() || tokenProvider != null) return
        synchronized(this) {
            if (prepareTask != null || tokenProvider != null) return
            PlayIntegrityState.update(PlayIntegrityStatus.Preparing)
            val task = integrityManager.prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(BuildConfig.PLAY_CLOUD_PROJECT_NUMBER)
                    .build(),
            )
            prepareTask = task
            task.addOnSuccessListener { provider ->
                tokenProvider = provider
                prepareTask = null
                PlayIntegrityState.update(PlayIntegrityStatus.Ready)
            }.addOnFailureListener { error ->
                prepareTask = null
                PlayIntegrityState.update(PlayIntegrityStatus.Unavailable, error)
            }
        }
    }

    fun applyTo(
        connection: HttpURLConnection,
        url: String,
        requestBody: String,
        highValueAction: Boolean,
    ) {
        if (!isManagedBackend(url)) return
        val requestHash = requestHash(requestBody)
        connection.setRequestProperty(HeaderPackage, appContext.packageName)
        connection.setRequestProperty(HeaderVersion, BuildConfig.VERSION_NAME)
        RuntimeSecurity.signerSha256Digests(appContext).firstOrNull()?.let {
            connection.setRequestProperty(HeaderSigner, it)
        }
        connection.setRequestProperty(HeaderRequestHash, requestHash)

        if (!highValueAction || !integrityEnabled()) return
        val token = requestToken(requestHash) ?: return
        connection.setRequestProperty(HeaderIntegrity, token)
    }

    private fun requestToken(requestHash: String): String? {
        val provider = tokenProvider ?: awaitPreparedProvider() ?: return null
        return runCatching {
            val request = StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
            Tasks.await(provider.request(request), IntegrityRequestTimeoutSeconds, TimeUnit.SECONDS).token()
        }.onFailure { error ->
            tokenProvider = null
            PlayIntegrityState.update(PlayIntegrityStatus.Unavailable, error)
            warmUp()
        }.getOrNull()
    }

    private fun awaitPreparedProvider(): StandardIntegrityManager.StandardIntegrityTokenProvider? {
        warmUp()
        tokenProvider?.let { return it }
        val task = prepareTask ?: return null
        return runCatching {
            Tasks.await(task, IntegrityPrepareTimeoutSeconds, TimeUnit.SECONDS).also { provider ->
                tokenProvider = provider
                PlayIntegrityState.update(PlayIntegrityStatus.Ready)
            }
        }.onFailure { error ->
            PlayIntegrityState.update(PlayIntegrityStatus.Unavailable, error)
        }.getOrNull()
    }

    private fun integrityEnabled(): Boolean =
        BuildConfig.SECURITY_HARDENING_ENABLED && BuildConfig.PLAY_CLOUD_PROJECT_NUMBER > 0L

    private fun isManagedBackend(url: String): Boolean {
        if (managedBackendHost.isBlank()) return false
        return runCatching { URI(url).host.orEmpty().equals(managedBackendHost, ignoreCase = true) }
            .getOrDefault(false)
    }

    companion object {
        private const val IntegrityRequestTimeoutSeconds = 8L
        private const val IntegrityPrepareTimeoutSeconds = 6L
        const val HeaderPackage = "X-Poctra-Package"
        const val HeaderVersion = "X-Poctra-Version"
        const val HeaderSigner = "X-Poctra-Signer"
        const val HeaderRequestHash = "X-Poctra-Request-Hash"
        const val HeaderIntegrity = "X-Poctra-Integrity"

        @Volatile
        private var instance: BackendRequestSecurity? = null

        fun get(context: Context): BackendRequestSecurity = instance ?: synchronized(this) {
            instance ?: BackendRequestSecurity(context).also { instance = it }
        }

        fun requestHash(body: String): String = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }
}
