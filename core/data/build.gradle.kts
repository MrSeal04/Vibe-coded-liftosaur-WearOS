plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
android {
    namespace = "dev.fquo.liftwear.data"
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":core:api"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
}
