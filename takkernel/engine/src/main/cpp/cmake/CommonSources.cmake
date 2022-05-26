# Support script for mapengine/CMakeLists.txt that sets sources, definitions, includes, link libraries, link directories,
# and compiler options that are used for all platform targets.

set(takengine_COMMON_INCS
    ${SRC_DIR}
    ${khronos_INCLUDE_DIRS}
    ${stl-soft_INCLUDE_DIRS}
    ${tinygltf-tak_INCLUDE_DIRS}
    ${tinygltfloader-tak_INCLUDE_DIRS}
    ${ttp-dist_INCLUDE_DIRS}
    ${libLAS_INCLUDE_DIRS}
)

set(takengine_core_COMMON_SRCS
    # Core
    ${SRC_DIR}/core/AbstractLayer2.cpp
    ${SRC_DIR}/core/AtakMapController.cpp
    ${SRC_DIR}/core/AtakMapView.cpp
    ${SRC_DIR}/core/Datum2.cpp
    ${SRC_DIR}/core/Ellipsoid.cpp
    ${SRC_DIR}/core/Ellipsoid2.cpp
    ${SRC_DIR}/core/GeoPoint.cpp
    ${SRC_DIR}/core/GeoPoint2.cpp
    ${SRC_DIR}/core/Globe.cpp
    ${SRC_DIR}/core/Layer.cpp
    ${SRC_DIR}/core/Layer2.cpp
    ${SRC_DIR}/core/LegacyAdapters.cpp
    ${SRC_DIR}/core/MapCamera.cpp
    ${SRC_DIR}/core/MapProjectionDisplayModel.cpp
    ${SRC_DIR}/core/MapRenderer.cpp
	${SRC_DIR}/core/MapRenderer2.cpp
    ${SRC_DIR}/core/MapSceneModel.cpp
    ${SRC_DIR}/core/MapSceneModel2.cpp
    ${SRC_DIR}/core/ProjectionFactory2.cpp
    ${SRC_DIR}/core/ProjectionFactory3.cpp
    ${SRC_DIR}/core/ProjectionSpi3.cpp
    ${SRC_DIR}/core/RenderContext.cpp
    ${SRC_DIR}/core/RenderSurface.cpp
)

set(takengine_currency_COMMON_SRCS
    # Currency
    ${SRC_DIR}/currency/Currency2.cpp
    ${SRC_DIR}/currency/CurrencyRegistry2.cpp
    ${SRC_DIR}/currency/CatalogDatabase2.cpp
)

set(takengine_db_COMMON_SRCS
    # DB
    ${SRC_DIR}/db/BindArgument.cpp
    ${SRC_DIR}/db/Cursor.cpp
    ${SRC_DIR}/db/CursorWrapper2.cpp
    ${SRC_DIR}/db/Database.cpp
    ${SRC_DIR}/db/Database2.cpp
    ${SRC_DIR}/db/DatabaseFactory.cpp
    ${SRC_DIR}/db/DatabaseInformation.cpp
    ${SRC_DIR}/db/DatabaseWrapper.cpp
    ${SRC_DIR}/db/DefaultDatabaseProvider.cpp
    ${SRC_DIR}/db/RowIterator.cpp
    ${SRC_DIR}/db/SpatiaLiteDB.cpp
    ${SRC_DIR}/db/Statement.cpp
    ${SRC_DIR}/db/WhereClauseBuilder2.cpp
)

set(takengine_elevation_COMMON_SRCS
    # Elevation
    ${SRC_DIR}/elevation/ElevationChunk.cpp
    ${SRC_DIR}/elevation/ElevationManager.cpp
    ${SRC_DIR}/elevation/ElevationChunkCursor.cpp
    ${SRC_DIR}/elevation/ElevationChunkFactory.cpp
    ${SRC_DIR}/elevation/ElevationHeatMapLayer.cpp
    ${SRC_DIR}/elevation/ElevationSource.cpp
    ${SRC_DIR}/elevation/ElevationSourceManager.cpp
    ${SRC_DIR}/elevation/MultiplexingElevationChunkCursor.cpp
    ${SRC_DIR}/elevation/TerrainSlopeAngleLayer.cpp
)

