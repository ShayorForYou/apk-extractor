plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.extractor"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.extractor"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI signs releases with the key from repository secrets (see .github/workflows/release.yml). Without these
    // variables, e.g. in Android Studio, release builds stay unsigned and the "Generate Signed APK" wizard is used.
    val releaseKeystore = System.getenv("SIGNING_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                // Whole-program optimization. A partial packageScope made R8 move app/library
                // classes out of their package while they still extended package-private
                // kotlin.collections classes -> IllegalAccessError at startup.
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.all { test ->
            // ./gradlew testDebugUnitTest -PsplitsDir=/path/to/split/apps
            listOf("splitsDir", "mergeOut", "testKeystore").forEach { key ->
                providers.gradleProperty(key).orNull?.let { test.systemProperty(key, it) }
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ---- APK size ---------------------------------------------------------------------------
    androidResources {
        // The UI is English-only; drop the translations bundled by AndroidX libraries.
        localeFilters += "en"
    }
    packaging {
        resources {
            excludes += listOf(
                // ARSCLib bundles an android.jar resource table per API level (~2.4 MB). ApkMerger
                // loads modules with frameworks disabled (merging is ID-based), so none are needed.
                "frameworks/**",
                // Build metadata that is never read at runtime.
                "META-INF/**/LICENSE.txt",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin/**",
            )
        }
    }
    dependenciesInfo {
        // Dependency metadata is only for Play's security scanning; not needed in the APK.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    // Split APK merging (resource table merge) + re-signing.
    implementation(libs.arsclib)
    implementation(libs.apksig)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}