
##
## takengine
##

# root source directory
set (SRCDIR sdk/src)

if (WIN32)
    set (TTP_DIR ../mapengine/cpp-cli/thirdparty)

    # assume Win32
    set (ANGLE_ARCH_SUFFIX Win32)
    set (TTP_PB_DIR ${TTP_DIR}/ttp-prebuilt)

    # is x64
    if (CMAKE_GENERATOR_PLATFORM STREQUAL "x64")
        set (ANGLE_ARCH_SUFFIX x64)
        set (TTP_PB_DIR ${TTP_DIR}/ttp-prebuilt-win64)
    endif ()
    message ("Windows arch == ${ANGLE_ARCH_SUFFIX}")
elseif (ANDROID)
   set (TTP_DIR ../../takthirdparty/builds/android-${ANDROID_ABI}-release)
endif ()

#
# Source file lists
#

set (takengine_COMMON_SRCS
    
    # Core
    ${SRCDIR}/core/AtakMapController.cpp
    ${SRCDIR}/core/AtakMapView.cpp
    ${SRCDIR}/core/Datum2.cpp
    ${SRCDIR}/core/Ellipsoid.cpp
    ${SRCDIR}/core/Ellipsoid2.cpp
    ${SRCDIR}/core/GeoPoint.cpp
    ${SRCDIR}/core/GeoPoint2.cpp
    ${SRCDIR}/core/Globe.cpp
    ${SRCDIR}/core/Layer.cpp
    ${SRCDIR}/core/Layer2.cpp
    ${SRCDIR}/core/LegacyAdapters.cpp
    ${SRCDIR}/core/MapCamera.cpp
    ${SRCDIR}/core/MapProjectionDisplayModel.cpp
    ${SRCDIR}/core/MapRenderer.cpp
    ${SRCDIR}/core/MapSceneModel.cpp
    ${SRCDIR}/core/MapSceneModel2.cpp
    ${SRCDIR}/core/ProjectionFactory2.cpp
    ${SRCDIR}/core/ProjectionFactory3.cpp
    ${SRCDIR}/core/ProjectionSpi3.cpp
    ${SRCDIR}/core/RenderContext.cpp
    ${SRCDIR}/core/RenderSurface.cpp

    # Currency
    ${SRCDIR}/currency/Currency2.cpp
    ${SRCDIR}/currency/CurrencyRegistry2.cpp
    ${SRCDIR}/currency/CatalogDatabase2.cpp

    # DB
    ${SRCDIR}/db/BindArgument.cpp
    ${SRCDIR}/db/Cursor.cpp
    ${SRCDIR}/db/CursorWrapper2.cpp
    ${SRCDIR}/db/Database.cpp
    ${SRCDIR}/db/Database2.cpp
    ${SRCDIR}/db/DatabaseFactory.cpp
    ${SRCDIR}/db/DatabaseInformation.cpp
    ${SRCDIR}/db/DatabaseWrapper.cpp
    ${SRCDIR}/db/DefaultDatabaseProvider.cpp
    ${SRCDIR}/db/RowIterator.cpp
    ${SRCDIR}/db/SpatiaLiteDB.cpp
    ${SRCDIR}/db/Statement.cpp
    ${SRCDIR}/db/WhereClauseBuilder2.cpp

    # Elevation
    ${SRCDIR}/elevation/ElevationChunk.cpp
    ${SRCDIR}/elevation/ElevationManager.cpp
    ${SRCDIR}/elevation/ElevationChunkCursor.cpp
    ${SRCDIR}/elevation/ElevationChunkFactory.cpp
    ${SRCDIR}/elevation/ElevationSource.cpp
    ${SRCDIR}/elevation/ElevationSourceManager.cpp
    ${SRCDIR}/elevation/MultiplexingElevationChunkCursor.cpp

    # Feature
    ${SRCDIR}/feature/AbstractFeatureDataStore2.cpp
    ${SRCDIR}/feature/BruteForceLimitOffsetFeatureCursor.cpp
    ${SRCDIR}/feature/DataSourceFeatureDataStore2.cpp
    ${SRCDIR}/feature/DataSourceFeatureDataStore3.cpp
    ${SRCDIR}/feature/DefaultDriverDefinition.cpp
    ${SRCDIR}/feature/DefaultDriverDefinition2.cpp
    ${SRCDIR}/feature/DefaultSchemaDefinition.cpp
    ${SRCDIR}/feature/DrawingTool.cpp
    ${SRCDIR}/feature/Envelope2.cpp
    ${SRCDIR}/feature/FDB.cpp
    ${SRCDIR}/feature/Feature.cpp
    ${SRCDIR}/feature/Feature2.cpp
    ${SRCDIR}/feature/FeatureCursor2.cpp
    ${SRCDIR}/feature/FeatureDatabase.cpp
    ${SRCDIR}/feature/FeatureDataSource.cpp
    ${SRCDIR}/feature/FeatureDataSource2.cpp
    ${SRCDIR}/feature/FeatureDataStore2.cpp
    ${SRCDIR}/feature/FeatureSet2.cpp
    ${SRCDIR}/feature/FeatureSetCursor2.cpp
    ${SRCDIR}/feature/FeatureSetDatabase.cpp
    ${SRCDIR}/feature/FeatureSpatialDatabase.cpp
    ${SRCDIR}/feature/Geometry.cpp
    ${SRCDIR}/feature/Geometry2.cpp
    ${SRCDIR}/feature/GeometryCollection.cpp
    ${SRCDIR}/feature/GeometryCollection2.cpp
    ${SRCDIR}/feature/GeometryFactory.cpp
    ${SRCDIR}/feature/GeometryTransformer.cpp
    ${SRCDIR}/feature/GpxDriverDefinition2.cpp
    ${SRCDIR}/feature/KMLDriverDefinition2.cpp
    ${SRCDIR}/feature/LegacyAdapters.cpp
    ${SRCDIR}/feature/LineString.cpp
    ${SRCDIR}/feature/LineString2.cpp
    ${SRCDIR}/feature/MultiplexingFeatureCursor.cpp
    ${SRCDIR}/feature/OGR_DriverDefinition.cpp
    ${SRCDIR}/feature/OGR_FeatureDataSource.cpp
    ${SRCDIR}/feature/OGR_SchemaDefinition.cpp
    ${SRCDIR}/feature/OGRDriverDefinition2.cpp
    ${SRCDIR}/feature/ParseGeometry.cpp
    ${SRCDIR}/feature/PersistentDataSourceFeatureDataStore2.cpp
    ${SRCDIR}/feature/Point.cpp
    ${SRCDIR}/feature/Point2.cpp
    ${SRCDIR}/feature/Polygon.cpp
    ${SRCDIR}/feature/Polygon2.cpp
    ${SRCDIR}/feature/ShapefileDriverDefinition2.cpp
    ${SRCDIR}/feature/Style.cpp

    #Formats
    ${SRCDIR}/formats/drg/DRG.cpp
    ${SRCDIR}/formats/egm/EGM96.cpp
    ${SRCDIR}/formats/gdal/GdalBitmapReader.cpp
    ${SRCDIR}/formats/glues/dict.c
    ${SRCDIR}/formats/glues/geom.c
    ${SRCDIR}/formats/glues/memalloc.c
    ${SRCDIR}/formats/glues/mesh.c
    ${SRCDIR}/formats/glues/normal.c
    ${SRCDIR}/formats/glues/priorityq.c
    ${SRCDIR}/formats/glues/render.c
    ${SRCDIR}/formats/glues/sweep.c
    ${SRCDIR}/formats/glues/tess.c
    ${SRCDIR}/formats/glues/tessmono.c
    ${SRCDIR}/formats/s3tc/S3TC.cpp
    ${SRCDIR}/formats/wmm/GeomagnetismLibrary.cpp
    
    # Math
    ${SRCDIR}/math/AABB.cpp
    ${SRCDIR}/math/Ellipsoid.cpp
    ${SRCDIR}/math/Ellipsoid2.cpp
    ${SRCDIR}/math/Frustum2.cpp
    ${SRCDIR}/math/GeometryModel.cpp
    ${SRCDIR}/math/GeometryModel2.cpp
    ${SRCDIR}/math/Matrix.cpp
    ${SRCDIR}/math/Matrix2.cpp
    ${SRCDIR}/math/Mesh.cpp
    ${SRCDIR}/math/Plane.cpp
    ${SRCDIR}/math/Plane2.cpp
    ${SRCDIR}/math/Sphere.cpp
    ${SRCDIR}/math/Sphere2.cpp
    ${SRCDIR}/math/Statistics.cpp
    ${SRCDIR}/math/Triangle.cpp
    ${SRCDIR}/math/Utils.cpp

    # Model
    ${SRCDIR}/model/Material.cpp
    ${SRCDIR}/model/Mesh.cpp
    ${SRCDIR}/model/MeshBuilder.cpp
    ${SRCDIR}/model/MeshTransformer.cpp
    ${SRCDIR}/model/Scene.cpp
    ${SRCDIR}/model/SceneBuilder.cpp
    ${SRCDIR}/model/SceneGraphBuilder.cpp
    ${SRCDIR}/model/SceneNode.cpp
    ${SRCDIR}/model/VertexDataLayout.cpp

    # Port
    ${SRCDIR}/port/Platform.cpp
    ${SRCDIR}/port/String.cpp
    ${SRCDIR}/port/StringBuilder.cpp

    # Raster
    ${SRCDIR}/raster/DatasetProjection.cpp
    ${SRCDIR}/raster/DefaultDatasetProjection.cpp
    ${SRCDIR}/raster/ImageInfo.cpp
    ${SRCDIR}/raster/osm/OSMUtils.cpp
    ${SRCDIR}/raster/mosaic/FilterMosaicDatabaseCursor2.cpp
    ${SRCDIR}/raster/mosaic/MosaicDatabase2.cpp
    ${SRCDIR}/raster/mosaic/MultiplexingMosaicDatabaseCursor2.cpp

    # Renderer
    ${SRCDIR}/renderer/AsyncBitmapLoader2.cpp
    ${SRCDIR}/renderer/Bitmap2.cpp
    ${SRCDIR}/renderer/BitmapFactory2.cpp
    ${SRCDIR}/renderer/GLDepthSampler.cpp
    ${SRCDIR}/renderer/GLES20FixedPipeline.cpp
    ${SRCDIR}/renderer/GLMatrix.cpp
    ${SRCDIR}/renderer/GLMegaTexture.cpp
    ${SRCDIR}/renderer/GLOffscreenFramebuffer.cpp
    ${SRCDIR}/renderer/GLRenderBatch.cpp
    ${SRCDIR}/renderer/GLRenderBatch2.cpp
    ${SRCDIR}/renderer/GLSLUtil.cpp
    ${SRCDIR}/renderer/GLText2.cpp
    ${SRCDIR}/renderer/GLText2_Android.cpp
    ${SRCDIR}/renderer/GLTexture.cpp
    ${SRCDIR}/renderer/GLTexture2.cpp
    ${SRCDIR}/renderer/GLTextureAtlas.cpp
    ${SRCDIR}/renderer/GLTextureAtlas2.cpp
    ${SRCDIR}/renderer/GLTextureCache.cpp
    ${SRCDIR}/renderer/GLTextureCache2.cpp
    ${SRCDIR}/renderer/GLWireframe.cpp
    ${SRCDIR}/renderer/GLWorkers.cpp
    ${SRCDIR}/renderer/RenderState.cpp
    ${SRCDIR}/renderer/Shader.cpp
    ${SRCDIR}/renderer/Skirt.cpp
    ${SRCDIR}/renderer/Tessellate.cpp
    ${SRCDIR}/renderer/core/GLAntiMeridianHelper.cpp
    ${SRCDIR}/renderer/core/GLLayer2.cpp
    ${SRCDIR}/renderer/core/GLLayerFactory2.cpp
    ${SRCDIR}/renderer/core/GLLayerSpi2.cpp
    ${SRCDIR}/renderer/core/GLMapRenderGlobals.cpp
    ${SRCDIR}/renderer/core/GLMapRenderable2.cpp
    ${SRCDIR}/renderer/core/GLMapView2.cpp
    ${SRCDIR}/renderer/elevation/ElMgrTerrainRenderService.cpp
    ${SRCDIR}/renderer/elevation/GLTerrainTile.cpp
    ${SRCDIR}/renderer/elevation/TerrainRenderService.cpp

    # Thread
    ${SRCDIR}/thread/Cond.cpp
    ${SRCDIR}/thread/Lock.cpp
    ${SRCDIR}/thread/Monitor.cpp
    ${SRCDIR}/thread/Mutex.cpp
    ${SRCDIR}/thread/RWMutex.cpp
    ${SRCDIR}/thread/ThreadPool.cpp
    ${SRCDIR}/thread/impl/ThreadImpl_common.cpp

    # Util
    ${SRCDIR}/util/AttributeSet.cpp
    ${SRCDIR}/util/AtomicRefCountable.cpp
    ${SRCDIR}/util/Blob.cpp
    ${SRCDIR}/util/BlockPoolAllocator.cpp
    ${SRCDIR}/util/ConfigOptions.cpp
    ${SRCDIR}/util/IO.cpp
    ${SRCDIR}/util/IO2.cpp
    ${SRCDIR}/util/DataInput2.cpp
    ${SRCDIR}/util/DataOutput2.cpp
    ${SRCDIR}/util/Distance.cpp
    ${SRCDIR}/util/GeomagneticField.cpp
    ${SRCDIR}/util/FutureTask.cpp
    ${SRCDIR}/util/Logging.cpp
    ${SRCDIR}/util/Logging2.cpp
    ${SRCDIR}/util/MathUtils.cpp
    ${SRCDIR}/util/Memory.cpp
    ${SRCDIR}/util/MemBuffer.cpp
    ${SRCDIR}/util/MemBuffer2.cpp
    ${SRCDIR}/util/ProcessingCallback.cpp
    ${SRCDIR}/util/ProtocolHandler.cpp
    ${SRCDIR}/util/Work.cpp
    ${SRCDIR}/util/ZipFile.cpp
)

