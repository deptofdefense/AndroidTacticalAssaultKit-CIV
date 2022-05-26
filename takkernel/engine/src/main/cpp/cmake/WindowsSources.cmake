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

set(takengine_core_WINDOWS_SRCS
    # Core
    ${SRC_DIR}/core/AbstractLayer.cpp
    ${SRC_DIR}/core/Datum.cpp
    ${SRC_DIR}/core/MultiLayer2.cpp
    ${SRC_DIR}/core/MultiLayerImpl.cpp
    ${SRC_DIR}/core/ProjectionFactory.cpp
    ${SRC_DIR}/core/ProxyLayer2.cpp
    ${SRC_DIR}/core/ProxyLayerImpl.cpp
    ${SRC_DIR}/core/Service.cpp
    ${SRC_DIR}/core/ServiceManagerBase2.cpp
    ${SRC_DIR}/core/ServiceManagerImpl.cpp
)

set(takengine_db_WINDOWS_SRCS
    # DB
    ${SRC_DIR}/db/CatalogDatabase.cpp
    ${SRC_DIR}/db/DatabaseFactory.cpp
    ${SRC_DIR}/db/DatabaseInformation.cpp
    ${SRC_DIR}/db/DefaultDatabaseProvider.cpp
    ${SRC_DIR}/db/WhereClauseBuilder.cpp
)

set(takengine_elevation_WINDOWS_SRCS
    # Elevation
    ${SRC_DIR}/elevation/AbstractElevationData.cpp
    ${SRC_DIR}/elevation/ElevationData.cpp
    ${SRC_DIR}/elevation/ElevationDataSpi.cpp
)

set(takengine_feature_WINDOWS_SRCS
    # Feature
    ${SRC_DIR}/feature/BruteForceLimitOffsetFeatureSetCursor.cpp
    ${SRC_DIR}/feature/DataSourceFeatureDataStore.cpp
    ${SRC_DIR}/feature/FeatureCatalogDatabase.cpp
    ${SRC_DIR}/feature/FeatureDataStore.cpp
    ${SRC_DIR}/feature/FeatureHitTestControl.cpp
    ${SRC_DIR}/feature/FeatureLayer.cpp
    ${SRC_DIR}/feature/FeatureLayer2.cpp
    ${SRC_DIR}/feature/FeatureSet.cpp
    ${SRC_DIR}/feature/GeometryTransformer.cpp
    ${SRC_DIR}/feature/HitTestService2.cpp
    ${SRC_DIR}/feature/KML_DriverDefinition.cpp
    ${SRC_DIR}/feature/KmlFeatureDataSource.cpp
    ${SRC_DIR}/feature/KMLFeatureDataSource2.cpp
    ${SRC_DIR}/feature/KMLModels.cpp
    ${SRC_DIR}/feature/KMLParser.cpp
    ${SRC_DIR}/feature/MultiplexingFeatureSetCursor.cpp
    ${SRC_DIR}/feature/OGDIDriverDefinition.cpp
    ${SRC_DIR}/feature/OGDIFeatureDataSource.cpp
    ${SRC_DIR}/feature/OGDISchemaDefinition.cpp
    ${SRC_DIR}/feature/PersistentDataSourceFeatureDataStore.cpp
    ${SRC_DIR}/feature/QuadBlob.cpp
    ${SRC_DIR}/feature/QuadBlob2.cpp
    ${SRC_DIR}/feature/RuntimeCachingFeatureDataStore.cpp
    ${SRC_DIR}/feature/RuntimeFeatureDataStore2.cpp
    ${SRC_DIR}/feature/SpatialCalculator.cpp
    ${SRC_DIR}/feature/SpatialCalculator2.cpp
    ${SRC_DIR}/feature/SQLiteDriverDefinition.cpp
)

set(takengine_formats_cesium3dtiles_WINDOWS_SRCS
    ${SRC_DIR}/formats/cesium3dtiles/PNTS.cpp
    ${SRC_DIR}/formats/cesium3dtiles/B3DM.cpp
    ${SRC_DIR}/formats/cesium3dtiles/C3DTTileset.cpp
)

set(takengine_formats_gltf_WINDOWS_SRCS
    ${SRC_DIR}/formats/gltf/GLTF.cpp
    ${SRC_DIR}/formats/gltf/GLTFv1.cpp
    ${SRC_DIR}/formats/gltf/GLTFv2.cpp
)

set (takengine_formats_las_WINDOWS_SRCS
    ${SRC_DIR}/formats/las/LAS.cpp
    ${SRC_DIR}/formats/las/LASSceneNode.cpp
)

