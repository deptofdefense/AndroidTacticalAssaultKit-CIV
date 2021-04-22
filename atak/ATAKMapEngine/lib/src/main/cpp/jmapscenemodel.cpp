#include "jmapscenemodel.h"

#include <core/GeoPoint2.h>
#include <core/MapSceneModel2.h>
#include <core/Projection2.h>
#include <core/ProjectionFactory3.h>
#include <feature/GeometryTransformer.h>
#include <math/Frustum2.h>
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

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_gsd
  (JNIEnv *env, jclass clazz, jdouble range, jdouble fov, jint height)
{
    return MapSceneModel2_gsd(range, fov, height);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_range
  (JNIEnv *env, jclass clazz, jdouble gsd, jdouble fov, jint height)
{
    return MapSceneModel2_range(gsd, fov, height);
}
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
JNIEXPORT void JNICALL Java_com_atakmap_map_MapSceneModel_set__JDIIIDDDZFFDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble dpi, jint width, jint height, jint srid, jdouble focusLat, jdouble focusLng, jdouble focusAlt, jboolean focusAltAbsolute, jfloat focusX, jfloat focusY, jdouble rotation, jdouble tilt, jdouble resolution)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    model->set(dpi,
               width, height,
               srid,
               GeoPoint2(focusLat, focusLng, focusAlt, focusAltAbsolute ? AltitudeReference::HAE : AltitudeReference::AGL, NAN, NAN),
               focusX, focusY,
               rotation,
               tilt,
               resolution);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_MapSceneModel_set__JJ
  (JNIEnv *env, jclass clazz, jlong ptr, jlong otherPtr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    MapSceneModel2 *other = JLONG_TO_INTPTR(MapSceneModel2, otherPtr);
    if(!model || !other) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    *model = *other;
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
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getDpi
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }
    return model->displayDpi;
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
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraNearMeters
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }
    return model->camera.nearMeters;
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
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_MapSceneModel_getCameraFarMeters
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    MapSceneModel2 *model = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }
    return model->camera.farMeters;
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

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_MapSceneModel_intersectsAAbbWgs84
  (JNIEnv *env, jclass clazz,
   jlong ptr,
   jdouble mbbMinX, jdouble mbbMinY, jdouble mbbMinZ,
   jdouble mbbMaxX, jdouble mbbMaxY, jdouble mbbMaxZ)
{
    MapSceneModel2 *scene = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    TAK::Engine::Feature::Envelope2 aabb(mbbMinX, mbbMinY, mbbMinZ, mbbMaxX, mbbMaxY, mbbMaxZ);

    // transform the MBB to the native projection
    const int srid = scene->projection->getSpatialReferenceID();
    if(srid != 4326)
        TAK::Engine::Feature::GeometryTransformer_transform(&aabb, aabb, 4326, srid);

    Matrix2 mx(scene->camera.projection);
    mx.concatenate(scene->camera.modelView);
    Frustum2 frustum(mx);
    bool result = frustum.intersects(
            AABB(
                    Point2<double>(aabb.minX, aabb.minY, aabb.minZ),
                    Point2<double>(aabb.maxX, aabb.maxY, aabb.maxZ)));
    // check IDL crossing
    if(!result && srid == 4326 && ((aabb.minX+aabb.maxX)/2.0)*scene->camera.location.x < 0.0) {
        const double hemishift = (scene->camera.location.x < 0.0) ? -360.0 : 360.0;
        result |= frustum.intersects(
            AABB(
                    Point2<double>(aabb.minX+hemishift, aabb.minY, aabb.minZ),
                    Point2<double>(aabb.maxX+hemishift, aabb.maxY, aabb.maxZ)));
    }
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_MapSceneModel_intersectsSphereWgs84
  (JNIEnv *, jclass clazz, jlong ptr, jdouble cx, jdouble cy, jdouble cz, jdouble radiusMeters)
{
    MapSceneModel2 *scene = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    Point2<double> center(cx, cy, cz);

    // transform the MBB to the native projection
    const int srid = scene->projection->getSpatialReferenceID();
    if(srid != 4326)
        scene->projection->forward(&center, GeoPoint2(center.y, center.x, center.z, AltitudeReference::HAE));

    Matrix2 mx(scene->camera.projection);
    mx.concatenate(scene->camera.modelView);
    // scale to nominal display meters for proper interpretation of radius
    mx.scale(scene->displayModel->projectionXToNominalMeters,
             scene->displayModel->projectionYToNominalMeters,
             scene->displayModel->projectionZToNominalMeters);

    center.x *= scene->displayModel->projectionXToNominalMeters;
    center.y *= scene->displayModel->projectionYToNominalMeters;
    center.z *= scene->displayModel->projectionZToNominalMeters;

    Frustum2 frustum(mx);
    bool result = frustum.intersects(Sphere2(center, radiusMeters));
    // check IDL crossing
    if(!result && srid == 4326 && center.x*scene->camera.location.x < 0.0) {
        const double hemishift = (scene->camera.location.x < 0.0) ? -360.0 : 360.0;
        result |= frustum.intersects(Sphere2(
                    Point2<double>(
                               center.x+(hemishift*scene->displayModel->projectionXToNominalMeters),
                                center.y,
                                center.z),
                    radiusMeters));
    }
    return result;
}
