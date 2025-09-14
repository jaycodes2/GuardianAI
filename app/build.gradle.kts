// file: build.gradle.kts (Module: app)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")

    // IMPORTANT: Make sure the Chaquopy plugin is applied.
//    id("com.chaquo.python")
}

android {
    namespace = "com.dsatm.guardianai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dsatm.guardianai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The ndk block must be here, inside the 'android.defaultConfig' block,
        // not inside the 'chaquopy' block. This is the standard Android Gradle
        // way of specifying supported ABIs.
//        ndk {
//            abiFilters += listOf("arm64-v8a", "x86_64")
//        }
    }

    // Chaquopy's configuration now resides in its own top-level block.
    // This is the new, preferred way to configure Chaquopy for Gradle Kotlin DSL.
//    chaquopy {
//        // This is where we configure the Python source code directory.
//        // It tells Chaquopy where to find your .py files.
//        sourceSets {
//            getByName("main") {
//                srcDirs("src/main/python")
//            }
//        }
//
//        defaultConfig {
//            // This is the critical part for installing your Python dependencies.
//            // easyocr depends on numpy and opencv-python-headless.
//            // All three must be installed together for your script to function.
//            // The python-bidi dependency is often needed by easyocr for languages
//            // with right-to-left text, but is not strictly necessary for English text
//            // and can be a source of errors, so it's a good idea to keep it commented out.
//            pip {
//                install("numpy")
//                install("pytesseract")
//                install("opencv-python-headless")
//            }
//        }
//    }

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

// file: build.gradle.kts (Module: app)
// All other dependencies go here as you had them.
dependencies {
    // This is the correct way to use the BOM. It manages versions for all
    // compose-related libraries to ensure they are compatible.
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom) // It's a good practice to include it here too

    // Use the correctly defined libraries from the BOM (no version numbers needed)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0") // This is also good practice
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.core.ktx)

    // Other core Android libraries
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Navigation and Biometric libraries (should have versions specified separately or via a TOML file)
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.core:core-ktx:1.13.1")

    // Link to the feature modules
    implementation(project(":image-redaction"))
    implementation(project(":audio-redaction"))
    implementation(project(":text-redaction"))
    implementation(project(":core"))
    implementation(project(":ner"))


    // Add this for compose previews
    debugImplementation(libs.androidx.ui.tooling)

    // Add this for compose icons
    implementation("androidx.compose.material:material-icons-extended")

    // External libraries
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta2")

    // Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Testing Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
