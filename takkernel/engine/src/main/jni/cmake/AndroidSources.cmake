# Support script for mapengine/CMakeLists.txt that sets sources, definitions, includes, link libraries, link directories,
# and compiler options that are specific to Android targets.

set(takenginejni_ANDROID_DEFS
    RTTI_ENABLED
    -DTE_GLES_VERSION=3
)

set(takenginejni_ANDROID_INCS
)

set(takenginejni_ANDROID_LDIRS
    ${ttp-dist_LIB_DIRS}
)

set(takenginejni_ANDROID_LIBS
    # TTP
    spatialite
    gdal

    # System
    log
    GLESv3
)

set(takenginejni_ANDROID_OPTS
    $<$<NOT:$<BOOL:${MSVC}>>:-O3>
)

set(takenginejni_ANDROID_SRCS
)
