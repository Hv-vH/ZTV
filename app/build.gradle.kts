import java.util.Properties

plugins {
    id("com.android.application")
}

val releaseKeyFile = rootProject.file("keystore.properties")
val releaseKey = Properties().apply {
    if (releaseKeyFile.isFile) {
        releaseKeyFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.cztvtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cztvtv"
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "0.4.1"
    }

    signingConfigs {
        if (releaseKeyFile.isFile) {
            create("localRelease") {
                storeFile = rootProject.file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("localRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        noCompress += "ttf"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
