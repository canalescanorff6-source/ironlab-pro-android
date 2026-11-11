plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ironlab.pro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ironlab.pro"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
    }

    // Optional, stable release signing: provide all four environment variables
    // via private GitHub Actions secrets or local environment. Never commit the key.
    val releaseKey = providers.environmentVariable("IRONLAB_KEYSTORE_FILE").orNull
    val releaseStorePass = providers.environmentVariable("IRONLAB_KEYSTORE_PASSWORD").orNull
    val releaseAlias = providers.environmentVariable("IRONLAB_KEY_ALIAS").orNull
    val releaseKeyPass = providers.environmentVariable("IRONLAB_KEY_PASSWORD").orNull
    val hasReleaseSigning = !releaseKey.isNullOrBlank() && !releaseStorePass.isNullOrBlank() &&
        !releaseAlias.isNullOrBlank() && !releaseKeyPass.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("ironlabRelease") {
                storeFile = file(requireNotNull(releaseKey))
                storePassword = releaseStorePass
                keyAlias = releaseAlias
                keyPassword = releaseKeyPass
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("ironlabRelease")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { viewBinding = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
}
