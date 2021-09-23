#include "jnativeglmaprenderable2.h"

#include <core/LegacyAdapters.h>
#include <renderer/core/GLMapRenderable2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/renderer/core/Interop.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_NativeGLMapRenderable2_draw
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mview, jint mrenderPass)
{
    TAKErr code(TE_Ok);
    GLMapRenderable2 *layer = JLONG_TO_INTPTR(GLMapRenderable2, ptr);
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
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_NativeGLMapRenderable2_release
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapRenderable2 *layer = JLONG_TO_INTPTR(GLMapRenderable2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    layer->release();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_NativeGLMapRenderable2_getRenderPass
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapRenderable2 *layer = JLONG_TO_INTPTR(GLMapRenderable2, ptr);
    if(!layer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    const int crenderPass = layer->getRenderPass();
    jint mrenderPass = 0;
    Renderer::Core::Interop_marshal(&mrenderPass, (GLMapView2::RenderPass)crenderPass);
    return mrenderPass;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_NativeGLMapRenderable2_destruct
  (JNIEnv *env, jclass jclazz, jobject mpointer)
{
    Pointer_destruct_iface<GLMapRenderable2>(env, mpointer);
}
