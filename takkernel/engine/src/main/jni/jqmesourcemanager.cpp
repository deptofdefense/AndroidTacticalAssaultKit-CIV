#include "jqmesourcemanager.h"

#include <formats/quantizedmesh/QMESourceLayer.h>

#include "common.h"
#include "interop/formats/quantizedmesh/Interop.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::QuantizedMesh;

JNIEXPORT void JNICALL Java_com_atakmap_map_formats_quantizedmesh_QMESourceManager_attachQMESourceLayerNative
(JNIEnv *env, jclass clazz, jobject msource)
{
    TAKErr code(TE_Ok);
    if (!msource) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    std::shared_ptr<QMESourceLayer> csource;
    code = TAKEngineJNI::Interop::Formats::QuantizedMesh::Interop_adapt(csource, env, msource, false);
    if (ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    code = QMESourceLayer_attach(csource);
    if (ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_formats_quantizedmesh_QMESourceManager_detachQMESourceLayerNative
(JNIEnv *env, jclass clazz, jobject msource)
{
    TAKErr code(TE_Ok);
    if(!msource) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    std::shared_ptr<QMESourceLayer> csource;
    code = TAKEngineJNI::Interop::Formats::QuantizedMesh::Interop_adapt(csource, env, msource, false);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    code = QMESourceLayer_detach(*csource);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}