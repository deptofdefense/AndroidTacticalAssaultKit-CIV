cmake_minimum_required (VERSION 3.7.2)
project (takengine)

# use C++11
set (CMAKE_CXX_STANDARD 11)

add_subdirectory (mapengine)

# only for windows right now
if (WIN32)
    add_subdirectory (taktcm)
endif ()