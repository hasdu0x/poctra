package com.poctra.wallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poctra.wallet.ui.PoctraApp
import com.poctra.wallet.ui.PoctraLaunchDisplayMillis
import com.poctra.wallet.ui.PoctraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepNativeLaunchAnimationVisible()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContent {
            val controller = remember { PoctraController(this) }
            LaunchedEffect(Unit) {
                intent?.data?.let { uri -> controller.handleDeepLink(uri) }
            }
            DisposableEffect(controller) {
                val callback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (!controller.handleBack()) {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
                val lifecycleObserver = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && !isChangingConfigurations) {
                        controller.lockForBackground()
                    }
                }
                onBackPressedDispatcher.addCallback(this@MainActivity, callback)
                lifecycle.addObserver(lifecycleObserver)
                onDispose {
                    callback.remove()
                    lifecycle.removeObserver(lifecycleObserver)
                    controller.dispose()
                }
            }
            DisposableEffect(controller.secureWindowRequired) {
                if (controller.secureWindowRequired) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(false)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(false)
                }
            }
            PoctraTheme(darkTheme = true) {
                PoctraApp(activity = this, controller = controller)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun keepNativeLaunchAnimationVisible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val decorView = window.decorView
        val startedAt = SystemClock.uptimeMillis()
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (SystemClock.uptimeMillis() - startedAt < PoctraLaunchDisplayMillis) return false
                if (decorView.viewTreeObserver.isAlive) {
                    decorView.viewTreeObserver.removeOnPreDrawListener(this)
                }
                return true
            }
        }
        decorView.viewTreeObserver.addOnPreDrawListener(listener)
        decorView.postDelayed({ decorView.invalidate() }, PoctraLaunchDisplayMillis)
    }
}
