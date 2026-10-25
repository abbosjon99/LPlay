// LPlay: the Leapmotor instrument-cluster window, shared by the CarPlay and Android Auto engines.
// Engine-neutral on purpose: it knows displays, windows, the left-edge fade and the launch/confirm/reclaim
// rules, and hands a Surface to whoever asks. The engines are connected in :carlink-core.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "uz.carhelper.lplay.cluster"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 25
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
}
