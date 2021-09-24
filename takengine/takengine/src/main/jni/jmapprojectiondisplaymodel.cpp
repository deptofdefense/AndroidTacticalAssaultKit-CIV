#include "jmapprojectiondisplaymodel.h"

#include <core/MapProjectionDisplayModel.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/math/ManagedGeometryModel.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Math;

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_isSupported
  (JNIEnv *env, jclass clazz, jint srid)
{
    TAKErr code(TE_Ok);
    std::shared_ptr<MapProjectionDisplayModel> model;
    code = MapProjectionDisplayModel_getModel(model, srid);
    return code == TE_Ok;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_registerImpl
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    if(!mpointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    if(!Pointer_makeShared<MapProjectionDisplayModel>(env, mpointer)) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    jlong ptr = env->GetLongField(mpointer, Pointer_class.value);
    std::shared_ptr<MapProjectionDisplayModel> *model = JLONG_TO_INTPTR(std::shared_ptr<MapProjectionDisplayModel>, ptr);

    MapProjectionDisplayModel_registerModel(*model);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_unregisterImpl
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapProjectionDisplayModel *model = JLONG_TO_INTPTR(MapProjectionDisplayModel, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    MapProjectionDisplayModel_unregisterModel(*model);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_create
  (JNIEnv *env, jclass clazz, jint srid, jlong geomModelPtr, jdouble projX, jdouble projY, jdouble projZ, jboolean zIsHeight)
{
    GeometryModel2Ptr geomModel(NULL, NULL);
    if(geomModelPtr)
        JLONG_TO_INTPTR(GeometryModel2, geomModelPtr)->clone(geomModel);
    std::shared_ptr<MapProjectionDisplayModel> retval(new MapProjectionDisplayModel(srid, std::move(geomModel), projX, projY, projZ, zIsHeight));
    return NewPointer(env, retval);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_wrap
  (JNIEnv *env, jclass clazz, jint srid, jobject mgeomModel, jdouble projX, jdouble projY, jdouble projZ, jboolean zIsHeight)
{
    GeometryModel2Ptr geomModel(NULL, NULL);
    if(mgeomModel)
        geomModel = GeometryModel2Ptr(new ManagedGeometryModel(*env, mgeomModel), Memory_deleter_const<GeometryModel2, ManagedGeometryModel>);
    std::shared_ptr<MapProjectionDisplayModel> retval(new MapProjectionDisplayModel(srid, std::move(geomModel), projX, projY, projZ, zIsHeight));
    return NewPointer(env, retval);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_destruct
  (JNIEnv *env, jclass clazz, jobject pointer)
{
    if(!pointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    Pointer_destruct_iface<GeometryModel2>(env, pointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_get
  (JNIEnv *env, jclass clazz, jint srid)
{
    TAKErr code(TE_Ok);
    std::shared_ptr<MapProjectionDisplayModel> model;
    code = MapProjectionDisplayModel_getModel(model, srid);
    if(code == TE_InvalidArg)
        return NULL;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, model);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_getEarth
  (JNIEnv *env, jclass clazz, jlong modelPtr)
{
    const MapProjectionDisplayModel *model = JLONG_TO_INTPTR(MapProjectionDisplayModel, modelPtr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    return NewPointer(env, model->earth.get(), true);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_getSRID
  (JNIEnv *env, jclass clazz, jlong modelPtr)
{
    const MapProjectionDisplayModel *model = JLONG_TO_INTPTR(MapProjectionDisplayModel, modelPtr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return model->srid;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_getZIsHeight
  (JNIEnv *env, jclass clazz, jlong modelPtr)
{
    const MapProjectionDisplayModel *model = JLONG_TO_INTPTR(MapProjectionDisplayModel, modelPtr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    return model->zIsHeight;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_getProjectionXToNominalMeters
  (JNIEnv *env, jclass clazz, jlong modelPtr)
{
    const MapProjectionDisplayModel *model = JLONG_TO_INTPTR(MapProjectionDisplayModel, modelPtr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return model->projectionXToNominalMeters;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_getProjectionYToNominalMeters
  (JNIEnv *env, jclass clazz, jlong modelPtr)
{
    const MapProjectionDisplayModel *model = JLONG_TO_INTPTR(MapProjectionDisplayModel, modelPtr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return model->projectionYToNominalMeters;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_projection_MapProjectionDisplayModel_getProjectionZToNominalMeters
  (JNIEnv *env, jclass clazz, jlong modelPtr)
{
    const MapProjectionDisplayModel *model = JLONG_TO_INTPTR(MapProjectionDisplayModel, modelPtr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return model->projectionZToNominalMeters;
}
