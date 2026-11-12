import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shayor.apkextractor"
    compileSdk {
        version = release(37)
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.shayor.apkextractor"
        minSdk = 24
        targetSdk = 37
        versionCode = 3

        versionName = "1.5.0"

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
    // Backdrop blur for the iOS-style glass bars (RenderEffect on Android 12+, a tinted scrim before).
    implementation(libs.haze)
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
// ---- ARSCLib framework stubs ----------------------------------------------------------------
// ARSCLib ships its own copies of android.content.res.XmlResourceParser, android.util.AttributeSet
// and org.xmlpull.v1.* so it can run on a plain JVM. On Android they shadow the framework types: R8's whole-program
// optimization treats them as app classes, finds nothing in the app that implements them, and
// "proves" every XmlResourceParser is null. It then replaced the result of PackageManager.getXml /
// loadXmlMetaData with null, which broke FileProvider (and so Share) in release builds only.
// Strip the stubs from the jar so R8 sees the real framework classes.
val androidStubsStripped = Attribute.of("com.shayor.apkextractor.androidStubsStripped", Boolean::class.javaObjectType)

abstract class StripAndroidStubs : TransformAction<TransformParameters.None> {
    private companion object {
        /** Packages the Android framework already provides (the device's copies always win at runtime). */
        val FRAMEWORK_PREFIXES = listOf("android/", "org/xmlpull/")
    }

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        if (!input.name.startsWith("ARSCLib-")) {
            outputs.file(input)
            return
        }
        val output = outputs.file("${input.nameWithoutExtension}-no-android-stubs.jar")
        ZipInputStream(input.inputStream().buffered()).use { zin ->
            ZipOutputStream(output.outputStream().buffered()).use { zout ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    if (FRAMEWORK_PREFIXES.any(entry.name::startsWith)) continue
                    zout.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    zin.copyTo(zout)
                    zout.closeEntry()
                }
            }
        }
    }
}

dependencies {
    attributesSchema { attribute(androidStubsStripped) }
    artifactTypes.getByName("jar") { attributes.attribute(androidStubsStripped, false) }
    registerTransform(StripAndroidStubs::class) {
        from.attribute(androidStubsStripped, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(androidStubsStripped, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }
}

// App variants only: JVM unit tests have no Android framework and need ARSCLib's own copies.
configurations.configureEach {
    if (isCanBeResolved && "Test" !in name && (name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath"))) {
        attributes.attribute(androidStubsStripped, true)
    }
}
