plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

fun gitShortSha(): String =
    try {
        val process =
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
        process.inputStream
            .bufferedReader()
            .readText()
            .trim()
            .ifEmpty { "unknown" }
    } catch (e: Exception) {
        "unknown"
    }

android {
    namespace = "io.github.derstrassi.karoofirefly"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.szaffarano.karoofirefly"
        minSdk = 24
        targetSdk = 35
        versionCode = 165
        versionName = "0.7.0"
        buildConfigField("String", "GIT_SHA", "\"${gitShortSha()}\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_PATH")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.sunrise.sunset)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.nordic.ble.client)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ANT Android SDK - place ant-lib.aar in app/libs/
    // Download from: https://github.com/ant-wireless/ANT-Android-SDKs
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
}

// Regenerates app/manifest.json from the current version and RELEASE_NOTES env var.
// The APK URL is static (/releases/latest/download/...) so it never needs updating.
// Invoked by the prepare-release skill; release notes come from RELEASE_NOTES.
tasks.register("generateManifest") {
    group = "release"
    description = "Regenerate app/manifest.json from the current version and RELEASE_NOTES"
    doLast {
        val manifestFile = file("manifest.json")

        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(manifestFile) as MutableMap<String, Any?>
        json["latestVersion"] = android.defaultConfig.versionName
        json["latestVersionCode"] = android.defaultConfig.versionCode
        json["latestApkUrl"] =
            "https://github.com/szaffarano/karoofirefly/releases/latest/download/karoo-light-controller.apk"
        System.getenv("RELEASE_NOTES")?.takeIf { it.isNotBlank() }?.let { json["releaseNotes"] = it }
        manifestFile.writeText(groovy.json.JsonBuilder(json).toPrettyString() + "\n")
    }
}
