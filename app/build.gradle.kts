plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "xyz.github124019.cosmediaplayerreplacer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "xyz.github124019.cosmediaplayerreplacer"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}