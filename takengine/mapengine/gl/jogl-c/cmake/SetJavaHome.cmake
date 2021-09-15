# Sets CMake environment variable JAVA_HOME to the required JDK installation pointed to by either the JAVA32_HOME or
# JAVA64_HOME system environment variable (depending on the target architecture). Note that setting the JAVA_HOME CMake
# variable does NOT affect the JAVA_HOME system environment variable.
#
# Setting JAVA_HOME is a feature of the FindJNI module, see https://cmake.org/cmake/help/v3.14/module/FindJNI.html.
#
# Due to an existing bug in the FindJNI module, FindJNI will not respect the JAVA_HOME CMake environment variable if a
# JDK is specified on the system PATH. For example, the 32-bit Windows build will not build successfully if a 64-bit JDK
# installation is pointed to on the system PATH. See https://gitlab.kitware.com/cmake/cmake/-/issues/19193 and
# https://gitlab.kitware.com/cmake/cmake/-/issues/13942.
function(set_java_home)
    # If we're targeting x86 and the JAVA32_HOME environment variable is not defined, throw an error and stop build generation.
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL x86)
        if(DEFINED ENV{JAVA32_HOME})
            message(STATUS "Using 32-bit JDK installation at $ENV{JAVA32_HOME}")
            set(ENV{JAVA_HOME} $ENV{JAVA32_HOME})
        else()
            message(FATAL_ERROR "Environment variable JAVA32_HOME must be set and pointing to a 32-bit JDK installation in order to build project GLESv2-JOGL.")
        endif()
    # If we're targeting x64 and the JAVA64_HOME environment variable is not defined, throw an error and stop build generation.
    else()
        if(DEFINED ENV{JAVA64_HOME})
            message(STATUS "Using 64-bit JDK installation at $ENV{JAVA64_HOME}")
            set(ENV{JAVA_HOME} $ENV{JAVA64_HOME})
        else()
            message(FATAL_ERROR "Environment variable JAVA64_HOME must be set and pointing to a 64-bit JDK installation in order to build project GLESv2-JOGL.")
        endif()
    endif()
endfunction()