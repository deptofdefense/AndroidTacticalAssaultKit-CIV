
##
## atakjni
##

if (ANDROID)
   set (TTP_DIR ../../../../../../takthirdparty/builds/android-${ANDROID_ABI}-release)
   set (LIBS_DIR ../jniLibs/${ANDROID_ABI})
   set (TE_DIR ../../../../../../takengine)
endif ()

#
# Source file lists
#

set (atakjni_COMMON_SRCS
    common.cpp
    atakjni.cpp
    jattributeset.cpp
    jconfigoptions.cpp
    jdatabaseimpl.cpp
    jdatatype.cpp
    jdrginfo.cpp
    jegm96.cpp
    jelevationmanager.cpp
    jelevationsourcemanager.cpp
    jelmgrterrainrenderservice.cpp
    jfeature.cpp
    jgdallibrary.cpp
    jgeometry.cpp
    jgeometryfactory.cpp
    jgeocalculations.cpp
    jgeomagneticfield.cpp
    jglobe.cpp
    jglmapview.cpp
    jglquadtilenode2.cpp
    jglrenderbatch2.cpp
    jglninepatch.cpp
    jgllayerfactory.cpp
    jgllinebatch.cpp
    jgltext.cpp
    jioproviderfactory.cpp
    jmapprojectiondisplaymodel.cpp
    jmapscenemodel.cpp
    jmatrix.cpp
    jmesh.cpp
    jmodelbuilder.cpp
    jmodels.cpp
    jnativeelevationchunk.cpp
    jnativeelevationsource.cpp
    jnativeelevationsourcecursor.cpp
    jnativefeaturecursor.cpp
    jnativefeaturesetcursor.cpp
    jnativefeaturedatasource.cpp
    jnativefeaturedatastore.cpp
    jnativefilecursor.cpp
    jnativegeometrymodel.cpp
    jnativegllayer3.cpp
    jnativegllayerspi2.cpp
    jnativeglmaprenderable2.cpp
    jnativelayer.cpp
    jnativemodel.cpp
    jnativeprojection.cpp
    jnativerunnable.cpp
    josrutils.cpp
    jprojectionfactory.cpp
    jqueryimpl.cpp
    jrendercontextinterop.cpp
    jstatementimpl.cpp
    jstyle.cpp
    junsafe.cpp
    jpersistentdatasourcefeaturedatastore2.cpp
    jpersistentrasterdatastore.cpp
    jskirt.cpp
    jtessellate.cpp
    jvertexdatalayout.cpp
    vsi_jfile.cpp

    # interop
    interop/JNIStringUTF.cpp
    interop/JNIByteArray.cpp
    interop/JNIDoubleArray.cpp
    interop/JNIFloatArray.cpp
    interop/JNIIntArray.cpp
    interop/JNILongArray.cpp
    interop/JNINotifyCallback.cpp
    interop/Pointer.cpp
    interop/db/Interop.cpp
    interop/db/ManagedDatabaseProvider.cpp
    interop/db/ManagedDatabase2.cpp
    interop/db/ManagedStatement.cpp
    interop/db/ManagedQuery.cpp
    interop/core/Interop.cpp
    interop/core/ManagedLayer.cpp
    interop/core/ManagedProjection.cpp
    interop/core/ManagedRenderContext.cpp
    interop/core/ManagedVisibilityListener.cpp
    interop/elevation/Interop.cpp
    interop/elevation/ManagedElevationChunk.cpp
    interop/elevation/ManagedElevationSource.cpp
    interop/feature/Interop.cpp
    interop/feature/ManagedFeatureDataSource2.cpp
    interop/java/JNICollection.cpp
    interop/java/JNIEnum.cpp
    interop/java/JNILocalRef.cpp
    interop/java/JNIPair.cpp
    interop/java/JNIPrimitive.cpp
    interop/java/JNIRunnable.cpp
    interop/math/Interop.cpp
    interop/math/ManagedGeometryModel.cpp
    interop/model/Interop.cpp
    interop/renderer/ManagedTextFormat2.cpp
    interop/renderer/core/Interop.cpp
    interop/renderer/core/ManagedGLLayer2.cpp
    interop/renderer/core/ManagedGLLayerSpi2.cpp
    interop/renderer/core/ManagedGLMapRenderable2.cpp
    interop/util/ManagedDataInput2.cpp
    interop/util/ManagedDataOutput2.cpp

    # impl
    ManagedModel.cpp

    # formats
    # Cesium 3D Tiles
    c3dt/jgltf.cpp
    c3dt/GLTF.cpp
    c3dt/GLTFRenderer.cpp
    c3dt/GLTFRenderer_v1.cpp
    c3dt/GLTFRenderer_v2.cpp
    c3dt/GLTFv1.cpp
    c3dt/GLTFv2.cpp
    c3dt/shaders.cpp
    c3dt/tiny_gltf.cpp
    c3dt/tiny_gltf_loader.cpp
)

