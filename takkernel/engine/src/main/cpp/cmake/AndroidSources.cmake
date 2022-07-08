# Support script for mapengine/CMakeLists.txt that sets sources, definitions, includes, link libraries, link directories,
# and compiler options that are specific to Android targets.

set(takengine_ANDROID_DEFS
    RTTI_ENABLED
    -DTE_GLES_VERSION=3
    SQLITE_HAS_CODEC
)



set(takengine_ANDROID_INCS
)

set(takengine_ANDROID_LDIRS
    ${ttp-dist_LIB_DIRS}
    ${libLAS_LIB_DIRS}
)

set(takengine_ANDROID_LIBS
    # TTP
    spatialite
    gdal

    # System
    log
    GLESv3

    las
    las_c
)

set(takengine_ANDROID_OPTS
    $<$<NOT:$<BOOL:${MSVC}>>:-O3>
)
