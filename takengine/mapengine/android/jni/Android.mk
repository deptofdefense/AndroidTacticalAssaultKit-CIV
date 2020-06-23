LOCAL_PATH := $(call my-dir)

TTP_DIST_DIR := $(LOCAL_PATH)/../../../../takthirdparty/builds

include $(CLEAR_VARS)
LOCAL_MODULE := ttp-prebuilt-libgdal
LOCAL_SRC_FILES := $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/lib/libgdal.so
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include/kml
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include/kml/third_party/boost_1_34_1
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include/libxml2
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ttp-prebuilt-libspatialite
LOCAL_SRC_FILES := $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/lib/libspatialite.so
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include/kml
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include/kml/third_party/boost_1_34_1
LOCAL_EXPORT_C_INCLUDES += $(TTP_DIST_DIR)/android-$(TARGET_ARCH_ABI)-release/include/libxml2
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

SRCDIR = ../../sdk/src

LOCAL_SHORT_COMMANDS := true
LOCAL_CFLAGS=-O3 -std=c++11 -D__GXX_EXPERIMENTAL_CXX0X__
LOCAL_CPPFLAGS := -std=c++11 -fexceptions
LOCAL_MODULE := takengine

### CORE FRAMEWORK ###

LOCAL_SRC_FILES += $(SRCDIR)/core/AtakMapController.cpp \
                   $(SRCDIR)/core/AtakMapView.cpp \
                   $(SRCDIR)/core/Datum2.cpp \
                   $(SRCDIR)/core/Ellipsoid.cpp \
                   $(SRCDIR)/core/Ellipsoid2.cpp \
                   $(SRCDIR)/core/GeoPoint.cpp \
                   $(SRCDIR)/core/GeoPoint2.cpp \
                   $(SRCDIR)/core/Layer.cpp \
                   $(SRCDIR)/core/Layer2.cpp \
                   $(SRCDIR)/core/LegacyAdapters.cpp \
                   $(SRCDIR)/core/MapCamera.cpp \
                   $(SRCDIR)/core/MapProjectionDisplayModel.cpp \
                   $(SRCDIR)/core/MapSceneModel.cpp \
                   $(SRCDIR)/core/MapSceneModel2.cpp \
                   $(SRCDIR)/core/ProjectionFactory2.cpp \
                   $(SRCDIR)/core/ProjectionFactory3.cpp \
                   $(SRCDIR)/core/ProjectionSpi3.cpp

# Currency
LOCAL_SRC_FILES += $(SRCDIR)/currency/Currency2.cpp \
                   $(SRCDIR)/currency/CurrencyRegistry2.cpp \
                   $(SRCDIR)/currency/CatalogDatabase2.cpp
# DB
LOCAL_SRC_FILES += $(SRCDIR)/db/Bindable.cpp \
                   $(SRCDIR)/db/BindArgument.cpp \
                   $(SRCDIR)/db/Cursor.cpp \
                   $(SRCDIR)/db/CursorWrapper2.cpp \
                   $(SRCDIR)/db/Database.cpp \
                   $(SRCDIR)/db/Database2.cpp \
                   $(SRCDIR)/db/DatabaseWrapper.cpp \
                   $(SRCDIR)/db/Query.cpp \
                   $(SRCDIR)/db/RowIterator.cpp \
                   $(SRCDIR)/db/SpatiaLiteDB.cpp \
                   $(SRCDIR)/db/Statement.cpp \
                   $(SRCDIR)/db/Statement2.cpp \
                   $(SRCDIR)/db/WhereClauseBuilder2.cpp
