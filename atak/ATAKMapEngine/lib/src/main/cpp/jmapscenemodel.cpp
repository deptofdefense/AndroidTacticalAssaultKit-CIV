#include "jmapscenemodel.h"

#include <core/GeoPoint2.h>
#include <core/MapSceneModel2.h>
#include <core/Projection2.h>
#include <core/ProjectionFactory3.h>
#include <math/Matrix.h>
#include <math/Point2.h>
#include <math/Rectangle.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Core;

JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_clone
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    MapSceneModel2Ptr retval(new MapSceneModel2(*model), Memory_deleter_const<MapSceneModel2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_MapSceneModel_destruct
  (JNIEnv *env, jclass clazz, jobject pointer)
{
    Pointer_destruct<MapSceneModel2>(env, pointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_create
  (JNIEnv *env, jclass clazz, jdouble dpi, jint width, jint height, jint srid, jdouble focusLat, jdouble focusLng, jdouble focusAlt, jboolean focusAltAbsolute, jfloat focusX, jfloat focusY, jdouble rotation, jdouble tilt, jdouble resolution)
{
    MapSceneModel2Ptr retval(new MapSceneModel2(dpi,
                                                width, height,
                                                srid,
                                                GeoPoint2(focusLat, focusLng, focusAlt, focusAltAbsolute ? AltitudeReference::HAE : AltitudeReference::AGL, NAN, NAN),
                                                focusX, focusY,
                                                rotation,
                                                tilt,
                                                resolution),
                             Memory_deleter_const<MapSceneModel2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_getEarth
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer(env, model->earth.get(), true);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_MapSceneModel_getProjection
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    return model->projection.get() ? model->projection->getSpatialReferenceID() : -1;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_getForward
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer(env, &model->forwardTransform, true);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_getInverse
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer(env, &model->inverseTransform, true);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_MapSceneModel_getWidth
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return model->width;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_MapSceneModel_getHeight
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return model->height;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_MapSceneModel_getFocusX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return model->focusX;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_MapSceneModel_getFocusY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return model->focusY;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getGsd
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->gsd;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_getDisplayModel
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer(env, model->displayModel.get(), true);
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_getCameraProjection
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer(env, &model->camera.projection, true);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_MapSceneModel_getCameraModelView
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer(env, &model->camera.modelView, true);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_MapSceneModel_getCameraLocation
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mlocation)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    env->SetDoubleField(mlocation, pointD_x, model->camera.location.x);
    env->SetDoubleField(mlocation, pointD_y, model->camera.location.y);
    env->SetDoubleField(mlocation, pointD_z, model->camera.location.z);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_MapSceneModel_getCameraTarget
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mtarget)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    env->SetDoubleField(mtarget, pointD_x, model->camera.target.x);
    env->SetDoubleField(mtarget, pointD_y, model->camera.target.y);
    env->SetDoubleField(mtarget, pointD_z, model->camera.target.z);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraRoll
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->camera.roll;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraElevation
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->camera.elevation;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraAzimuth
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->camera.azimuth;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraFov
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->camera.fov;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraAspectRatio
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->camera.aspectRatio;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraNear
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->camera.near;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraFar
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    return model->camera.far;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_MapSceneModel_isCameraPerspective
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    return model->camera.mode == MapCamera2::Perspective;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_MapSceneModel_forward
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jboolean altAbsolute, jobject mpoint)
{
    TAKErr code(TE_Ok);
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    Point2<double> cpoint;
    code = model->forward(&cpoint, GeoPoint2(lat, lng, alt, altAbsolute ? AltitudeReference::HAE : AltitudeReference::AGL, NAN, NAN));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;

    env->SetDoubleField(mpoint, pointD_x, cpoint.x);
    env->SetDoubleField(mpoint, pointD_y, cpoint.y);
    env->SetDoubleField(mpoint, pointD_z, cpoint.z);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_MapSceneModel_inverse__JDDDZLcom_atakmap_coremap_maps_coords_GeoPoint_2
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble x, jdouble y, jdouble z, jboolean nearestIfOffEarth, jobject mgeo)
{
    TAKErr code(TE_Ok);
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    GeoPoint2 cgeo;
    code = model->inverse(&cgeo, Point2<float>(x, y, z), nearestIfOffEarth);
    if(code != TE_Ok)
        return false;

    code = Interop_copy(mgeo, env, cgeo);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;

    return true;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_MapSceneModel_inverse__JDDDJLcom_atakmap_coremap_maps_coords_GeoPoint_2
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble x, jdouble y, jdouble z, jlong geomModelPtr, jobject mgeo)
{
    TAKErr code(TE_Ok);
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    GeometryModel2 *geomModel = JLONG_TO_INTPTR(GeometryModel2, geomModelPtr);
    if(!geomModel) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    GeoPoint2 cgeo;
    code = model->inverse(&cgeo, Point2<float>(x, y, z), *geomModel);
    if(code != TE_Ok)
        return false;

    code = Interop_copy(mgeo, env, cgeo);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;

    return true;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_MapSceneModel_setPerspectiveCameraEnabled
  (JNIEnv *env, jclass clazz, jboolean enabled)
{
    MapSceneModel2_setCameraMode(enabled ? MapCamera2::Perspective : MapCamera2::Scale);
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_MapSceneModel_isPerspectiveCameraEnabled
  (JNIEnv *env, jclass clazz)
{
    return MapSceneModel2_getCameraMode() == MapCamera2::Perspective;
}
  
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_MapSceneModel_intersects
  (JNIEnv *env, jclass clazz,
   jlong ptr,
   jdouble mbbMinX, jdouble mbbMinY, jdouble mbbMinZ,
   jdouble mbbMaxX, jdouble mbbMaxY, jdouble mbbMaxZ)
{
    MapSceneModel2 *scene = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    Matrix2 xform(scene->forwardTransform);

    double minX;
    double minY;
    double minZ;
    double maxX;
    double maxY;
    double maxZ;

    // transform the MBB to the native projection
    if(scene->projection->getSpatialReferenceID() != 4326) {
        GeoPoint2 points[8];
        points[0] = GeoPoint2(mbbMinY, mbbMinX, mbbMinZ, AltitudeReference::HAE);
        points[1] = GeoPoint2(mbbMinY, mbbMaxX, mbbMinZ, AltitudeReference::HAE);
        points[2] = GeoPoint2(mbbMaxY, mbbMaxX, mbbMinZ, AltitudeReference::HAE);
        points[3] = GeoPoint2(mbbMaxY, mbbMinX, mbbMinZ, AltitudeReference::HAE);
        points[4] = GeoPoint2(mbbMinY, mbbMinX, mbbMaxZ, AltitudeReference::HAE);
        points[5] = GeoPoint2(mbbMinY, mbbMaxX, mbbMaxZ, AltitudeReference::HAE);
        points[6] = GeoPoint2(mbbMaxY, mbbMaxX, mbbMaxZ, AltitudeReference::HAE);
        points[7] = GeoPoint2(mbbMaxY, mbbMinX, mbbMaxZ, AltitudeReference::HAE);

        std::size_t idx = 0u;
        for( ; idx < 8u; idx++) {
            Point2<double> scratch;
            if(scene->projection->forward(&scratch, points[idx]) != TE_Ok)
                continue;
            mbbMinX = scratch.x;
            mbbMinY = scratch.y;
            mbbMinZ = scratch.z;
            mbbMaxX = scratch.x;
            mbbMaxY = scratch.y;
            mbbMaxZ = scratch.z;
            break;
        }
        if(idx == 8u)
            return false;
        for( ; idx < 8u; idx++) {
            Point2<double> scratch;
            if(scene->projection->forward(&scratch, points[idx]) != TE_Ok)
                continue;
            if(scratch.x < mbbMinX)        mbbMinX = scratch.x;
            else if(scratch.x > mbbMaxX)   mbbMaxX = scratch.x;
            if(scratch.y < mbbMinY)        mbbMinY = scratch.y;
            else if(scratch.y > mbbMaxY)   mbbMaxY = scratch.y;
            if(scratch.z < mbbMinZ)        mbbMinZ = scratch.z;
            else if(scratch.z > mbbMaxZ)   mbbMaxZ = scratch.z;
        }
    }

    Point2<double> points[8];
    points[0] = Point2<double>(mbbMinX, mbbMinY, mbbMinZ);
    points[1] = Point2<double>(mbbMinX, mbbMaxY, mbbMinZ);
    points[2] = Point2<double>(mbbMaxX, mbbMaxY, mbbMinZ);
    points[3] = Point2<double>(mbbMaxX, mbbMinY, mbbMinZ);
    points[4] = Point2<double>(mbbMinX, mbbMinY, mbbMaxZ);
    points[5] = Point2<double>(mbbMinX, mbbMaxY, mbbMaxZ);
    points[6] = Point2<double>(mbbMaxX, mbbMaxY, mbbMaxZ);
    points[7] = Point2<double>(mbbMaxX, mbbMinY, mbbMaxZ);

    std::size_t idx = 0u;
    for( ; idx < 8u; idx++) {
        Point2<double> scratch;
        if(xform.transform(&scratch, points[idx]) != TE_Ok)
            continue;
        minX = scratch.x;
        minY = scratch.y;
        minZ = scratch.z;
        maxX = scratch.x;
        maxY = scratch.y;
        maxZ = scratch.z;
        break;
    }
    if(idx == 8u)
        return false;
    for( ; idx < 8u; idx++) {
        Point2<double> scratch;
        if(xform.transform(&scratch, points[idx]) != TE_Ok)
            continue;
        if(scratch.x < minX)        minX = scratch.x;
        else if(scratch.x > maxX)   maxX = scratch.x;
        if(scratch.y < minY)        minY = scratch.y;
        else if(scratch.y > maxY)   maxY = scratch.y;
        if(scratch.z < minZ)        minZ = scratch.z;
        else if(scratch.z > maxZ)   maxZ = scratch.z;
    }

#if 1
    // XXX - observing intersect failure for equirectangular with perspective camera on Y-axis
    if(scene->projection->getSpatialReferenceID() == 4326 && scene->camera.mode == MapCamera2::Perspective)
        return atakmap::math::Rectangle<double>::intersects(0, 0, scene->width, scene->height, minX, 0, maxX, scene->height);
    else
#endif
    return atakmap::math::Rectangle<double>::intersects(0, 0, scene->width, scene->height, minX, minY, maxX, maxY);
}