set(takengine_feature_COMMON_SRCS
    # Feature
    ${SRC_DIR}/feature/AbstractFeatureDataStore2.cpp
    ${SRC_DIR}/feature/BruteForceLimitOffsetFeatureCursor.cpp
    ${SRC_DIR}/feature/DataSourceFeatureDataStore2.cpp
    ${SRC_DIR}/feature/DataSourceFeatureDataStore3.cpp
    ${SRC_DIR}/feature/DefaultDriverDefinition.cpp
    ${SRC_DIR}/feature/DefaultDriverDefinition2.cpp
    ${SRC_DIR}/feature/DefaultSchemaDefinition.cpp
    ${SRC_DIR}/feature/DrawingTool.cpp
    ${SRC_DIR}/feature/Envelope2.cpp
    ${SRC_DIR}/feature/FDB.cpp
    ${SRC_DIR}/feature/Feature.cpp
    ${SRC_DIR}/feature/Feature2.cpp
    ${SRC_DIR}/feature/FeatureCursor2.cpp
    ${SRC_DIR}/feature/FeatureDatabase.cpp
    ${SRC_DIR}/feature/FeatureDataSource.cpp
    ${SRC_DIR}/feature/FeatureDataSource2.cpp
    ${SRC_DIR}/feature/FeatureDataStore2.cpp
    ${SRC_DIR}/feature/FeatureSet2.cpp
    ${SRC_DIR}/feature/FeatureSetCursor2.cpp
    ${SRC_DIR}/feature/FeatureSetDatabase.cpp
    ${SRC_DIR}/feature/FeatureSpatialDatabase.cpp
    ${SRC_DIR}/feature/FilterFeatureCursor2.cpp
    ${SRC_DIR}/feature/FilterFeatureDataStore2.cpp
    ${SRC_DIR}/feature/FilterFeatureSetCursor2.cpp
    ${SRC_DIR}/feature/Geometry.cpp
    ${SRC_DIR}/feature/Geometry2.cpp
    ${SRC_DIR}/feature/GeometryCollection.cpp
    ${SRC_DIR}/feature/GeometryCollection2.cpp
    ${SRC_DIR}/feature/GeometryFactory.cpp
    ${SRC_DIR}/feature/GeometryTransformer.cpp
    ${SRC_DIR}/feature/GpxDriverDefinition2.cpp
    ${SRC_DIR}/feature/KMLDriverDefinition2.cpp
    ${SRC_DIR}/feature/LegacyAdapters.cpp
    ${SRC_DIR}/feature/LineString.cpp
    ${SRC_DIR}/feature/LineString2.cpp
    ${SRC_DIR}/feature/MultiplexingFeatureCursor.cpp
    ${SRC_DIR}/feature/OGR_DriverDefinition.cpp
    ${SRC_DIR}/feature/OGR_FeatureDataSource.cpp
    ${SRC_DIR}/feature/OGR_SchemaDefinition.cpp
    ${SRC_DIR}/feature/OGRDriverDefinition2.cpp
    ${SRC_DIR}/feature/ParseGeometry.cpp
    ${SRC_DIR}/feature/PersistentDataSourceFeatureDataStore2.cpp
    ${SRC_DIR}/feature/Point.cpp
    ${SRC_DIR}/feature/Point2.cpp
    ${SRC_DIR}/feature/Polygon.cpp
    ${SRC_DIR}/feature/Polygon2.cpp
    ${SRC_DIR}/feature/QuadBlob.cpp
    ${SRC_DIR}/feature/QuadBlob2.cpp
    ${SRC_DIR}/feature/ShapefileDriverDefinition2.cpp
    ${SRC_DIR}/feature/SpatialCalculator.cpp
    ${SRC_DIR}/feature/SpatialCalculator2.cpp
    ${SRC_DIR}/feature/Style.cpp
)

set (takengine_formats_drg_COMMON_SRCS
    ${SRC_DIR}/formats/drg/DRG.cpp
)

set (takengine_formats_dted_COMMON_SRCS
    ${SRC_DIR}/formats/dted/DtedChunkReader.cpp
    ${SRC_DIR}/formats/dted/DtedElevationSource.cpp
    ${SRC_DIR}/formats/dted/DtedSampler.cpp
)

set (takengine_formats_egm_COMMON_SRCS
    ${SRC_DIR}/formats/egm/EGM96.cpp
)

set (takengine_formats_gdal_COMMON_SRCS
    ${SRC_DIR}/formats/gdal/GdalBitmapReader.cpp
    ${SRC_DIR}/formats/gdal/GdalDatasetProjection2.cpp
)

set (takengine_formats_glues_COMMON_SRCS
    ${SRC_DIR}/formats/glues/dict.c
    ${SRC_DIR}/formats/glues/geom.c
    ${SRC_DIR}/formats/glues/memalloc.c
    ${SRC_DIR}/formats/glues/mesh.c
    ${SRC_DIR}/formats/glues/normal.c
    ${SRC_DIR}/formats/glues/priorityq.c
    ${SRC_DIR}/formats/glues/render.c
    ${SRC_DIR}/formats/glues/sweep.c
    ${SRC_DIR}/formats/glues/tess.c
    ${SRC_DIR}/formats/glues/tessmono.c
)

set (takengine_formats_mbtiles_COMMON_SRCS
    ${SRC_DIR}/formats/mbtiles/MBTilesInfo.cpp
    ${SRC_DIR}/formats/mbtiles/MVTFeatureDataSource.cpp
    ${SRC_DIR}/formats/mbtiles/MVTFeatureDataStore.cpp
)

set (takengine_formats_msaccess_COMMON_SRCS
    ${SRC_DIR}/formats/msaccess/MsAccessDatabaseFactory.cpp
)

set (takengine_formats_ogr_COMMON_SRCS
    ${SRC_DIR}/formats/ogr/AutoStyleSchemaHandler.cpp
    ${SRC_DIR}/formats/ogr/OGRFeatureDataStore.cpp
    ${SRC_DIR}/formats/ogr/OGRUtils.cpp
    ${SRC_DIR}/formats/ogr/OGR_Content.cpp
    ${SRC_DIR}/formats/ogr/OGR_Content2.cpp
    ${SRC_DIR}/formats/ogr/OGR_FeatureDataSource2.cpp
)

set (takengine_formats_osr_COMMON_SRCS
    ${SRC_DIR}/formats/osr/OSRProjectionSpi.cpp
)

set (takengine_formats_pfps_COMMON_SRCS
    ${SRC_DIR}/formats/pfps/FalconViewFeatureDataSource.cpp
)

set (takengine_formats_quantizedmesh_COMMON_SRCS
    ${SRC_DIR}/formats/quantizedmesh/QMESourceLayer.cpp
    ${SRC_DIR}/formats/quantizedmesh/TileCoord.cpp
    ${SRC_DIR}/formats/quantizedmesh/TileExtents.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/EdgeIndices.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/IndexData.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/Indices.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/QMElevationSampler.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/QMElevationSource.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/TerrainData.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/TerrainDataCache.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/TileCoordImpl.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/TileHeader.cpp
    ${SRC_DIR}/formats/quantizedmesh/impl/VertexData.cpp
)

set (takengine_formats_s3tc_COMMON_SRCS
    ${SRC_DIR}/formats/s3tc/S3TC.cpp
)

set (takengine_formats_slat_COMMON_SRCS
    ${SRC_DIR}/formats/slat/cons.cpp
    ${SRC_DIR}/formats/slat/deltat.cpp
    ${SRC_DIR}/formats/slat/helpers.cpp
    ${SRC_DIR}/formats/slat/rslib.cpp
    ${SRC_DIR}/formats/slat/smeph.cpp
    ${SRC_DIR}/formats/slat/rs.cpp
    ${SRC_DIR}/formats/slat/CelestialIllumination.cpp
)

