#include "jrendercontextinterop.h"

#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/ManagedRenderContext.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;


JNIEXPORT jobject JNICALL Java_gov_tak_api_engine_map_RenderContextInterop_wrap
  (JNIEnv *env, jclass clazz, jobject mctx)
{
    std::unique_ptr<RenderContext, void(*)(const RenderContext *)> retval(new Core::ManagedRenderContext(*env, mctx), Memory_deleter_const<RenderContext, Core::ManagedRenderContext>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jboolean JNICALL Java_gov_tak_api_engine_map_RenderContextInterop_hasObject
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    RenderContext *cctx = JLONG_TO_INTPTR(RenderContext, ptr);
    if(!ptr)
        return false;
    return !!dynamic_cast<Core::ManagedRenderContext *>(cctx);
}
JNIEXPORT jobject JNICALL Java_gov_tak_api_engine_map_RenderContextInterop_getObject
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    RenderContext *cctx = JLONG_TO_INTPTR(RenderContext, ptr);
    if(!ptr)
        return NULL;
    Core::ManagedRenderContext *cimpl = dynamic_cast<Core::ManagedRenderContext *>(cctx);
    if(!cimpl)
        return NULL;
    return cimpl->impl;
}
JNIEXPORT void JNICALL Java_gov_tak_api_engine_map_RenderContextInterop_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct_iface<RenderContext>(env, mpointer);
}