set (atakjni_ANDROID_SRCS
)

set (atakjni_WINDOWS_SRCS
)

#
# Definition lists
#

set (atakjni_COMMON_DEFS
)

set (atakjni_WINDOWS_DEFS
)

set (atakjni_ANDROID_DEFS
	RTTI_ENABLED
	-DTE_GLES_VERSION=3
)

#
# Link directory lists
#

set (atakjni_COMMON_LDIRS
    ${LIBS_DIR}
)

set (atakjni_WINDOWS_LDIRS
)

set (atakjni_ANDROID_LDIRS
)

#
# Link library lists
#

set (atakjni_COMMON_LIBS
)

set (atakjni_ANDROID_LIBS

    # CMake targets
    takengine

    # TTP
    spatialite
    gdal

    # System
    log
    GLESv3
)

set (atakjni_WINDOWS_LIBS
)

#
# Include directories
#

set (atakjni_COMMON_INCS
    .
    ${TTP_DIR}/include
    ${TE_DIR}/mapengine/sdk/src
    ${TE_DIR}/thirdparty
    ${TE_DIR}/include/json
)

set (atakjni_WINDOWS_INCS
)

set (atakjni_ANDROID_INCS
)

#
# Targets
#

if (WIN32)
    set (atakjni_SRCS ${atakjni_COMMON_SRCS} ${atakjni_WINDOWS_SRCS})
    set (atakjni_DEFS ${atakjni_COMMON_DEFS} ${atakjni_WINDOWS_DEFS})

    # add MSVC def (not there by default)
    if (MSVC)
        set (atakjni_DEFS ${atakjni_DEFS} MSVC)
    endif (MSVC)

    set (atakjni_LDIRS ${atakjni_COMMON_LDIRS} ${atakjni_WINDOWS_LDIRS})
    set (atakjni_LIBS ${atakjni_COMMON_LIBS} ${atakjni_WINDOWS_LIBS})
    set (atakjni_INCS ${atakjni_COMMON_INCS} ${atakjni_WINDOWS_INCS})
elseif (ANDROID)
    set (atakjni_SRCS ${atakjni_COMMON_SRCS} ${atakjni_ANDROID_SRCS})
    set (atakjni_DEFS ${atakjni_COMMON_DEFS} ${atakjni_ANDROID_DEFS})
    set (atakjni_LDIRS ${atakjni_COMMON_LDIRS} ${atakjni_ANDROID_LDIRS})
    set (atakjni_LIBS ${atakjni_COMMON_LIBS} ${atakjni_ANDROID_LIBS})
    set (atakjni_INCS ${atakjni_COMMON_INCS} ${atakjni_ANDROID_INCS})
endif ()


link_directories (${atakjni_LDIRS})
add_library (atakjni SHARED ${atakjni_SRCS})
target_include_directories (atakjni PUBLIC ${atakjni_INCS})
target_link_libraries (atakjni ${atakjni_LIBS})
target_compile_definitions (atakjni PUBLIC ${atakjni_DEFS})