# Features
LOCAL_SRC_FILES += $(SRCDIR)/feature/AbstractFeatureDataStore2.cpp \
                   $(SRCDIR)/feature/BruteForceLimitOffsetFeatureCursor.cpp \
                   $(SRCDIR)/feature/DataSourceFeatureDataStore2.cpp \
                   $(SRCDIR)/feature/DataSourceFeatureDataStore3.cpp \
                   $(SRCDIR)/feature/DefaultDriverDefinition.cpp \
                   $(SRCDIR)/feature/DefaultDriverDefinition2.cpp \
                   $(SRCDIR)/feature/DefaultSchemaDefinition.cpp \
                   $(SRCDIR)/feature/DrawingTool.cpp \
                   $(SRCDIR)/feature/Envelope2.cpp \
                   $(SRCDIR)/feature/FDB.cpp \
                   $(SRCDIR)/feature/Feature.cpp \
                   $(SRCDIR)/feature/Feature2.cpp \
                   $(SRCDIR)/feature/FeatureCursor2.cpp \
                   $(SRCDIR)/feature/FeatureDatabase.cpp \
                   $(SRCDIR)/feature/FeatureDataSource.cpp \
                   $(SRCDIR)/feature/FeatureDataSource2.cpp \
                   $(SRCDIR)/feature/FeatureDataStore2.cpp \
                   $(SRCDIR)/feature/FeatureSet2.cpp \
                   $(SRCDIR)/feature/FeatureSetCursor2.cpp \
                   $(SRCDIR)/feature/FeatureSetDatabase.cpp \
                   $(SRCDIR)/feature/FeatureSpatialDatabase.cpp \
                   $(SRCDIR)/feature/Geometry.cpp \
                   $(SRCDIR)/feature/Geometry2.cpp \
                   $(SRCDIR)/feature/GeometryCollection.cpp \
                   $(SRCDIR)/feature/GeometryCollection2.cpp \
                   $(SRCDIR)/feature/GeometryFactory.cpp \
                   $(SRCDIR)/feature/GpxDriverDefinition2.cpp \
                   $(SRCDIR)/feature/KMLDriverDefinition2.cpp \
                   $(SRCDIR)/feature/LegacyAdapters.cpp \
                   $(SRCDIR)/feature/LineString.cpp \
                   $(SRCDIR)/feature/LineString2.cpp \
                   $(SRCDIR)/feature/MultiplexingFeatureCursor.cpp \
                   $(SRCDIR)/feature/OGR_DriverDefinition.cpp \
                   $(SRCDIR)/feature/OGR_FeatureDataSource.cpp \
                   $(SRCDIR)/feature/OGR_SchemaDefinition.cpp \
                   $(SRCDIR)/feature/OGRDriverDefinition2.cpp \
                   $(SRCDIR)/feature/ParseGeometry.cpp \
                   $(SRCDIR)/feature/PersistentDataSourceFeatureDataStore2.cpp \
                   $(SRCDIR)/feature/Point.cpp \
                   $(SRCDIR)/feature/Point2.cpp \
                   $(SRCDIR)/feature/Polygon.cpp \
                   $(SRCDIR)/feature/Polygon2.cpp \
                   $(SRCDIR)/feature/ShapefileDriverDefinition2.cpp \
                   $(SRCDIR)/feature/Style.cpp \
                   $(SRCDIR)/util/AttributeSet.cpp
# Elevation
LOCAL_SRC_FILES += $(SRCDIR)/elevation/ElevationChunk.cpp \
                   $(SRCDIR)/elevation/ElevationManager.cpp \
                   $(SRCDIR)/elevation/ElevationChunkCursor.cpp \
                   $(SRCDIR)/elevation/ElevationChunkFactory.cpp \
                   $(SRCDIR)/elevation/ElevationSource.cpp \
                   $(SRCDIR)/elevation/ElevationSourceManager.cpp \
                   $(SRCDIR)/elevation/MultiplexingElevationChunkCursor.cpp

# Math
LOCAL_SRC_FILES += $(SRCDIR)/math/AABB.cpp \
                   $(SRCDIR)/math/Ellipsoid.cpp \
                   $(SRCDIR)/math/Ellipsoid2.cpp \
                   $(SRCDIR)/math/GeometryModel.cpp \
                   $(SRCDIR)/math/GeometryModel2.cpp \
                   $(SRCDIR)/math/Matrix.cpp \
                   $(SRCDIR)/math/Matrix2.cpp \
                   $(SRCDIR)/math/Mesh.cpp \
                   $(SRCDIR)/math/Plane.cpp \
                   $(SRCDIR)/math/Plane2.cpp \
                   $(SRCDIR)/math/Sphere.cpp \
                   $(SRCDIR)/math/Sphere2.cpp \
                   $(SRCDIR)/math/Triangle.cpp \
                   $(SRCDIR)/math/Utils.cpp

# Model
LOCAL_SRC_FILES += $(SRCDIR)/model/Material.cpp \
                   $(SRCDIR)/model/Mesh.cpp \
                   $(SRCDIR)/model/MeshBuilder.cpp \
                   $(SRCDIR)/model/MeshTransformer.cpp \
                   $(SRCDIR)/model/Scene.cpp \
                   $(SRCDIR)/model/SceneBuilder.cpp \
                   $(SRCDIR)/model/SceneGraphBuilder.cpp \
                   $(SRCDIR)/model/SceneNode.cpp \
                   $(SRCDIR)/model/VertexDataLayout.cpp

# Imagery
LOCAL_SRC_FILES += $(SRCDIR)/raster/DatasetProjection.cpp \
                   $(SRCDIR)/raster/DefaultDatasetProjection.cpp \
                   $(SRCDIR)/raster/ImageInfo.cpp \
                   $(SRCDIR)/raster/osm/OSMUtils.cpp \
                   $(SRCDIR)/raster/mosaic/FilterMosaicDatabaseCursor2.cpp \
                   $(SRCDIR)/raster/mosaic/MosaicDatabase2.cpp \
                   $(SRCDIR)/raster/mosaic/MultiplexingMosaicDatabaseCursor2.cpp

