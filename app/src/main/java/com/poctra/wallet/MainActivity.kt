package com.poctra.wallet

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poctra.wallet.ui.PoctraApp
import com.poctra.wallet.ui.PoctraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { PoctraController(this) }
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
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            PoctraTheme(darkTheme = controller.darkMode) {
                PoctraApp(activity = this, controller = controller)
            }
        }
    }
}
