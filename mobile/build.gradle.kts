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
    namespace = "dev.fquo.liftwear.mobile"
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig {
        // MUST match :wear exactly - Data Layer requirement.
        applicationId = "dev.fquo.liftwear"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
            isMinifyEnabled = false      // no shrinker rules written or tested yet
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
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