# Renderer
LOCAL_SRC_FILES += $(SRCDIR)/renderer/AsyncBitmapLoader2.cpp \
                   $(SRCDIR)/renderer/Bitmap2.cpp \
                   $(SRCDIR)/renderer/BitmapFactory.cpp \
                   $(SRCDIR)/renderer/BitmapFactory2_Android.cpp \
                   $(SRCDIR)/renderer/GLES20FixedPipeline.cpp \
                   $(SRCDIR)/renderer/GLMatrix.cpp \
                   $(SRCDIR)/renderer/GLRenderBatch2.cpp \
                   $(SRCDIR)/renderer/GLSLUtil.cpp \
                   $(SRCDIR)/renderer/GLTexture.cpp \
                   $(SRCDIR)/renderer/GLTexture2.cpp \
                   $(SRCDIR)/renderer/GLTextureAtlas.cpp \
                   $(SRCDIR)/renderer/GLTextureAtlas2.cpp \
                   $(SRCDIR)/renderer/GLTextureCache.cpp \
                   $(SRCDIR)/renderer/GLTextureCache2.cpp \
                   $(SRCDIR)/renderer/core/GLMapRenderGlobals.cpp \
                   $(SRCDIR)/renderer/Tessellate.cpp

# Platform
LOCAL_SRC_FILES += $(SRCDIR)/port/Platform.cpp \
                   $(SRCDIR)/port/String.cpp \
                   $(SRCDIR)/port/StringBuilder.cpp

# Thread
LOCAL_SRC_FILES += $(SRCDIR)/thread/Cond.cpp \
                   $(SRCDIR)/thread/Lock.cpp \
                   $(SRCDIR)/thread/Monitor.cpp \
                   $(SRCDIR)/thread/Mutex.cpp \
                   $(SRCDIR)/thread/RWMutex.cpp \
                   $(SRCDIR)/thread/ThreadPool.cpp \
                   $(SRCDIR)/thread/impl/ThreadImpl_common.cpp \
                   $(SRCDIR)/thread/impl/ThreadImpl_libpthread.cpp

# Util
LOCAL_SRC_FILES += $(SRCDIR)/util/AtomicCounter_NDK.cpp \
                   $(SRCDIR)/util/AtomicRefCountable.cpp \
                   $(SRCDIR)/util/Blob.cpp \
                   $(SRCDIR)/util/ConfigOptions.cpp \
                   $(SRCDIR)/util/IO.cpp \
                   $(SRCDIR)/util/IO2.cpp \
                   $(SRCDIR)/util/DataInput2.cpp \
                   $(SRCDIR)/util/DataOutput2.cpp \
                   $(SRCDIR)/util/Distance.cpp \
				   $(SRCDIR)/util/GeomagneticField.cpp \
                   $(SRCDIR)/util/FutureTask.cpp \
                   $(SRCDIR)/util/Logging.cpp \
                   $(SRCDIR)/util/Logging2.cpp \
                   $(SRCDIR)/util/MathUtils.cpp \
				   $(SRCDIR)/util/Memory.cpp \
                   $(SRCDIR)/util/MemBuffer.cpp \
                   $(SRCDIR)/util/MemBuffer2.cpp \
				   $(SRCDIR)/util/ProcessingCallback.cpp \
				   $(SRCDIR)/util/ProtocolHandler.cpp \
				   $(SRCDIR)/util/ZipFile.cpp

### FORMATS ###

LOCAL_SRC_FILES += $(SRCDIR)/formats/drg/DRG.cpp
LOCAL_SRC_FILES += $(SRCDIR)/formats/wmm/GeomagnetismLibrary.cpp
LOCAL_SRC_FILES += $(SRCDIR)/formats/egm/EGM96.cpp
LOCAL_SRC_FILES += $(SRCDIR)/formats/glues/dict.c                  \
                   $(SRCDIR)/formats/glues/geom.c                  \
                   $(SRCDIR)/formats/glues/memalloc.c              \
                   $(SRCDIR)/formats/glues/mesh.c                  \
                   $(SRCDIR)/formats/glues/normal.c                \
                   $(SRCDIR)/formats/glues/priorityq.c             \
                   $(SRCDIR)/formats/glues/render.c                \
                   $(SRCDIR)/formats/glues/sweep.c                 \
                   $(SRCDIR)/formats/glues/tess.c                  \
                   $(SRCDIR)/formats/glues/tessmono.c              
LOCAL_SRC_FILES += $(SRCDIR)/formats/s3tc/S3TC.cpp

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../../../../pgsc-utils/src
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../../../thirdparty/stlsoft/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../../sdk/src
LOCAL_LDLIBS := -llog -lGLESv3
#LOCAL_SHARED_LIBRARIES += ttp-prebuilt-headers
LOCAL_SHARED_LIBRARIES += ttp-prebuilt-libgdal
LOCAL_SHARED_LIBRARIES += ttp-prebuilt-libspatialite
LOCAL_CPPFLAGS := -DRTTI_ENABLED -DTE_GLES_VERSION=3

include $(BUILD_SHARED_LIBRARY)
