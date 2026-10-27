import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
}

android {
    namespace = "pt.vereda.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // CI run number makes every build an upgrade of the previous one on the phone.
    val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "pt.vereda.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        // Shared debug key (not secret, same as Android Studio's default passwords) so every build,
        // from CI or a laptop, installs over the previous one without uninstalling first.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The release key (spec: Phase 4) comes only from CI secrets, never from the repo.
        val releaseStore = providers.environmentVariable("VEREDA_RELEASE_STORE").orNull
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.environmentVariable("VEREDA_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("VEREDA_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("VEREDA_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:geo"))
    implementation(project(":core:catalog"))
    implementation(project(":core:route"))
    implementation(project(":core:maps"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.maplibre.android)
}
