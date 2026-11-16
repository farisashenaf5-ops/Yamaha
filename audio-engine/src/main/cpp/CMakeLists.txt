cmake_minimum_required(VERSION 3.22.1)
project(psr_oboe_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Locate Oboe via Prefab (configured in audio-engine/build.gradle.kts)
find_package(oboe REQUIRED CONFIG)
find_library(log-lib log)

add_library(psr_oboe_audio SHARED
    OboeAudioEngine.cpp
    OboeJniBridge.cpp
)

target_include_directories(psr_oboe_audio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_options(psr_oboe_audio PRIVATE
    -O3
    -fno-math-errno
    -Wall
    -Wextra
)

target_link_options(psr_oboe_audio PRIVATE
    "-Wl,-z,max-page-size=16384"
)

target_link_libraries(psr_oboe_audio
    oboe::oboe
    ${log-lib}
)
