plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.srmatta.treino"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.srmatta.treino"
        minSdk = 23          // Android 6.0 ou superior (o J7 Prime 2 roda 7.1 / 8.1)
        targetSdk = 34
        versionCode = 8
        versionName = "1.6.0"
    }

    // Chave fixa: todo APK novo tem a mesma assinatura, então atualiza por cima sem perder dados.
    signingConfigs {
        create("fixed") {
            storeFile = file("srmatta.jks")
            storePassword = "srmatta123"
            keyAlias = "srmatta"
            keyPassword = "srmatta123"
        }
    }

    buildTypes {
        release {
            // R8 encolhe o código e os recursos: o APK final fica pequeno.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assina com a chave fixa do projeto para o APK ser instalável direto no celular.
            signingConfig = signingConfigs.getByName("fixed")
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
