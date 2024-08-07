cmake_minimum_required(VERSION 3.22.1)

project("envapp")

add_library(
        memory_allocator
        SHARED
        memory_allocator.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        memory_allocator
        ${log-lib})
