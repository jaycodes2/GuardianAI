# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("whisper_jni")

# Add the C++ source files for our JNI bridge and the whisper.cpp library
# You will need to add the required .cpp and .h files from the whisper.cpp repository here
add_library( # Sets the name of the library.
        whisper_jni

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        whisper-jni.cpp
        # --> ADD WHISPER.CPP SOURCE FILES HERE <--
        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.
find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log
        )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
target_link_libraries( # Specifies the target library.
        whisper_jni

        # Links the target library to the log library
        # specified in the find_library() call.
        ${log-lib}
        )
