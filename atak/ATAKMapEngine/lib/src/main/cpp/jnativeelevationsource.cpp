#include "jnativeelevationsource.h"

#include <list>

#include <elevation/ElevationSource.h>
#include <port/STLListAdapter.h>
#include <util/Memory.h>

#include "interop/JNIIntArray.h"
#include "interop/JNINotifyCallback.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"
#include "interop/elevation/ManagedElevationSource.h"

#include "common.h"

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Elevation;

namespace
{
    class CallbackForwarder : public ElevationSource::OnContentChangedListener
    {
    public :
        CallbackForwarder(JNIEnv *env, jobject impl) NOTHROWS;
        ~CallbackForwarder() NOTHROWS;
    public :
        TAKErr onContentChanged(const ElevationSource &source) NOTHROWS;
    private :
        jobject impl;
    };
}

JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<ElevationSource>(env, jpointer);
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_wrap
  (JNIEnv *env, jclass clazz, jobject msource)
{
    std::unique_ptr<ElevationSource, void(*)(const ElevationSource *)> retval(new ManagedElevationSource(env, msource), Memory_deleter_const<ElevationSource, ManagedElevationSource>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_hasObject
  (JNIEnv *, jclass clazz, jlong ptr)
{
    ElevationSource *base = JLONG_TO_INTPTR(ElevationSource, ptr);
    ManagedElevationSource *impl = dynamic_cast<ManagedElevationSource *>(base);
    return !!impl;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_getObject
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ElevationSource *base = JLONG_TO_INTPTR(ElevationSource, ptr);
    ManagedElevationSource *impl = dynamic_cast<ManagedElevationSource *>(base);
    if(!impl)
        return NULL;
    return env->NewLocalRef(impl->impl);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_getEnvelope
  (JNIEnv *env, jclass clazz, jlong ptr, jdoubleArray jarr)
{
    ElevationSource *source = JLONG_TO_INTPTR(ElevationSource, ptr);
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    const Envelope2 &bounds = source->getBounds();
    JNIDoubleArray arr(*env, jarr, 0);
    arr[0] = bounds.minX;
    arr[1] = bounds.minY;
    arr[2] = bounds.minZ;
    arr[3] = bounds.maxX;
    arr[4] = bounds.maxY;
    arr[5] = bounds.maxZ;

}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_query
  (JNIEnv *env, jclass clazz, jlong ptr, jlong paramsPtr)
{
    TAKErr code(TE_Ok);
    ElevationSource *source = JLONG_TO_INTPTR(ElevationSource, ptr);
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    ElevationChunkCursorPtr result(NULL, NULL);
    if(paramsPtr)
        code = source->query(result, *JLONG_TO_INTPTR(ElevationSource::QueryParameters, paramsPtr));
    else
        code = source->query(result, ElevationSource::QueryParameters());
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return NewPointer(env, std::move(result));
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_getName
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ElevationSource *source = JLONG_TO_INTPTR(ElevationSource, ptr);
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return env->NewStringUTF(source->getName());
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_addOnContentChangedListener
  (JNIEnv *env, jclass clazz, jlong ptr, jobject jnotifyCallback)
{
    ElevationSource *source = JLONG_TO_INTPTR(ElevationSource, ptr);
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    if(!jnotifyCallback) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    std::unique_ptr<ElevationSource::OnContentChangedListener, void(*)(const ElevationSource::OnContentChangedListener *)> clistener(new CallbackForwarder(env, jnotifyCallback), Memory_deleter_const<ElevationSource::OnContentChangedListener, CallbackForwarder>);
    code = source->addOnContentChangedListener(clistener.get());
    if(ATAKMapEngineJNI_checkOrThrow(env, code)) {
        return NULL;
    }

    return NewPointer(env, std::move(clistener));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_removeOnContentChangedListener
  (JNIEnv *env, jclass clazz, jlong ptr, jobject jnotifyCallbackPointer)
{
    ElevationSource *source = JLONG_TO_INTPTR(ElevationSource, ptr);
    if(!jnotifyCallbackPointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    ElevationSource::OnContentChangedListener *clistener = Pointer_get<ElevationSource::OnContentChangedListener>(env, jnotifyCallbackPointer);
    if(source)
        code = source->removeOnContentChangedListener(clistener);
    if(ATAKMapEngineJNI_checkOrThrow(env, code)) {
        return;
    }

    Pointer_destruct_iface<ElevationSource::OnContentChangedListener>(env, jnotifyCallbackPointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1create
  (JNIEnv *env, jclass clazz)
{
    typedef std::unique_ptr<ElevationSource::QueryParameters, void(*)(const ElevationSource::QueryParameters *)> ElevationSourceQueryParametersPtr;
    return NewPointer(env, ElevationSourceQueryParametersPtr(new ElevationSource::QueryParameters(), Memory_deleter_const<ElevationSource::QueryParameters>));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<ElevationSource::QueryParameters>(env, jpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1set
  (JNIEnv *env, jclass clazz, jlong ptr, jlong spatialFilterPtr, jdouble tgtRes, jdouble maxRes, jdouble minRes, jobjectArray typesArr, jboolean authSpec, jboolean auth, jdouble minCe, jdouble minLE, jintArray orderArr, jboolean flagsSpec, jint flags)
{
    TAKErr code(TE_Ok);
    ElevationSource::QueryParameters *params = JLONG_TO_INTPTR(ElevationSource::QueryParameters, ptr);
    if(!params) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    if(spatialFilterPtr)
        Geometry_clone(params->spatialFilter, *JLONG_TO_INTPTR(Geometry2, spatialFilterPtr));
    params->targetResolution = tgtRes;
    params->maxResolution = maxRes;
    params->minResolution = minRes;
    if(typesArr) {
        using namespace TAK::Engine::Port;

        params->types = Collection<String>::Ptr(new STLListAdapter<String>(), Memory_deleter_const<Collection<String>, STLListAdapter<String>>);

        for(std::size_t i = 0u; i < env->GetArrayLength(typesArr); i++) {
            TAK::Engine::Port::String ctype;
            code = JNIStringUTF_get(ctype, *env, (jstring)env->GetObjectArrayElement(typesArr, i));
            TE_CHECKBREAK_CODE(code);
            code = params->types->add(ctype);
            TE_CHECKBREAK_CODE(code);
        }
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
    if(authSpec)
        params->authoritative = std::unique_ptr<bool, void(*)(const bool *)>(new bool(auth), Memory_deleter_const<bool>);
    params->minCE = minCe;
    params->minLE = minLE;
    if(orderArr) {
        using namespace TAK::Engine::Port;

        JNIIntArray morder(*env, orderArr, JNI_ABORT);
        params->order = Collection<ElevationSource::QueryParameters::Order>::Ptr(new STLListAdapter<ElevationSource::QueryParameters::Order>, Memory_deleter_const<Collection<ElevationSource::QueryParameters::Order>, STLListAdapter<ElevationSource::QueryParameters::Order>>);
        for(std::size_t i = 0; i < morder.length(); i++) {
            code = params->order->add((ElevationSource::QueryParameters::Order)morder[i]);
            TE_CHECKBREAK_CODE(code);
        }
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
    if(flagsSpec)
        params->flags = std::unique_ptr<unsigned int, void(*)(const unsigned int *)>(new unsigned int(flags), Memory_deleter_const<unsigned int>);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1Order_1getResolutionAsc
  (JNIEnv *env, jclass clazz)
{
    return ElevationSource::QueryParameters::Order ::ResolutionAsc;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1Order_1getResolutionDesc
  (JNIEnv *env, jclass clazz)
{
    return ElevationSource::QueryParameters::Order ::ResolutionDesc;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1Order_1getCEAsc
  (JNIEnv *env, jclass clazz)
{
    return ElevationSource::QueryParameters::Order ::CEAsc;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1Order_1getCEDesc
  (JNIEnv *env, jclass clazz)
{
    return ElevationSource::QueryParameters::Order::CEDesc;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1Order_1getLEAsc
  (JNIEnv *env, jclass clazz)
{
    return ElevationSource::QueryParameters::Order ::LEAsc;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_QueryParameters_1Order_1getLEDesc
  (JNIEnv *env, jclass clazz)
{
    return ElevationSource::QueryParameters::Order ::LEDesc;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_OnContentChangedListener_1onContentChanged
  (JNIEnv *env, jclass clazz, jlong cbptr, jlong srcptr)
{
    ElevationSource::OnContentChangedListener *cb = JLONG_TO_INTPTR(ElevationSource::OnContentChangedListener, cbptr);
    ElevationSource *source = JLONG_TO_INTPTR(ElevationSource, srcptr);

    if(!cb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    // XXX - self unsubscription
    cb->onContentChanged(*source);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSource_OnContentChangedListener_1destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<ElevationSource::OnContentChangedListener>(env, jpointer);
}

namespace
{
    CallbackForwarder::CallbackForwarder(JNIEnv *env_, jobject impl_) NOTHROWS :
            impl(env_->NewGlobalRef(impl_))
    {}
    CallbackForwarder::~CallbackForwarder() NOTHROWS
    {
        if(impl) {
            LocalJNIEnv env;
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
    TAKErr CallbackForwarder::onContentChanged(const ElevationSource &source) NOTHROWS
    {
        if(!impl)
            return TE_Done;
        const TAKErr code = JNINotifyCallback_eventOccurred(impl);
        if(code == TE_Done) {
            LocalJNIEnv env;
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
        return code;
    }
}

