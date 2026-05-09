plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.safeglow.edge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.safeglow.edge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.safeglow.edge.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("main").kotlin.srcDirs("src/main/kotlin")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // LiteRT
    implementation(libs.litertlm.android)
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.coroutines.android)

    // Android core + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ML Kit Text Recognition v2 (bundled — offline, no network)
    implementation(libs.mlkit.text.recognition)

    // Compose camera permission helper
    implementation(libs.accompanist.permissions)

    // Hilt + Navigation Compose (hiltViewModel() in composables)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle runtime compose (collectAsStateWithLifecycle)
    implementation(libs.lifecycle.runtime.compose)

    // Concurrent futures KTX (ListenableFuture.await() for ProcessCameraProvider)
    implementation(libs.concurrent.futures.ktx)

    // Guava (provides ListenableFuture type used by ProcessCameraProvider.getInstance())
    implementation(libs.guava)

    // Test
    testImplementation(libs.junit)

    // Android instrumented test
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
