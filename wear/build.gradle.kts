import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing. :wear and :mobile MUST end up with the SAME certificate - the Data Layer
// refuses to pair apps whose signatures differ - so both read this one keystore.
//
// It lives outside the repo (next to the API key) because this repo is public and a signing
// key is not recoverable: lose it and no future build can ever upgrade an existing install.
// Absent, release builds are simply unsigned rather than failing, so a fresh clone still builds.
val keystoreProps: Properties? = run {
    val f = file("${System.getProperty("user.home")}/.config/liftwear/keystore.properties")
    if (!f.exists()) null else {
        val props = Properties()
        FileInputStream(f).use { props.load(it) }
        props
    }
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
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // VERSION_NAME goes into the X-Liftosaur-Client header
    }
    signingConfigs {
        keystoreProps?.let { p ->
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 matters more on a watch than on a phone: unshrunk, this APK carried 36 MB of
            // dex, and a sideloaded install runs it uncompiled until the watch next charges
            // overnight. The libraries that reflect on our classes bring their own keep rules;
            // an R8 build is verified by reading a cached workout and history on hardware.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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

    // Debug only: the DesignGalleryActivity seeds the Room cache directly so the Tile and
    // the complication can be seen in every state without an API key. :core:data keeps Room
    // as an implementation detail, and it should stay that way for the release build.
    debugImplementation(libs.room.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
}
