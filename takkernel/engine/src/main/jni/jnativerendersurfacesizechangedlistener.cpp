#include "jnativerendersurfacesizechangedlistener.h"

#include <core/RenderSurface.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Core;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_gov_tak_platform_engine_map_NativeRenderSurfaceSizeChangedListener_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct<RenderSurface::OnSizeChangedListener>(env, mpointer);
}
JNIEXPORT void JNICALL Java_gov_tak_platform_engine_map_NativeRenderSurfaceSizeChangedListener_onSizeChanged
  (JNIEnv *env, jclass clazz, jlong surfacePtr, jlong callbackPtr, jint width, jint height)
{
    RenderSurface *surface = JLONG_TO_INTPTR(RenderSurface, surfacePtr);
    RenderSurface::OnSizeChangedListener *callback = JLONG_TO_INTPTR(RenderSurface::OnSizeChangedListener, callbackPtr);
    callback->onSizeChanged(*surface, width, height);
}
