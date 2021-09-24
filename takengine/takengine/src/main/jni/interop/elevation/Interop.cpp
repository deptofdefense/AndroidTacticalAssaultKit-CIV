#include "interop/elevation/Interop.h"

#include <thread/Lock.h>
#include <thread/Mutex.h>
#include <util/Memory.h>
#include <interop/Pointer.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/elevation/ManagedElevationSource.h"

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Elevation;

namespace {
    struct {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } NativeElevationSource_class;

    struct {
        /**
         * key is raw pointer to the
         */
        std::map<const ElevationSource *, jobject> nativeElevationSourceInstances;
        std::map<jobject, std::weak_ptr<ElevationSource>> managedElevationSourceInstances;
        Mutex mutex;
    } ElevationSourceInterop;

    typedef std::unique_ptr<ElevationSource, void(*)(const ElevationSource *)> ElevationSourcePtr;

    bool checkInit(JNIEnv *env) NOTHROWS;
    bool Interop_class_init(JNIEnv *env) NOTHROWS;
    jobject Interop_findInternedObjectNoSync(JNIEnv *env, const ElevationSource &csource) NOTHROWS;
}


TAKErr TAKEngineJNI::Interop::Elevation::Interop_adapt(std::shared_ptr<TAK::Engine::Elevation::ElevationSource> &value, JNIEnv *env, jobject msource, const bool force) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!msource)
        return TE_InvalidArg;
    LockPtr lock(NULL, NULL);
    if(!force) {
        // check if it's a wrapper, and return
        if(Interop_isWrapper<ElevationSource>(env, msource)) {
            do {
                jobject msourcePtr = env->GetObjectField(msource, NativeElevationSource_class.pointer);
                if(!Pointer_makeShared<ElevationSource>(env, msourcePtr))
                    break;
                value = *JLONG_TO_INTPTR(std::shared_ptr<ElevationSource>, env->GetLongField(msourcePtr, Pointer_class.value));
                return TE_Ok;
            } while(false);
        }

        code = Lock_create(lock, ElevationSourceInterop.mutex);
        TE_CHECKRETURN_CODE(code);
        for(auto it = ElevationSourceInterop.managedElevationSourceInstances.begin(); it != ElevationSourceInterop.managedElevationSourceInstances.end(); it++) {
            if(env->IsSameObject(msource, it->first)) {
                value = it->second.lock();
                if(value.get())
                    return TE_Ok;
                env->DeleteWeakGlobalRef(it->first);
                ElevationSourceInterop.managedElevationSourceInstances.erase(it);
                break;
            } else if(env->IsSameObject(NULL, it->first)) {
                // erase cleared references
                env->DeleteWeakGlobalRef(it->first);
                ElevationSourceInterop.managedElevationSourceInstances.erase(it);
            }
        }
    }

    value = ElevationSourcePtr(new ManagedElevationSource(env, msource), Memory_deleter_const<ElevationSource, ManagedElevationSource>);
    if(!force)
        ElevationSourceInterop.managedElevationSourceInstances[env->NewWeakGlobalRef(msource)] = value;

    return code;
}
jobject TAKEngineJNI::Interop::Elevation::Interop_adapt(JNIEnv *env, const std::shared_ptr<TAK::Engine::Elevation::ElevationSource> &csource, const bool force) NOTHROWS
{
    const ManagedElevationSource *impl = dynamic_cast<const ManagedElevationSource *>(csource.get());
    if(impl)
        return impl->impl;

    LockPtr lock(NULL, NULL);
    // XXX - no failover on lock acuisition failure...won't intern
    if(!force && (Lock_create(lock, ElevationSourceInterop.mutex) == TE_Ok)) {
        jobject msource = Interop_findInternedObjectNoSync(env, *csource);
        if(msource)
            return msource;
    }
    if(!checkInit(env))
        return NULL;
    jobject retval = env->NewObject(NativeElevationSource_class.id, NativeElevationSource_class.ctor, NewPointer(env, csource), NULL);
    if(!force && lock.get())
        ElevationSourceInterop.nativeElevationSourceInstances[csource.get()] = env->NewWeakGlobalRef(retval);
    return retval;
}
jobject TAKEngineJNI::Interop::Elevation::Interop_getObject(JNIEnv *env, const ElevationSource &csource) NOTHROWS
{
    const ManagedElevationSource *impl = dynamic_cast<const ManagedElevationSource *>(&csource);
    if(impl)
        return impl->impl;

    // look up in intern
    do {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, ElevationSourceInterop.mutex);
        TE_CHECKBREAK_CODE(code);

        return Interop_findInternedObjectNoSync(env, csource);
    } while(false);
    return NULL;
}

template<>
bool TAKEngineJNI::Interop::Elevation::Interop_isWrapper<ElevationSource>(JNIEnv *env, jobject msource)
{
    if(!checkInit(env))
        return false;
    return msource && ATAKMapEngineJNI_equals(env, env->GetObjectClass(msource), NativeElevationSource_class.id);
}
bool TAKEngineJNI::Interop::Elevation::Interop_isWrapper(const TAK::Engine::Elevation::ElevationSource &csource)
{
    const ManagedElevationSource *impl = dynamic_cast<const ManagedElevationSource *>(&csource);
    return !!impl;
}

namespace
{
    bool checkInit(JNIEnv *env) NOTHROWS
    {
        static bool clinit = Interop_class_init(env);
        return clinit;
    }
    bool Interop_class_init(JNIEnv *env) NOTHROWS
    {
        NativeElevationSource_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/NativeElevationSource");
        NativeElevationSource_class.pointer = env->GetFieldID(NativeElevationSource_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        NativeElevationSource_class.ctor = env->GetMethodID(NativeElevationSource_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");
        return true;
    }

    jobject Interop_findInternedObjectNoSync(JNIEnv *env, const ElevationSource &csource) NOTHROWS
    {
        // look up in intern
        do {
            auto entry = ElevationSourceInterop.nativeElevationSourceInstances.find(&csource);
            if(entry == ElevationSourceInterop.nativeElevationSourceInstances.end())
                break;
            jobject retval = env->NewLocalRef(entry->second);
            if(!retval) { // weak ref was cleared
                env->DeleteWeakGlobalRef(entry->second);
                ElevationSourceInterop.nativeElevationSourceInstances.erase(entry);
            }
            return retval;
        } while(false);
        return NULL;
    }
}
