#include <jni.h>
#include <string>

// Assuming whisper class from whisper_native is accessible here
#include "whisper.h"

static Whisper whisper;

extern "C"
JNIEXPORT jboolean  extern "C" JNICALL
Java_your_package_audio_1redaction_WhisperNativeClient_loadModel(JNIEnv* env, jobject, jstring modelPath_, jstring vocabPath_) {
    const char* modelPath = env->GetStringUTFChars(modelPath_, nullptr);
    const char* vocabPath = env->GetStringUTFChars(vocabPath_, nullptr);

    bool loadResult = whisper.loadModel(modelPath, vocabPath);

    env->ReleaseStringUTFChars(modelPath_, modelPath);
    env->ReleaseStringUTFChars(vocabPath_, vocabPath);

    return loadResult;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_your_package_audio_1redaction_WhisperNativeClient_transcribe(JNIEnv* env, jobject, jstring waveFilePath_) {
    const char* waveFilePath = env->GetStringUTFChars(waveFilePath_, nullptr);

    std::string text = whisper.transcribe(std::string(waveFilePath));

    env->ReleaseStringUTFChars(waveFilePath_, waveFilePath);

    return env->NewStringUTF(text.c_str());
}