set (takengine_formats_wmm_COMMON_SRCS
    ${SRC_DIR}/formats/wmm/GeomagnetismLibrary.cpp
)

set(takengine_formats_COMMON_SRCS
    # Formats
    ${takengine_formats_drg_COMMON_SRCS}
    ${takengine_formats_dted_COMMON_SRCS}
    ${takengine_formats_egm_COMMON_SRCS}
    ${takengine_formats_gdal_COMMON_SRCS}
    ${takengine_formats_glues_COMMON_SRCS}
    ${takengine_formats_mbtiles_COMMON_SRCS}
    ${takengine_formats_msaccess_COMMON_SRCS}
    ${takengine_formats_ogr_COMMON_SRCS}
    ${takengine_formats_osr_COMMON_SRCS}
    ${takengine_formats_pfps_COMMON_SRCS}
    ${takengine_formats_quantizedmesh_COMMON_SRCS}
    ${takengine_formats_s3tc_COMMON_SRCS}
    ${takengine_formats_slat_COMMON_SRCS}
    ${takengine_formats_wmm_COMMON_SRCS}
)

set(takengine_math_COMMON_SRCS
    # Math
    ${SRC_DIR}/math/AABB.cpp
    ${SRC_DIR}/math/Ellipsoid.cpp
    ${SRC_DIR}/math/Ellipsoid2.cpp
    ${SRC_DIR}/math/Frustum2.cpp
    ${SRC_DIR}/math/GeometryModel.cpp
    ${SRC_DIR}/math/GeometryModel2.cpp
    ${SRC_DIR}/math/Matrix.cpp
    ${SRC_DIR}/math/Matrix2.cpp
	${SRC_DIR}/math/Mesh.cpp
	${SRC_DIR}/math/PackedRay.cpp
	${SRC_DIR}/math/PackedVector.cpp
    ${SRC_DIR}/math/Plane.cpp
    ${SRC_DIR}/math/Plane2.cpp
    ${SRC_DIR}/math/Sphere.cpp
    ${SRC_DIR}/math/Sphere2.cpp
    ${SRC_DIR}/math/Statistics.cpp
    ${SRC_DIR}/math/Triangle.cpp
    ${SRC_DIR}/math/Utils.cpp
)

set(takengine_model_COMMON_SRCS
    # Model
    ${SRC_DIR}/model/Georeferencer.cpp
    ${SRC_DIR}/model/Material.cpp
    ${SRC_DIR}/model/Mesh.cpp
    ${SRC_DIR}/model/MeshBuilder.cpp
    ${SRC_DIR}/model/MeshTransformer.cpp
    ${SRC_DIR}/model/Scene.cpp
    ${SRC_DIR}/model/SceneBuilder.cpp
    ${SRC_DIR}/model/SceneGraphBuilder.cpp
    ${SRC_DIR}/model/SceneNode.cpp
    ${SRC_DIR}/model/VertexDataLayout.cpp
    ${SRC_DIR}/model/ZipCommentGeoreferencer.cpp
    ${SRC_DIR}/model/ZipCommentInfo.cpp
)

set(takengine_port_COMMON_SRCS
    # Port
    ${SRC_DIR}/port/Platform.cpp
    ${SRC_DIR}/port/String.cpp
    ${SRC_DIR}/port/StringBuilder.cpp
)

set(takengine_raster_base_COMMON_SRCS
    ${SRC_DIR}/raster/DatasetDescriptor.cpp
    ${SRC_DIR}/raster/DatasetProjection.cpp
    ${SRC_DIR}/raster/DatasetProjection2.cpp
    ${SRC_DIR}/raster/DefaultDatasetProjection.cpp
    ${SRC_DIR}/raster/ImageDatasetDescriptor.cpp
    ${SRC_DIR}/raster/ImageInfo.cpp
    ${SRC_DIR}/raster/MosaicDatasetDescriptor.cpp
    ${SRC_DIR}/raster/PrecisionImagery.cpp
    ${SRC_DIR}/raster/PrecisionImageryFactory.cpp
    ${SRC_DIR}/raster/PrecisionImagerySpi.cpp
)

set(takengine_raster_gdal_COMMON_SRCS
    ${SRC_DIR}/raster/gdal/GdalDatasetProjection.cpp
    ${SRC_DIR}/raster/gdal/GdalLibrary.cpp
    ${SRC_DIR}/raster/gdal/RapidPositioningControlB.cpp
)

set(takengine_raster_osm_COMMON_SRCS
    ${SRC_DIR}/raster/osm/OSMUtils.cpp
)

set(takengine_raster_mosaic_COMMON_SRCS
    ${SRC_DIR}/raster/mosaic/FilterMosaicDatabaseCursor2.cpp
    ${SRC_DIR}/raster/mosaic/MosaicDatabase2.cpp
    ${SRC_DIR}/raster/mosaic/MultiplexingMosaicDatabaseCursor2.cpp
)

set(takengine_raster_tilematrix_COMMON_SRCS
    ${SRC_DIR}/raster/tilematrix/TileMatrix.cpp
)

set(takengine_raster_tilereader_COMMON_SRCS
    ${SRC_DIR}/raster/tilereader/TileReader.cpp
)

set(takengine_raster_COMMON_SRCS
    # Raster
    ${takengine_raster_base_COMMON_SRCS}
    ${takengine_raster_gdal_COMMON_SRCS}
    ${takengine_raster_osm_COMMON_SRCS}
    ${takengine_raster_mosaic_COMMON_SRCS}
    ${takengine_raster_tilematrix_COMMON_SRCS}
    ${takengine_raster_tilereader_COMMON_SRCS}
)

