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
        versionCode = 11
        versionName = "0.6.4"
    }

    // The family's debug key (copied from Fidelis), so every build installs over the last one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../annales-debug.keystore")
            storePassword = "fidelis123"
            keyAlias = "fidelis"
            keyPassword = "fidelis123"
        }
    }

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
