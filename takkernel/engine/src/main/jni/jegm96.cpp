#include "jegm96.h"

#include <cmath>

#include <elevation/ElevationManager.h>

#include "common.h"

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Util;

JNIEXPORT jdouble JNICALL Java_com_atakmap_coremap_maps_conversion_EGM96_getOffset
  (JNIEnv *env, jclass clazz, jdouble lat, jdouble lng)
{
    TAKErr code(TE_Ok);
    double retval;
    code = ElevationManager_getGeoidHeight(&retval, lat, lng);
    if(code == TE_InvalidArg) // bad coordinate
        return NAN;
    else if(code == TE_IllegalState) // not loaded
        return 0.0;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NAN;
    else
        return retval;
}