set(takengine_renderer_base_COMMON_SRCS
    ${SRC_DIR}/renderer/AsyncBitmapLoader2.cpp
    ${SRC_DIR}/renderer/Bitmap2.cpp
    ${SRC_DIR}/renderer/BitmapFactory2.cpp
    ${SRC_DIR}/renderer/GLDepthSampler.cpp
    ${SRC_DIR}/renderer/GLES20FixedPipeline.cpp
    ${SRC_DIR}/renderer/GLMatrix.cpp
    ${SRC_DIR}/renderer/GLMegaTexture.cpp
    ${SRC_DIR}/renderer/GLNinePatch.cpp
    ${SRC_DIR}/renderer/GLOffscreenFramebuffer.cpp
    ${SRC_DIR}/renderer/GLRenderBatch.cpp
    ${SRC_DIR}/renderer/GLRenderBatch2.cpp
    ${SRC_DIR}/renderer/GLSLUtil.cpp
    ${SRC_DIR}/renderer/GLText2.cpp
    ${SRC_DIR}/renderer/GLTexture.cpp
    ${SRC_DIR}/renderer/GLTexture2.cpp
    ${SRC_DIR}/renderer/GLTextureAtlas.cpp
    ${SRC_DIR}/renderer/GLTextureAtlas2.cpp
    ${SRC_DIR}/renderer/GLTextureCache.cpp
    ${SRC_DIR}/renderer/GLTextureCache2.cpp
    ${SRC_DIR}/renderer/GLWireframe.cpp
    ${SRC_DIR}/renderer/GLWorkers.cpp
    ${SRC_DIR}/renderer/HeightMap.cpp
	${SRC_DIR}/renderer/RenderAttributes.cpp
    ${SRC_DIR}/renderer/RenderState.cpp
    ${SRC_DIR}/renderer/Shader.cpp
    ${SRC_DIR}/renderer/Skirt.cpp
    ${SRC_DIR}/renderer/Tessellate.cpp
)

set(takengine_renderer_core_COMMON_SRCS
    ${SRC_DIR}/renderer/core/controls/IlluminationControlImpl.cpp
    ${SRC_DIR}/renderer/core/ColorControl.cpp
    ${SRC_DIR}/renderer/core/GLAntiMeridianHelper.cpp
    ${SRC_DIR}/renderer/core/GLLabel.cpp
    ${SRC_DIR}/renderer/core/GLLabelManager.cpp
    ${SRC_DIR}/renderer/core/GLAtmosphere.cpp
    ${SRC_DIR}/renderer/core/GLDiagnostics.cpp
    ${SRC_DIR}/renderer/core/GLDirtyRegion.cpp
    ${SRC_DIR}/renderer/core/GLGlobe.cpp
    ${SRC_DIR}/renderer/core/GLGlobeBase.cpp
    ${SRC_DIR}/renderer/core/GLGlobeSurfaceRenderer.cpp
    ${SRC_DIR}/renderer/core/GLLayer2.cpp
    ${SRC_DIR}/renderer/core/GLLayerFactory2.cpp
    ${SRC_DIR}/renderer/core/GLLayerSpi2.cpp
    ${SRC_DIR}/renderer/core/GLMapRenderGlobals.cpp
    ${SRC_DIR}/renderer/core/GLMapRenderable2.cpp
    ${SRC_DIR}/renderer/core/GLMapView2.cpp
)

set(takengine_renderer_elevation_COMMON_SRCS
    ${SRC_DIR}/renderer/elevation/ElMgrTerrainRenderService.cpp
    ${SRC_DIR}/renderer/elevation/GLElevationHeatMapLayer.cpp
    ${SRC_DIR}/renderer/elevation/GLTerrainSlopeAngleLayer.cpp
    ${SRC_DIR}/renderer/elevation/GLTerrainTile.cpp
    ${SRC_DIR}/renderer/elevation/TerrainRenderService.cpp
	${SRC_DIR}/renderer/elevation/TerrainTileShaders.cpp
)

set(takengine_renderer_COMMON_SRCS
    # Renderer
    ${takengine_renderer_base_COMMON_SRCS}
    ${takengine_renderer_core_COMMON_SRCS}
    ${takengine_renderer_elevation_COMMON_SRCS}
)

set (takengine_simd_COMMON_SRCS
	${SRC_DIR}/simd/simd.cpp
)

set(takengine_thread_COMMON_SRCS
    # Thread
    ${SRC_DIR}/thread/Cond.cpp
    ${SRC_DIR}/thread/Lock.cpp
    ${SRC_DIR}/thread/Monitor.cpp
    ${SRC_DIR}/thread/Mutex.cpp
    ${SRC_DIR}/thread/RWMutex.cpp
    ${SRC_DIR}/thread/ThreadPool.cpp
    ${SRC_DIR}/thread/impl/ThreadImpl_common.cpp
)

set(takengine_util_COMMON_SRCS
    # Util
    ${SRC_DIR}/util/AttributeSet.cpp
    ${SRC_DIR}/util/AtomicRefCountable.cpp
    ${SRC_DIR}/util/Blob.cpp
    ${SRC_DIR}/util/BlockPoolAllocator.cpp
    ${SRC_DIR}/util/ConfigOptions.cpp
    ${SRC_DIR}/util/IO.cpp
    ${SRC_DIR}/util/IO2.cpp
    ${SRC_DIR}/util/DataInput2.cpp
    ${SRC_DIR}/util/DataOutput2.cpp
    ${SRC_DIR}/util/Distance.cpp
    ${SRC_DIR}/util/GeomagneticField.cpp
    ${SRC_DIR}/util/FutureTask.cpp
    ${SRC_DIR}/util/Logging.cpp
    ${SRC_DIR}/util/Logging2.cpp
    ${SRC_DIR}/util/MathUtils.cpp
    ${SRC_DIR}/util/Memory.cpp
    ${SRC_DIR}/util/MemBuffer.cpp
    ${SRC_DIR}/util/MemBuffer2.cpp
    ${SRC_DIR}/util/ProcessingCallback.cpp
    ${SRC_DIR}/util/ProtocolHandler.cpp
    ${SRC_DIR}/util/SyncObject.cpp
    ${SRC_DIR}/util/Work.cpp
    ${SRC_DIR}/util/ZipFile.cpp
)

