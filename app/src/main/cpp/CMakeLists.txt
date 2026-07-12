cmake_minimum_required(VERSION 3.22.1)

project(poctra_privacy LANGUAGES CXX)

if(ANDROID_ABI STREQUAL "armeabi-v7a" OR ANDROID_ABI STREQUAL "x86")
    add_library(poctra_privacy SHARED privacy_jni_unavailable.cpp)
else()
    add_library(
        poctra_privacy
        SHARED
        privacy_jni.cpp
        pvac/pvac_c_api.cpp
    )
endif()

target_include_directories(
    poctra_privacy
    PRIVATE
    pvac
    pvac/include
)

target_compile_features(poctra_privacy PRIVATE cxx_std_17)
target_compile_options(
    poctra_privacy
    PRIVATE
    -O2
    -fexceptions
    -frtti
    -Wall
    -Wextra
    -Wno-unused-parameter
)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(poctra_privacy PRIVATE -march=armv8-a+crypto)
elseif(ANDROID_ABI STREQUAL "x86_64")
    target_compile_options(poctra_privacy PRIVATE -maes -msse2)
endif()

find_library(log_lib log)
target_link_libraries(poctra_privacy PRIVATE ${log_lib})
