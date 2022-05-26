#include "jdrginfo.h"

#include <formats/drg/DRG.h>

#include "common.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIStringUTF.h"

using namespace TAK::Engine::Formats::DRG;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_raster_drg_DRGInfo_parse
  (JNIEnv *env, jclass clazz, jstring mfilename, jdoubleArray mbounds)
{
    if(!mfilename) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    if(!mbounds) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    TAK::Engine::Port::String cfilename;
    code = JNIStringUTF_get(cfilename, *env, mfilename);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    DRGInfo drg;
    code = DRG_parseName(&drg, cfilename);
    if(code != TE_Ok)
        return false;
    JNIDoubleArray cbounds(*env, mbounds, 0);
    cbounds[0] = drg.minLat;
    cbounds[1] = drg.minLng;
    cbounds[2] = drg.maxLat;
    cbounds[3] = drg.maxLng;
    return true;
}