set (takengine_ANDROID_SRCS
    # Core
    # Currency
    # DB
    # Elevation
    # Features
    # Formats
    # Math
    # Model
    # Port
    # Raster
    
    # Renderer
    ${SRCDIR}/renderer/BitmapFactory.cpp

    # Thread
    ${SRCDIR}/thread/impl/ThreadImpl_libpthread.cpp

    # Util
    ${SRCDIR}/util/AtomicCounter_NDK.cpp
)

set (takengine_WINDOWS_SRCS
    # Core
    ${SRCDIR}/core/AbstractLayer.cpp
    ${SRCDIR}/core/AbstractLayer2.cpp
    ${SRCDIR}/core/Datum.cpp
    ${SRCDIR}/core/MultiLayer2.cpp
    ${SRCDIR}/core/MultiLayerImpl.cpp
    ${SRCDIR}/core/ProjectionFactory.cpp
    ${SRCDIR}/core/ProxyLayer2.cpp
    ${SRCDIR}/core/ProxyLayerImpl.cpp
    ${SRCDIR}/core/Service.cpp
    ${SRCDIR}/core/ServiceManagerBase2.cpp
    ${SRCDIR}/core/ServiceManagerImpl.cpp

    # Currency

    # DB
    ${SRCDIR}/db/CatalogDatabase.cpp
    ${SRCDIR}/db/DatabaseFactory.cpp
    ${SRCDIR}/db/DatabaseInformation.cpp
    ${SRCDIR}/db/DefaultDatabaseProvider.cpp
    ${SRCDIR}/db/WhereClauseBuilder.cpp

    # Elevation
    ${SRCDIR}/elevation/AbstractElevationData.cpp
    ${SRCDIR}/elevation/ElevationData.cpp
    ${SRCDIR}/elevation/ElevationDataSpi.cpp

    # Feature
    ${SRCDIR}/feature/BruteForceLimitOffsetFeatureSetCursor.cpp
    ${SRCDIR}/feature/DataSourceFeatureDataStore.cpp
    ${SRCDIR}/feature/FeatureCatalogDatabase.cpp
    ${SRCDIR}/feature/FeatureDataStore.cpp
    ${SRCDIR}/feature/FeatureHitTestControl.cpp
    ${SRCDIR}/feature/FeatureLayer.cpp
    ${SRCDIR}/feature/FeatureLayer2.cpp
    ${SRCDIR}/feature/FeatureSet.cpp
    ${SRCDIR}/feature/GeometryTransformer.cpp
    ${SRCDIR}/feature/HitTestService2.cpp
    ${SRCDIR}/feature/KML_DriverDefinition.cpp
    ${SRCDIR}/feature/KmlFeatureDataSource.cpp
    ${SRCDIR}/feature/KMLFeatureDataSource2.cpp
    ${SRCDIR}/feature/KMLModels.cpp
    ${SRCDIR}/feature/KMLParser.cpp
    ${SRCDIR}/feature/MultiplexingFeatureSetCursor.cpp
    ${SRCDIR}/feature/OGDIDriverDefinition.cpp
    ${SRCDIR}/feature/OGDIFeatureDataSource.cpp
    ${SRCDIR}/feature/OGDISchemaDefinition.cpp
    ${SRCDIR}/feature/PersistentDataSourceFeatureDataStore.cpp
    ${SRCDIR}/feature/QuadBlob.cpp
    ${SRCDIR}/feature/QuadBlob2.cpp
    ${SRCDIR}/feature/RuntimeCachingFeatureDataStore.cpp
    ${SRCDIR}/feature/RuntimeFeatureDataStore2.cpp
    ${SRCDIR}/feature/SpatialCalculator.cpp
    ${SRCDIR}/feature/SpatialCalculator2.cpp

    #Formats
    ${SRCDIR}/formats/cesium3dtiles/B3DM.cpp
    ${SRCDIR}/formats/cesium3dtiles/C3DTTilesset.cpp
    ${SRCDIR}/formats/gdal/GdalDatasetProjection2.cpp
    ${SRCDIR}/formats/gltf/GLTF.cpp
    ${SRCDIR}/formats/gltf/GLTFv1.cpp
    ${SRCDIR}/formats/gltf/GLTFv2.cpp
    ${SRCDIR}/formats/mbtiles/MBTilesInfo.cpp
    ${SRCDIR}/formats/ogr/OGRFeatureDataStore.cpp
    ${SRCDIR}/formats/osr/OSRProjectionSpi.cpp

    # Math
    ${SRCDIR}/math/Frustum.cpp

    # Model
    ${SRCDIR}/model/ASSIMPSceneSpi.cpp
    ${SRCDIR}/model/Cesium3DTilesSceneInfoSpi.cpp
    ${SRCDIR}/model/Cesium3DTilesSceneSpi.cpp
    ${SRCDIR}/model/ContextCaptureGeoreferencer.cpp
    ${SRCDIR}/model/ContextCaptureSceneInfoSpi.cpp
    ${SRCDIR}/model/ContextCaptureSceneSpi.cpp
    ${SRCDIR}/model/DAESceneInfoSpi.cpp
    ${SRCDIR}/model/KMZSceneInfoSpi.cpp
    ${SRCDIR}/model/OBJSceneInfoSpi.cpp
    ${SRCDIR}/model/Pix4dGeoreferencer.cpp
    ${SRCDIR}/model/PLYSceneInfoSpi.cpp
    ${SRCDIR}/model/ResourceMapper.cpp
    ${SRCDIR}/model/SceneInfo.cpp
    ${SRCDIR}/model/SceneLayer.cpp

    # Port

    # Raster
    ${SRCDIR}/raster/apass/ApassLayerInfoSpi.cpp
    ${SRCDIR}/raster/gdal/GdalDatasetProjection.cpp
    ${SRCDIR}/renderer/map/layer/raster/gdal/GdalGraphicUtils.cpp
    ${SRCDIR}/raster/gdal/GdalLayerInfo.cpp
    ${SRCDIR}/raster/gdal/GdalLibrary.cpp
    ${SRCDIR}/raster/gdal/RapidPositioningControlB.cpp
    ${SRCDIR}/raster/mosaic/ATAKMosaicDatabase.cpp
    ${SRCDIR}/raster/mosaic/MosaicDatabase.cpp
    ${SRCDIR}/raster/mosaic/MosaicDatabaseFactory2.cpp
    ${SRCDIR}/raster/mosaic/MosaicDatabaseSpi2.cpp
    ${SRCDIR}/raster/mosaic/MosaicUtils.cpp
    ${SRCDIR}/raster/pfps/PfpsLayerInfoSpi.cpp
    ${SRCDIR}/raster/pfps/PfpsMapTypeFrame.cpp
    ${SRCDIR}/raster/pfps/PfpsUtils.cpp
    ${SRCDIR}/raster/tilematrix/TileClient.cpp
    ${SRCDIR}/raster/tilematrix/TileClientFactory.cpp
    ${SRCDIR}/raster/tilematrix/TileContainerFactory.cpp
    ${SRCDIR}/raster/tilematrix/TileMatrix.cpp
    ${SRCDIR}/raster/tilematrix/TileScraper.cpp
    ${SRCDIR}/raster/tilereader/TileReader.cpp
    ${SRCDIR}/raster/tilereader/TileReader2.cpp
    ${SRCDIR}/raster/tilereader/TileReaderFactory2.cpp
    ${SRCDIR}/raster/AbstractDataStoreRasterLayer.cpp
    ${SRCDIR}/raster/AbstractRasterLayer.cpp
    ${SRCDIR}/raster/AutoSelectService.cpp
    ${SRCDIR}/raster/DatasetDescriptor.cpp
    ${SRCDIR}/raster/DatasetProjection2.cpp
    ${SRCDIR}/raster/ImageDatasetDescriptor.cpp
    ${SRCDIR}/raster/ImageryFileType.cpp
    ${SRCDIR}/raster/LayerDatabase.cpp
    ${SRCDIR}/raster/LocalRasterDataStore.cpp
    ${SRCDIR}/raster/MosaicDatasetDescriptor.cpp
    ${SRCDIR}/raster/PersistentRasterDataStore.cpp
    ${SRCDIR}/raster/PrecisionImagery.cpp
    ${SRCDIR}/raster/PrecisionImageryFactory.cpp
    ${SRCDIR}/raster/PrecisionImagerySpi.cpp
    ${SRCDIR}/raster/RasterDataAccess2.cpp
    ${SRCDIR}/raster/RasterDataStore.cpp

    # Renderer
    ${SRCDIR}/renderer/core/ColorControl.cpp
    ${SRCDIR}/renderer/map/GLAsynchronousMapRenderable.cpp
    ${SRCDIR}/renderer/core/GLAsynchronousMapRenderable2.cpp
    ${SRCDIR}/renderer/core/GLAsynchronousMapRenderable3.cpp
    ${SRCDIR}/renderer/core/GLContentIndicator.cpp
    ${SRCDIR}/renderer/core/GLLabel.cpp
    ${SRCDIR}/renderer/core/GLLabelManager.cpp
    ${SRCDIR}/renderer/map/layer/GLLayerFactory.cpp
    ${SRCDIR}/renderer/map/layer/GLLayerSpi.cpp
    ${SRCDIR}/renderer/core/GLMapBatchable2.cpp
    ${SRCDIR}/renderer/map/GLMapRenderer.cpp
    ${SRCDIR}/renderer/map/GLMapView.cpp
    ${SRCDIR}/renderer/core/GLMapView2.cpp
    ${SRCDIR}/renderer/core/GLOffscreenVertex.cpp
    ${SRCDIR}/renderer/core/GLResolvable.cpp
    ${SRCDIR}/renderer/core/GLResolvableMapRenderable2.cpp
    ${SRCDIR}/renderer/core/GLTerrain.cpp
    ${SRCDIR}/renderer/core/LegacyAdapters.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometry2.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometry3.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometryCollection2.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometryCollection3.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometryFeatureDataStoreRenderer.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometryFeatureDataStoreRenderer2.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometryRenderer2.cpp
    ${SRCDIR}/renderer/feature/GLBatchGeometryRenderer3.cpp
    ${SRCDIR}/renderer/feature/GLBatchLineString2.cpp
    ${SRCDIR}/renderer/feature/GLBatchLineString3.cpp
    ${SRCDIR}/renderer/feature/GLBatchMultiLineString2.cpp
    ${SRCDIR}/renderer/feature/GLBatchMultiLineString3.cpp
    ${SRCDIR}/renderer/feature/GLBatchMultiPoint2.cpp
    ${SRCDIR}/renderer/feature/GLBatchMultiPoint3.cpp
    ${SRCDIR}/renderer/feature/GLBatchMultiPolygon2.cpp
    ${SRCDIR}/renderer/feature/GLBatchMultiPolygon3.cpp
    ${SRCDIR}/renderer/feature/GLBatchPoint2.cpp
    ${SRCDIR}/renderer/feature/GLBatchPoint3.cpp
    ${SRCDIR}/renderer/feature/GLBatchPointBuffer.cpp
    ${SRCDIR}/renderer/feature/GLBatchPolygon2.cpp
    ${SRCDIR}/renderer/feature/GLBatchPolygon3.cpp
    ${SRCDIR}/renderer/impl/BitmapAdapter_MSVC.cpp
    ${SRCDIR}/renderer/model/GLMaterial.cpp
    ${SRCDIR}/renderer/model/GLMesh.cpp
    ${SRCDIR}/renderer/model/GLProgressiveScene.cpp
    ${SRCDIR}/renderer/model/GLScene.cpp
    ${SRCDIR}/renderer/model/GLSceneFactory.cpp
    ${SRCDIR}/renderer/model/GLSceneLayer.cpp
    ${SRCDIR}/renderer/model/GLSceneNode.cpp
    ${SRCDIR}/renderer/model/GLSceneNodeLoader.cpp
    ${SRCDIR}/renderer/model/GLSceneSpi.cpp
    ${SRCDIR}/renderer/model/HitTestControl.cpp
    ${SRCDIR}/renderer/model/MaterialManager.cpp
    ${SRCDIR}/renderer/model/SceneLayerControl.cpp
    ${SRCDIR}/renderer/model/SceneObjectControl.cpp
    ${SRCDIR}/renderer/raster/mosaic/GLMosaicMapLayer.cpp
    ${SRCDIR}/renderer/raster/tilematrix/GLTile.cpp
    ${SRCDIR}/renderer/raster/tilematrix/GLTiledLayerCore.cpp
    ${SRCDIR}/renderer/raster/tilematrix/GLTileMatrixLayer.cpp
    ${SRCDIR}/renderer/raster/tilematrix/GLTilePatch.cpp
    ${SRCDIR}/renderer/raster/tilematrix/GLZoomLevel.cpp
    ${SRCDIR}/renderer/raster/tilereader/GLQuadTileNode2.cpp
    ${SRCDIR}/renderer/raster/tilereader/GLTiledMapLayer2.cpp
    ${SRCDIR}/renderer/raster/tilereader/GLTileMesh.cpp
    ${SRCDIR}/renderer/raster/GLMapLayer2.cpp
    ${SRCDIR}/renderer/raster/ImagerySelectionControl.cpp
    ${SRCDIR}/renderer/raster/RasterDataAccessControl.cpp
    ${SRCDIR}/renderer/AsyncBitmapLoader.cpp
    ${SRCDIR}/renderer/Bitmap.cpp
    ${SRCDIR}/renderer/BitmapFactory2.cpp
    ${SRCDIR}/renderer/GLBackground.cpp
    ${SRCDIR}/renderer/GLLinesEmulation.cpp
    ${SRCDIR}/renderer/GLNinePatch.cpp
    ${SRCDIR}/renderer/GLRenderBatch.cpp
    ${SRCDIR}/renderer/GLText.cpp
    ${SRCDIR}/renderer/GLText2.cpp
    ${SRCDIR}/renderer/GLText2_MSVC.cpp
    ${SRCDIR}/renderer/GLTriangulate.cpp
    ${SRCDIR}/renderer/GLTriangulate2.cpp
    ${SRCDIR}/renderer/RenderAttributes.cpp
    ${SRCDIR}/renderer/RendererUtils.cpp
    ${SRCDIR}/renderer/RenderState.cpp
    
    # Thread
    ${SRCDIR}/thread/impl/ThreadImpl_WIN32.cpp
    
    # Util
    ${SRCDIR}/util/AtomicCounter_WinAPI.cpp
    ${SRCDIR}/util/Disposable.cpp
    ${SRCDIR}/util/ErrorHandling.cpp
    ${SRCDIR}/util/HttpProtocolHandler.cpp
    ${SRCDIR}/util/SyncObject.cpp
    ${SRCDIR}/util/URI.cpp
    
    # vscompat
    ${SRCDIR}/../../cpp-cli/vscompat/unistd.cpp
)