set(takengine_formats_mbtiles_WINDOWS_SRCS
    ${SRC_DIR}/formats/mbtiles/MBTilesInfo.cpp
)

set(takengine_formats_pfps_WINDOWS_SRCS
    ${SRC_DIR}/formats/pfps/DrsDriverDefinition.cpp
    ${SRC_DIR}/formats/pfps/DrsSchemaDefinition.cpp
)

set(takengine_formats_WINDOWS_SRCS
    # Formats
    ${takengine_formats_cesium3dtiles_WINDOWS_SRCS}
    ${takengine_formats_gltf_WINDOWS_SRCS}
    ${takengine_formats_las_WINDOWS_SRCS}
    ${takengine_formats_mbtiles_WINDOWS_SRCS}
    ${takengine_formats_pfps_WINDOWS_SRCS}
)

set(takengine_math_WINDOWS_SRCS
    # Math
    ${SRC_DIR}/math/Frustum.cpp
)

set(takengine_model_WINDOWS_SRCS
    # Model
    ${SRC_DIR}/model/ASSIMPSceneSpi.cpp
    ${SRC_DIR}/model/Cesium3DTilesSceneInfoSpi.cpp
    ${SRC_DIR}/model/Cesium3DTilesSceneSpi.cpp
    ${SRC_DIR}/model/ContextCaptureGeoreferencer.cpp
    ${SRC_DIR}/model/ContextCaptureSceneInfoSpi.cpp
    ${SRC_DIR}/model/ContextCaptureSceneSpi.cpp
    ${SRC_DIR}/model/DAESceneInfoSpi.cpp
    ${SRC_DIR}/model/KMZSceneInfoSpi.cpp
    ${SRC_DIR}/model/LASSceneInfoSpi.cpp
    ${SRC_DIR}/model/LASSceneSpi.cpp
    ${SRC_DIR}/model/OBJSceneInfoSpi.cpp
    ${SRC_DIR}/model/Pix4dGeoreferencer.cpp
    ${SRC_DIR}/model/PLYSceneInfoSpi.cpp
    ${SRC_DIR}/model/ResourceMapper.cpp
    ${SRC_DIR}/model/SceneInfo.cpp
    ${SRC_DIR}/model/SceneLayer.cpp
)

set(takengine_raster_base_WINDOWS_SRCS
    ${SRC_DIR}/raster/AbstractDataStoreRasterLayer.cpp
    ${SRC_DIR}/raster/AbstractRasterLayer.cpp
    ${SRC_DIR}/raster/AutoSelectService.cpp
    ${SRC_DIR}/raster/DatasetDescriptor.cpp
    ${SRC_DIR}/raster/DatasetProjection2.cpp
    ${SRC_DIR}/raster/ImageDatasetDescriptor.cpp
    ${SRC_DIR}/raster/ImageryFileType.cpp
    ${SRC_DIR}/raster/LayerDatabase.cpp
    ${SRC_DIR}/raster/LocalRasterDataStore.cpp
    ${SRC_DIR}/raster/MosaicDatasetDescriptor.cpp
    ${SRC_DIR}/raster/PersistentRasterDataStore.cpp
    ${SRC_DIR}/raster/PrecisionImagery.cpp
    ${SRC_DIR}/raster/PrecisionImageryFactory.cpp
    ${SRC_DIR}/raster/PrecisionImagerySpi.cpp
    ${SRC_DIR}/raster/RasterDataAccess2.cpp
    ${SRC_DIR}/raster/RasterDataStore.cpp
)

set(takengine_raster_apass_WINDOWS_SRCS
    ${SRC_DIR}/raster/apass/ApassLayerInfoSpi.cpp
)

set(takengine_raster_gdal_WINDOWS_SRCS
    ${SRC_DIR}/renderer/map/layer/raster/gdal/GdalGraphicUtils.cpp
    ${SRC_DIR}/raster/gdal/GdalLayerInfo.cpp
)

set(takengine_raster_mosaic_WINDOWS_SRCS
    ${SRC_DIR}/raster/mosaic/ATAKMosaicDatabase.cpp
    ${SRC_DIR}/raster/mosaic/MosaicDatabase.cpp
    ${SRC_DIR}/raster/mosaic/MosaicDatabaseFactory2.cpp
    ${SRC_DIR}/raster/mosaic/MosaicDatabaseSpi2.cpp
    ${SRC_DIR}/raster/mosaic/MosaicUtils.cpp
)

