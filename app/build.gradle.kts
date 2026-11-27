plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// A release workflow will pass -PversionName=1.2.3 and -PversionCode=10203 (code = major*10000 + minor*100 + patch).
// Local builds use 0.0.1 / 1.
val appVersionName: String = (findProperty("versionName") as String?) ?: "0.0.1"
val appVersionCode: Int = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.feerlessleadr.downbeat"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.feerlessleadr.downbeat"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // When this build was made, shown at the bottom of Settings so it is clear which build is installed.
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    signingConfigs {
        // The release key comes from the environment. Nothing is read from files in the repo.
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("SIGNING_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // A release build signed with the debug key (local test only) installs as its own app.
            if (System.getenv("SIGNING_KEYSTORE_PATH") == null) {
                applicationIdSuffix = ".rtest"
                versionNameSuffix = "-rtest"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// A release APK signed with the debug key must never be published. Building one needs an explicit opt-in.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any {
        it.path.startsWith(":app:") && it.name.contains("Release") &&
            (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package"))
    }
    if (buildsRelease && System.getenv("SIGNING_KEYSTORE_PATH") == null && findProperty("allowDebugSigning") == null) {
        throw GradleException("A release build needs the release signing key (SIGNING_KEYSTORE_PATH). For a local test build that must not be published, pass -PallowDebugSigning.")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
