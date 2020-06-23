#include "jcotpointdata.h"
#include "cotmessageio.h"

JNIEXPORT jdouble JNICALL
Java_com_atakmap_commoncommo_CoTPointData_getNoValueNative
        (JNIEnv *env, jclass cls)
{
    return COMMO_COT_POINT_NO_VALUE;
}
