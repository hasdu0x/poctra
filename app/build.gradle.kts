import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val releaseSigningPropertiesFile = providers.environmentVariable("POCTRA_SIGNING_PROPERTIES")
    .map(::file)
    .getOrElse(file("${System.getProperty("user.home")}/.poctra/release-signing.properties"))
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use { input -> load(input) }
    }
}
val releaseSigningPropertyNames = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseSigningReady = releaseSigningPropertiesFile.isFile &&
    releaseSigningPropertyNames.all { releaseSigningProperties.getProperty(it).isNullOrBlank().not() }

val playCloudProjectNumber = providers.gradleProperty("POCTRA_PLAY_CLOUD_PROJECT_NUMBER")
    .orElse("0")
    .get()
    .trim()
    .toLongOrNull()
    ?: 0L
val allowedSignerSha256 = providers.gradleProperty("POCTRA_ALLOWED_SIGNER_SHA256")
    .orElse("")
    .get()
    .trim()
val securityBackendUrl = providers.gradleProperty("POCTRA_SECURITY_BACKEND_URL")
    .orElse("https://rpc.xme.my.id")
    .get()
    .trimEnd('/')

android {
    namespace = "com.poctra.wallet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.poctra.wallet"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"

        buildConfigField("long", "PLAY_CLOUD_PROJECT_NUMBER", "${playCloudProjectNumber}L")
        buildConfigField("String", "ALLOWED_SIGNER_SHA256", allowedSignerSha256.asBuildConfigString())
        buildConfigField("String", "SECURITY_BACKEND_URL", securityBackendUrl.asBuildConfigString())

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SECURITY_HARDENING_ENABLED", "false")
        }
        release {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("boolean", "SECURITY_HARDENING_ENABLED", "true")
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Checks that production release signing is configured outside the repository."
    doLast {
        if (!releaseSigningPropertiesFile.isFile) {
            throw GradleException(
                "Release signing config not found at ${releaseSigningPropertiesFile.absolutePath}. " +
                    "Set POCTRA_SIGNING_PROPERTIES to use another protected file.",
            )
        }
        val missingProperties = releaseSigningPropertyNames.filter {
            releaseSigningProperties.getProperty(it).isNullOrBlank()
        }
        if (missingProperties.isNotEmpty()) {
            throw GradleException(
                "Release signing config is missing: ${missingProperties.joinToString()}.",
            )
        }
        val configuredStore = file(releaseSigningProperties.getProperty("storeFile"))
        if (!configuredStore.isFile) {
            throw GradleException("Release keystore not found at ${configuredStore.absolutePath}.")
        }
    }
}

tasks.matching {
    it.name in setOf("assembleRelease", "bundleRelease", "installRelease")
}.configureEach {
    dependsOn(verifyReleaseSigning)
    dependsOn("verifyReleaseSecurity")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.6.7")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("com.google.android.play:integrity:1.6.0")
    
    // MPAndroidChart for advanced trading charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.7")
}

tasks.register("verifyReleaseSecurity") {
    group = "verification"
    description = "Checks required release attestation and backend settings before signing."
    doLast {
        if (playCloudProjectNumber <= 0L) {
            throw GradleException("POCTRA_PLAY_CLOUD_PROJECT_NUMBER must be configured for a production release.")
        }
        val signerDigests = allowedSignerSha256
            .split(',', ';', ' ', '\n')
            .map { it.filter(Char::isLetterOrDigit).uppercase() }
            .filter(String::isNotBlank)
        if (signerDigests.isEmpty() || signerDigests.any { !it.matches(Regex("[0-9A-F]{64}")) }) {
            throw GradleException("POCTRA_ALLOWED_SIGNER_SHA256 must contain valid Play App Signing SHA-256 hex digest(s).")
        }
        if (!securityBackendUrl.startsWith("https://")) {
            throw GradleException("POCTRA_SECURITY_BACKEND_URL must use HTTPS for a production release.")
        }
    }
}