set(takengine_COMMON_SRCS
    ${takengine_core_COMMON_SRCS}
    ${takengine_currency_COMMON_SRCS}
    ${takengine_db_COMMON_SRCS}
    ${takengine_elevation_COMMON_SRCS}
    ${takengine_feature_COMMON_SRCS}
    ${takengine_formats_COMMON_SRCS}
    ${takengine_math_COMMON_SRCS}
    ${takengine_model_COMMON_SRCS}
    ${takengine_port_COMMON_SRCS}
    ${takengine_raster_COMMON_SRCS}
    ${takengine_renderer_COMMON_SRCS}
	${takengine_simd_COMMON_SRCS}
    ${takengine_thread_COMMON_SRCS}
    ${takengine_util_COMMON_SRCS}
)

set(takengine_core_COMMON_HEADERS
    # Core
    ${SRC_DIR}/core/AtakMapController.h
    ${SRC_DIR}/core/AtakMapView.h
    ${SRC_DIR}/core/Datum2.h
    ${SRC_DIR}/core/Ellipsoid.h
    ${SRC_DIR}/core/Ellipsoid2.h
    ${SRC_DIR}/core/GeoPoint.h
    ${SRC_DIR}/core/GeoPoint2.h
    ${SRC_DIR}/core/Globe.h
    ${SRC_DIR}/core/Layer.h
    ${SRC_DIR}/core/Layer2.h
    ${SRC_DIR}/core/LegacyAdapters.h
    ${SRC_DIR}/core/MapCamera.h
    ${SRC_DIR}/core/MapProjectionDisplayModel.h
    ${SRC_DIR}/core/MapRenderer.h
    ${SRC_DIR}/core/MapRenderer2.h
    ${SRC_DIR}/core/MapSceneModel.h
    ${SRC_DIR}/core/MapSceneModel2.h
    ${SRC_DIR}/core/ProjectionFactory2.h
    ${SRC_DIR}/core/ProjectionFactory3.h
    ${SRC_DIR}/core/ProjectionSpi3.h
    ${SRC_DIR}/core/RenderContext.h
    ${SRC_DIR}/core/RenderSurface.h
)

set(takengine_currency_COMMON_HEADERS
    # Currency
    ${SRC_DIR}/currency/Currency2.h
    ${SRC_DIR}/currency/CurrencyRegistry2.h
    ${SRC_DIR}/currency/CatalogDatabase2.h
)

set(takengine_db_COMMON_HEADERS
    # DB
    ${SRC_DIR}/db/BindArgument.h
    ${SRC_DIR}/db/Cursor.h
    ${SRC_DIR}/db/CursorWrapper2.h
    ${SRC_DIR}/db/Database.h
    ${SRC_DIR}/db/Database2.h
    ${SRC_DIR}/db/DatabaseFactory.h
    ${SRC_DIR}/db/DatabaseInformation.h
    ${SRC_DIR}/db/DatabaseWrapper.h
    ${SRC_DIR}/db/DefaultDatabaseProvider.h
    ${SRC_DIR}/db/RowIterator.h
    ${SRC_DIR}/db/SpatiaLiteDB.h
    ${SRC_DIR}/db/Statement.h
    ${SRC_DIR}/db/WhereClauseBuilder2.h
)

set(takengine_elevation_COMMON_HEADERS
    # Elevation
    ${SRC_DIR}/elevation/ElevationChunk.h
    ${SRC_DIR}/elevation/ElevationManager.h
    ${SRC_DIR}/elevation/ElevationChunkCursor.h
    ${SRC_DIR}/elevation/ElevationChunkFactory.h
    ${SRC_DIR}/elevation/ElevationHeatMapLayer.h
    ${SRC_DIR}/elevation/ElevationSource.h
    ${SRC_DIR}/elevation/ElevationSourceManager.h
    ${SRC_DIR}/elevation/MultiplexingElevationChunkCursor.h
    ${SRC_DIR}/elevation/TerrainSlopeAngleLayer.h
)

set(takengine_feature_COMMON_HEADERS
    # Feature
    ${SRC_DIR}/feature/AbstractFeatureDataStore2.h
    ${SRC_DIR}/feature/BruteForceLimitOffsetFeatureCursor.h
    ${SRC_DIR}/feature/DataSourceFeatureDataStore2.h
    ${SRC_DIR}/feature/DataSourceFeatureDataStore3.h
    ${SRC_DIR}/feature/DefaultDriverDefinition.h
    ${SRC_DIR}/feature/DefaultDriverDefinition2.h
    ${SRC_DIR}/feature/DefaultSchemaDefinition.h
    ${SRC_DIR}/feature/DrawingTool.h
    ${SRC_DIR}/feature/Envelope2.h
    ${SRC_DIR}/feature/FDB.h
    ${SRC_DIR}/feature/Feature.h
    ${SRC_DIR}/feature/Feature2.h
    ${SRC_DIR}/feature/FeatureCursor2.h
    ${SRC_DIR}/feature/FeatureDatabase.h
    ${SRC_DIR}/feature/FeatureDataSource.h
    ${SRC_DIR}/feature/FeatureDataSource2.h
    ${SRC_DIR}/feature/FeatureDataStore2.h
    ${SRC_DIR}/feature/FeatureSet2.h
    ${SRC_DIR}/feature/FeatureSetCursor2.h
    ${SRC_DIR}/feature/FeatureSetDatabase.h
    ${SRC_DIR}/feature/FeatureSpatialDatabase.h
    ${SRC_DIR}/feature/FilterFeatureCursor2.h
    ${SRC_DIR}/feature/FilterFeatureDataStore2.h
    ${SRC_DIR}/feature/FilterFeatureSetCursor2.h
    ${SRC_DIR}/feature/Geometry.h
    ${SRC_DIR}/feature/Geometry2.h
    ${SRC_DIR}/feature/GeometryCollection.h
    ${SRC_DIR}/feature/GeometryCollection2.h
    ${SRC_DIR}/feature/GeometryFactory.h
    ${SRC_DIR}/feature/GeometryTransformer.h
    ${SRC_DIR}/feature/GpxDriverDefinition2.h
    ${SRC_DIR}/feature/KMLDriverDefinition2.h
    ${SRC_DIR}/feature/LegacyAdapters.h
    ${SRC_DIR}/feature/LineString.h
    ${SRC_DIR}/feature/LineString2.h
    ${SRC_DIR}/feature/MultiplexingFeatureCursor.h
    ${SRC_DIR}/feature/OGR_DriverDefinition.h
    ${SRC_DIR}/feature/OGR_FeatureDataSource.h
    ${SRC_DIR}/feature/OGR_SchemaDefinition.h
    ${SRC_DIR}/feature/OGRDriverDefinition2.h
    ${SRC_DIR}/feature/ParseGeometry.h
    ${SRC_DIR}/feature/PersistentDataSourceFeatureDataStore2.h
    ${SRC_DIR}/feature/Point.h
    ${SRC_DIR}/feature/Point2.h
    ${SRC_DIR}/feature/Polygon.h
    ${SRC_DIR}/feature/Polygon2.h
    ${SRC_DIR}/feature/QuadBlob.h
    ${SRC_DIR}/feature/QuadBlob2.h
    ${SRC_DIR}/feature/ShapefileDriverDefinition2.h
    ${SRC_DIR}/feature/SpatialCalculator.h
    ${SRC_DIR}/feature/SpatialCalculator2.h
    ${SRC_DIR}/feature/Style.h
)

