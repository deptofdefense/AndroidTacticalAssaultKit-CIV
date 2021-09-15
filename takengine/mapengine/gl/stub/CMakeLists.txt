# This project builds a stubbed-out version of OpenGL for Embedded Systems (GLES) as a shared library with
# the name libGLESv2. The shared library that's created exposes the GLESv3 API, however the name "libGLESv2" is retained
# for the purposes of maintaining ABI compatibility with ANGLE (it ships a library of the same name that covers all GLES APIs).
#
# The stubbed version of libGLESv2 is used solely as a link-time dependency for the takengine and takenginejni projects,
# where the true implementation of the stub is dynamically linked at runtime.

# Targeting 3.14.7 as that is the first CMake version to support the "Visual Studio 16 2019" build generator.
cmake_minimum_required(VERSION 3.14.7)
project(glesv2-stub VERSION 1.0
                    DESCRIPTION "GLESv2-stub project that acts as a link-time-only dependency for other pgsc-raster projects."
                    LANGUAGES C)

# Enforce the use of C99 as a baseline standard.
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# To match existing Visual Studio project configurations, generate "Debug" and "Release" configurations only.
set(CMAKE_CONFIGURATION_TYPES Debug Release)

# Add the sub-project in the "src" directory to this project (contains target-specific configurations).
add_subdirectory(src)
