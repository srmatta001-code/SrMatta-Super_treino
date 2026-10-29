plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "srmatta-release"
val releaseKeystore = file("srmatta-release.jks")
val hasReleaseSigning = !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() && releaseKeystore.exists()

android {
    namespace = "br.srmatta.treino"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.srmatta.treino"
        minSdk = 23
        targetSdk = 34
        versionCode = 8
        versionName = "1.6.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

dependencies {
    implementation("androidx.core:core:1.13.1")
}
