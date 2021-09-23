#include "jglrenderglobals.h"

#include <renderer/core/GLMapRenderGlobals.h>

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLRenderGlobals_setRelativeDisplayDensity
  (JNIEnv *env, jclass clazz, jfloat v)
{
    TAK::Engine::Renderer::Core::GLMapRenderGlobals_setRelativeDisplayDensity(v);
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_opengl_GLRenderGlobals_getRelativeDisplayDensity
  (JNIEnv *env, jclass clazz)
{
    return TAK::Engine::Renderer::Core::GLMapRenderGlobals_getRelativeDisplayDensity();
}