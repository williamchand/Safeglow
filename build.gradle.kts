plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// Force kotlin-metadata-jvm to 2.3.21 across all configurations so that
// Hilt's annotation processor can read Kotlin 2.3.0 metadata emitted by
// litertlm-android:0.10.2 (compiled with Kotlin 2.3.0).
// Hilt 2.54 bundles kotlin-metadata-jvm 2.2.x which only supports up to 2.2.0 metadata.
subprojects {
    configurations.all {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.21")
    }
}
