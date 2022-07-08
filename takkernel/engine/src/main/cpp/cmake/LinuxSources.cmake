# Support script for mapengine/CMakeLists.txt that sets sources, definitions, includes, link libraries, link directories,
# and compiler options that are specific to Android targets.

set(takengine_LINUX_DEFS
    RTTI_ENABLED
    -DTE_GLES_VERSION=3
    SQLITE_HAS_CODEC
)

set(takengine_LINUX_INCS
)

set(takengine_LINUX_LDIRS
    ${ttp-dist_LIB_DIRS}
    ${GLES-stub_LIB_DIRS}
    ${libLAS_LIB_DIRS}
)

set(takengine_LINUX_LIBS
    # GLES
    GLESv2

    # TTP
    spatialite
    gdal

    las
    las_c
)

set(takengine_LINUX_OPTS
    $<$<NOT:$<BOOL:${MSVC}>>:-O3>
)

set(takengine_LINUX_SRCS
)

set(takengine_LINUX_HEADERS
)