set(takengine_raster_pfps_WINDOWS_SRCS
    ${SRC_DIR}/raster/pfps/PfpsLayerInfoSpi.cpp
    ${SRC_DIR}/raster/pfps/PfpsMapTypeFrame.cpp
    ${SRC_DIR}/raster/pfps/PfpsUtils.cpp
)

set(takengine_raster_tilematrix_WINDOWS_SRCS
    ${SRC_DIR}/raster/tilematrix/TileClient.cpp
    ${SRC_DIR}/raster/tilematrix/TileClientFactory.cpp
    ${SRC_DIR}/raster/tilematrix/TileContainerFactory.cpp
    ${SRC_DIR}/raster/tilematrix/TileMatrix.cpp
    ${SRC_DIR}/raster/tilematrix/TileScraper.cpp
)

set(takengine_raster_tilereader_WINDOWS_SRCS
    ${SRC_DIR}/raster/tilereader/TileReader2.cpp
    ${SRC_DIR}/raster/tilereader/TileReaderFactory2.cpp
)

set(takengine_raster_WINDOWS_SRCS
    # Raster
    ${takengine_raster_base_WINDOWS_SRCS}
    ${takengine_raster_apass_WINDOWS_SRCS}
    ${takengine_raster_gdal_WINDOWS_SRCS}
    ${takengine_raster_mosaic_WINDOWS_SRCS}
    ${takengine_raster_pfps_WINDOWS_SRCS}
    ${takengine_raster_tilematrix_WINDOWS_SRCS}
    ${takengine_raster_tilereader_WINDOWS_SRCS}
)

set(takengine_renderer_base_WINDOWS_SRCS
    ${SRC_DIR}/renderer/AsyncBitmapLoader.cpp
    ${SRC_DIR}/renderer/Bitmap.cpp
    ${SRC_DIR}/renderer/BitmapFactory2.cpp
    ${SRC_DIR}/renderer/GLBackground.cpp
    ${SRC_DIR}/renderer/GLLinesEmulation.cpp
    ${SRC_DIR}/renderer/GLNinePatch.cpp
    ${SRC_DIR}/renderer/GLRenderBatch.cpp
    ${SRC_DIR}/renderer/GLText.cpp
    ${SRC_DIR}/renderer/GLText2.cpp
    ${SRC_DIR}/renderer/GLText2_MSVC.cpp
    ${SRC_DIR}/renderer/GLTriangulate.cpp
    ${SRC_DIR}/renderer/GLTriangulate2.cpp
    ${SRC_DIR}/renderer/RenderAttributes.cpp
    ${SRC_DIR}/renderer/RendererUtils.cpp
    ${SRC_DIR}/renderer/RenderState.cpp
)

set(takengine_renderer_core_WINDOWS_SRCS
    ${SRC_DIR}/renderer/core/ColorControl.cpp
    ${SRC_DIR}/renderer/core/GLAsynchronousMapRenderable3.cpp
    ${SRC_DIR}/renderer/core/GLDiagnostics.cpp
    ${SRC_DIR}/renderer/core/GLContent.cpp
    ${SRC_DIR}/renderer/core/GLContentIndicator.cpp
    ${SRC_DIR}/renderer/core/GLLabel.cpp
    ${SRC_DIR}/renderer/core/GLLabelManager.cpp
    ${SRC_DIR}/renderer/core/GLMapBatchable2.cpp
    ${SRC_DIR}/renderer/core/GLMapView2.cpp
    ${SRC_DIR}/renderer/core/GLOffscreenVertex.cpp
    ${SRC_DIR}/renderer/core/GLResolvable.cpp
    ${SRC_DIR}/renderer/core/GLResolvableMapRenderable2.cpp
)

set(takengine_renderer_feature_WINDOWS_SRCS
    ${SRC_DIR}/renderer/feature/GLBatchGeometry3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchGeometryCollection3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchGeometryFeatureDataStoreRenderer2.cpp
    ${SRC_DIR}/renderer/feature/GLBatchGeometryRenderer3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchLineString3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchMultiLineString3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchMultiPoint3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchMultiPolygon3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchPoint3.cpp
    ${SRC_DIR}/renderer/feature/GLBatchPointBuffer.cpp
    ${SRC_DIR}/renderer/feature/GLBatchPolygon3.cpp
)

set(takengine_renderer_impl_WINDOWS_SRCS
    ${SRC_DIR}/renderer/impl/BitmapAdapter_MSVC.cpp
)

