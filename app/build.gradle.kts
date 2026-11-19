plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.morninglock.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.morninglock.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // A shared debug key, committed on purpose (Android's standard public password "android"),
    // so every build, local or on GitHub Actions, is signed the same way and a new release
    // installs over the old one. It is not used for anything secret.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
