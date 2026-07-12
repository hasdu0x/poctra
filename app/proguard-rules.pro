-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.poctra.wallet.privacy.NativePvacBridge {
    *;
}