#
# Definition lists
#

set (takengine_COMMON_DEFS
)

set (takengine_WINDOWS_DEFS
    use_namespace
    WIN32_LEAN_AND_MEAN
    EMULATE_GL_LINES
    _USE_MATH_DEFINES
    NOMINMAX
    ENGINE_EXPORTS
    ZLIB_DLL
    ZLIB_WINAPI
    _SCL_SECURE_NO_WARNINGS
    _CRT_SECURE_NO_WARNINGS
)

set (takengine_ANDROID_DEFS
    RTTI_ENABLED
    -DTE_GLES_VERSION=3
    SQLITE_HAS_CODEC
)

#
# Link directory lists
#

set (takengine_COMMON_LDIRS
)

set (takengine_WINDOWS_LDIRS
    ${TTP_PB_DIR}
    ${TTP_DIR}/angle
)

set (takengine_ANDROID_LDIRS
    ${TTP_DIR}/lib
)

#
# Link library lists
#

set (takengine_COMMON_LIBS
)

set (takengine_ANDROID_LIBS

    # TTP
    spatialite
    gdal

    # System
    log
    GLESv3
)

set (takengine_WINDOWS_LIBS

    # Configuration dependent TTP
    debug Debug_${ANGLE_ARCH_SUFFIX}/lib/libGLESv2
    optimized Release_${ANGLE_ARCH_SUFFIX}/lib/libGLESv2
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

    # System
    Dbghelp
)

