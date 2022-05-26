# This project generates the takengine JNI build, which builds the native sources that satisfy the JNI methods defined
# by the map engine Java classes in takengine/src/main/java. The artifact output by this build has the name takenginejni
# (Windows) or libtakenginejni (Android/Linux).

# Find and configure the JNI include directories required to build the takenginejni target.
include(${CMAKE_CURRENT_SOURCE_DIR}/../../../cmake/support/ValidateJavaExists.cmake)
validate_java_exists("takenginejni")

if(NOT ANDROID)
    find_package(JNI REQUIRED)
    message(STATUS "JNI_INCLUDE_DIRS found at ${JNI_INCLUDE_DIRS}")
endif()

# `libtakenginejni` sources
set(SRC_DIR .)

# Set the common sources.
include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/CommonSources.cmake)

# If we're targeting a Windows platform, set Windows-specific variables, sources, includes, etc..
if(WIN32)
    # Set the ANGLE architecture suffix and TTP pre-built directory based on whether we're targeting a 64-bit or 32-bit architecture.
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
        set(ANGLE_ARCH_SUFFIX x64)
    else()
        set(ANGLE_ARCH_SUFFIX Win32)
    endif()

    message(STATUS "Targeting Windows architecture: ${ANGLE_ARCH_SUFFIX}")

    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/WindowsSources.cmake)

    set(takenginejni_DEFS ${takenginejni_WINDOWS_DEFS})
    set(takenginejni_LIBS ${takenginejni_WINDOWS_LIBS})
    set(takenginejni_LDIRS ${takenginejni_WINDOWS_LDIRS})
    set(takenginejni_INCS ${takenginejni_COMMON_INCS} ${takenginejni_WINDOWS_INCS})
    set(takenginejni_OPTS ${takenginejni_WINDOWS_OPTS})
    set(takenginejni_SRCS ${takenginejni_COMMON_SRCS} ${takenginejni_WINDOWS_SRCS})
# If we're targeting an Android platform, set Android-specific variables, sources, includes, etc..
elseif(ANDROID)
    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/AndroidSources.cmake)

    set(takenginejni_DEFS ${takenginejni_ANDROID_DEFS})
    set(takenginejni_LIBS ${takenginejni_ANDROID_LIBS})
    set(takenginejni_LDIRS ${takenginejni_ANDROID_LDIRS})
    set(takenginejni_INCS ${takenginejni_COMMON_INCS} ${takenginejni_ANDROID_INCS})
    set(takenginejni_OPTS ${takenginejni_ANDROID_OPTS})
    set(takenginejni_SRCS ${takenginejni_COMMON_SRCS} ${takenginejni_ANDROID_SRCS})
# If we're targeting the MacOS platform, set MacOS-specific variables, sources, includes, etc..
elseif(APPLE)
    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/MacOSSources.cmake)

    set(takenginejni_DEFS ${takenginejni_MACOS_DEFS})
    set(takenginejni_LIBS ${takenginejni_MACOS_LIBS})
    set(takenginejni_LDIRS ${takenginejni_MACOS_LDIRS})
    set(takenginejni_INCS ${takenginejni_COMMON_INCS} ${takenginejni_MACOS_INCS})
    set(takenginejni_OPTS ${takenginejni_MACOS_OPTS})
    set(takenginejni_SRCS ${takenginejni_COMMON_SRCS} ${takenginejni_MACOS_SRCS})
# If we're targeting a Linux platform, set Linux-specific variables, sources, includes, etc..
elseif(UNIX)
    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/LinuxSources.cmake)

    set(takenginejni_DEFS ${takenginejni_LINUX_DEFS})
    set(takenginejni_LIBS ${takenginejni_LINUX_LIBS})
    set(takenginejni_LDIRS ${takenginejni_LINUX_LDIRS})
    set(takenginejni_INCS ${takenginejni_COMMON_INCS} ${takenginejni_LINUX_INCS})
    set(takenginejni_OPTS ${takenginejni_LINUX_OPTS})
    set(takenginejni_SRCS ${takenginejni_COMMON_SRCS} ${takenginejni_LINUX_SRCS})
else()
    message(FATAL_ERROR "Attempting to generate takengine build for unsupported target platform ${CMAKE_SYSTEM_NAME}, stopping generation.")
endif()

add_library(takenginejni SHARED ${takenginejni_SRCS})
target_compile_definitions(takenginejni PRIVATE ${takenginejni_DEFS})
target_include_directories(takenginejni PRIVATE ${takenginejni_INCS})
target_link_directories(takenginejni PRIVATE ${takenginejni_LDIRS})
target_link_libraries(takenginejni PRIVATE takengine ${takenginejni_LIBS})
target_compile_options(takenginejni PRIVATE ${takenginejni_OPTS})

if(WIN32)
    # Set target properties, where all VS properties are based on existing VS project properties found in takenginejni.vcxproj.
    set_target_properties(takenginejni
        PROPERTIES VS_GLOBAL_ROOTNAMESPACE takenginejni
    )
endif()

if(NOT ANDROID)
    install(TARGETS takenginejni
        RUNTIME DESTINATION ${CMAKE_INSTALL_PREFIX}
        LIBRARY DESTINATION ${CMAKE_INSTALL_PREFIX}
    )
endif()
