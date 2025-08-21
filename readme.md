# GuardianAI: On-Device AI Security for Sensitive Data

## Overview
GuardianAI is an Android application designed to detect and redact sensitive data in images, audio, and text entirely offline, without using cloud services. The app provides a secure, modular framework to handle sensitive content while maintaining usability and modern UI. The system integrates Android UI components with Python-based AI/ML backends for content analysis and redaction.

## Key Features
- **Offline Processing:** All detection and redaction happen on-device.
- **Modular Architecture:** Separate modules for Image, Audio, and Text redaction.
- **Secure UI:** Blurred previews of sensitive content are shown by default. Original content is decrypted or displayed only after user authentication.
- **Python Backend Integration:** Each module can use Python scripts for AI/ML-based detection and redaction, integrated using Chaquopy.

## Project Structure
```
GuardianAI/
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ java/com/dsatm/guardianai/
│  │  │  │  ├─ MainActivity.kt
│  │  │  │  └─ ui/
│  │  │  │     ├─ components/
│  │  │  │     └─ screens/
│  │  │  └─ res/
│  │  └─ AndroidManifest.xml
│  └─ build.gradle.kts
├─ image-redaction/
│  ├─ src/
│  └─ python/
├─ audio-redaction/
│  ├─ src/
│  └─ python/
├─ text-redaction/
│  ├─ src/
│  └─ python/
├─ settings.gradle.kts
└─ build.gradle.kts
```

## Module Guidelines
Each module has a Kotlin wrapper interface used by the app to call Python scripts:

```kotlin
interface ImageRedactionModule {
    fun redactImage(inputPath: String, outputPath: String): Boolean
}

interface AudioRedactionModule {
    fun redactAudio(inputPath: String, outputPath: String): Boolean
}

interface TextRedactionModule {
    fun redactText(inputText: String): String
}
```

Place all Python scripts inside the module's `python/` folder, use relative imports, and call functions via the Kotlin wrapper using Chaquopy.

## How to Add New Logic
1. Add Python scripts to the appropriate module's `python/` folder.
2. Ensure each Python function matches the Kotlin wrapper’s expected signature.
3. Test Python functions independently before integration.
4. Call the Python function from Kotlin using the wrapper interface.

## Dependencies
- **Android:** Jetpack Compose, Material3
- **Python (via Chaquopy):** numpy, Pillow, OpenCV, Torch/TFLite (as needed for AI/ML)

## Build & Run
```bash
git clone <repository-url>
cd GuardianAI
```
1. Open the project in Android Studio.
2. Sync Gradle and ensure Chaquopy plugin is configured.
3. Build and run on a device with Android 26+.

## Notes
- The app is fully offline.
- All sensitive data handling is local, encrypted, and protected.
- The UI is modular, allowing future expansion of modules or features without impacting existing code.

