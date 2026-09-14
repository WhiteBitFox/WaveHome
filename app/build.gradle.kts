import org.gradle.kotlin.dsl.implementation
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")

}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String = localProperties.getProperty(name).orEmpty()

fun buildConfigString(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.example.wavehome"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.wavehome"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = localProperty("GOOGLE_MAPS_API_KEY")
        buildConfigField(
            "String",
            "GOOGLE_MAPS_API_KEY",
            buildConfigString(localProperty("GOOGLE_MAPS_API_KEY"))
        )
        buildConfigField(
            "String",
            "OPEN_WEATHER_API_KEY",
            buildConfigString(localProperty("OPEN_WEATHER_API_KEY"))
        )

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.remote.creation.compose)
    implementation(libs.androidx.compose.ui.graphics)
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(libs.junit)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(composeBom)
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.animation:animation:1.6.3")
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.ui:ui-graphics:1.6.1")
    implementation("androidx.compose.ui:ui-tooling:1.6.3")
    implementation("androidx.compose.material3:material3:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation("io.coil-kt:coil-compose:2.0.0")
    implementation("io.coil-kt:coil-gif:2.0.0")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.runtime:runtime-rxjava2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.8.0")
    //room
    implementation ("androidx.room:room-runtime:2.5.0")
    implementation ("androidx.room:room-ktx:2.5.0")
    ksp ("androidx.room:room-compiler:2.5.0")  // kapt - alternatywnie boś głupi

    //maps
    implementation ("com.google.maps.android:maps-compose:2.11.4")
    implementation ("com.google.android.gms:play-services-maps:18.2.0")
    implementation ("com.google.android.gms:play-services-auth:20.7.0")
    implementation ("com.google.android.gms:play-services-auth-api-phone:18.0.1")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-fitness:21.2.0")
    implementation ("com.google.maps.android:maps-compose:2.11.4")
    implementation("io.karn:notify:1.4.0")
    implementation("pub.devrel:easypermissions:3.0.0")

    //home
    implementation ("com.google.android.gms:play-services-home:17.1.0")
    implementation ("com.google.android.gms:play-services-home-types:17.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    //LiteRT
    val litertVersion = "1.0.1"
    implementation("com.google.ai.edge.litert:litert:$litertVersion")
    implementation("com.google.ai.edge.litert:litert-gpu:$litertVersion")
    implementation("com.google.ai.edge.litert:litert-gpu-api:$litertVersion")
    //implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    //Google
    implementation("androidx.credentials:credentials:1.2.1")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    //implementation("androidx.credentials:credentials-play-services-auth:20.7.0") - masz przy mapach

    //Health
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")

// Biblioteki Support Library dla TF

//    implementation("com.github.haifengl:smile-core:3.0.3")
//    implementation("com.github.haifengl:smile-kotlin:5.0.0")
//    implementation("com.github.haifengl:smile-nlp:5.0.0")




    //OAuth 2.0
    //implementation("net.openid:appauth:0.11.1")
}
