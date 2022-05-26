#include "jtilecoord.h"

#include <formats/quantizedmesh/TileCoord.h>

using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;


JNIEXPORT jdouble JNICALL Java_com_atakmap_map_formats_quantizedmesh_TileCoord_getLatitude
(JNIEnv *env, jclass clazz, jdouble yCoord, jint level)
{
    return TileCoord_getLatitude(yCoord, level);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_formats_quantizedmesh_TileCoord_getLongitude
        (JNIEnv *env, jclass clazz, jdouble xCoord, jint level)
{
    return TileCoord_getLongitude(xCoord, level);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_formats_quantizedmesh_TileCoord_getSpacing
        (JNIEnv *env, jclass clazz, jint level)
{
    return TileCoord_getSpacing(level);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_formats_quantizedmesh_TileCoord_getTileX
        (JNIEnv *env, jclass clazz, jdouble lng, jint level)
{
    return TileCoord_getTileX(lng, level);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_formats_quantizedmesh_TileCoord_getTileY
        (JNIEnv *env, jclass clazz, jdouble lat, jint level)
{
    return TileCoord_getTileY(lat, level);
}

