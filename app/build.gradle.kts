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
        versionCode = 10
        versionName = "2.2"
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

// Garde-fou : un widget n'accepte que certaines vues ; sinon « Impossible de charger le widget ».
val checkWidgetLayouts by tasks.registering {
    val dir = file("src/main/res/layout")
    doLast {
        val allowed = setOf(
            "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout", "AnalogClock", "Button",
            "Chronometer", "ImageButton", "ImageView", "ProgressBar", "TextView", "ViewFlipper",
            "ListView", "GridView", "StackView", "AdapterViewFlipper", "TextClock", "ViewStub", "merge", "include",
        )
        dir.listFiles { f -> f.name.startsWith("widget_") }?.forEach { f ->
            Regex("<([A-Za-z.]+)").findAll(f.readText()).map { it.groupValues[1] }
                .filter { it !in allowed && it != "?xml" }
                .forEach { throw GradleException("${f.name} : <$it> n'est pas autorisé dans un widget") }
        }
    }
}
tasks.named("preBuild") { dependsOn(checkWidgetLayouts) }
