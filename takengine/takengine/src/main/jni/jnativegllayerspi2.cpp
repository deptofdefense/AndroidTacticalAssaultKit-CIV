#include "jnativegllayerspi2.h"

#include <core/LegacyAdapters.h>
#include <renderer/core/GLLayerSpi2.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/renderer/core/Interop.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace {
    struct
    {
        jclass id;
        jmethodID ctor;
    } NativeGLLayerSpi2_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool NativeGLLayerSpi2_class_init(JNIEnv &env) NOTHROWS;
}

JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_getPointer
  (JNIEnv *env, jclass clazz, jobject mspi)
{
    TAKErr code(TE_Ok);
    bool isWrapper = false;
    code = Renderer::Core::Interop_isWrapper<GLLayerSpi2>(&isWrapper, *env, mspi);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0LL;
    if(!isWrapper)
        return 0LL;
    std::shared_ptr<GLLayerSpi2> cspi;
    code = Renderer::Core::Interop_marshal(cspi, *env, mspi);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0LL;
    return INTPTR_TO_JLONG(cspi.get());
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_wrap
  (JNIEnv *env, jclass clazz, jobject mspi)
{
    TAKErr code(TE_Ok);
    GLLayerSpi2Ptr cspi(NULL, NULL);
    code = Renderer::Core::Interop_marshal(cspi, *env, mspi);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(cspi));
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_hasPointer
  (JNIEnv *env, jclass clazz, jobject mspi)
{
    if(!mspi) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    bool retval = false;
    Renderer::Core::Interop_isWrapper<GLLayerSpi2>(&retval, *env, mspi);
    return retval;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_create__Lcom_atakmap_interop_Pointer_2Ljava_lang_Object_2
  (JNIEnv *env, jclass clazz, jobject mpointer, jobject mowner)
{
    if(!checkInit(*env)) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_IllegalState);
        return NULL;
    }

    return env->NewObject(NativeGLLayerSpi2_class.id, NativeGLLayerSpi2_class.ctor, mpointer, mowner);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_hasObject
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLayerSpi2 *cspi = JLONG_TO_INTPTR(GLLayerSpi2, ptr);
    if(!cspi) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    bool retval = false;
    Renderer::Core::Interop_isWrapper(&retval, *env, *cspi);
    return retval;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_getObject
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLayerSpi2 *cspi = JLONG_TO_INTPTR(GLLayerSpi2, ptr);
    if(!cspi) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    Java::JNILocalRef mspi(*env, NULL);
    Renderer::Core::Interop_marshal(mspi, *env, *cspi);
    return mspi.release();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct_iface<GLLayerSpi2>(env, mpointer);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_getPriority
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX - priority is reserved to registration in factory
    return 0;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_opengl_NativeGLLayerSpi2_create__JLcom_atakmap_map_opengl_GLMapView_2Lcom_atakmap_map_layer_Layer_2
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mview, jobject mlayer)
{
    TAKErr code(TE_Ok);
    GLLayerSpi2 *cspi = JLONG_TO_INTPTR(GLLayerSpi2, ptr);
    if(!cspi) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    std::shared_ptr<atakmap::core::Layer> clayer;
    code = Core::Interop_marshal(clayer, *env, mlayer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    std::shared_ptr<Layer2> clayer2;
    code = LegacyAdapters_find(clayer2, *clayer);
    if(code == TE_InvalidArg)
        return NULL; // no mapping exists, will not be supported
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    std::shared_ptr<GLGlobeBase> cview;
    code = Renderer::Core::Interop_marshal(cview, *env, mview);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    GLLayer2Ptr cgllayer(NULL, NULL);
    code = cspi->create(cgllayer, *cview, *clayer2);
    if(code == TE_InvalidArg)
        return NULL; // input not supported for create
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    Java::JNILocalRef mgllayer(*env, NULL);
    code = Renderer::Core::Interop_marshal(mgllayer, *env, std::move(cgllayer));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return mgllayer.release();
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = NativeGLLayerSpi2_class_init(env);
        return clinit;
    }
    bool NativeGLLayerSpi2_class_init(JNIEnv &env) NOTHROWS
    {
        NativeGLLayerSpi2_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/opengl/NativeGLLayerSpi2");
        NativeGLLayerSpi2_class.ctor = env.GetMethodID(NativeGLLayerSpi2_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");
        return true;
    }
}