set (takengine_formats_drg_COMMON_HEADERS
    ${SRC_DIR}/formats/drg/DRG.h
)

set (takengine_formats_dted_COMMON_HEADERS
    ${SRC_DIR}/formats/dted/DtedChunkReader.h
    ${SRC_DIR}/formats/dted/DtedElevationSource.h
    ${SRC_DIR}/formats/dted/DtedSampler.h
)

set (takengine_formats_egm_COMMON_HEADERS
    ${SRC_DIR}/formats/egm/EGM96.h
)

set (takengine_formats_gdal_COMMON_HEADERS
    ${SRC_DIR}/formats/gdal/GdalBitmapReader.h
    ${SRC_DIR}/formats/gdal/GdalDatasetProjection2.h
)

set (takengine_formats_glues_COMMON_HEADERS
    ${SRC_DIR}/formats/glues/dict.h
    ${SRC_DIR}/formats/glues/dict-list.h
    ${SRC_DIR}/formats/glues/geom.h
    ${SRC_DIR}/formats/glues/glues.h
    ${SRC_DIR}/formats/glues/memalloc.h
    ${SRC_DIR}/formats/glues/mesh.h
    ${SRC_DIR}/formats/glues/normal.h
    ${SRC_DIR}/formats/glues/priorityq.h
    ${SRC_DIR}/formats/glues/priorityq-heap.h
    ${SRC_DIR}/formats/glues/priorityq-sort.h
    ${SRC_DIR}/formats/glues/render.h
    ${SRC_DIR}/formats/glues/sweep.h
    ${SRC_DIR}/formats/glues/tess.h
    ${SRC_DIR}/formats/glues/tessmono.h
)

set (takengine_formats_mbtiles_COMMON_HEADERS
    ${SRC_DIR}/formats/mbtiles/MBTilesInfo.h
    ${SRC_DIR}/formats/mbtiles/MVTFeatureDataSource.h
    ${SRC_DIR}/formats/mbtiles/MVTFeatureDataStore.h
)

set (takengine_formats_msaccess_COMMON_HEADERS
    ${SRC_DIR}/formats/msaccess/MsAccessDatabaseFactory.h
)

set (takengine_formats_ogr_COMMON_HEADERS
    ${SRC_DIR}/formats/ogr/AutoStyleSchemaHandler.h
    ${SRC_DIR}/formats/ogr/OGRFeatureDataStore.h
    ${SRC_DIR}/formats/ogr/OGRUtils.h
    ${SRC_DIR}/formats/ogr/OGR_Content.h
    ${SRC_DIR}/formats/ogr/OGR_Content2.h
    ${SRC_DIR}/formats/ogr/OGR_FeatureDataSource2.h
)

set (takengine_formats_osr_COMMON_HEADERS
    ${SRC_DIR}/formats/osr/OSRProjectionSpi.h
)

set (takengine_formats_pfps_COMMON_HEADERS
    ${SRC_DIR}/formats/pfps/FalconViewFeatureDataSource.h
)

set (takengine_formats_quantizedmesh_COMMON_HEADERS
    ${SRC_DIR}/formats/quantizedmesh/QMESourceLayer.h
    ${SRC_DIR}/formats/quantizedmesh/TileCoord.h
    ${SRC_DIR}/formats/quantizedmesh/TileExtents.h
    ${SRC_DIR}/formats/quantizedmesh/impl/EdgeIndices.h
    ${SRC_DIR}/formats/quantizedmesh/impl/IndexData.h
    ${SRC_DIR}/formats/quantizedmesh/impl/Indices.h
    ${SRC_DIR}/formats/quantizedmesh/impl/QMElevationSampler.h
    ${SRC_DIR}/formats/quantizedmesh/impl/QMElevationSource.h
    ${SRC_DIR}/formats/quantizedmesh/impl/TerrainData.h
    ${SRC_DIR}/formats/quantizedmesh/impl/TerrainDataCache.h
    ${SRC_DIR}/formats/quantizedmesh/impl/TileCoordImpl.h
    ${SRC_DIR}/formats/quantizedmesh/impl/TileHeader.h
    ${SRC_DIR}/formats/quantizedmesh/impl/VertexData.h
)

set (takengine_formats_s3tc_COMMON_HEADERS
    ${SRC_DIR}/formats/s3tc/S3TC.h
)

set (takengine_formats_slat_COMMON_HEADERS
    ${SRC_DIR}/formats/slat/cons.h
    ${SRC_DIR}/formats/slat/deltat.h
    ${SRC_DIR}/formats/slat/helpers.h
    ${SRC_DIR}/formats/slat/rslib.h
    ${SRC_DIR}/formats/slat/smeph.h
    ${SRC_DIR}/formats/slat/rs.h
    ${SRC_DIR}/formats/slat/CelestialIllumination.h
)