set(takengine_renderer_model_WINDOWS_SRCS
    ${SRC_DIR}/renderer/model/GLBatch.cpp
    ${SRC_DIR}/renderer/model/GLC3DTRenderer.cpp
    ${SRC_DIR}/renderer/model/GLMaterial.cpp
    ${SRC_DIR}/renderer/model/GLMesh.cpp
    ${SRC_DIR}/renderer/model/GLScene.cpp
    ${SRC_DIR}/renderer/model/GLSceneFactory.cpp
    ${SRC_DIR}/renderer/model/GLSceneLayer.cpp
    ${SRC_DIR}/renderer/model/GLSceneNode.cpp
    ${SRC_DIR}/renderer/model/GLSceneNodeLoader.cpp
    ${SRC_DIR}/renderer/model/GLSceneSpi.cpp
    ${SRC_DIR}/renderer/model/HitTestControl.cpp
    ${SRC_DIR}/renderer/model/MaterialManager.cpp
    ${SRC_DIR}/renderer/model/SceneLayerControl.cpp
    ${SRC_DIR}/renderer/model/SceneObjectControl.cpp
)

set(takengine_renderer_raster_base_WINDOWS_SRCS
    ${SRC_DIR}/renderer/raster/GLMapLayer2.cpp
    ${SRC_DIR}/renderer/raster/ImagerySelectionControl.cpp
    ${SRC_DIR}/renderer/raster/RasterDataAccessControl.cpp
)

set(takengine_renderer_raster_mosaic_WINDOWS_SRCS
    ${SRC_DIR}/renderer/raster/mosaic/GLMosaicMapLayer.cpp
)

set(takengine_renderer_raster_tilematrix_WINDOWS_SRCS
    ${SRC_DIR}/renderer/raster/tilematrix/GLTile.cpp
    ${SRC_DIR}/renderer/raster/tilematrix/GLTiledLayerCore.cpp
    ${SRC_DIR}/renderer/raster/tilematrix/GLTileMatrixLayer.cpp
    ${SRC_DIR}/renderer/raster/tilematrix/GLTilePatch.cpp
    ${SRC_DIR}/renderer/raster/tilematrix/GLZoomLevel.cpp
)

set(takengine_renderer_raster_tilereader_WINDOWS_SRCS
    ${SRC_DIR}/renderer/raster/tilereader/GLQuadTileNode2.cpp
    ${SRC_DIR}/renderer/raster/tilereader/GLTiledMapLayer2.cpp
    ${SRC_DIR}/renderer/raster/tilereader/GLTileMesh.cpp
    ${SRC_DIR}/renderer/raster/tilereader/TileReadRequestPriotizer.cpp
)

set(takengine_renderer_raster_WINDOWS_SRCS
    ${takengine_renderer_raster_base_WINDOWS_SRCS}
    ${takengine_renderer_raster_mosaic_WINDOWS_SRCS}
    ${takengine_renderer_raster_tilematrix_WINDOWS_SRCS}
    ${takengine_renderer_raster_tilereader_WINDOWS_SRCS}
)

set(takengine_renderer_WINDOWS_SRCS
    # Renderer
    ${takengine_renderer_base_WINDOWS_SRCS}
    ${takengine_renderer_core_WINDOWS_SRCS}
    ${takengine_renderer_elevation_WINDOWS_SRCS}
    ${takengine_renderer_feature_WINDOWS_SRCS}
    ${takengine_renderer_impl_WINDOWS_SRCS}
    ${takengine_renderer_model_WINDOWS_SRCS}
    ${takengine_renderer_raster_WINDOWS_SRCS}
)

set(takengine_thread_WINDOWS_SRCS
    # Thread
    ${SRC_DIR}/thread/impl/ThreadImpl_WIN32.cpp
)

set(takengine_util_WINDOWS_SRCS
    # Util
    ${SRC_DIR}/util/AtomicCounter_WinAPI.cpp
    ${SRC_DIR}/util/Disposable.cpp
    ${SRC_DIR}/util/ErrorHandling.cpp
    ${SRC_DIR}/util/HttpProtocolHandler.cpp
    ${SRC_DIR}/util/IO.cpp
    ${SRC_DIR}/util/URI.cpp
    ${SRC_DIR}/util/URIOfflineCache.cpp
)

set(takengine_vscompat_WINDOWS_SRCS
    # vscompat
    ${SRC_DIR}/../cpp-cli/vscompat/unistd.cpp
)

