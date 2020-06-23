#include "jgeomagneticfield.h"

#include <cmath>

#include <util/GeomagneticField.h>

#include "common.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

JNIEXPORT jfloat JNICALL Java_com_atakmap_coremap_maps_conversion_GeomagneticField_getDeclination
  (JNIEnv *env, jclass clazz, jdouble latitude, jdouble longitude, jdouble hae, jint year, jint month, jint day)
{
    TAKErr code(TE_Ok);
    double value;
    code = GeomagneticField_getDeclination(&value, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE), year, month, day);
    if(code != TE_Ok)
        return NAN;
    return value;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_coremap_maps_conversion_GeomagneticField_getFieldStrength
  (JNIEnv *env, jclass clazz, jdouble latitude, jdouble longitude, jdouble hae, jint year, jint month, jint day)
{
    TAKErr code(TE_Ok);
    double value;
    code = GeomagneticField_getFieldStrength(&value, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE), year, month, day);
    if(code != TE_Ok)
        return NAN;
    return value;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_coremap_maps_conversion_GeomagneticField_getHorizontalStrength
  (JNIEnv *env, jclass clazz, jdouble latitude, jdouble longitude, jdouble hae, jint year, jint month, jint day)
{
    TAKErr code(TE_Ok);
    double value;
    code = GeomagneticField_getHorizontalStrength(&value, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE), year, month, day);
    if(code != TE_Ok)
        return NAN;
    return value;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_coremap_maps_conversion_GeomagneticField_getInclination
  (JNIEnv *env, jclass clazz, jdouble latitude, jdouble longitude, jdouble hae, jint year, jint month, jint day)
{
    TAKErr code(TE_Ok);
    double value;
    code = GeomagneticField_getInclination(&value, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE), year, month, day);
    if(code != TE_Ok)
        return NAN;
    return value;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_coremap_maps_conversion_GeomagneticField_getX
  (JNIEnv *env, jclass clazz, jdouble latitude, jdouble longitude, jdouble hae, jint year, jint month, jint day)
{
    TAKErr code(TE_Ok);
    double value;
    code = GeomagneticField_getX(&value, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE), year, month, day);
    if(code != TE_Ok)
        return NAN;
    return value;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_coremap_maps_conversion_GeomagneticField_getY
  (JNIEnv *env, jclass clazz, jdouble latitude, jdouble longitude, jdouble hae, jint year, jint month, jint day)
{
    TAKErr code(TE_Ok);
    double value;
    code = GeomagneticField_getY(&value, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE), year, month, day);
    if(code != TE_Ok)
        return NAN;
    return value;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_coremap_maps_conversion_GeomagneticField_getZ
  (JNIEnv *env, jclass clazz, jdouble latitude, jdouble longitude, jdouble hae, jint year, jint month, jint day)
{
    TAKErr code(TE_Ok);
    double value;
    code = GeomagneticField_getZ(&value, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE), year, month, day);
    if(code != TE_Ok)
        return NAN;
    return value;
}