set (takengine_formats_wmm_COMMON_HEADERS
    ${SRC_DIR}/formats/wmm/EGM9615.h
    ${SRC_DIR}/formats/wmm/GeomagnetismHeader.h
)

set(takengine_formats_COMMON_HEADERS
    # Formats
    ${takengine_formats_drg_COMMON_HEADERS}
    ${takengine_formats_dted_COMMON_HEADERS}
    ${takengine_formats_egm_COMMON_HEADERS}
    ${takengine_formats_gdal_COMMON_HEADERS}
    ${takengine_formats_glues_COMMON_HEADERS}
    ${takengine_formats_mbtiles_COMMON_HEADERS}
    ${takengine_formats_msaccess_COMMON_HEADERS}
    ${takengine_formats_ogr_COMMON_HEADERS}
    ${takengine_formats_osr_COMMON_HEADERS}
    ${takengine_formats_pfps_COMMON_HEADERS}
    ${takengine_formats_quantizedmesh_COMMON_HEADERS}
    ${takengine_formats_s3tc_COMMON_HEADERS}
    ${takengine_formats_slat_COMMON_HEADERS}
    ${takengine_formats_wmm_COMMON_HEADERS}
)

set(takengine_math_COMMON_HEADERS
    # Math
    ${SRC_DIR}/math/AABB.h
    ${SRC_DIR}/math/Ellipsoid.h
    ${SRC_DIR}/math/Ellipsoid2.h
    ${SRC_DIR}/math/Frustum2.h
    ${SRC_DIR}/math/GeometryModel.h
    ${SRC_DIR}/math/GeometryModel2.h
    ${SRC_DIR}/math/Matrix.h
    ${SRC_DIR}/math/Matrix2.h
    ${SRC_DIR}/math/Mesh.h
	${SRC_DIR}/math/PackedRay.h
	${SRC_DIR}/math/PackedVector.h
    ${SRC_DIR}/math/Plane.h
    ${SRC_DIR}/math/Plane2.h
    ${SRC_DIR}/math/Sphere.h
    ${SRC_DIR}/math/Sphere2.h
    ${SRC_DIR}/math/Statistics.h
    ${SRC_DIR}/math/Triangle.h
    ${SRC_DIR}/math/Utils.h
)

set(takengine_model_COMMON_HEADERS
    # Model
    ${SRC_DIR}/model/Georeferencer.h
    ${SRC_DIR}/model/Material.h
    ${SRC_DIR}/model/Mesh.h
    ${SRC_DIR}/model/MeshBuilder.h
    ${SRC_DIR}/model/MeshTransformer.h
    ${SRC_DIR}/model/Scene.h
    ${SRC_DIR}/model/SceneBuilder.h
    ${SRC_DIR}/model/SceneGraphBuilder.h
    ${SRC_DIR}/model/SceneNode.h
    ${SRC_DIR}/model/VertexDataLayout.h
    ${SRC_DIR}/model/ZipCommentGeoreferencer.h
    ${SRC_DIR}/model/ZipCommentInfo.h
)

set(takengine_port_COMMON_HEADERS
    # Port
    ${SRC_DIR}/port/Platform.h
    ${SRC_DIR}/port/String.h
    ${SRC_DIR}/port/StringBuilder.h
)

set(takengine_raster_base_COMMON_HEADERS
    ${SRC_DIR}/raster/DatasetDescriptor.h
    ${SRC_DIR}/raster/DatasetProjection.h
    ${SRC_DIR}/raster/DatasetProjection2.h
    ${SRC_DIR}/raster/DefaultDatasetProjection.h
    ${SRC_DIR}/raster/ImageDatasetDescriptor.h
    ${SRC_DIR}/raster/ImageInfo.h
    ${SRC_DIR}/raster/MosaicDatasetDescriptor.h
    ${SRC_DIR}/raster/PrecisionImagery.h
    ${SRC_DIR}/raster/PrecisionImageryFactory.h
    ${SRC_DIR}/raster/PrecisionImagerySpi.h
)

set(takengine_raster_osm_COMMON_HEADERS
    ${SRC_DIR}/raster/osm/OSMUtils.h
)

set(takengine_raster_mosaic_COMMON_HEADERS
    ${SRC_DIR}/raster/mosaic/FilterMosaicDatabaseCursor2.h
    ${SRC_DIR}/raster/mosaic/MosaicDatabase2.h
    ${SRC_DIR}/raster/mosaic/MultiplexingMosaicDatabaseCursor2.h
)

set(takengine_raster_tilematrix_COMMON_HEADERS
    ${SRC_DIR}/raster/tilematrix/TileMatrix.h
)

set(takengine_raster_COMMON_HEADERS
    # Raster
    ${takengine_raster_base_COMMON_HEADERS}
    ${takengine_raster_osm_COMMON_HEADERS}
    ${takengine_raster_mosaic_COMMON_HEADERS}
    ${takengine_raster_tilematrix_COMMON_HEADERS}
)

set(takengine_renderer_base_COMMON_HEADERS
    ${SRC_DIR}/renderer/AsyncBitmapLoader2.h
    ${SRC_DIR}/renderer/Bitmap2.h
    ${SRC_DIR}/renderer/BitmapFactory2.h
    ${SRC_DIR}/renderer/GLDepthSampler.h
    ${SRC_DIR}/renderer/GLES20FixedPipeline.h
    ${SRC_DIR}/renderer/GLMatrix.h
    ${SRC_DIR}/renderer/GLMegaTexture.h
    ${SRC_DIR}/renderer/GLNinePatch.h
    ${SRC_DIR}/renderer/GLOffscreenFramebuffer.h
    ${SRC_DIR}/renderer/GLRenderBatch.h
    ${SRC_DIR}/renderer/GLRenderBatch2.h
    ${SRC_DIR}/renderer/GLSLUtil.h
    ${SRC_DIR}/renderer/GLText2.h
    ${SRC_DIR}/renderer/GLTexture.h
    ${SRC_DIR}/renderer/GLTexture2.h
    ${SRC_DIR}/renderer/GLTextureAtlas.h
    ${SRC_DIR}/renderer/GLTextureAtlas2.h
    ${SRC_DIR}/renderer/GLTextureCache.h
    ${SRC_DIR}/renderer/GLTextureCache2.h
    ${SRC_DIR}/renderer/GLWireframe.h
    ${SRC_DIR}/renderer/GLWorkers.h
    ${SRC_DIR}/renderer/HeightMap.h
    ${SRC_DIR}/renderer/RenderState.h
    ${SRC_DIR}/renderer/Shader.h
    ${SRC_DIR}/renderer/Skirt.h
    ${SRC_DIR}/renderer/Tessellate.h
)

