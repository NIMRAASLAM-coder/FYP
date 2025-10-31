plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)

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
    }
}


dependencies {

    // AndroidX & UI Essentials
    implementation(libs.androidx.core.ktx) // Kept the second one, assuming it's correctly defined in libs.versions.toml
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.google.android.material:material:1.9.0") // Redundant, but sometimes necessary if libs.material is an older version

    // Image Loading (Glide)
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Firebase Bill of Materials (BOM)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // Firebase Libraries (Rely on BOM version)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx") {
        // TARGETED FIX: Exclude older firebase-common to resolve Duplicate Class error
        exclude(group = "com.google.firebase", module = "firebase-common")
    }

    // Google Services (for Google Sign-In)
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // REDUNDANCY REMOVAL: These lines are likely redundant or conflict
    // when using the BOM and the play-services-auth dependency above.
    // implementation(libs.firebase.auth)
    // implementation(libs.androidx.credentials)
    // implementation(libs.androidx.credentials.play.services.auth)
    // implementation(libs.googleid)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    // CameraX dependencies
    val cameraXVersion = "1.3.1" // Use the latest stable version
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // Kotlin coroutines for threading
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

}