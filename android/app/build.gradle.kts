import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release builds make one APK per processor type (see splits below); run them on their own, not together with debug.
val releaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

android {
    // Nexonic's profile listers for the video engine (engine/nexonic_profiles.py), shared with the desktop apps.
    sourceSets["main"].assets.srcDir("../../engine")
    namespace = "com.nexonic.downloadmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexonic.downloadmanager.personal"
        minSdk = 29
        targetSdk = 35
        // Same version as the Mac and Windows apps (VERSION at the repository root), e.g. 0.6.7 -> code 607.
        val version = rootProject.file("../VERSION").readText().trim()
        val (major, minor, patch) = version.split(".").map { it.takeWhile(Char::isDigit).ifEmpty { "0" }.toInt() }
        // Dedicated Personal package: increment this code for all future in-place updates.
        versionCode = 60200
        versionName = "0.6.2"
        // The video engine (Python, yt-dlp, ffmpeg) is native code. Debug builds: phones (ARM) plus x86_64 for emulators.
        // Release builds pick their processor types with the splits below (Gradle refuses both at once).
        if (!releaseBuild) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // Only the app's own languages: the libraries' texts in about 80 other languages are left out of the APK.
        resourceConfigurations += listOf("en", "ar", "de", "es", "fr", "hi", "in", "ja", "ko", "pt", "ru", "tr", "ur", "zh-rCN", "zh-rTW")
    }

    // The engine is about 50 MB per processor type, so the release is one APK per type: arm64 (almost every phone)
    // and 32-bit ARM (older phones). Debug builds stay a single APK for every type (emulators, screenshots).
    splits {
        abi {
            isEnable = releaseBuild
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    // Release signing comes from the environment (GitHub secrets); keys are never stored in the repository.
    val keystore = System.getenv("NDM_ANDROID_KEYSTORE")
    signingConfigs {
        if (!keystore.isNullOrEmpty() && file(keystore).exists()) {
            create("release") {
                storeFile = file(keystore)
                storeType = "pkcs12"
                storePassword = System.getenv("NDM_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NDM_ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("NDM_ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Side-by-side QA package: never replace existing .debug installs or delete their data.
            applicationIdSuffix = ".qa2"
            versionNameSuffix = "-qa"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // The video engine runs its programs from the installed native libraries, so they must be unpacked.
        jniLibs { useLegacyPackaging = true }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Video engine: yt-dlp (with Python) and ffmpeg, the same engine as the Mac and Windows apps.
    val youtubedlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    // FFmpeg is not taken from youtubedl-android (a 78 MB general-purpose build): scripts/build-ffmpeg.sh builds a
    // re-packing-only one into src/main/jniLibs as libffmpeg.so and libffprobe.so, where the library looks for it.
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // The real org.json for unit tests (the Android one is only a stub there).
    testImplementation("org.json:json:20240303")
}

/**
 * AndroidIDE / Android Studio normally build directly from source, while GitHub
 * Actions historically injected this file before invoking Gradle. Prepare it
 * here too, and verify it before packaging any APK.
 *
 * The APK still builds in debug mode without connectivity: in that case the
 * verified offline importer remains available instead of crashing on startup.
 * Release artifacts are never allowed to ship without the pinned engine.
 */
val pinnedYtDlp = layout.projectDirectory.file("src/main/assets/verified-yt-dlp-2026.10.26").asFile
val pinnedYtDlpSha = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
val preparePinnedYtDlp = tasks.register("preparePinnedYtDlp") {
    group = "build setup"
    description = "Fetch and SHA-256 verify yt-dlp for all Android build environments"
    doLast {
        fun verified(file: File): Boolean {
            if (!file.isFile || file.length() != 3_073_072L) return false
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(bytes)
                    if (n < 0) break
                    hash.update(bytes, 0, n)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == pinnedYtDlpSha
        }
        if (pinnedYtDlp.exists()) {
            if (!verified(pinnedYtDlp)) throw GradleException("Bundled yt-dlp exists but has the wrong SHA-256; refusing to package it")
        } else {
            val partial = File(pinnedYtDlp.parentFile, pinnedYtDlp.name + ".download")
            try {
                pinnedYtDlp.parentFile.mkdirs()
                val connection = (URI(
                    "https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp"
                ).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "NexonicPersonalGradle/0.6.1")
                }
                try {
                    if (connection.responseCode != 200) {
                        throw IOException("GitHub returned HTTP " + connection.responseCode)
                    }
                    connection.inputStream.use { source ->
                        partial.outputStream().use { out -> source.copyTo(out) }
                    }
                } finally {
                    connection.disconnect()
                }
                if (!verified(partial)) throw GradleException("Official yt-dlp download failed SHA-256 verification")
                if (!partial.renameTo(pinnedYtDlp)) throw IOException("Could not activate verified build asset")
            } catch (e: Exception) {
                partial.delete()
                if (releaseBuild) throw GradleException("Release requires verified bundled yt-dlp. Download it or build with network access.", e)
                logger.warn("Pinned yt-dlp unavailable in local build; runtime manual verified import remains available: " + e.javaClass.simpleName)
            }
        }
    }
}
// Explicit dependency ensures the asset is prepared before Gradle snapshots APK assets.
tasks.configureEach {
    if (name == "preBuild" || (name.startsWith("merge") && name.endsWith("Assets"))) {
        dependsOn(preparePinnedYtDlp)
    }
}

