#include "jnativelayer.h"

#include <core/Layer.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_NativeLayer_getPointer
  (JNIEnv *env, jclass clazz, jobject mlayer)
{
    bool isWrapper;
    if(Core::Interop_isWrapper<atakmap::core::Layer>(&isWrapper, *env, mlayer) != TE_Ok || !isWrapper)
        return 0LL;
    std::shared_ptr<atakmap::core::Layer> clayer;
    if(Core::Interop_marshal(clayer, *env, mlayer) != TE_Ok)
        return 0LL;
    return INTPTR_TO_JLONG(clayer.get());
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_NativeLayer_wrap
  (JNIEnv *env, jclass clazz, jobject mlayer)
{
    TAKErr code(TE_Ok);
    std::shared_ptr<atakmap::core::Layer> clayer;
    code = Core::Interop_marshal(clayer, *env, mlayer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, clayer);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_NativeLayer_hasPointer
  (JNIEnv *env, jclass clazz, jobject mlayer)
{
    bool retval;
    Core::Interop_isWrapper<atakmap::core::Layer>(&retval, *env, mlayer);
    return retval;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_NativeLayer_create
  (JNIEnv *env, jclass clazz, jobject mlayer, jobject mowner)
{
    TAKErr code(TE_Ok);
    std::shared_ptr<atakmap::core::Layer> clayer;
    code = Core::Interop_marshal(clayer, *env, mlayer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, clayer);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_NativeLayer_hasObject
  (JNIEnv *env, jclass clazz, jlong clayerPtr)
{
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, clayerPtr);
    if(!clayer)
        return false;
    bool retval = false;
    Core::Interop_isWrapper(&retval, *env, *clayer);
    return retval;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_NativeLayer_getObject
  (JNIEnv *env, jclass clazz, jlong clayerPtr)
{
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, clayerPtr);
    if(!clayer)
        return NULL;
    Java::JNILocalRef retval(*env, NULL);
    Core::Interop_marshal(retval, *env, *clayer);
    return retval.release();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_NativeLayer_destruct
  (JNIEnv *env, jclass jclazz, jobject mpointer)
{
    Pointer_destruct_iface<atakmap::core::Layer>(env, mpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_NativeLayer_setVisible
  (JNIEnv *env, jclass jclazz, jlong ptr, jboolean visible)
{
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, ptr);
    if(!clayer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    clayer->setVisible(visible);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_NativeLayer_isVisible
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, ptr);
    if(!clayer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    return clayer->isVisible();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_NativeLayer_addOnLayerVisibleChangedListener
  (JNIEnv *env, jclass jclazz, jlong ptr, jobject mlistener)
{
    TAKErr code(TE_Ok);
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, ptr);
    if(!clayer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    std::shared_ptr<atakmap::core::Layer::VisibilityListener> clistener;
    code = Core::Interop_marshal(clistener, *env, mlistener);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    clayer->addVisibilityListener(clistener.get());
    return NewPointer(env, clistener);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_NativeLayer_removeOnLayerVisibleChangedListener
  (JNIEnv *env, jclass jclazz, jlong ptr, jobject mlistenerPointer)
{
    TAKErr code(TE_Ok);
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, ptr);
    if(clayer)
        clayer->removeVisibilityListener(Pointer_get<atakmap::core::Layer::VisibilityListener>(env, mlistenerPointer));
    Pointer_destruct_iface<atakmap::core::Layer::VisibilityListener>(env, mlistenerPointer);
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_NativeLayer_getName
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, ptr);
    if(!clayer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    return env->NewStringUTF(clayer->getName());
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_NativeLayer_VisibilityListener_1destruct
  (JNIEnv *env, jclass jclazz, jobject mpointer)
{
    Pointer_destruct_iface<atakmap::core::Layer::VisibilityListener>(env, mpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_NativeLayer_VisibilityListener_1visibilityChanged
  (JNIEnv *env, jclass jclazz, jlong ptr, jobject mlayer)
{
    do {
        TAKErr code(TE_Ok);
        atakmap::core::Layer::VisibilityListener *clistener = JLONG_TO_INTPTR(atakmap::core::Layer::VisibilityListener, ptr);
        if (!clistener)
            break;

        std::shared_ptr<atakmap::core::Layer> csubject;
        code = Core::Interop_marshal(csubject, *env, mlayer);
        TE_CHECKBREAK_CODE(code);

        clistener->visibilityChanged(*csubject);
    } while(false);
}
