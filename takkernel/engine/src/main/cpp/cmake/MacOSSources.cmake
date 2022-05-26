# Support script for mapengine/CMakeLists.txt that sets sources, definitions, includes, link libraries, link directories,
# and compiler options that are specific to Android targets.

set(takengine_MACOS_DEFS
    RTTI_ENABLED
    -DTE_GLES_VERSION=3
    SQLITE_HAS_CODEC
)

set(takengine_MACOS_INCS
)

set(takengine_MACOS_LDIRS
    ${ttp-dist_LIB_DIRS}
    ${GLES-stub_LIB_DIRS}
    ${libLAS_LIB_DIRS}
)

set(takengine_MACOS_LIBS
    # GLES
    GLESv2

    # TTP
    spatialite
    gdal
    geos
    geos_c
    charset
    proj
    z

    las
    las_c
)

set(takengine_MACOS_OPTS
    $<$<NOT:$<BOOL:${MSVC}>>:-O3>
)

set(takengine_MACOS_SRCS
    # Renderer
    ${SRC_DIR}/renderer/BitmapFactory.cpp
    ${SRC_DIR}/renderer/core/GLMapViewDebug.cpp

    # Thread
    ${SRC_DIR}/thread/impl/ThreadImpl_libpthread.cpp

    # Util
    ${SRC_DIR}/util/AtomicCounter_OSAtomic.cpp
)

set(takengine_MACOS_HEADERS 
)
