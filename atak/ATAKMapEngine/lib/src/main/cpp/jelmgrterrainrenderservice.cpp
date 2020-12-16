#include "jnativeterrainternderservice.h"

#include <vector>

#include <core/RenderContext.h>
#include <elevation/ElevationSourceManager.h>
#include <port/STLVectorAdapter.h>
#include <renderer/GLRenderContext.h>
#include <renderer/elevation/TerrainRenderService.h>
#include <renderer/elevation/ElMgrTerrainRenderService.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/feature/Interop.h"
#include "interop/java/JNICollection.h"
#include "interop/math/Interop.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_create
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    RenderContext *cctx = JLONG_TO_INTPTR(RenderContext, ptr);
    std::unique_ptr<TerrainRenderService, void(*)(const TerrainRenderService *)> retval(new ElMgrTerrainRenderService(*cctx), Memory_deleter_const<TerrainRenderService, ElMgrTerrainRenderService>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct_iface<TerrainRenderService>(env, mpointer);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_getTerrainVersion
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainRenderService *svc = JLONG_TO_INTPTR(TerrainRenderService, ptr);
    if(!svc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return svc->getTerrainVersion();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_lock
  (JNIEnv *env, jclass clazz, jlong ptr, jlong viewptr, jobject mtiles)
{
    TAKErr code(TE_Ok);
    TerrainRenderService *svc = JLONG_TO_INTPTR(TerrainRenderService, ptr);
    if(!svc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, viewptr);
    if(!mtiles) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }
    const int retval = svc->getTerrainVersion();
    std::vector<std::shared_ptr<const TerrainTile>> ctiles;
    STLVectorAdapter<std::shared_ptr<const TerrainTile>> ctiles_w(ctiles);
    if(cview)
        code = svc->lock(ctiles_w, cview->renderPasses[0u].scene, cview->drawSrid, cview->renderPasses[0u].drawVersion);
    else
        code = svc->lock(ctiles_w);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return -1;
    for(std::size_t i = 0u; i < ctiles.size(); i++) {
        Java::JNILocalRef mtilePointer(*env, NewPointer(env, ctiles[i]));
        code = Java::JNICollection_add(*env, mtiles, mtilePointer);
        TE_CHECKBREAK_CODE(code);
    }
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return -1;
    return retval;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_unlock
  (JNIEnv *env, jclass clazz, jlong ptr, jobjectArray mtilePtrsArr, jint count)
{
    TAKErr code(TE_Ok);
    TerrainRenderService *svc = JLONG_TO_INTPTR(TerrainRenderService, ptr);
    if(!svc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(!mtilePtrsArr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(count < 0) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    STLVectorAdapter<std::shared_ptr<const TerrainTile>> ctiles;
    for(std::size_t i = 0; i < (std::size_t)count; i++) {
        std::shared_ptr<TerrainTile> ctile;
        code = Pointer_get(ctile, *env, env->GetObjectArrayElement(mtilePtrsArr, i));
        TE_CHECKBREAK_CODE(code);
        code = ctiles.add(ctile);
        TE_CHECKBREAK_CODE(code);
    }
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    code = svc->unlock(ctiles);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_getElevation
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng)
{
    TAKErr code(TE_Ok);
    TerrainRenderService *svc = JLONG_TO_INTPTR(TerrainRenderService, ptr);
    if(!svc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    double el;
    code = svc->getElevation(&el, lat, lng);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NAN;
    return el;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct<TerrainTile>(env, mpointer);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getSkirtIndexOffset
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return tile->skirtIndexOffset;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getNumIndices
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    if(!tile->data.value) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return tile->data.value->getNumIndices();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getMesh
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    if(!tile->data.value)
        return NULL;

    return NewPointer(env, tile->data.value);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getLocalFrame
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    Java::JNILocalRef mlocalFrame(*env, NULL);
    code = Math::Interop_marshal(mlocalFrame, *env, tile->data.localFrame);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return mlocalFrame.release();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getSrid
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return tile->data.srid;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getAAbbWgs84
  (JNIEnv *env, jclass clazz, jlong ptr, jobject maabb)
{
    TAKErr code(TE_Ok);
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    TAK::Engine::Feature::Envelope2 caabb(tile->aabb_wgs84);
    if (!tile->hasData) {
        caabb.minZ = 0.0;
        caabb.maxZ = 0.0;
    }
    code = Feature::Interop_marshal(maabb, *env, caabb);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1hasData
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return tile->hasData;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1isHeightMap
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return tile->heightmap;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getNumPostsX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return tile->posts_x;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1getNumPostsY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return tile->posts_y;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_ElMgrTerrainRenderService_TerrainTile_1isInvertYAxis
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TerrainTile *tile = JLONG_TO_INTPTR(TerrainTile, ptr);
    if(!tile) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    return tile->invert_y_axis;
}
