# This project builds OpenGL for Embedded Systems (GLES) as a shared library with the name libGLESv2. The shared library
# that's created exposes the GLESv3 API, however the name "libGLESv2" is retained for the purposes of maintaining ABI
# compatibility with ANGLE (it ships a library of the same name that covers all GLES APIs).
#
# GLESv2-JOGL (specifically the libGLESv2 artifact) is a runtime dependency of the :takengine and :takengine-support-android
# Gradle projects.

# Targeting 3.14.7 as that is the first CMake version to support the "Visual Studio 16 2019" build generator.
cmake_minimum_required(VERSION 3.14.7)
project(glesv2-jogl VERSION 1.0
                    DESCRIPTION "GLESv2-JOGL project that builds OpenGL for Embedded Systems (v2) as a shared library."
                    LANGUAGES C)

# Set JAVA_HOME to the appropriate JDK installation for FindJNI to use. The system PATH env-var must not contain a JDK
# reference in order for this to function properly. See SetJavaHome.cmake for more details.
include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/SetJavaHome.cmake)
set_java_home()

# Find the JNI libraries on the local system, or fail the build generation if they cannot be found.
find_package(JNI REQUIRED)

message(STATUS "JNI_INCLUDE_DIRS found at ${JNI_INCLUDE_DIRS}")

# Enforce the use of C99 as a baseline standard.
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# To match existing Visual Studio project, generate  "Debug" and "Release" configurations only.
set(CMAKE_CONFIGURATION_TYPES Debug Release)

# Add the sub-project in the "src" directory to this project.
add_subdirectory(src)