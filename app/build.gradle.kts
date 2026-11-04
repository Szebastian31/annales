plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.annales.journal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.annales.journal"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "0.9.0"
    }

    // The family's debug key (copied from Fidelis), so every build installs over the last one.

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.coroutines.android)
}

// The family signing key never lives in git. GitHub builds get it from the FAMILY_* secrets,
// local builds from ~/.gradle/gradle.properties.
val familyProp: (String) -> String? = { k -> System.getenv(k) ?: (findProperty(k) as String?) }
val familyKeystore: java.io.File? = familyProp("FAMILY_KEYSTORE_PATH")?.let { file(it) }
    ?: System.getenv("FAMILY_KEYSTORE_B64")?.let { b64 ->
        java.io.File(System.getProperty("java.io.tmpdir"), "family-${rootProject.name}.jks")
            .apply { writeBytes(java.util.Base64.getMimeDecoder().decode(b64)) }
    }

android {
    signingConfigs {
        create("family") {
            if (familyKeystore != null) {
                storeFile = familyKeystore
                storePassword = familyProp("FAMILY_KEYSTORE_PASSWORD")
                keyAlias = familyProp("FAMILY_KEY_ALIAS")
                keyPassword = familyProp("FAMILY_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("family") }
        getByName("release") { signingConfig = signingConfigs.getByName("family") }
    }
}
