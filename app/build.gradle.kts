// file: build.gradle.kts (Module: app)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

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

// All other dependencies go here as you had them.
dependencies {
    // ...
    implementation(project(":image-redaction"))
    implementation(project(":audio-redaction"))
    implementation(project(":text-redaction"))


    implementation("com.google.mlkit:text-recognition:16.0.0")

    implementation("androidx.core:core-ktx:1.13.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
