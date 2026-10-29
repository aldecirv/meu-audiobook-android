plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Keystore fixo fornecido pelo CI (mesma chave usada nos outros apps).
val auroraKeystorePath: String? = System.getenv("AURORA_KEYSTORE_PATH")

android {
    namespace = "com.meuaudiobook.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meuaudiobook.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (auroraKeystorePath != null) {
            create("release") {
                storeFile = file(auroraKeystorePath)
                storePassword = System.getenv("AURORA_KEYSTORE_PASS")
                keyAlias = System.getenv("AURORA_KEY_ALIAS")
                keyPassword = System.getenv("AURORA_KEY_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (auroraKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (auroraKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
}