set(takengine_WINDOWS_SRCS
    ${takengine_core_WINDOWS_SRCS}
    ${takengine_db_WINDOWS_SRCS}
    ${takengine_elevation_WINDOWS_SRCS}
    ${takengine_feature_WINDOWS_SRCS}
    ${takengine_formats_WINDOWS_SRCS}
    ${takengine_math_WINDOWS_SRCS}
    ${takengine_model_WINDOWS_SRCS}
    ${takengine_raster_WINDOWS_SRCS}
    ${takengine_renderer_WINDOWS_SRCS}
    ${takengine_thread_WINDOWS_SRCS}
    ${takengine_util_WINDOWS_SRCS}
    ${takengine_vscompat_WINDOWS_SRCS}
)

set(takengine_core_WINDOWS_HEADERS
    # Core
    ${SRC_DIR}/core/AbstractLayer.h
    ${SRC_DIR}/core/AbstractLayer2.h
    ${SRC_DIR}/core/Datum.h
    ${SRC_DIR}/core/MultiLayer2.h
    ${SRC_DIR}/core/MultiLayerImpl.h
    ${SRC_DIR}/core/ProjectionFactory.h
    ${SRC_DIR}/core/ProxyLayer2.h
    ${SRC_DIR}/core/ProxyLayerImpl.h
    ${SRC_DIR}/core/Service.h
    ${SRC_DIR}/core/ServiceManagerBase2.h
    ${SRC_DIR}/core/ServiceManagerImpl.h
)

set(takengine_db_WINDOWS_HEADERS
    # DB
    ${SRC_DIR}/db/CatalogDatabase.h
    ${SRC_DIR}/db/DatabaseFactory.h
    ${SRC_DIR}/db/DatabaseInformation.h
    ${SRC_DIR}/db/DefaultDatabaseProvider.h
    ${SRC_DIR}/db/WhereClauseBuilder.h
)

set(takengine_elevation_WINDOWS_HEADERS
    # Elevation
    ${SRC_DIR}/elevation/AbstractElevationData.h
    ${SRC_DIR}/elevation/ElevationData.h
    ${SRC_DIR}/elevation/ElevationDataSpi.h
)

set(takengine_feature_WINDOWS_HEADERS
    # Feature
    ${SRC_DIR}/feature/BruteForceLimitOffsetFeatureSetCursor.h
    ${SRC_DIR}/feature/DataSourceFeatureDataStore.h
    ${SRC_DIR}/feature/FeatureCatalogDatabase.h
    ${SRC_DIR}/feature/FeatureDataStore.h
    ${SRC_DIR}/feature/FeatureHitTestControl.h
    ${SRC_DIR}/feature/FeatureLayer.h
    ${SRC_DIR}/feature/FeatureLayer2.h
    ${SRC_DIR}/feature/FeatureSet.h
    ${SRC_DIR}/feature/GeometryTransformer.h
    ${SRC_DIR}/feature/HitTestService2.h
    ${SRC_DIR}/feature/KML_DriverDefinition.h
    ${SRC_DIR}/feature/KmlFeatureDataSource.h
    ${SRC_DIR}/feature/KMLFeatureDataSource2.h
    ${SRC_DIR}/feature/KMLModels.h
    ${SRC_DIR}/feature/KMLParser.h
    ${SRC_DIR}/feature/MultiplexingFeatureSetCursor.h
    ${SRC_DIR}/feature/OGDIDriverDefinition.h
    ${SRC_DIR}/feature/OGDIFeatureDataSource.h
    ${SRC_DIR}/feature/OGDISchemaDefinition.h
    ${SRC_DIR}/feature/PersistentDataSourceFeatureDataStore.h
    ${SRC_DIR}/feature/QuadBlob.h
    ${SRC_DIR}/feature/QuadBlob2.h
    ${SRC_DIR}/feature/RuntimeCachingFeatureDataStore.h
    ${SRC_DIR}/feature/RuntimeFeatureDataStore2.h
    ${SRC_DIR}/feature/SpatialCalculator.h
    ${SRC_DIR}/feature/SpatialCalculator2.h
    ${SRC_DIR}/feature/SQLiteDriverDefinition.h
)

set(takengine_formats_cesium3dtiles_WINDOWS_HEADERS
    ${SRC_DIR}/formats/cesium3dtiles/PNTS.h
    ${SRC_DIR}/formats/cesium3dtiles/B3DM.h
    ${SRC_DIR}/formats/cesium3dtiles/C3DTTileset.h
)

set(takengine_formats_gltf_WINDOWS_HEADERS
    ${SRC_DIR}/formats/gltf/GLTF.h
)

set (takengine_formats_las_WINDOWS_HEADERS
    ${SRC_DIR}/formats/las/LAS.h
    ${SRC_DIR}/formats/las/LASSceneNode.h
)

