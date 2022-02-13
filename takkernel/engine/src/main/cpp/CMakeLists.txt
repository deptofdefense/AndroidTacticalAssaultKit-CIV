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

    set(takengine_DEFS ${takengine_WINDOWS_DEFS})
    set(takengine_LIBS ${takengine_WINDOWS_LIBS})
    set(takengine_LDIRS ${takengine_WINDOWS_LDIRS})
    set(takengine_INCS ${takengine_COMMON_INCS} ${takengine_WINDOWS_INCS})
    set(takengine_OPTS ${takengine_WINDOWS_OPTS})
    set(takengine_SRCS ${takengine_COMMON_SRCS} ${takengine_WINDOWS_SRCS})
    set(takengine_HEADERS ${takengine_COMMON_HEADERS} ${takengine_WINDOWS_HEADERS})
    source_group("Header Files\\core" FILES ${takengine_core_COMMON_HEADERS} ${takengine_core_WINDOWS_HEADERS})
    source_group("Header Files\\currency" FILES ${takengine_currency_COMMON_HEADERS})
    source_group("Header Files\\db" FILES ${takengine_db_COMMON_HEADERS} ${takengine_db_WINDOWS_HEADERS})
    source_group("Header Files\\elevation" FILES ${takengine_elevation_COMMON_HEADERS} ${takengine_elevation_WINDOWS_HEADERS})
    source_group("Header Files\\feature" FILES ${takengine_feature_COMMON_HEADERS} ${takengine_feature_WINDOWS_HEADERS})
    source_group("Header Files\\formats\\cesium3dtiles" FILES ${takengine_formats_cesium3dtiles_WINDOWS_HEADERS})
    source_group("Header Files\\formats\\drg" FILES ${takengine_formats_drg_COMMON_HEADERS})
    source_group("Header Files\\formats\\dted" FILES ${takengine_formats_dted_COMMON_HEADERS})
    source_group("Header Files\\formats\\egm" FILES ${takengine_formats_egm_COMMON_HEADERS})
    source_group("Header Files\\formats\\gdal" FILES ${takengine_formats_gdal_COMMON_HEADERS} ${takengine_formats_gdal_WINDOWS_HEADERS})
    source_group("Header Files\\formats\\gltf" FILES ${takengine_formats_gltf_WINDOWS_HEADERS})
    source_group("Header Files\\formats\\glues" FILES ${takengine_formats_glues_COMMON_HEADERS})
    source_group("Header Files\\formats\\las" FILES ${takengine_formats_las_COMMON_HEADERS})
    source_group("Header Files\\formats\\mbtiles" FILES ${takengine_formats_mbtiles_COMMON_HEADERS} ${takengine_formats_mbtiles_WINDOWS_HEADERS})
    source_group("Header Files\\formats\\msaccess" FILES ${takengine_formats_msaccess_COMMON_HEADERS} ${takengine_formats_msaccess_WINDOWS_HEADERS})
    source_group("Header Files\\formats\\ogr" FILES ${takengine_formats_ogr_COMMON_HEADERS})
    source_group("Header Files\\formats\\osr" FILES ${takengine_formats_osr_COMMON_HEADERS})
    source_group("Header Files\\formats\\pfps" FILES ${takengine_formats_pfps_COMMON_HEADERS} ${takengine_formats_pfps_WINDOWS_HEADERS})
    source_group("Header Files\\formats\\quantizedmesh" FILES ${takengine_formats_quantizedmesh_COMMON_HEADERS})
    source_group("Header Files\\formats\\s3tc" FILES ${takengine_formats_s3tc_COMMON_HEADERS})
    source_group("Header Files\\formats\\slat" FILES ${takengine_formats_slat_COMMON_HEADERS})
    source_group("Header Files\\formats\\wmm" FILES ${takengine_formats_wmm_COMMON_HEADERS})
    source_group("Header Files\\math" FILES ${takengine_math_COMMON_HEADERS} ${takengine_math_WINDOWS_HEADERS})
    source_group("Header Files\\model" FILES ${takengine_model_COMMON_HEADERS} ${takengine_model_WINDOWS_HEADERS})
    source_group("Header Files\\port" FILES ${takengine_port_COMMON_HEADERS} ${takengine_port_WINDOWS_HEADERS})
    source_group("Header Files\\raster" FILES ${takengine_raster_base_COMMON_HEADERS} ${takengine_raster_base_WINDOWS_HEADERS})
    source_group("Header Files\\raster\\apass" FILES ${takengine_raster_apass_WINDOWS_HEADERS})
    source_group("Header Files\\raster\\gdal" FILES ${takengine_raster_gdal_WINDOWS_HEADERS})
    source_group("Header Files\\raster\\mosaic" FILES ${takengine_raster_mosaic_COMMON_HEADERS} ${takengine_raster_mosaic_WINDOWS_HEADERS})
    source_group("Header Files\\raster\\osm" FILES ${takengine_raster_osm_COMMON_HEADERS})
    source_group("Header Files\\raster\\pfps" FILES ${takengine_raster_pfps_WINDOWS_HEADERS})
    source_group("Header Files\\raster\\tilematrix" FILES ${takengine_raster_tilematrix_COMMON_HEADERS} ${takengine_raster_tilematrix_WINDOWS_HEADERS})
    source_group("Header Files\\raster\\tilereader" FILES ${takengine_raster_tilereader_WINDOWS_HEADERS})
    source_group("Header Files\\renderer" FILES ${takengine_renderer_base_COMMON_HEADERS} ${takengine_renderer_base_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\core" FILES ${takengine_renderer_core_COMMON_HEADERS} ${takengine_renderer_core_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\elevation" FILES ${takengine_renderer_elevation_COMMON_HEADERS} ${takengine_renderer_elevation_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\feature" FILES ${takengine_renderer_feature_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\impl" FILES ${takengine_renderer_impl_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\model" FILES ${takengine_renderer_model_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\raster" FILES ${takengine_renderer_raster_base_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\raster\\mosaic" FILES ${takengine_renderer_raster_mosaic_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\raster\\tilematrix" FILES ${takengine_renderer_raster_tilematrix_WINDOWS_HEADERS})
    source_group("Header Files\\renderer\\raster\\tilereader" FILES ${takengine_renderer_raster_tilereader_WINDOWS_HEADERS})
    source_group("Header Files\\thread" FILES ${takengine_thread_COMMON_HEADERS})
    source_group("Header Files\\thread\\impl" FILES ${takengine_thread_impl_COMMON_HEADERS})
    source_group("Header Files\\util" FILES ${takengine_util_COMMON_HEADERS} ${takengine_util_WINDOWS_HEADERS})
    source_group("Header Files\\vscompat" FILES ${takengine_vscompat_WINDOWS_HEADERS})
    source_group("Source Files\\core" FILES ${takengine_core_COMMON_SRCS} ${takengine_core_WINDOWS_SRCS})
    source_group("Source Files\\currency" FILES ${takengine_currency_COMMON_SRCS})
    source_group("Source Files\\db" FILES ${takengine_db_COMMON_SRCS} ${takengine_db_WINDOWS_SRCS})
    source_group("Source Files\\elevation" FILES ${takengine_elevation_COMMON_SRCS} ${takengine_elevation_WINDOWS_SRCS})
    source_group("Source Files\\feature" FILES ${takengine_feature_COMMON_SRCS} ${takengine_feature_WINDOWS_SRCS})
    source_group("Source Files\\formats\\cesium3dtiles" FILES ${takengine_formats_cesium3dtiles_WINDOWS_SRCS})
    source_group("Source Files\\formats\\drg" FILES ${takengine_formats_drg_COMMON_SRCS})
    source_group("Source Files\\formats\\dted" FILES ${takengine_formats_dted_COMMON_SRCS})
    source_group("Source Files\\formats\\egm" FILES ${takengine_formats_egm_COMMON_SRCS})
    source_group("Source Files\\formats\\gdal" FILES ${takengine_formats_gdal_COMMON_SRCS} ${takengine_formats_gdal_WINDOWS_SRCS})
    source_group("Source Files\\formats\\gltf" FILES ${takengine_formats_gltf_WINDOWS_SRCS})
    source_group("Source Files\\formats\\glues" FILES ${takengine_formats_glues_COMMON_SRCS})
    source_group("Source Files\\formats\\las" FILES ${takengine_formats_las_COMMON_SRCS})
    source_group("Source Files\\formats\\mbtiles" FILES ${takengine_formats_mbtiles_COMMON_SRCS} ${takengine_formats_mbtiles_WINDOWS_SRCS})
    source_group("Source Files\\formats\\msaccess" FILES ${takengine_formats_msaccess_COMMON_SRCS} ${takengine_formats_msaccess_WINDOWS_SRCS})
    source_group("Source Files\\formats\\ogr" FILES ${takengine_formats_ogr_COMMON_SRCS})
    source_group("Source Files\\formats\\osr" FILES ${takengine_formats_osr_COMMON_SRCS})
    source_group("Source Files\\formats\\pfps" FILES ${takengine_formats_pfps_COMMON_SRCS} ${takengine_formats_pfps_WINDOWS_SRCS})
    source_group("Source Files\\formats\\quantizedmesh" FILES ${takengine_formats_quantizedmesh_COMMON_SRCS})
    source_group("Source Files\\formats\\s3tc" FILES ${takengine_formats_s3tc_COMMON_SRCS})
    source_group("Source Files\\formats\\slat" FILES ${takengine_formats_slat_COMMON_SRCS})
    source_group("Source Files\\formats\\wmm" FILES ${takengine_formats_wmm_COMMON_SRCS})
    source_group("Source Files\\math" FILES ${takengine_math_COMMON_SRCS} ${takengine_math_WINDOWS_SRCS})
    source_group("Source Files\\model" FILES ${takengine_model_COMMON_SRCS} ${takengine_model_WINDOWS_SRCS})
    source_group("Source Files\\port" FILES ${takengine_port_COMMON_SRCS} ${takengine_port_WINDOWS_SRCS})
    source_group("Source Files\\raster" FILES ${takengine_raster_base_COMMON_SRCS} ${takengine_raster_base_WINDOWS_SRCS})
    source_group("Source Files\\raster\\apass" FILES ${takengine_raster_apass_WINDOWS_SRCS})
    source_group("Source Files\\raster\\gdal" FILES ${takengine_raster_gdal_WINDOWS_SRCS})
    source_group("Source Files\\raster\\mosaic" FILES ${takengine_raster_mosaic_COMMON_SRCS} ${takengine_raster_mosaic_WINDOWS_SRCS})
    source_group("Source Files\\raster\\osm" FILES ${takengine_raster_osm_COMMON_SRCS})
    source_group("Source Files\\raster\\pfps" FILES ${takengine_raster_pfps_WINDOWS_SRCS})
    source_group("Source Files\\raster\\tilematrix" FILES ${takengine_raster_tilematrix_COMMON_SRCS} ${takengine_raster_tilematrix_WINDOWS_SRCS})
    source_group("Source Files\\raster\\tilereader" FILES ${takengine_raster_tilereader_WINDOWS_SRCS})
    source_group("Source Files\\renderer" FILES ${takengine_renderer_base_COMMON_SRCS} ${takengine_renderer_base_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\core" FILES ${takengine_renderer_core_COMMON_SRCS} ${takengine_renderer_core_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\elevation" FILES ${takengine_renderer_elevation_COMMON_SRCS} ${takengine_renderer_elevation_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\feature" FILES ${takengine_renderer_feature_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\impl" FILES ${takengine_renderer_impl_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\model" FILES ${takengine_renderer_model_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\raster" FILES ${takengine_renderer_raster_base_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\raster\\mosaic" FILES ${takengine_renderer_raster_mosaic_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\raster\\tilematrix" FILES ${takengine_renderer_raster_tilematrix_WINDOWS_SRCS})
    source_group("Source Files\\renderer\\raster\\tilereader" FILES ${takengine_renderer_raster_tilereader_WINDOWS_SRCS})
    source_group("Source Files\\thread" FILES ${takengine_thread_COMMON_SRCS} ${takengine_thread_WINDOWS_SRCS})
    source_group("Source Files\\util" FILES ${takengine_util_COMMON_SRCS} ${takengine_util_WINDOWS_SRCS})
    source_group("Source Files\\vscompat" FILES ${takengine_vscompat_WINDOWS_SRCS})

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
