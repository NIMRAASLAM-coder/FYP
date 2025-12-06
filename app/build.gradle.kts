import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

// Helper to read local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
var geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""

// Sanitize the API key: remove surrounding quotes if the user added them in local.properties
if (geminiApiKey.startsWith("\"") && geminiApiKey.endsWith("\"")) {
    geminiApiKey = geminiApiKey.substring(1, geminiApiKey.length - 1)
}

android {
    namespace = "com.fyp.nextshot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fyp.nextshot"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Define the API Key in BuildConfig
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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
        viewBinding = true
        buildConfig = true
    }
}


dependencies {

    // AndroidX & UI Essentials
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // implementation("com.google.android.material:material:1.11.0") // Redundant with libs.material

    // Image Loading (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // implementation(libs.firebase.auth) // Removed to avoid conflict with BOM
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    
    // Google AI Client
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // Guava (for ListenableFuture) - Needed by CameraX
    implementation("com.google.guava:guava:33.0.0-android")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")


    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Firebase Bill of Materials (BOM) - Updated to fix conflicts
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Firebase Libraries (Rely on BOM version)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx") 
    // Removed explicit exclude as updated BOM handles dependencies better

    // Google Services (for Google Sign-In)
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    // CameraX dependencies
    val cameraXVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // Kotlin coroutines for threading
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