set(takengine_formats_mbtiles_WINDOWS_HEADERS
    ${SRC_DIR}/formats/mbtiles/MBTilesInfo.h
)

set(takengine_formats_pfps_WINDOWS_HEADERS
    ${SRC_DIR}/formats/pfps/DrsDriverDefinition.h
    ${SRC_DIR}/formats/pfps/DrsSchemaDefinition.h
)

set(takengine_formats_WINDOWS_HEADERS
    # Formats
    ${takengine_formats_cesium3dtiles_WINDOWS_HEADERS}
    ${takengine_formats_gltf_WINDOWS_HEADERS}
    ${takengine_formats_las_WINDOWS_HEADERS}
    ${takengine_formats_mbtiles_WINDOWS_HEADERS}
    ${takengine_formats_pfps_WINDOWS_HEADERS}
)

set(takengine_math_WINDOWS_HEADERS
    # Math
    ${SRC_DIR}/math/Frustum.h
)

set(takengine_model_WINDOWS_HEADERS
    # Model
    ${SRC_DIR}/model/ASSIMPSceneSpi.h
    ${SRC_DIR}/model/Cesium3DTilesSceneInfoSpi.h
    ${SRC_DIR}/model/Cesium3DTilesSceneSpi.h
    ${SRC_DIR}/model/ContextCaptureGeoreferencer.h
    ${SRC_DIR}/model/ContextCaptureSceneInfoSpi.h
    ${SRC_DIR}/model/ContextCaptureSceneSpi.h
    ${SRC_DIR}/model/DAESceneInfoSpi.h
    ${SRC_DIR}/model/KMZSceneInfoSpi.h
    ${SRC_DIR}/model/LASSceneInfoSpi.h
    ${SRC_DIR}/model/LASSceneSpi.h
    ${SRC_DIR}/model/OBJSceneInfoSpi.h
    ${SRC_DIR}/model/Pix4dGeoreferencer.h
    ${SRC_DIR}/model/PLYSceneInfoSpi.h
    ${SRC_DIR}/model/ResourceMapper.h
    ${SRC_DIR}/model/SceneInfo.h
    ${SRC_DIR}/model/SceneLayer.h
)

set(takengine_raster_base_WINDOWS_HEADERS
    ${SRC_DIR}/raster/AbstractDataStoreRasterLayer.h
    ${SRC_DIR}/raster/AbstractRasterLayer.h
    ${SRC_DIR}/raster/AutoSelectService.h
    ${SRC_DIR}/raster/DatasetDescriptor.h
    ${SRC_DIR}/raster/DatasetProjection2.h
    ${SRC_DIR}/raster/ImageDatasetDescriptor.h
    ${SRC_DIR}/raster/ImageryFileType.h
    ${SRC_DIR}/raster/LayerDatabase.h
    ${SRC_DIR}/raster/LocalRasterDataStore.h
    ${SRC_DIR}/raster/MosaicDatasetDescriptor.h
    ${SRC_DIR}/raster/PersistentRasterDataStore.h
    ${SRC_DIR}/raster/PrecisionImagery.h
    ${SRC_DIR}/raster/PrecisionImageryFactory.h
    ${SRC_DIR}/raster/PrecisionImagerySpi.h
    ${SRC_DIR}/raster/RasterDataAccess2.h
    ${SRC_DIR}/raster/RasterDataStore.h
)

set(takengine_raster_apass_WINDOWS_HEADERS
    ${SRC_DIR}/raster/apass/ApassLayerInfoSpi.h
)

set(takengine_raster_gdal_WINDOWS_HEADERS
    ${SRC_DIR}/raster/gdal/GdalDatasetProjection.h
    ${SRC_DIR}/renderer/map/layer/raster/gdal/GdalGraphicUtils.h
    ${SRC_DIR}/raster/gdal/GdalLayerInfo.h
    ${SRC_DIR}/raster/gdal/GdalLibrary.h
    ${SRC_DIR}/raster/gdal/RapidPositioningControlB.h
)

set(takengine_raster_mosaic_WINDOWS_HEADERS
    ${SRC_DIR}/raster/mosaic/ATAKMosaicDatabase.h
    ${SRC_DIR}/raster/mosaic/MosaicDatabase.h
    ${SRC_DIR}/raster/mosaic/MosaicDatabaseFactory2.h
    ${SRC_DIR}/raster/mosaic/MosaicDatabaseSpi2.h
    ${SRC_DIR}/raster/mosaic/MosaicUtils.h
)

