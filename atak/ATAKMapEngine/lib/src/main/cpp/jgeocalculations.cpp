#include "jgeocalculations.h"

#include <core/GeoPoint2.h>

#include "common.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

namespace
{
    struct {
        jclass id;
        jmethodID ctor__DD;
        jmethodID ctor__DDD;
    } GeoPoint_class;

    bool init(JNIEnv &env) NOTHROWS;
    jobject NewGeoPoint(JNIEnv &env, double lat, double lng) NOTHROWS;
    jobject NewGeoPoint(JNIEnv &env, double lat, double lng, double alt) NOTHROWS;
}

#define TEJNI_GC_HAS_FLAG(b, f) \
    ((b)&(com_atakmap_coremap_maps_coords_GeoCalculations_##f))

JNIEXPORT jdouble JNICALL Java_com_atakmap_coremap_maps_coords_GeoCalculations_distance
  (JNIEnv *env, jclass clazz, jdouble lat1, jdouble lng1, jdouble alt1, jdouble lat2, jdouble lng2, jdouble alt2, jint flags)
{
    const GeoPoint2 a(lat1, lng1, alt1, AltitudeReference::HAE);
    const GeoPoint2 b(lat2, lng2, alt2, AltitudeReference::HAE);
    const bool quick = TEJNI_GC_HAS_FLAG(flags, CALC_QUICK);
    if(TEJNI_GC_HAS_FLAG(flags, CALC_SLANT)) {
        return GeoPoint2_slantDistance(a, b);
    } else {
        return GeoPoint2_distance(a, b, quick);
    }
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_coremap_maps_coords_GeoCalculations_slantAngle
  (JNIEnv *env, jclass clazz, jdouble lat1, jdouble lng1, jdouble alt1, jdouble lat2, jdouble lng2, jdouble alt2, jint flags)
{
    const GeoPoint2 a(lat1, lng1, alt1, AltitudeReference::HAE);
    const GeoPoint2 b(lat2, lng2, alt2, AltitudeReference::HAE);
    const bool quick = TEJNI_GC_HAS_FLAG(flags, CALC_QUICK);
    return GeoPoint2_slantAngle(a, b, quick);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_coremap_maps_coords_GeoCalculations_bearing
  (JNIEnv *env, jclass clazz, jdouble lat1, jdouble lng1, jdouble lat2, jdouble lng2, jint flags)
{
    const GeoPoint2 a(lat1, lng1);
    const GeoPoint2 b(lat2, lng2);
    const bool quick = TEJNI_GC_HAS_FLAG(flags, CALC_QUICK);
    return GeoPoint2_bearing(a, b, quick);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_coremap_maps_coords_GeoCalculations_midpoint
  (JNIEnv *env, jclass clazz, jdouble lat1, jdouble lng1, jdouble alt1, jdouble lat2, jdouble lng2, jdouble alt2, jint flags)
{
    const GeoPoint2 a(lat1, lng1, alt1, AltitudeReference::HAE);
    const GeoPoint2 b(lat2, lng2, alt2, AltitudeReference::HAE);
    const bool quick = TEJNI_GC_HAS_FLAG(flags, CALC_QUICK);
    if(TEJNI_GC_HAS_FLAG(flags, CALC_SLANT)) {
        // XXX - need SDK impl with ECEF
        GeoPoint2 midpoint = GeoPoint2_midpoint(a, b, quick);
        midpoint.altitude = (a.altitude+b.altitude)/2.0;
        midpoint.altitudeRef = AltitudeReference::HAE;
        return NewGeoPoint(*env, midpoint.latitude, midpoint.longitude, midpoint.altitude);
    } else {
        GeoPoint2 midpoint = GeoPoint2_midpoint(a, b, quick);
        return NewGeoPoint(*env, midpoint.latitude, midpoint.longitude);
    }
}
JNIEXPORT jobject JNICALL Java_com_atakmap_coremap_maps_coords_GeoCalculations_pointAtDistance__DDDDI
  (JNIEnv *env, jclass clazz, jdouble lat, jdouble lng, jdouble azimuth, jdouble distance, jint flags)
{
    const GeoPoint2 a(lat, lng);
    GeoPoint2 result = GeoPoint2_pointAtDistance(GeoPoint2(lat, lng), azimuth, distance, false);
    return NewGeoPoint(*env, result.latitude, result.longitude);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_coremap_maps_coords_GeoCalculations_pointAtDistance__DDDDDDI
  (JNIEnv *env, jclass clazz, jdouble lat, jdouble lng, jdouble alt, jdouble azimuth, jdouble distance, jdouble inclination, jint flags)
{
    const GeoPoint2 a(lat, lng);
    GeoPoint2 result = GeoPoint2_pointAtDistance(GeoPoint2(lat, lng, alt, AltitudeReference::HAE), azimuth, distance, inclination, false);
    return NewGeoPoint(*env, result.latitude, result.longitude, result.altitude);
}

namespace
{
    bool init(JNIEnv &env) NOTHROWS
    {
        GeoPoint_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/coremap/maps/coords/GeoPoint");
        if(!GeoPoint_class.id)
            return false;
        GeoPoint_class.ctor__DD = env.GetMethodID(GeoPoint_class.id, "<init>", "(DD)V");
        if(!GeoPoint_class.ctor__DD)
            return false;
        GeoPoint_class.ctor__DDD = env.GetMethodID(GeoPoint_class.id, "<init>", "(DDD)V");
        if(!GeoPoint_class.ctor__DDD)
            return false;
        return true;
    }
    jobject NewGeoPoint(JNIEnv &env, double lat, double lng) NOTHROWS
    {
        static bool initialized = init(env);
        if(!initialized) {
            ATAKMapEngineJNI_checkOrThrow(&env, TE_IllegalState);
            return NULL;
        }

        return env.NewObject(GeoPoint_class.id, GeoPoint_class.ctor__DD, lat, lng);
    }
    jobject NewGeoPoint(JNIEnv &env, double lat, double lng, double alt) NOTHROWS
    {
        static bool initialized = init(env);
        if(!initialized) {
            ATAKMapEngineJNI_checkOrThrow(&env, TE_IllegalState);
            return NULL;
        }

        return env.NewObject(GeoPoint_class.id, GeoPoint_class.ctor__DDD, lat, lng, alt);
    }
}
