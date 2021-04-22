#include "jnativegllayer3.h"

#include <core/LegacyAdapters.h>
#include <renderer/core/GLLayer2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/renderer/core/Interop.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayer3_start
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLayer2 *layer = JLONG_TO_INTPTR(GLLayer2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    layer->start();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayer3_stop
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLayer2 *layer = JLONG_TO_INTPTR(GLLayer2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    layer->stop();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayer3_getSubject
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    GLLayer2 *layer = JLONG_TO_INTPTR(GLLayer2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    std::shared_ptr<Layer2> csubject2(std::move(Layer2Ptr(&layer->getSubject(), Memory_leaker_const<Layer2>)));
    std::shared_ptr<atakmap::core::Layer> csubject;
    code = LegacyAdapters_adapt(csubject, csubject2);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    Java::JNILocalRef msubject(*env, NULL);
    code = Core::Interop_marshal(msubject, *env, csubject);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return msubject.release();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayer3_draw
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mview, jint mrenderPass)
{
    TAKErr code(TE_Ok);
    GLLayer2 *layer = JLONG_TO_INTPTR(GLLayer2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    std::shared_ptr<GLGlobeBase> cview;
    code = Renderer::Core::Interop_marshal(cview, *env, mview);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    GLMapView2::RenderPass crenderPass;
    Renderer::Core::Interop_marshal(&crenderPass, mrenderPass);
    layer->draw(*cview, crenderPass);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayer3_release
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLayer2 *layer = JLONG_TO_INTPTR(GLLayer2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    layer->release();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayer3_getRenderPass
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLayer2 *layer = JLONG_TO_INTPTR(GLLayer2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    const int crenderPass = layer->getRenderPass();
    jint mrenderPass = 0;
    Renderer::Core::Interop_marshal(&mrenderPass, (GLMapView2::RenderPass)crenderPass);
    return mrenderPass;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayer3_destruct
  (JNIEnv *env, jclass jclazz, jobject mpointer)
{
  Pointer_destruct_iface<GLLayer2>(env, mpointer);
}