set(takengine_raster_pfps_WINDOWS_HEADERS
    ${SRC_DIR}/raster/pfps/PfpsLayerInfoSpi.h
    ${SRC_DIR}/raster/pfps/PfpsMapTypeFrame.h
    ${SRC_DIR}/raster/pfps/PfpsUtils.h
)

set(takengine_raster_tilematrix_WINDOWS_HEADERS
    ${SRC_DIR}/raster/tilematrix/TileClient.h
    ${SRC_DIR}/raster/tilematrix/TileClientFactory.h
    ${SRC_DIR}/raster/tilematrix/TileContainerFactory.h
    ${SRC_DIR}/raster/tilematrix/TileMatrix.h
    ${SRC_DIR}/raster/tilematrix/TileScraper.h
)

set(takengine_raster_tilereader_WINDOWS_HEADERS
    ${SRC_DIR}/raster/tilereader/TileReader.h
    ${SRC_DIR}/raster/tilereader/TileReader2.h
    ${SRC_DIR}/raster/tilereader/TileReaderFactory2.h
)

set(takengine_raster_WINDOWS_HEADERS
    # Raster
    ${takengine_raster_base_WINDOWS_HEADERS}
    ${takengine_raster_apass_WINDOWS_HEADERS}
    ${takengine_raster_gdal_WINDOWS_HEADERS}
    ${takengine_raster_mosaic_WINDOWS_HEADERS}
    ${takengine_raster_pfps_WINDOWS_HEADERS}
    ${takengine_raster_tilematrix_WINDOWS_HEADERS}
    ${takengine_raster_tilereader_WINDOWS_HEADERS}
)

set(takengine_renderer_base_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/AsyncBitmapLoader.h
    ${SRC_DIR}/renderer/Bitmap.h
    ${SRC_DIR}/renderer/BitmapFactory2.h
    ${SRC_DIR}/renderer/GLBackground.h
    ${SRC_DIR}/renderer/GLLinesEmulation.h
    ${SRC_DIR}/renderer/GLNinePatch.h
    ${SRC_DIR}/renderer/GLRenderBatch.h
    ${SRC_DIR}/renderer/GLText.h
    ${SRC_DIR}/renderer/GLText2.h
    ${SRC_DIR}/renderer/GLTriangulate.h
    ${SRC_DIR}/renderer/GLTriangulate2.h
    ${SRC_DIR}/renderer/RenderAttributes.h
    ${SRC_DIR}/renderer/RendererUtils.h
    ${SRC_DIR}/renderer/RenderState.h
)

set(takengine_renderer_core_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/core/ColorControl.h
    ${SRC_DIR}/renderer/core/GLAsynchronousMapRenderable3.h
    ${SRC_DIR}/renderer/core/GLDiagnostics.h
    ${SRC_DIR}/renderer/core/GLContent.h
    ${SRC_DIR}/renderer/core/GLContentIndicator.h
    ${SRC_DIR}/renderer/core/GLLabel.h
    ${SRC_DIR}/renderer/core/GLLabelManager.h
    ${SRC_DIR}/renderer/core/GLMapBatchable2.h
    ${SRC_DIR}/renderer/core/GLMapView2.h
    ${SRC_DIR}/renderer/core/GLOffscreenVertex.h
    ${SRC_DIR}/renderer/core/GLResolvable.h
    ${SRC_DIR}/renderer/core/GLResolvableMapRenderable2.h
)

set(takengine_renderer_elevation_WINDOWS_HEADERS
)

set(takengine_renderer_feature_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/feature/GLBatchGeometry3.h
    ${SRC_DIR}/renderer/feature/GLBatchGeometryCollection3.h
    ${SRC_DIR}/renderer/feature/GLBatchGeometryFeatureDataStoreRenderer2.h
    ${SRC_DIR}/renderer/feature/GLBatchGeometryRenderer3.h
    ${SRC_DIR}/renderer/feature/GLBatchLineString3.h
    ${SRC_DIR}/renderer/feature/GLBatchMultiLineString3.h
    ${SRC_DIR}/renderer/feature/GLBatchMultiPoint3.h
    ${SRC_DIR}/renderer/feature/GLBatchMultiPolygon3.h
    ${SRC_DIR}/renderer/feature/GLBatchPoint3.h
    ${SRC_DIR}/renderer/feature/GLBatchPointBuffer.h
    ${SRC_DIR}/renderer/feature/GLBatchPolygon3.h
)

set(takengine_renderer_impl_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/impl/BitmapAdapter_MSVC.h
)