set(takengine_renderer_core_COMMON_HEADERS
    ${SRC_DIR}/renderer/core/controls/IlluminationControl.h
    ${SRC_DIR}/renderer/core/controls/IlluminationControlImpl.h
    ${SRC_DIR}/renderer/core/ColorControl.h
    ${SRC_DIR}/renderer/core/GLAntiMeridianHelper.h
    ${SRC_DIR}/renderer/core/GLLabel.h
    ${SRC_DIR}/renderer/core/GLLabelManager.h
    ${SRC_DIR}/renderer/core/GLAtmosphere.h
    ${SRC_DIR}/renderer/core/GLDiagnostics.h
    ${SRC_DIR}/renderer/core/GLDirtyRegion.h
    ${SRC_DIR}/renderer/core/GLGlobe.h
    ${SRC_DIR}/renderer/core/GLGlobeBase.h
    ${SRC_DIR}/renderer/core/GLGlobeSurfaceRenderer.h
    ${SRC_DIR}/renderer/core/GLLayer2.h
    ${SRC_DIR}/renderer/core/GLLayerFactory2.h
    ${SRC_DIR}/renderer/core/GLLayerSpi2.h
    ${SRC_DIR}/renderer/core/GLMapRenderGlobals.h
    ${SRC_DIR}/renderer/core/GLMapRenderable2.h
    ${SRC_DIR}/renderer/core/GLMapView2.h
)

set(takengine_renderer_elevation_COMMON_HEADERS
    ${SRC_DIR}/renderer/elevation/ElMgrTerrainRenderService.h
    ${SRC_DIR}/renderer/elevation/GLElevationHeatMapLayer.h
    ${SRC_DIR}/renderer/elevation/GLTerrainSlopeAngleLayer.cpp
    ${SRC_DIR}/renderer/elevation/GLTerrainTile.h
    ${SRC_DIR}/renderer/elevation/TerrainRenderService.h
)

set(takengine_renderer_COMMON_HEADERS
    # Renderer
    ${takengine_renderer_base_COMMON_HEADERS}
    ${takengine_renderer_core_COMMON_HEADERS}
    ${takengine_renderer_elevation_COMMON_HEADERS}
)

set(takengine_simd_COMMON_HEADERS
	${SRC_DIR}/simd/simd.h
)

set(takengine_thread_impl_COMMON_HEADERS
    # Thread
    ${SRC_DIR}/thread/impl/CondImpl.h
    ${SRC_DIR}/thread/impl/MutexImpl.h
    ${SRC_DIR}/thread/impl/ThreadImpl.h
)

set(takengine_thread_COMMON_HEADERS
    # Thread
    ${takengine_thread_impl_COMMON_HEADERS}
    ${SRC_DIR}/thread/Cond.h
    ${SRC_DIR}/thread/Lock.h
    ${SRC_DIR}/thread/Monitor.h
    ${SRC_DIR}/thread/Mutex.h
    ${SRC_DIR}/thread/RWMutex.h
    ${SRC_DIR}/thread/Thread.h
    ${SRC_DIR}/thread/ThreadPool.h
)

set(takengine_util_COMMON_HEADERS
    # Util
    ${SRC_DIR}/util/AttributeSet.h
    ${SRC_DIR}/util/AtomicRefCountable.h
    ${SRC_DIR}/util/Blob.h
    ${SRC_DIR}/util/BlockPoolAllocator.h
    ${SRC_DIR}/util/ConfigOptions.h
    ${SRC_DIR}/util/IO.h
    ${SRC_DIR}/util/IO2.h
    ${SRC_DIR}/util/DataInput2.h
    ${SRC_DIR}/util/DataOutput2.h
    ${SRC_DIR}/util/Distance.h
    ${SRC_DIR}/util/GeomagneticField.h
    ${SRC_DIR}/util/FutureTask.h
    ${SRC_DIR}/util/Logging.h
    ${SRC_DIR}/util/Logging2.h
    ${SRC_DIR}/util/MathUtils.h
    ${SRC_DIR}/util/Memory.h
    ${SRC_DIR}/util/MemBuffer.h
    ${SRC_DIR}/util/MemBuffer2.h
    ${SRC_DIR}/util/ProcessingCallback.h
    ${SRC_DIR}/util/ProtocolHandler.h
    ${SRC_DIR}/util/Work.h
    ${SRC_DIR}/util/ZipFile.h
)

set(takengine_COMMON_HEADERS
    ${takengine_core_COMMON_HEADERS}
    ${takengine_currency_COMMON_HEADERS}
    ${takengine_db_COMMON_HEADERS}
    ${takengine_elevation_COMMON_HEADERS}
    ${takengine_feature_COMMON_HEADERS}
    ${takengine_formats_COMMON_HEADERS}
    ${takengine_math_COMMON_HEADERS}
    ${takengine_model_COMMON_HEADERS}
    ${takengine_port_COMMON_HEADERS}
    ${takengine_raster_COMMON_HEADERS}
    ${takengine_renderer_COMMON_HEADERS}
	${takengine_simd_COMMON_HEADERS}
    ${takengine_thread_COMMON_HEADERS}
    ${takengine_util_COMMON_HEADERS}
)

