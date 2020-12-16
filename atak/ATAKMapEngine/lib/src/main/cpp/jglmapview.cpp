#include "jglmapview.h"

#include <core/MapSceneModel2.h>
#include <elevation/ElevationManager.h>
#include <math/Rectangle.h>
#include <raster/osm/OSMUtils.h>
#include <renderer/core/GLMapView2.h>
#include <util/Logging2.h>

#include "common.h"
#include "interop/JNIFloatArray.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/java/JNICollection.h"
#include "interop/java/JNILocalRef.h"
#include "interop/renderer/core/Interop.h"
#include "interop/renderer/core/ManagedGLLayer2.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jfieldID drawMapScale;
        jfieldID drawMapResolution;
        jfieldID drawLat;
        jfieldID drawLng;
        jfieldID drawRotation;
        jfieldID drawTilt;
        jfieldID animationFactor;
        jfieldID drawVersion;
        jfieldID targeting;
        jfieldID westBound;
        jfieldID southBound;
        jfieldID northBound;
        jfieldID eastBound;
        jfieldID eastBoundUnwrapped;
        jfieldID westBoundUnwrapped;
        jfieldID crossesIDL;
        jfieldID idlHelper;
        jfieldID _left;
        jfieldID _right;
        jfieldID _top;
        jfieldID _bottom;
        jfieldID drawSrid;
        jfieldID focusx;
        jfieldID focusy;
        jfieldID upperLeft;
        jfieldID upperRight;
        jfieldID lowerRight;
        jfieldID lowerLeft;
        jfieldID settled;
        jfieldID renderPump;
        jfieldID rigorousRegistrationResolutionEnabled;
        jfieldID animationLastTick;
        jfieldID animationDelta;
        jfieldID sceneModelVersion;
        jfieldID scene;
        jfieldID oscene;
        jfieldID sceneModelForwardMatrix;
        jfieldID hardwareTransformResolutionThreshold;
        jfieldID elevationScaleFactor;
        jfieldID terrainBlendEnabled;
        jfieldID terrainBlendFactor;
        jfieldID continuousScrollEnabled;
    } GLMapView_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool GLMapView_class_init(JNIEnv &env) NOTHROWS;

    TAKErr collectTiles(void *opaque, const std::shared_ptr<const TerrainTile> &tile) NOTHROWS
    {
        std::vector<std::shared_ptr<const TerrainTile>> &arg = *static_cast<std::vector<std::shared_ptr<const TerrainTile>> *>(opaque);
        arg.push_back(tile);
        return TE_Ok;
    }

    TAK::Engine::Math::Point2<double> adjustCamLocation(const MapSceneModel2 &model) NOTHROWS;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_GLMapView_create
  (JNIEnv *env, jclass clazz, jlong ctxPtr, jlong viewPtr, jint left, jint bottom, jint right, jint top)
{
    RenderContext *cctx = JLONG_TO_INTPTR(RenderContext, ctxPtr);
    if(!cctx) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    atakmap::core::AtakMapView *cview = JLONG_TO_INTPTR(atakmap::core::AtakMapView, viewPtr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    GLMapView2Ptr retval(new GLMapView2(*cctx, *cview, left, bottom, right, top), Memory_deleter_const<GLMapView2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct<GLMapView2>(env, mpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_render
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->render();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_release
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->release();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setBaseMap
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mbasemap)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLMapRenderable2Ptr cbasemap(nullptr, nullptr);
    if(mbasemap)
        Renderer::Core::Interop_marshal(cbasemap, *env, mbasemap);
    cview->setBaseMap(std::move(cbasemap));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_sync
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mview)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    if(!checkInit(*env))
        return;

#define SET_FIELD(sm, fid) \
    env->sm(mview, GLMapView_class.fid, cview->fid)

    SET_FIELD(SetDoubleField, drawMapScale);
    SET_FIELD(SetDoubleField, drawMapResolution);
    SET_FIELD(SetDoubleField, drawLat);
    SET_FIELD(SetDoubleField, drawLng);
    SET_FIELD(SetDoubleField, drawRotation);
    SET_FIELD(SetDoubleField, drawTilt);
    SET_FIELD(SetDoubleField, animationFactor);
    SET_FIELD(SetIntField, drawVersion);
    //env->SetBooleanField(mview, GLMapView_class.targeting, (cview->targeting ? JNI_TRUE : JNI_FALSE));
    SET_FIELD(SetDoubleField, westBound);
    SET_FIELD(SetDoubleField, southBound);
    SET_FIELD(SetDoubleField, northBound);
    SET_FIELD(SetDoubleField, eastBound);
    // Note: no SDK field, must be handled downstream
    //SET_FIELD(SetDoubleField, eastBoundUnwrapped);
    //SET_FIELD(SetDoubleField, westBoundUnwrapped);
    //env->SetBooleanField(mview, GLMapView_class.crossesIDL, (cview->crossesIDL ? JNI_TRUE : JNI_FALSE));
    // XXX - TODO
    //SET_FIELD(GLMapView_class, idlHelper, "Lcom/atakmap/map/opengl/GLAntiMeridianHelper;");
    // Note: field name mismatch
    env->SetIntField(mview, GLMapView_class._left, cview->left);
    env->SetIntField(mview, GLMapView_class._right, cview->right);
    env->SetIntField(mview, GLMapView_class._top, cview->top);
    env->SetIntField(mview, GLMapView_class._bottom, cview->bottom);
    SET_FIELD(SetIntField, drawSrid);
    SET_FIELD(SetFloatField, focusx);
    SET_FIELD(SetFloatField, focusy);
    Java::JNILocalRef mupperLeft(*env, env->GetObjectField(mview, GLMapView_class.upperLeft));
    Core::Interop_copy(mupperLeft, env, cview->upperLeft);
    Java::JNILocalRef mupperRight(*env, env->GetObjectField(mview, GLMapView_class.upperRight));
    Core::Interop_copy(mupperRight, env, cview->upperRight);
    Java::JNILocalRef mlowerRight(*env, env->GetObjectField(mview, GLMapView_class.lowerRight));
    Core::Interop_copy(mlowerRight, env, cview->lowerRight);
    Java::JNILocalRef mlowerLeft(*env, env->GetObjectField(mview, GLMapView_class.lowerLeft));
    Core::Interop_copy(mlowerLeft, env, cview->lowerLeft);
    //env->SetBooleanField(mview, GLMapView_class.settled, (cview->settled ? JNI_TRUE : JNI_FALSE));
    SET_FIELD(SetIntField, renderPump);
    // XXX - no field in SDK
    //SET_FIELD(SetBooleanField, rigorousRegistrationResolutionEnabled);
    SET_FIELD(SetLongField, animationLastTick);
    SET_FIELD(SetLongField, animationDelta);
    SET_FIELD(SetIntField, sceneModelVersion);
    Java::JNILocalRef mscene(*env, env->GetObjectField(mview, GLMapView_class.scene));
    Core::Interop_marshal(mscene, *env, cview->scene);
    env->SetObjectField(mview, GLMapView_class.scene, mscene);
    // XXX - no field in SDK
    //SET_FIELD(GLMapView_class, oscene, "Lcom/atakmap/map/MapSceneModel;");
    Java::JNILocalRef msceneModelForwardMatrix(*env, env->GetObjectField(mview, GLMapView_class.sceneModelForwardMatrix));
    JNIFloatArray_copy((jfloatArray)msceneModelForwardMatrix.get(), 0u, *env, reinterpret_cast<const jfloat *>(&cview->sceneModelForwardMatrix[0u]), 16u);
    SET_FIELD(SetDoubleField, hardwareTransformResolutionThreshold);
    SET_FIELD(SetDoubleField, elevationScaleFactor);
    // XXX - no field in SDK
    //SET_FIELD(SetBooleanField, terrainBlendEnabled);
    //SET_FIELD(SetDoubleField, terrainBlendFactor);
    //env->SetBooleanField(mview, GLMapView_class.continuousScrollEnabled, (cview->continuousScrollEnabled ? JNI_TRUE : JNI_FALSE));
#undef SET_FIELD
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_start
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->start();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_stop
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->stop();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_intern
  (JNIEnv *env, jclass clazz, jobject mview)
{
    TAKErr code(TE_Ok);
    if(! mview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    std::shared_ptr<GLMapView2> cview;
    code = Renderer::Core::Interop_marshal(cview, *env, mview);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_getTerrainMeshElevation
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    double retval;
    if(cview->getTerrainMeshElevation(&retval, lat, lng) != TE_Ok)
        return NAN;
    return retval;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_intersectWithTerrain2
  (JNIEnv *env, jclass clazz, jlong viewptr, jlong sceneptr, jfloat x, jfloat y, jobject mresult)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, viewptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    MapSceneModel2 *cscene = JLONG_TO_INTPTR(MapSceneModel2, sceneptr);
    if(!cscene) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    GeoPoint2 cresult;
    if(cview->intersectWithTerrain2(&cresult, *cscene, x, y) != TE_Ok)
        return false;
    return (Core::Interop_copy(mresult, env, cresult) == TE_Ok);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_getTerrainTiles
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mtiles)
{
    TAKErr code(TE_Ok);
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(!mtiles) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    std::vector<std::shared_ptr<const TerrainTile>> ctiles;
    code = cview->visitTerrainTiles(collectTiles, &ctiles);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    for(std::size_t i = 0u; i < ctiles.size(); i++) {
        Java::JNILocalRef mtilePointer(*env, NewPointer(env, ctiles[i]));
        code = Java::JNICollection_add(*env, mtiles, mtilePointer);
        TE_CHECKBREAK_CODE(code);
    }
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_getTerrainVersion
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }
    return cview->getTerrainVersion();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_GLMapView_getTerrainRenderService
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    return NewPointer<TAK::Engine::Renderer::Elevation::TerrainRenderService>(env, &cview->getTerrainRenderService(), true);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_forwardD
  (JNIEnv *env, jclass clazz, jlong ptr, jlong srcBufPtr, jint srcSize, jlong dstBufPtr, jint dstSize, jint count)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(!dstBufPtr || !srcBufPtr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(count < 0)  {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->forward(JLONG_TO_INTPTR(float, dstBufPtr), dstSize, JLONG_TO_INTPTR(double, srcBufPtr), srcSize, count);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_forwardF
  (JNIEnv *env, jclass clazz, jlong ptr, jlong srcBufPtr, jint srcSize, jlong dstBufPtr, jint dstSize, jint count)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(!dstBufPtr || !srcBufPtr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(count < 0)  {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->forward(JLONG_TO_INTPTR(float, dstBufPtr), dstSize, JLONG_TO_INTPTR(float, srcBufPtr), srcSize, count);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_estimateResolutionFromViewAABB
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble ullat, jdouble ullng, jdouble lrlat, jdouble lrlng, jobject mclosest)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    MapSceneModel2 cmodel(cview->renderPasses[0u].scene);
    cmodel.camera.target.y = cview->drawLat;
    cmodel.camera.target.x = cview->drawLng;
    cmodel.width = (cview->right-cview->left);
    cmodel.height = (cview->top-cview->bottom);
    return Java_com_atakmap_map_opengl_GLMapView_estimateResolutionFromModelAABB(env, clazz, INTPTR_TO_JLONG(&cmodel), ullat, ullng, lrlat, lrlng, mclosest);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_estimateResolutionFromModelAABB
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble ullat, jdouble ullng, jdouble lrlat, jdouble lrlng, jobject mclosest)
{
    // XXX - adaptive algorithm pending perspective camera
    TAKErr code(TE_Ok);
    MapSceneModel2 *cmodel = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!cmodel) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    const TAK::Engine::Core::GeoPoint2 tgt(cmodel->camera.target.y, cmodel->camera.target.x);
    if (atakmap::math::Rectangle<double>::contains(ullng, lrlat, lrlng, ullat, tgt.longitude, tgt.latitude))
        return cmodel->gsd;

    const double sceneHalfWidth = cmodel->width / 2.0;
    const double sceneHalfHeight = cmodel->height / 2.0;
    const double sceneRadius = cmodel->gsd*sqrt((sceneHalfWidth*sceneHalfWidth) + (sceneHalfHeight*sceneHalfHeight));

    const TAK::Engine::Core::GeoPoint2 aoiCentroid((ullat + lrlat) / 2.0, (ullng + lrlng) / 2.0);
    double aoiRadius;
    {
        const double uld = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, TAK::Engine::Core::GeoPoint2(ullat, ullng), true);
        const double urd = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, TAK::Engine::Core::GeoPoint2(ullat, lrlng), true);
        const double lrd = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, TAK::Engine::Core::GeoPoint2(lrlat, lrlng), true);
        const double lld = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, TAK::Engine::Core::GeoPoint2(lrlat, ullng), true);
        aoiRadius = std::max(std::max(uld, urd), std::max(lrd, lld));
    }
    const double d = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, tgt, true);
    if((d-aoiRadius) < sceneRadius)
        return cmodel->gsd;
    // observation is that the value returned here really does not matter
    return cmodel->gsd*pow(2.0, 1.0+log((d-aoiRadius)-sceneRadius)/log(2.0));
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_estimateResolutionFromViewSphere
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble centerX, jdouble centerY, jdouble centerZ, jdouble radius, jobject mclosest)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    MapSceneModel2 *cmodel = &cview->renderPasses[0u].scene;
    return Java_com_atakmap_map_opengl_GLMapView_estimateResolutionFromModelSphere(env, clazz, INTPTR_TO_JLONG(cmodel), centerX, centerY, centerZ, radius, mclosest);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_estimateResolutionFromModelSphere
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble centerX, jdouble centerY, jdouble centerZ, jdouble radius, jobject mclosest)
{
    TAKErr code(TE_Ok);
    MapSceneModel2 *cmodel = JLONG_TO_INTPTR(MapSceneModel2, ptr);
    if(!cmodel) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    double gsd;
    if(cmodel->camera.elevation > -90.0) {
        // get eye pos as LLA
        TAK::Engine::Math::Point2<double> eyeProj;
        if (cmodel->camera.mode == MapCamera2::Scale) {
            eyeProj = adjustCamLocation(*cmodel);
        } else {
            eyeProj = cmodel->camera.location;
        }
        GeoPoint2 eye;
        cmodel->projection->inverse(&eye, eyeProj);
        if(eye.longitude < -180.0)
            eye.longitude += 360.0;
        else if(eye.longitude > 180.0)
            eye.longitude -= 360.0;

        GeoPoint2 closest(centerY, centerX, centerZ, AltitudeReference::HAE);


        if(mclosest) {
            Core::Interop_copy(mclosest, env, closest);
        }

        // XXX - find closest LLA on tile
        if(fabs(centerX-eye.longitude) > 180.0) {
            // XXX - wrapping
        }

        const double closestslant = GeoPoint2_slantDistance(eye, closest);
        if(closestslant <= radius)
            return cmodel->gsd;

        const double camlocx = cmodel->camera.location.x*cmodel->displayModel->projectionXToNominalMeters;
        const double camlocy = cmodel->camera.location.y*cmodel->displayModel->projectionYToNominalMeters;
        const double camlocz = cmodel->camera.location.z*cmodel->displayModel->projectionZToNominalMeters;
        const double camtgtx = cmodel->camera.target.x*cmodel->displayModel->projectionXToNominalMeters;
        const double camtgty = cmodel->camera.target.y*cmodel->displayModel->projectionYToNominalMeters;
        const double camtgtz = cmodel->camera.target.z*cmodel->displayModel->projectionZToNominalMeters;

        double camtgtslant;
        TAK::Engine::Math::Vector2_length(&camtgtslant, TAK::Engine::Math::Point2<double>(camlocx-camtgtx, camlocy-camtgty, camlocz-camtgtz));

        return (closestslant/camtgtslant)*cmodel->gsd;
    } else {
        return cmodel->gsd;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_set_1terrainBlendFactor
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat v)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    cview->terrainBlendFactor = v;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_set_1targeting
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean v)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    if(!checkInit(*env))
        return;

    cview->targeting = v;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1targeting
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return cview->targeting;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1crossesIDL
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return cview->crossesIDL;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1settled
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return cview->settled;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1rigorousRegistrationResolutionEnabled
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return cview->targeting;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1terrainBlendEnabled
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    // XXX -
    //return cview->terrainBlendEnabled;
    return false;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1continuousScrollEnabled
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return cview->continuousScrollEnabled;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_getDisplayMode
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }
    return cview->view.getProjection();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setDisplayMode
  (JNIEnv *env, jclass clazz, jlong ptr, jint srid)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->view.setProjection(srid);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_lookAt
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jdouble res, jdouble rot, jdouble tilt, jboolean animate)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    // perform collision detection
    if (cview->scene.camera.mode == MapCamera2::Perspective) {
        atakmap::math::Point<float> focusxy;
        cview->view.getController()->getFocusPoint(&focusxy);
        MapSceneModel2 newModel(cview->view.getDisplayDpi(),
                                cview->view.getWidth(), cview->view.getHeight(),
                                cview->view.getProjection(),
                                GeoPoint2(lat, lng),
                                focusxy.x, focusxy.y,
                                rot, tilt, res);
        GeoPoint2 cameraLocation;
        newModel.projection->inverse(&cameraLocation, newModel.camera.location);
        double localEl = 0.0;
        cview->getTerrainMeshElevation(&localEl, cameraLocation.latitude, cameraLocation.longitude);
        if (cameraLocation.altitude - 2.0 < localEl) {
            double eladj = (localEl + 2.0) - cameraLocation.altitude;
            GeoPoint2 adjCamLoc(cameraLocation.latitude, cameraLocation.longitude, localEl + 2.0, AltitudeReference::HAE);
            GeoPoint2 adjTgtLoc(lat, lng, eladj, AltitudeReference::HAE);
            Point2<double> camAdjxyz;
            newModel.projection->forward(&camAdjxyz, adjCamLoc);
            Point2<double> tgtAdjxyz;
            newModel.projection->forward(&tgtAdjxyz, adjTgtLoc);

            Point2<double> adjTgtSurface;
            if(newModel.displayModel->earth->intersect(&adjTgtSurface, Ray2<double>(camAdjxyz, Vector4<double>(tgtAdjxyz.x-camAdjxyz.x, tgtAdjxyz.y-camAdjxyz.y, tgtAdjxyz.z-camAdjxyz.z)))) {
                GeoPoint2 newFocus;
                newModel.projection->inverse(&newFocus, adjTgtSurface);
                lat = newFocus.latitude;
                lng = newFocus.longitude;

                double od;
                Vector2_length(&od, Point2<double>(newModel.camera.target.x-newModel.camera.location.x, newModel.camera.target.y-newModel.camera.location.y, newModel.camera.target.z-newModel.camera.location.z));
                double nd;
                Vector2_length(&nd, Point2<double>(adjTgtSurface.x-camAdjxyz.x, adjTgtSurface.y-camAdjxyz.y, adjTgtSurface.z-camAdjxyz.z));
                res *= nd / od;
            }
        }
    }

    const double mapScale = atakmap::core::AtakMapView_getMapScale(cview->view.getDisplayDpi(), res);
    cview->view.updateView(atakmap::core::GeoPoint(lat, lng, alt, atakmap::core::AltitudeReference::HAE), mapScale, rot, tilt, NAN, NAN, animate);
    return true;
}

