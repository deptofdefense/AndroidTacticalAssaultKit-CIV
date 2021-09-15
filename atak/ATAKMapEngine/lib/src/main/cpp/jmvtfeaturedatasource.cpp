#include "jmvtfeaturedatasource.h"

#include <formats/mbtiles/MVTFeatureDataSource.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Formats::MBTiles;
using namespace TAK::Engine::Util;

JNIEXPORT jobject JNICALL Java_com_atakmap_map_formats_mapbox_MvtFeatureDataSource_create
  (JNIEnv *env, jclass clazz)
{
    return TAKEngineJNI::Interop::NewPointer(env, FeatureDataSourcePtr(new MVTFeatureDataSource(), Memory_deleter_const<FeatureDataSource2, MVTFeatureDataSource>));
}
