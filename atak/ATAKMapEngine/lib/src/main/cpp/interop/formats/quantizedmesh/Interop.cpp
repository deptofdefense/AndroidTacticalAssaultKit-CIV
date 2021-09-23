#include "interop/formats/quantizedmesh/Interop.h"

#include <thread/Lock.h>
#include <thread/Mutex.h>
#include <util/Memory.h>
#include <interop/Pointer.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/formats/quantizedmesh/ManagedQMESourceLayer.h"

using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Formats::QuantizedMesh;

namespace {

    struct {
        /**
         * key is raw pointer to the
         */
        std::map<const QMESourceLayer *, jobject> nativeQMESourceInstances;
        std::map<jobject, std::weak_ptr<QMESourceLayer>> managedQMESourceInstances;
        Mutex mutex;
    } QMESourceLayerInterop;

    typedef std::unique_ptr<QMESourceLayer, void(*)(const QMESourceLayer *)> QMESourceLayerPtr;

}


TAKErr TAKEngineJNI::Interop::Formats::QuantizedMesh::Interop_adapt(std::shared_ptr<TAK::Engine::Formats::QuantizedMesh::QMESourceLayer> &value, JNIEnv *env, jobject msource, const bool force) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!msource)
        return TE_InvalidArg;
    LockPtr lock(NULL, NULL);
    if(!force) {
        code = Lock_create(lock, QMESourceLayerInterop.mutex);
        TE_CHECKRETURN_CODE(code);
        for(auto it = QMESourceLayerInterop.managedQMESourceInstances.begin(); it != QMESourceLayerInterop.managedQMESourceInstances.end(); it++) {
            if(env->IsSameObject(msource, it->first)) {
                value = it->second.lock();
                if(value.get())
                    return TE_Ok;
                env->DeleteWeakGlobalRef(it->first);
                QMESourceLayerInterop.managedQMESourceInstances.erase(it);
                break;
            } else if(env->IsSameObject(NULL, it->first)) {
                // erase cleared references
                env->DeleteWeakGlobalRef(it->first);
                QMESourceLayerInterop.managedQMESourceInstances.erase(it);
            }
        }
    }

    value = QMESourceLayerPtr(new ManagedQMESourceLayer(env, msource), Memory_deleter_const<QMESourceLayer, ManagedQMESourceLayer>);
    if(!force)
        QMESourceLayerInterop.managedQMESourceInstances[env->NewWeakGlobalRef(msource)] = value;

    return code;
}


jobject TAKEngineJNI::Interop::Formats::QuantizedMesh::Interop_adapt(JNIEnv *env, const std::shared_ptr<TAK::Engine::Formats::QuantizedMesh::QMESourceLayer> &csource, const bool force) NOTHROWS
{
    const ManagedQMESourceLayer *impl = dynamic_cast<const ManagedQMESourceLayer *>(csource.get());
    if(impl)
        return impl->impl;
    else
        return NULL;
}