#
# Include directories
#

set (takengine_COMMON_INCS
    gl/khronos/OpenGL/api
    gl/khronos/EGL/api
    ${SRCDIR}
    ../thirdparty/stlsoft/include
)

set (takengine_WINDOWS_INCS
    ../thirdparty
    ${TTP_PB_DIR}/include/libkml
    ${TTP_PB_DIR}/include
    ${TTP_PB_DIR}/include/libxml2
    cpp-cli/vscompat
)

set (takengine_ANDROID_INCS
    ${TTP_DIR}/include
    ${TTP_DIR}/include/kml
    ${TTP_DIR}/include/kml/third_party/boost_1_34_1
    ${TTP_DIR}/include/libxml2
)

#
# Targets
#

if (WIN32)
    set (takengine_SRCS ${takengine_COMMON_SRCS} ${takengine_WINDOWS_SRCS})
    set (takengine_DEFS ${takengine_COMMON_DEFS} ${takengine_WINDOWS_DEFS})

    # add MSVC def (not there by default)
    if (MSVC)
        set (takengine_DEFS ${takengine_DEFS} MSVC)
    endif (MSVC)

    set (takengine_LDIRS ${takengine_COMMON_LDIRS} ${takengine_WINDOWS_LDIRS})
    set (takengine_LIBS ${takengine_COMMON_LIBS} ${takengine_WINDOWS_LIBS})
    set (takengine_INCS ${takengine_COMMON_INCS} ${takengine_WINDOWS_INCS})
elseif (ANDROID)
    set (takengine_SRCS ${takengine_COMMON_SRCS} ${takengine_ANDROID_SRCS})
    set (takengine_DEFS ${takengine_COMMON_DEFS} ${takengine_ANDROID_DEFS})
    set (takengine_LDIRS ${takengine_COMMON_LDIRS} ${takengine_ANDROID_LDIRS})
    set (takengine_LIBS ${takengine_COMMON_LIBS} ${takengine_ANDROID_LIBS})
    set (takengine_INCS ${takengine_COMMON_INCS} ${takengine_ANDROID_INCS})
endif ()


link_directories (${takengine_LDIRS})
add_library (takengine SHARED ${takengine_SRCS})
target_include_directories (takengine PUBLIC ${takengine_INCS})
target_link_libraries (takengine ${takengine_LIBS})
target_compile_definitions (takengine PUBLIC ${takengine_DEFS})
