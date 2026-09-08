plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "dev.fquo.liftwear.wear"
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig {
        // MUST match :mobile exactly - Data Layer requires identical
        // applicationId, versionCode and signing certificate.
        applicationId = "dev.fquo.liftwear"
        minSdk = 33          // Wear OS 4
        targetSdk = 36       // Wear OS 6
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // VERSION_NAME goes into the X-Liftosaur-Client header
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:data"))
    implementation(project(":core:datalayer"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    // Previews live in src/debug so the WearPreview* annotations never ship in release.
    debugImplementation(libs.wear.compose.tooling)
    implementation(libs.wear.core)
    implementation(libs.wear.input)
    implementation(libs.wear.ongoing)
    implementation(libs.wear.tiles)
    implementation(libs.protolayout)
    implementation(libs.protolayout.expression)
    implementation(libs.wear.tiles.material)
    implementation(libs.watchface.complications)

    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.work.runtime)
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
}
