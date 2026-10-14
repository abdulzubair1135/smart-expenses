cmake_minimum_required(VERSION 3.22.1)

project("smarttracker_native")

add_library(
    smarttracker_native
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    smarttracker_native
    ${log-lib}
)