set(takengine_renderer_model_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/model/GLBatch.h
    ${SRC_DIR}/renderer/model/GLC3DTRenderer.h
    ${SRC_DIR}/renderer/model/GLMaterial.h
    ${SRC_DIR}/renderer/model/GLMesh.h
    ${SRC_DIR}/renderer/model/GLScene.h
    ${SRC_DIR}/renderer/model/GLSceneFactory.h
    ${SRC_DIR}/renderer/model/GLSceneLayer.h
    ${SRC_DIR}/renderer/model/GLSceneNode.h
    ${SRC_DIR}/renderer/model/GLSceneNodeLoader.h
    ${SRC_DIR}/renderer/model/GLSceneSpi.h
    ${SRC_DIR}/renderer/model/HitTestControl.h
    ${SRC_DIR}/renderer/model/MaterialManager.h
    ${SRC_DIR}/renderer/model/SceneLayerControl.h
    ${SRC_DIR}/renderer/model/SceneObjectControl.h
)

set(takengine_renderer_raster_base_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/raster/GLMapLayer2.h
    ${SRC_DIR}/renderer/raster/ImagerySelectionControl.h
    ${SRC_DIR}/renderer/raster/RasterDataAccessControl.h
)

set(takengine_renderer_raster_mosaic_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/raster/mosaic/GLMosaicMapLayer.h
)

set(takengine_renderer_raster_tilematrix_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/raster/tilematrix/GLTile.h
    ${SRC_DIR}/renderer/raster/tilematrix/GLTiledLayerCore.h
    ${SRC_DIR}/renderer/raster/tilematrix/GLTileMatrixLayer.h
    ${SRC_DIR}/renderer/raster/tilematrix/GLTilePatch.h
    ${SRC_DIR}/renderer/raster/tilematrix/GLZoomLevel.h
)

set(takengine_renderer_raster_tilereader_WINDOWS_HEADERS
    ${SRC_DIR}/renderer/raster/tilereader/GLQuadTileNode2.h
    ${SRC_DIR}/renderer/raster/tilereader/GLTiledMapLayer2.h
    ${SRC_DIR}/renderer/raster/tilereader/GLTileMesh.h
    ${SRC_DIR}/renderer/raster/tilereader/TileReadRequestPrioritizer.h
)

set(takengine_renderer_raster_WINDOWS_HEADERS
    ${takengine_renderer_raster_base_WINDOWS_HEADERS}
    ${takengine_renderer_raster_mosaic_WINDOWS_HEADERS}
    ${takengine_renderer_raster_tilematrix_WINDOWS_HEADERS}
    ${takengine_renderer_raster_tilereader_WINDOWS_HEADERS}
)

set(takengine_renderer_WINDOWS_HEADERS
    # Renderer
    ${takengine_renderer_base_WINDOWS_HEADERS}
    ${takengine_renderer_core_WINDOWS_HEADERS}
    ${takengine_renderer_elevation_WINDOWS_HEADERS}
    ${takengine_renderer_feature_WINDOWS_HEADERS}
    ${takengine_renderer_impl_WINDOWS_HEADERS}
    ${takengine_renderer_model_WINDOWS_HEADERS}
    ${takengine_renderer_raster_WINDOWS_HEADERS}
)

set(takengine_util_WINDOWS_HEADERS
    # Util
    ${SRC_DIR}/util/Disposable.h
    ${SRC_DIR}/util/ErrorHandling.h
    ${SRC_DIR}/util/HttpProtocolHandler.h
    ${SRC_DIR}/util/SyncObject.h
    ${SRC_DIR}/util/URI.h
    ${SRC_DIR}/util/URIOfflineCache.h
)

set(takengine_vscompat_WINDOWS_HEADERS
    # vscompat
    ${SRC_DIR}/../cpp-cli/vscompat/unistd.h
)

set(takengine_WINDOWS_HEADERS
    ${takengine_core_WINDOWS_HEADERS}
    ${takengine_db_WINDOWS_HEADERS}
    ${takengine_elevation_WINDOWS_HEADERS}
    ${takengine_feature_WINDOWS_HEADERS}
    ${takengine_formats_WINDOWS_HEADERS}
    ${takengine_math_WINDOWS_HEADERS}
    ${takengine_model_WINDOWS_HEADERS}
    ${takengine_raster_WINDOWS_HEADERS}
    ${takengine_renderer_WINDOWS_HEADERS}
    ${takengine_util_WINDOWS_HEADERS}
    ${takengine_vscompat_WINDOWS_HEADERS}
)
