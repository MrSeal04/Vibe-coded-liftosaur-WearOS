plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "dev.fquo.liftwear.datalayer"
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api(libs.play.services.wearable)
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
