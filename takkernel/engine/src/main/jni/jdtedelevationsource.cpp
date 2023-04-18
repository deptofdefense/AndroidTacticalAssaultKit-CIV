#include "com_atakmap_map_formats_dted_DtedElevationSource.h"

#include <formats/dted/DtedElevationSource.h>
#include <util/Error.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/elevation/Interop.h"

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Formats::DTED;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jobject JNICALL Java_com_atakmap_map_formats_dted_DtedElevationSource_create
  (JNIEnv *env, jclass clazz, jstring mpath)
{
    TAKErr code(TE_Ok);
    if(!mpath) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAK::Engine::Port::String cpath;
    code = JNIStringUTF_get(cpath, *env, mpath);
    if(ATAKMapEngineJNI_checkOrThrow(env, code)) {
        return NULL;
    }

    ElevationSourcePtr csource(new(std::nothrow) DtedElevationSource(cpath), Memory_deleter_const<ElevationSource, DtedElevationSource>);
    if(ATAKMapEngineJNI_checkOrThrow(env, !!csource ? TE_Ok : TE_OutOfMemory)) {
        return NULL;
    }

    return Elevation::Interop_adapt(env, std::move(csource), false);
}
