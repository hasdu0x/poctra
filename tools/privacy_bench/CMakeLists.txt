cmake_minimum_required(VERSION 3.22.1)

project(poctra_privacy_bench LANGUAGES CXX)

set(PVAC_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../app/src/main/cpp/pvac")

add_executable(
    poctra_privacy_bench
    main.cpp
    "${PVAC_ROOT}/pvac_c_api.cpp"
)

target_include_directories(
    poctra_privacy_bench
    PRIVATE
    "${PVAC_ROOT}"
    "${PVAC_ROOT}/include"
)

target_compile_features(poctra_privacy_bench PRIVATE cxx_std_17)
target_compile_definitions(poctra_privacy_bench PRIVATE PVAC_PROFILE=1)
target_compile_options(
    poctra_privacy_bench
    PRIVATE
    -O3
    -DNDEBUG
    -flto=thin
    -fno-semantic-interposition
    -fexceptions
    -frtti
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
    -Wall
    -Wextra
    -Wno-unused-parameter
)
target_link_options(
    poctra_privacy_bench
    PRIVATE
    -Wl,--gc-sections
    -flto=thin
)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(poctra_privacy_bench PRIVATE -march=armv8-a+crypto)
elseif(ANDROID_ABI STREQUAL "x86_64")
    target_compile_options(poctra_privacy_bench PRIVATE -maes -msse2)
endif()