/*
 * Class:     com_atakmap_map_opengl_GLMapView
 * Method:    getMapSceneModel
 * Signature: (JZZ)Lcom/atakmap/map/MapSceneModel;
 */
JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_GLMapView_getMapSceneModel
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean instant, jboolean llOrigin)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    if(instant) {
        MapSceneModel2Ptr retval(NULL, NULL);
        {
            ReadLock rlock(cview->renderPassMutex);
            retval = MapSceneModel2Ptr(new MapSceneModel2(cview->renderPasses[0].scene),
                                       Memory_deleter_const<MapSceneModel2>);
        }
        if(!llOrigin) {
            // flip forward/inverse matrices
            retval->inverseTransform.translate(0.0, retval->height, 0.0);
            retval->inverseTransform.scale(1.0, -1.0, 1.0);


            retval->forwardTransform.preConcatenate(Matrix2(1.0, 0.0, 0.0, 0.0,
                                                            0.0, -1.0, 0.0, 0.0,
                                                            0.0, 0.0, 1.0, 0.0,
                                                            0.0, 0.0, 0.0, 1.0));
            retval->forwardTransform.preConcatenate(Matrix2(1.0, 0.0, 0.0, 0.0,
                                                            0.0, 1.0, 0.0, retval->height,
                                                            0.0, 0.0, 1.0, 0.0,
                                                            0.0, 0.0, 0.0, 1.0));
        }

        return NewPointer(env, std::move(retval));
    } else {
        atakmap::math::Point<float> focus;
        cview->view.getController()->getFocusPoint(&focus);
        atakmap::core::GeoPoint geolegacy;
        cview->view.getPoint(&geolegacy);
        GeoPoint2 geo;
        GeoPoint_adapt(&geo, geolegacy);
        MapSceneModel2Ptr retval(new MapSceneModel2(cview->view.getDisplayDpi(),
                                                    cview->view.getWidth(),
                                                    cview->view.getHeight(),
                                                    cview->view.getProjection(),
                                                    geo,
                                                    focus.x, focus.y,
                                                    cview->view.getMapRotation(),
                                                    cview->view.getMapTilt(),
                                                    cview->view.getMapResolution()),
                                 Memory_deleter_const<MapSceneModel2>);
        if(llOrigin) {
            // flip forward/inverse matrices
            retval->inverseTransform.translate(0.0, retval->height, 0.0);
            retval->inverseTransform.scale(1.0, -1.0, 1.0);


            retval->forwardTransform.preConcatenate(Matrix2(1.0, 0.0, 0.0, 0.0,
                                                            0.0, -1.0, 0.0, 0.0,
                                                            0.0, 0.0, 1.0, 0.0,
                                                            0.0, 0.0, 0.0, 1.0));
            retval->forwardTransform.preConcatenate(Matrix2(1.0, 0.0, 0.0, 0.0,
                                                            0.0, 1.0, 0.0, retval->height,
                                                            0.0, 0.0, 1.0, 0.0,
                                                            0.0, 0.0, 0.0, 1.0));
        }

        return NewPointer(env, std::move(retval));
    }
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_isAnimating
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    return cview->view.isAnimating();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setFocusPointOffset
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat x, jfloat y)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    cview->view.setFocusPointOffset(x, -1.0f*y);
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_opengl_GLMapView_getFocusPointOffsetX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0f;
    }
    atakmap::math::Point<float> focus;
    cview->view.getController()->getFocusPoint(&focus);
    return focus.x-(cview->view.getWidth()/2.0f);
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_opengl_GLMapView_getFocusPointOffsetY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0f;
    }
    atakmap::math::Point<float> focus;
    cview->view.getController()->getFocusPoint(&focus);
    return (cview->view.getHeight()/2.0f) - focus.y;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setSize
  (JNIEnv *env, jclass clazz, jlong ptr, jint width, jint height)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->view.setSize(width, height);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setDisplayDpi
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble dpi)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->view.setDisplayDpi(dpi);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_inverse
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble x, jdouble y, jdouble z, jint mode, jobject mlla)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    MapSceneModel2 scene;
    {
        ReadLock lock(cview->renderPassMutex);
        scene = cview->renderPasses[0u].scene;
    }

    Point2<double> xyz(x, y, z);
    GeoPoint2 clla;
    switch(mode) {
        case com_atakmap_map_opengl_GLMapView_INVERSE_MODE_ABSOLUTE :
            // inverse xyz into map projection space
            if(scene.inverseTransform.transform(&xyz, xyz) != TE_Ok)
                return false;
            // convert map projection coordinate to LLA
            if(scene.projection->inverse(&clla, xyz) != TE_Ok)
                return false;
            break;
        case com_atakmap_map_opengl_GLMapView_INVERSE_MODE_MODEL :
            if(scene.inverse(&clla, Point2<float>(x, y)) != TE_Ok)
                return false;
            break;
        case com_atakmap_map_opengl_GLMapView_INVERSE_MODE_TERRAIN :
            if(cview->intersectWithTerrain2(&clla, scene, x, y) != TE_Ok)
                return false;
            break;
        default :
            return false;
    }

    Core::Interop_copy(mlla, env, clla);
    return true;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setElevationExaggerationFactor
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble factor)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(factor <= 0.0) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->view.setElevationExaggerationFactor(factor);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_getElevationExaggerationFactor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 1.0;
    }
    return cview->view.getElevationExaggerationFactor();
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = GLMapView_class_init(env);
        return clinit;
    }
    bool GLMapView_class_init(JNIEnv &env) NOTHROWS
    {
#define SET_FIELD_DEFINITION(c, m, sig) \
    c.m = env.GetFieldID(c.id, #m, sig)

        GLMapView_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/opengl/GLMapView");
        SET_FIELD_DEFINITION(GLMapView_class, drawMapScale, "D");
        SET_FIELD_DEFINITION(GLMapView_class, drawMapResolution, "D");
        SET_FIELD_DEFINITION(GLMapView_class, drawLat, "D");
        SET_FIELD_DEFINITION(GLMapView_class, drawLng, "D");
        SET_FIELD_DEFINITION(GLMapView_class, drawRotation, "D");
        SET_FIELD_DEFINITION(GLMapView_class, drawTilt, "D");
        SET_FIELD_DEFINITION(GLMapView_class, animationFactor, "D");
        SET_FIELD_DEFINITION(GLMapView_class, drawVersion, "I");
        SET_FIELD_DEFINITION(GLMapView_class, targeting, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, westBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class, southBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class, northBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class, eastBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class, eastBoundUnwrapped, "D");
        SET_FIELD_DEFINITION(GLMapView_class, westBoundUnwrapped, "D");
        SET_FIELD_DEFINITION(GLMapView_class, crossesIDL, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, idlHelper, "Lcom/atakmap/map/opengl/GLAntiMeridianHelper;");
        SET_FIELD_DEFINITION(GLMapView_class, _left, "I");
        SET_FIELD_DEFINITION(GLMapView_class, _right, "I");
        SET_FIELD_DEFINITION(GLMapView_class, _top, "I");
        SET_FIELD_DEFINITION(GLMapView_class, _bottom, "I");
        SET_FIELD_DEFINITION(GLMapView_class, drawSrid, "I");
        SET_FIELD_DEFINITION(GLMapView_class, focusx, "F");
        SET_FIELD_DEFINITION(GLMapView_class, focusy, "F");
        SET_FIELD_DEFINITION(GLMapView_class, upperLeft, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class, upperRight, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class, lowerRight, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class, lowerLeft, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class, settled, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, renderPump, "I");
        SET_FIELD_DEFINITION(GLMapView_class, rigorousRegistrationResolutionEnabled, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, animationLastTick, "J");
        SET_FIELD_DEFINITION(GLMapView_class, animationDelta, "J");
        SET_FIELD_DEFINITION(GLMapView_class, sceneModelVersion, "I");
        SET_FIELD_DEFINITION(GLMapView_class, scene, "Lcom/atakmap/map/MapSceneModel;");
        SET_FIELD_DEFINITION(GLMapView_class, oscene, "Lcom/atakmap/map/MapSceneModel;");
        SET_FIELD_DEFINITION(GLMapView_class, sceneModelForwardMatrix, "[F");
        SET_FIELD_DEFINITION(GLMapView_class, hardwareTransformResolutionThreshold, "D");
        SET_FIELD_DEFINITION(GLMapView_class, elevationScaleFactor, "D");
        SET_FIELD_DEFINITION(GLMapView_class, terrainBlendEnabled, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, terrainBlendFactor, "D");
        SET_FIELD_DEFINITION(GLMapView_class, continuousScrollEnabled, "Z");
#undef SET_FIELD_DEFINITION

        return true;
    }

    TAK::Engine::Math::Point2<double> adjustCamLocation(const MapSceneModel2 &view) NOTHROWS
    {
        const double camLocAdj = 2.5;

        const double camlocx = view.camera.location.x*view.displayModel->projectionXToNominalMeters;
        const double camlocy = view.camera.location.y*view.displayModel->projectionYToNominalMeters;
        const double camlocz = view.camera.location.z*view.displayModel->projectionZToNominalMeters;
        const double camtgtx = view.camera.target.x*view.displayModel->projectionXToNominalMeters;
        const double camtgty = view.camera.target.y*view.displayModel->projectionYToNominalMeters;
        const double camtgtz = view.camera.target.z*view.displayModel->projectionZToNominalMeters;

        double len;
        TAK::Engine::Math::Vector2_length(&len, TAK::Engine::Math::Point2<double>(camlocx-camtgtx, camlocy-camtgty, camlocz-camtgtz));

        const double dirx = (camlocx - camtgtx)/len;
        const double diry = (camlocy - camtgty)/len;
        const double dirz = (camlocz - camtgtz)/len;
        return TAK::Engine::Math::Point2<double>(
                    (camtgtx + (dirx*len*camLocAdj)) / view.displayModel->projectionXToNominalMeters,
                    (camtgty + (diry*len*camLocAdj)) / view.displayModel->projectionYToNominalMeters,
                    (camtgtz + (dirz*len*camLocAdj)) / view.displayModel->projectionZToNominalMeters);
    }
}