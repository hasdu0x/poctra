-keepattributes *Annotation*
-allowaccessmodification
-adaptclassstrings
-renamesourcefileattribute SourceFile

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepnames class com.poctra.wallet.privacy.NativePvacBridge
-keepclassmembers class com.poctra.wallet.privacy.NativePvacBridge {
    native <methods>;
}
