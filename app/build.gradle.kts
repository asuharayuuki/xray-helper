plugins {
    id("com.android.application")
}

android {
    namespace = "org.eu.suisei.xray"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.eu.suisei.xray"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}
