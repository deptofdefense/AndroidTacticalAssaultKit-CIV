#include "com_atakmap_map_CameraController.h"

#include <core/CameraController.h>
#include <core/GeoPoint2.h>
#include <core/MapRenderer2.h>
#include <core/MapSceneModel2.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI;

#define JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide) \
    switch(mcollide) { \
        case com_atakmap_map_CameraController_COLLIDE_ABORT : \
            ccollide = MapRenderer::Abort; \
            break; \
        case com_atakmap_map_CameraController_COLLIDE_ADJUST_CAMERA : \
            ccollide = MapRenderer::AdjustCamera; \
            break; \
        case com_atakmap_map_CameraController_COLLIDE_ADJUST_FOCUS : \
            ccollide = MapRenderer::AdjustFocus; \
            break; \
        case com_atakmap_map_CameraController_COLLIDE_IGNORE : \
            ccollide = MapRenderer::Ignore; \
            break; \
    }

JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_panBy
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat tx, jfloat ty, jint mcollide, jboolean poleSmoothScroll, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_panBy(*renderer, tx, ty, ccollide, poleSmoothScroll, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_panTo__JDDDFFIZZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jfloat x, jfloat y, jint mcollide, jboolean poleSmoothScroll, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_panTo(*renderer, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), x, y, ccollide, poleSmoothScroll, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_zoomBy__JDIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble scaleFactor, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_zoomBy(*renderer, scaleFactor, ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_zoomBy__JDDDDFFIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble scaleFactor, jdouble lat, jdouble lng, jdouble alt, jfloat focusx, jfloat focusy, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_zoomBy(*renderer, scaleFactor, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), focusx, focusy, ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_zoomTo__JDDDDFFIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble gsd, jdouble lat, jdouble lng, jdouble alt, jfloat focusx, jfloat focusy, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_zoomTo(*renderer, gsd, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), focusx, focusy, ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_rotateBy
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble lat, jdouble lng, jdouble alt, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_rotateBy(*renderer, theta, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_rotateTo__JDDDDFFIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble lat, jdouble lng, jdouble alt, jfloat focusx, jfloat focusy, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_rotateTo(*renderer, theta, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), focusx, focusy, ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_tiltBy__JDDDDIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble lat, jdouble lng, jdouble alt, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_tiltBy(*renderer, theta, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_tiltBy__JDDDDFFIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble lat, jdouble lng, jdouble alt, jfloat focusx, jfloat focusy, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_tiltBy(*renderer, theta, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), focusx, focusy, ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_tiltTo__JDDDDFFIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble lat, jdouble lng, jdouble alt, jfloat focusx, jfloat focusy, jint mcollide, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    MapRenderer::CameraCollision ccollide;
    JCAMERA_CONTROLLER_MARSHAL_COLLIDE(mcollide, ccollide);
    CameraController_tiltTo(*renderer, theta, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), focusx, focusy, ccollide, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_panTo__JDDDZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    CameraController_panTo(*renderer, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_rotateTo__JDZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    CameraController_rotateTo(*renderer, theta, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_tiltTo__JDZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    CameraController_tiltTo(*renderer, theta, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_zoomTo__JDZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble gsd, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    CameraController_zoomTo(*renderer, gsd, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_CameraController_tiltTo__JDDDDZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble lat, jdouble lng, jdouble alt, jboolean animate)
{
    MapRenderer2 *renderer = JLONG_TO_INTPTR(MapRenderer2, ptr);
    if(!renderer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    CameraController_tiltTo(*renderer, theta, GeoPoint2(lat, lng, alt, AltitudeReference::HAE), animate);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_CameraController_computeRelativeDensityRatio
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat x, jfloat y)
{
    MapSceneModel2 *sm = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!sm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return CameraController_computeRelativeDensityRatio(*sm, x, y);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_CameraController_createTangentPlane
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt)
{
    MapSceneModel2 *sm = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!sm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return nullptr;
    }
    Plane2 plane = CameraController_createTangentPlane(*sm, GeoPoint2(lat, lng, alt, AltitudeReference::HAE));
    return Interop::NewPointer(env, GeometryModel2Ptr(new Plane2(plane), Memory_deleter_const<GeometryModel2, Plane2>));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_CameraController_createFocusAltitudeModel
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt)
{
    MapSceneModel2 *sm = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!sm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return nullptr;
    }
    GeometryModel2Ptr cmodel(nullptr, nullptr);
    if(CameraController_createFocusAltitudeModel(cmodel, *sm, GeoPoint2(lat, lng, alt, AltitudeReference::HAE)) != TE_Ok)
        return NULL;
    return Interop::NewPointer(env, std::move(cmodel));
}
