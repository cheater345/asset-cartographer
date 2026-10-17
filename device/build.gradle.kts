plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.nyx.cartographer.device"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // Generates the IRemoteFs binder for the read-only user service.
        aidl = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.shizuku.api)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
}
