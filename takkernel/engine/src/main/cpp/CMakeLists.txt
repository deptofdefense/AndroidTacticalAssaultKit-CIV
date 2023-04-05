# Primary takengine CMake project responsible for generating the takengine shared library build. The build generated
# by this project outputs an artifact with the name takengine (Windows) or libtakengine (Android).
#
# The sources, definitions, includes, etc. required for this project are specified across multiple files:
#     CommonSources.cmake - Contains common sources, definitions, etc. required for all target platforms.
#     AndroidSources.cmake - Contains sources, definitions, etc. required for Android-based target platforms.
#     WindowsSources.cmake - Contains sources, definitions, etc. required for Windows-based target platforms.

# Set root takengine source directory.
set(SRC_DIR .)

# Set the common sources.
include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/CommonSources.cmake)

# If we're targeting a Windows platform, set Windows-specific variables, sources, includes, etc..
if(WIN32)
    message(STATUS "Targeting Windows architecture: ${ANGLE_ARCH_SUFFIX}")

    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/WindowsSources.cmake)

	get_filename_component(buildDirRelFilePath "${SRC_DIR}"
						   REALPATH)

	file(GLOB_RECURSE _source_list *.h* *.hpp*)
	foreach(_source IN ITEMS ${_source_list})
		get_filename_component(_source_path "${_source}" PATH)
		string(REPLACE ${buildDirRelFilePath} "" _group_path ${_source_path})
		string(REPLACE "/" "\\" _group_path "${_group_path}")
		source_group("Header Files\\${_group_path}" FILES "${_source}")
	endforeach()

	file(GLOB_RECURSE _source_list *.c*)
	foreach(_source IN ITEMS ${_source_list})
		get_filename_component(_source_path "${_source}" PATH)
		string(REPLACE ${buildDirRelFilePath} "" _group_path ${_source_path})
		string(REPLACE "/" "\\" _group_path "${_group_path}")
		source_group("Source Files\\${_group_path}" FILES "${_source}")
	endforeach()
    source_group("Source Files\\vscompat" FILES ${takengine_vscompat_COMMON_SRCS})
	
    set(takengine_DEFS ${takengine_WINDOWS_DEFS})
    set(takengine_LIBS ${takengine_WINDOWS_LIBS})
    set(takengine_LDIRS ${takengine_WINDOWS_LDIRS})
    set(takengine_INCS ${takengine_COMMON_INCS} ${takengine_WINDOWS_INCS})
    set(takengine_OPTS ${takengine_WINDOWS_OPTS})
    set(takengine_SRCS ${takengine_COMMON_SRCS} ${takengine_WINDOWS_SRCS})
    set(takengine_HEADERS ${takengine_COMMON_HEADERS} ${takengine_WINDOWS_HEADERS})
    set(CMAKE_CXX_FLAGS  "${CMAKE_CXX_FLAGS} -msse3")
    # If we're targeting an Android platform, set Android-specific variables, sources, includes, etc..
elseif(ANDROID)

    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/AndroidSources.cmake)

    set(takengine_DEFS ${takengine_ANDROID_DEFS})
    set(takengine_LIBS ${takengine_ANDROID_LIBS})
    set(takengine_LDIRS ${takengine_ANDROID_LDIRS})
    set(takengine_INCS ${takengine_COMMON_INCS} ${takengine_ANDROID_INCS})
    set(takengine_OPTS ${takengine_ANDROID_OPTS})
    set(takengine_SRCS ${takengine_COMMON_SRCS} ${takengine_ANDROID_SRCS})
    set(takengine_HEADERS ${takengine_COMMON_HEADERS} ${takengine_ANDROID_HEADERS})
# If we're targeting the MacOS platform, set Linux-specific variables, sources, includes, etc..
elseif(APPLE)
    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/MacOSSources.cmake)

    set(takengine_DEFS ${takengine_MACOS_DEFS})
    set(takengine_LIBS ${takengine_MACOS_LIBS})
    set(takengine_LDIRS ${takengine_MACOS_LDIRS})
    set(takengine_INCS ${takengine_COMMON_INCS} ${takengine_MACOS_INCS})
    set(takengine_OPTS ${takengine_MACOS_OPTS})
    set(takengine_SRCS ${takengine_COMMON_SRCS} ${takengine_MACOS_SRCS})
    set(takengine_HEADERS ${takengine_COMMON_HEADERS} ${takengine_MACOS_HEADERS})
# If we're targeting a Linux platform, set Linux-specific variables, sources, includes, etc..
elseif(UNIX)
    include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/LinuxSources.cmake)

    set(takengine_DEFS ${takengine_LINUX_DEFS})
    set(takengine_LIBS ${takengine_LINUX_LIBS})
    set(takengine_LDIRS ${takengine_LINUX_LDIRS})
    set(takengine_INCS ${takengine_COMMON_INCS} ${takengine_LINUX_INCS})
    set(takengine_OPTS ${takengine_LINUX_OPTS})
    set(takengine_SRCS ${takengine_COMMON_SRCS} ${takengine_LINUX_SRCS})
    set(takengine_HEADERS ${takengine_COMMON_HEADERS} ${takengine_LINUX_HEADERS})

    set(CMAKE_CXX_FLAGS  "${CMAKE_CXX_FLAGS} -msse3")
else()
    message(FATAL_ERROR "Attempting to generate takengine build for unsupported target platform ${CMAKE_SYSTEM_NAME}, stopping generation.")
endif()

add_library(takengine SHARED ${takengine_SRCS} ${takengine_HEADERS})
target_compile_definitions(takengine PRIVATE ${takengine_DEFS})
target_include_directories(takengine PRIVATE ${takengine_INCS})
target_link_directories(takengine PRIVATE ${takengine_LDIRS})
target_link_libraries(takengine PRIVATE ${takengine_LIBS})
target_compile_options(takengine PRIVATE ${takengine_OPTS})

if (WIN32)
    target_link_options(takengine PRIVATE "$<$<CONFIG:Release>:/DEBUG>")
    target_link_options(takengine PRIVATE "$<$<CONFIG:Release>:/OPT:REF>")
    target_link_options(takengine PRIVATE "$<$<CONFIG:Release>:/OPT:ICF>")
endif ()
if(NOT ANDROID)
    install(TARGETS takengine
        RUNTIME DESTINATION ${CMAKE_INSTALL_PREFIX}
        LIBRARY DESTINATION ${CMAKE_INSTALL_PREFIX}
    )
endif()

install(TARGETS takengine
        EXPORT libtakengine-targets
        LIBRARY DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/../../../../exports/libtakengine/${EXPORT_INSTALL_SUBDIR}/lib
        ARCHIVE DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/../../../../exports/libtakengine/${EXPORT_INSTALL_SUBDIR}/lib
        RUNTIME DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/../../../../exports/libtakengine/${EXPORT_INSTALL_SUBDIR}/bin
)
install(EXPORT libtakengine-targets
        FILE libtakengine-targets.cmake
        NAMESPACE libtakengine::
        DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/../../../../exports/libtakengine/${EXPORT_INSTALL_SUBDIR}/cmake
)
