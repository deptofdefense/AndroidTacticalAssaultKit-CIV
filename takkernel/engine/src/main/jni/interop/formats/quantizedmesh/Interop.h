#ifndef TAKENGINEJNI_INTEROP_FORMATS_QUANTIZEDMESH_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_FORMATS_QUANTIZEDMESH_INTEROP_H_INCLUDED

#include <jni.h>

#include <formats/quantizedmesh/QMESourceLayer.h>
#include <port/Platform.h>
#include <util/Error.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Formats {
            namespace QuantizedMesh {
                TAK::Engine::Util::TAKErr Interop_adapt(std::shared_ptr<TAK::Engine::Formats::QuantizedMesh::QMESourceLayer> &value, JNIEnv *env, jobject msource, const bool forceWrap) NOTHROWS;
                jobject Interop_adapt(JNIEnv *env, const std::shared_ptr<TAK::Engine::Formats::QuantizedMesh::QMESourceLayer> &csource, const bool forceWrap) NOTHROWS;

                jobject Interop_getObject(JNIEnv *env, const TAK::Engine::Formats::QuantizedMesh::QMESourceLayer &csource) NOTHROWS;
            }
        }
    }
}

#endif
