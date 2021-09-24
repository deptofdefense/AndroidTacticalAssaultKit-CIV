#include "jgllayerfactory.h"

#include <core/LegacyAdapters.h>
#include <renderer/core/GLLayerFactory2.h>
#include <renderer/core/GLMapView2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"
#include "interop/renderer/core/Interop.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_GLLayerFactory_register
  (JNIEnv *env, jclass clazz, jobject mpointer, jint priority)
{
    TAKErr code(TE_Ok);
    std::shared_ptr<GLLayerSpi2> cspi;
    code = Pointer_get<GLLayerSpi2>(cspi, *env, mpointer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    GLLayerFactory2_registerSpi(cspi, priority);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_GLLayerFactory_unregister
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLayerSpi2 *cspi = JLONG_TO_INTPTR(GLLayerSpi2, ptr);
    GLLayerFactory2_unregisterSpi(*cspi);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_GLLayerFactory_create
  (JNIEnv *env, jclass clazz, jlong viewptr, jlong layerptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, viewptr);
    Layer2 *clayer = JLONG_TO_INTPTR(Layer2, layerptr);

    GLLayer2Ptr cretval(NULL, NULL);
    if(GLLayerFactory2_create(cretval, *cview, *clayer) != TE_Ok)
        return NULL;
    Java::JNILocalRef mretval(*env, NULL);
    // we use the `shared_ptr` overload here as `unique_ptr` forces wrapping
    std::shared_ptr<GLLayer2> cretval_shared(std::move(cretval));
    if(Renderer::Core::Interop_marshal(mretval, *env, cretval_shared) != TE_Ok)
        return NULL;
    return mretval.release();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_GLLayerFactory_adaptJavaToLayer2
  (JNIEnv *env, jclass clazz, jobject mlayerPointer)
{
    TAKErr code(TE_Ok);
    std::shared_ptr<atakmap::core::Layer> clayer;
    code = Pointer_get(clayer, *env, mlayerPointer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    std::shared_ptr<Layer2> clayer2;
    code = LegacyAdapters_adapt(clayer2, clayer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, clayer2);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_GLLayerFactory_adaptNativeToLayer2
  (JNIEnv *env, jclass clazz, jlong layerptr)
{
    TAKErr code(TE_Ok);
    std::shared_ptr<atakmap::core::Layer> clayer = std::unique_ptr<atakmap::core::Layer, void(*)(const atakmap::core::Layer *)>(JLONG_TO_INTPTR(atakmap::core::Layer, layerptr), Memory_leaker_const<atakmap::core::Layer>);
    std::shared_ptr<Layer2> clayer2;
    code = LegacyAdapters_adapt(clayer2, clayer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, clayer2);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_GLLayerFactory_destructLayer2
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct_iface<Layer2>(env, mpointer);
}
