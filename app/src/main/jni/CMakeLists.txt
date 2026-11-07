cmake_minimum_required(VERSION 3.18)
project(piperncnn)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20261020-android/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(piperncnn SHARED
        piperncnn.cpp
        piper.cpp
        simpleg2p.cpp)

target_link_libraries(piperncnn ncnn android log c++_shared)
