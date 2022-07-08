# Support script for mapengine/CMakeLists.txt that sets sources, definitions, includes, link libraries, link directories,
# and compiler options that are specific to Windows targets.

set(takengine_WINDOWS_DEFS
    use_namespace
    WIN32_LEAN_AND_MEAN
    WIN32
    EMULATE_GL_LINES
    _USE_MATH_DEFINES
    NOMINMAX
    ENGINE_EXPORTS
    SQLITE_HAS_CODEC
    ZLIB_DLL
    ZLIB_WINAPI
    $<IF:$<CONFIG:Debug>,_DEBUG,_NDEBUG>
    _SCL_SECURE_NO_WARNINGS
    _CRT_SECURE_NO_WARNINGS
    $<$<BOOL:${MSVC}>:MSVC>
)

set(takengine_WINDOWS_INCS
    ${SRC_DIR}/../cpp-cli/vscompat
)

set(takengine_WINDOWS_LIBS
    # GLES
    lib/GLESv2

    # Configuration dependent TTP
    debug debuglib/libkmlbase
    optimized lib/libkmlbase
    debug debuglib/libkmlconvenience
    optimized lib/libkmlconvenience
    debug debuglib/libkmldom
    optimized lib/libkmldom
    debug debuglib/libkmlengine
    optimized lib/libkmlengine
    debug debuglib/libkmlregionator
    optimized lib/libkmlregionator
    debug debuglib/libkmlxsd
    optimized lib/libkmlxsd

    # General TTP
    lib/sqlite3_i
    lib/spatialite_i
    lib/libxml2
    lib/geos_c_i
    lib/proj_i
    lib/minizip_static
    lib/libexpat
    lib/uriparser
    lib/zlibwapi
    lib/gdal_i
    lib/ogdi
    lib/assimp
    lib/libcurl
    lib/libssl
    lib/libcrypto

    # XXX--liblas (anomaly on liblas windows build CMake path for release is in "Debug" folder)
    Debug/liblas
    Debug/liblas_c

    # System
    Dbghelp
)

set(takengine_WINDOWS_LDIRS
    #XXX-- package_info provides only the one lib path. Ideally it would be both and release and debug libraries would have unique names
    ${ttp-dist_LIB_DIRS}/..
    ${GLES-stub_LIB_DIRS}/..
    ${libLAS_LIB_DIRS}
)

set(takengine_WINDOWS_OPTS
    # Set optimization level based on configuration.
    $<IF:$<CONFIG:Debug>,/Od,/O2>

    # Create PDBs for Debug and Release
    $<$<CONFIG:Release>:/Zi>

    # Treat warnings 4456, 4458, and (if generating Debug configuration) 4706 as errors.
    /we4456
    /we4458
    $<$<CONFIG:Debug>:/we4706>

    # Disable warnings 4091, 4100, 4127, 4251, 4275, 4290, and (if generating Release configuration) 4800.
    /wd4091
    /wd4100
    /wd4127
    /wd4251
    /wd4275
    /wd4290
    $<$<CONFIG:Release>:/wd4800>

    # Set Warning Level to 3 and Treat warnings as Errors
    /W3
    /WX
)

