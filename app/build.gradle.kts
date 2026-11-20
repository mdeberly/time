plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.eink.horlogemeteo"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.eink.horlogemeteo"
        minSdk = 26
        // Android 9 : seule façon pour une appli de couper/allumer le Wi-Fi et le Bluetooth elle-même.
        targetSdk = 28
        versionCode = 9
        versionName = "2.1"
    }

    // Clé de signature fixe : chaque nouvelle version peut s'installer par-dessus la précédente.
    signingConfigs {
        create("fixe") {
            storeFile = file("horlogemeteo.keystore")
            storePassword = "horlogemeteo"
            keyAlias = "horlogemeteo"
            keyPassword = "horlogemeteo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixe")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixe")
        }
    }

    lint {
        // targetSdk volontairement ancien (voir defaultConfig) : appli installée hors Play Store.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        checkReleaseBuilds = false
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
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
