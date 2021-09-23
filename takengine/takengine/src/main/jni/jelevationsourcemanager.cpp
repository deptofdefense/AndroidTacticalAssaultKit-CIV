#include "jelevationsourcemanager.h"

#include <list>
#include <map>

#include <elevation/ElevationSourceManager.h>
#include <port/STLListAdapter.h>
#include <thread/Lock.h>
#include <thread/Mutex.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/elevation/Interop.h"
#include "interop/java/JNICollection.h"

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    class CallbackForwarder : public ElevationSourcesChangedListener
    {
    public :
        CallbackForwarder(JNIEnv *env, jobject impl) NOTHROWS;
        virtual ~CallbackForwarder() NOTHROWS;
    public :
        virtual TAKErr onSourceAttached(const std::shared_ptr<ElevationSource> &src) NOTHROWS;
        virtual TAKErr onSourceDetached(const ElevationSource &src) NOTHROWS;
    public :
        jobject impl;
    };

    struct
    {
        jclass id;
        jmethodID onSourceAttached;
        jmethodID onSourceDetached;
    } ElevationSourceManager_OnSourcesChangedListener_class;

    Mutex &listenerMutex() NOTHROWS;
    std::list<std::unique_ptr<CallbackForwarder>> &listeners() NOTHROWS;

    bool checkInit(JNIEnv *env) NOTHROWS;
    bool ElevationSourceManager_class_init(JNIEnv *env) NOTHROWS;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_ElevationSourceManager_addOnSourcesChangedListener
  (JNIEnv *env, jclass clazz, jobject mlistener)
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, listenerMutex());
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    std::list<std::unique_ptr<CallbackForwarder>> &cbs = listeners();
    for(auto it = cbs.begin(); it != cbs.end(); it++) {
        if(env->IsSameObject((*it)->impl, mlistener))
            return;
    }

    std::unique_ptr<CallbackForwarder> cb(new CallbackForwarder(env, mlistener));
    code = ElevationSourceManager_addOnSourcesChangedListener(cb.get());
    cbs.push_back(std::move(cb));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;

}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_ElevationSourceManager_removeOnSourcesChangedListener
  (JNIEnv *env, jclass clazz, jobject mlistener)
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, listenerMutex());
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    std::list<std::unique_ptr<CallbackForwarder>> &cbs = listeners();
    std::unique_ptr<CallbackForwarder> cb;
    for(auto it = cbs.begin(); it != cbs.end(); it++) {
        if(env->IsSameObject((*it)->impl, mlistener)) {
            cb = std::move(*it);
            cbs.erase(it);
            break;
        }
    }

    code = ElevationSourceManager_removeOnSourcesChangedListener(cb.get());
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_ElevationSourceManager_attach
  (JNIEnv *env, jclass clazz, jobject msource)
{
    TAKErr code(TE_Ok);
    if(!msource) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    std::shared_ptr<ElevationSource> csource;
    code = Elevation::Interop_adapt(csource, env, msource, false);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    code = ElevationSourceManager_attach(csource);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_ElevationSourceManager_detach
  (JNIEnv *env, jclass clazz, jobject msource)
{
    TAKErr code(TE_Ok);
    if(!msource) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    std::shared_ptr<ElevationSource> csource;
    code = Elevation::Interop_adapt(csource, env, msource, false);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    code = ElevationSourceManager_detach(*csource);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_ElevationSourceManager_findSource
  (JNIEnv *env, jclass clazz, jstring mname)
{
    TAKErr code(TE_Ok);
    TAK::Engine::Port::String cname;
    code = JNIStringUTF_get(cname, *env, mname);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    std::shared_ptr<ElevationSource> csource;
    code = ElevationSourceManager_findSource(csource, cname);
    if(code == TE_InvalidArg)
        return NULL;
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return Elevation::Interop_adapt(env, csource, false);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_ElevationSourceManager_getSources
  (JNIEnv *env, jclass clazz, jobject msources)
{
    TAKErr code(TE_Ok);
    if(!msources) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    std::list<std::shared_ptr<ElevationSource>> csources;
    TAK::Engine::Port::STLListAdapter<std::shared_ptr<ElevationSource>> csources_w(csources);
    code = ElevationSourceManager_getSources(csources_w);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;

    for(auto it = csources.begin(); it != csources.end(); it++) {
        code = Java::JNICollection_add(*env, msources, Elevation::Interop_adapt(env, *it, false));
        TE_CHECKBREAK_CODE(code);
    }
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}

namespace
{
    CallbackForwarder::CallbackForwarder(JNIEnv *env_, jobject impl_) NOTHROWS :
        impl(env_->NewGlobalRef(impl_))
    {
        checkInit(env_);
    }
    CallbackForwarder::~CallbackForwarder() NOTHROWS
    {
        if(impl) {
            LocalJNIEnv env;
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
    TAKErr CallbackForwarder::onSourceAttached(const std::shared_ptr<ElevationSource> &csrc) NOTHROWS
    {
        TAKErr code(TE_Ok);
        LocalJNIEnv env;
        // look up the object
        jobject msrc = Elevation::Interop_getObject(env, *csrc);
        // if not previously interop'd, intern a new wrapper
        if(!msrc)
            msrc = Elevation::Interop_adapt(env, csrc, false);
        if(!msrc)
            return TE_Done;
        if(env->ExceptionCheck())
            return TE_Err;
        env->CallVoidMethod(impl, ElevationSourceManager_OnSourcesChangedListener_class.onSourceAttached, msrc);
        if(env->ExceptionCheck())
            return TE_Err;
    }
    TAKErr CallbackForwarder::onSourceDetached(const ElevationSource &csrc) NOTHROWS
    {
        TAKErr code(TE_Ok);
        LocalJNIEnv env;
        jobject msrc = Elevation::Interop_getObject(env, csrc);
        if(!msrc)
            return TE_Done;
        if(env->ExceptionCheck())
            return TE_Err;
        env->CallVoidMethod(impl, ElevationSourceManager_OnSourcesChangedListener_class.onSourceDetached, msrc);
        if(env->ExceptionCheck())
            return TE_Err;
    }

    Mutex &listenerMutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }
    std::list<std::unique_ptr<CallbackForwarder>> &listeners() NOTHROWS
    {
        static std::list<std::unique_ptr<CallbackForwarder>> l;
        return l;
    }

    bool checkInit(JNIEnv *env) NOTHROWS
    {
        static bool clinit = ElevationSourceManager_class_init(env);
        return clinit;
    }
    bool ElevationSourceManager_class_init(JNIEnv *env) NOTHROWS
    {
        ElevationSourceManager_OnSourcesChangedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/ElevationSourceManager$OnSourcesChangedListener");
        ElevationSourceManager_OnSourcesChangedListener_class.onSourceAttached = env->GetMethodID(ElevationSourceManager_OnSourcesChangedListener_class.id, "onSourceAttached", "(Lcom/atakmap/map/elevation/ElevationSource;)V");
        ElevationSourceManager_OnSourcesChangedListener_class.onSourceDetached = env->GetMethodID(ElevationSourceManager_OnSourcesChangedListener_class.id, "onSourceDetached", "(Lcom/atakmap/map/elevation/ElevationSource;)V");

        return true;
    }
}
