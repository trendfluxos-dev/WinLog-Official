plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val deviceApiKey = providers.environmentVariable("DEVICE_API_KEY").getOrElse("")
android {
    namespace = "com.winstone.callcollector"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.winstone.callcollector"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
        buildConfigField("String", "DEVICE_API_KEY", "\"${deviceApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
