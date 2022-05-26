#include "jglmapview.h"

#include <core/MapSceneModel2.h>
#include <elevation/ElevationManager.h>
#include <math/Rectangle.h>
#include <port/STLListAdapter.h>
#include <port/STLVectorAdapter.h>
#include <raster/osm/OSMUtils.h>
#include <renderer/core/GLGlobeBase.h>
#include <renderer/core/GLGlobeSurfaceRenderer.h>
#include <renderer/core/GLGlobe.h>
#include <renderer/core/GLMapView2.h>
#include <renderer/core/GLLabelManager.h>
#include <util/Logging2.h>

#include "common.h"
#include "interop/JNIFloatArray.h"
#include "interop/JNIIntArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/feature/Interop.h"
#include "interop/java/JNICollection.h"
#include "interop/java/JNILocalRef.h"
#include "interop/renderer/core/Interop.h"
#include "interop/renderer/core/ManagedGLLayer2.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Core::Controls;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    typedef std::unique_ptr<GLGlobeBase, void(*)(const GLGlobeBase *)> GLGlobeBasePtr;

    struct
    {
        jclass id;
        jfieldID animationFactor;
        jfieldID eastBoundUnwrapped;
        jfieldID westBoundUnwrapped;
        jfieldID idlHelper;
        jfieldID settled;
        jfieldID rigorousRegistrationResolutionEnabled;
        jfieldID animationLastTick;
        jfieldID animationDelta;
        jfieldID sceneModelVersion;
        jfieldID oscene;
        jfieldID hardwareTransformResolutionThreshold;
        jfieldID elevationScaleFactor;
        jfieldID terrainBlendEnabled;
        jfieldID terrainBlendFactor;
        jfieldID continuousScrollEnabled;
        jfieldID currentPass;
        jfieldID currentScene;

        jmethodID dispatchCameraChanged;

        struct
        {
            jclass id;
            jfieldID drawMapResolution;
            jfieldID drawLat;
            jfieldID drawLng;
            jfieldID drawRotation;
            jfieldID drawTilt;
            jfieldID drawVersion;
            jfieldID targeting;
            jfieldID westBound;
            jfieldID southBound;
            jfieldID northBound;
            jfieldID eastBound;
            jfieldID crossesIDL;
            jfieldID left;
            jfieldID right;
            jfieldID top;
            jfieldID bottom;
            jfieldID drawSrid;
            jfieldID focusx;
            jfieldID focusy;
            jfieldID upperLeft;
            jfieldID upperRight;
            jfieldID lowerRight;
            jfieldID lowerLeft;
            jfieldID renderPump;
            jfieldID scene;
            jfieldID sceneModelForwardMatrix;
            jfieldID relativeScaleHint;
        } State_class;
    } GLMapView_class;

    class CameraChangedForwarder : public MapRenderer2::OnCameraChangedListener
    {
    public :
        CameraChangedForwarder(JNIEnv &env, jobject impl) NOTHROWS;
        ~CameraChangedForwarder() NOTHROWS;
    public :
        TAKErr onCameraChanged(const MapRenderer2 &renderer) NOTHROWS override;
    private :
        jobject impl;
    };

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
  (JNIEnv *env, jclass clazz, jlong ctxPtr, jlong viewPtr, jint left, jint bottom, jint right, jint top, jboolean orthoOnly)
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
    GLGlobeBasePtr retval(nullptr, nullptr);
    if(!orthoOnly)
        retval = GLGlobeBasePtr(new GLGlobe(*cctx, *cview, left, bottom, right, top), Memory_deleter_const<GLGlobeBase, GLGlobe>);
    else
        retval = GLGlobeBasePtr(new GLMapView2(*cctx, *cview, left, bottom, right, top), Memory_deleter_const<GLGlobeBase, GLMapView2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct<GLGlobeBase>(env, mpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_render
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->render();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_release
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->release();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setBaseMap
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mbasemap)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLMapRenderable2Ptr cbasemap(nullptr, nullptr);
    if(mbasemap)
        Renderer::Core::Interop_marshal(cbasemap, *env, mbasemap);
    cview->setBaseMap(std::move(cbasemap));
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_opengl_GLMapView_getLabelManager
   (JNIEnv *env, jclass clazz, jlong viewPtr)
{
    GLMapView2 *cview = JLONG_TO_INTPTR(GLMapView2, viewPtr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return INTPTR_TO_JLONG(cview->getLabelManager());
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_sync
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mview, jboolean current)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    if(!checkInit(*env))
        return;

    Java::JNILocalRef mstate(*env, env->GetObjectField(mview, current ? GLMapView_class.currentPass : GLMapView_class.currentScene));
    const GLGlobeBase::State *cstate = current ? cview->renderPass : &cview->renderPasses[0u];

#define SET_FIELD(sm, fid) \
    env->sm(mview, GLMapView_class.fid, cview->fid)
#define SET_STATE_FIELD(sm, fid) \
    env->sm(mstate, GLMapView_class.State_class.fid, cstate->fid)

    SET_STATE_FIELD(SetDoubleField, drawMapResolution);
    SET_STATE_FIELD(SetDoubleField, drawLat);
    SET_STATE_FIELD(SetDoubleField, drawLng);
    SET_STATE_FIELD(SetDoubleField, drawRotation);
    SET_STATE_FIELD(SetDoubleField, drawTilt);
    SET_FIELD(SetDoubleField, animationFactor);
    SET_STATE_FIELD(SetIntField, drawVersion);
    //env->SetBooleanField(mview, GLMapView_class.targeting, (cview->targeting ? JNI_TRUE : JNI_FALSE));
    SET_STATE_FIELD(SetDoubleField, westBound);
    SET_STATE_FIELD(SetDoubleField, southBound);
    SET_STATE_FIELD(SetDoubleField, northBound);
    SET_STATE_FIELD(SetDoubleField, eastBound);
    // Note: no SDK field, must be handled downstream
    //SET_FIELD(SetDoubleField, eastBoundUnwrapped);
    //SET_FIELD(SetDoubleField, westBoundUnwrapped);
    //env->SetBooleanField(mview, GLMapView_class.crossesIDL, (cview->crossesIDL ? JNI_TRUE : JNI_FALSE));
    // XXX - TODO
    //SET_FIELD(GLMapView_class, idlHelper, "Lcom/atakmap/map/opengl/GLAntiMeridianHelper;");
    SET_STATE_FIELD(SetIntField, left);
    SET_STATE_FIELD(SetIntField, right);
    SET_STATE_FIELD(SetIntField, top);
    SET_STATE_FIELD(SetIntField, bottom);
    SET_STATE_FIELD(SetIntField, drawSrid);
    SET_STATE_FIELD(SetFloatField, focusx);
    SET_STATE_FIELD(SetFloatField, focusy);
    Java::JNILocalRef mupperLeft(*env, env->GetObjectField(mstate, GLMapView_class.State_class.upperLeft));
    Core::Interop_copy(mupperLeft, env, cstate->upperLeft);
    Java::JNILocalRef mupperRight(*env, env->GetObjectField(mstate, GLMapView_class.State_class.upperRight));
    Core::Interop_copy(mupperRight, env, cstate->upperRight);
    Java::JNILocalRef mlowerRight(*env, env->GetObjectField(mstate, GLMapView_class.State_class.lowerRight));
    Core::Interop_copy(mlowerRight, env, cstate->lowerRight);
    Java::JNILocalRef mlowerLeft(*env, env->GetObjectField(mstate, GLMapView_class.State_class.lowerLeft));
    Core::Interop_copy(mlowerLeft, env, cstate->lowerLeft);
    //env->SetBooleanField(mview, GLMapView_class.settled, (cview->settled ? JNI_TRUE : JNI_FALSE));
    SET_STATE_FIELD(SetIntField, renderPump);
    // XXX - no field in SDK
    //SET_FIELD(SetBooleanField, rigorousRegistrationResolutionEnabled);
    SET_FIELD(SetLongField, animationLastTick);
    SET_FIELD(SetLongField, animationDelta);
    SET_FIELD(SetIntField, sceneModelVersion);
    Java::JNILocalRef mscene(*env, env->GetObjectField(mstate, GLMapView_class.State_class.scene));
    Core::Interop_marshal(mscene.get(), *env, cstate->scene);

    // XXX - no field in SDK
    //SET_FIELD(GLMapView_class, oscene, "Lcom/atakmap/map/MapSceneModel;");
    Java::JNILocalRef msceneModelForwardMatrix(*env, env->GetObjectField(mstate, GLMapView_class.State_class.sceneModelForwardMatrix));
    JNIFloatArray_copy((jfloatArray)msceneModelForwardMatrix.get(), 0u, *env, reinterpret_cast<const jfloat *>(&cstate->sceneModelForwardMatrix[0u]), 16u);
    SET_FIELD(SetDoubleField, elevationScaleFactor);
    // XXX - no field in SDK
    //SET_FIELD(SetBooleanField, terrainBlendEnabled);
    //SET_FIELD(SetDoubleField, terrainBlendFactor);
    //env->SetBooleanField(mview, GLMapView_class.continuousScrollEnabled, (cview->continuousScrollEnabled ? JNI_TRUE : JNI_FALSE));

    const float relativeScale = ((float)(cstate->right-cstate->left) / cstate->viewport.width);
    env->SetFloatField(mstate, GLMapView_class.State_class.relativeScaleHint, relativeScale);
#undef SET_FIELD
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_start
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    cview->start();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_stop
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
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

    std::shared_ptr<GLGlobeBase> cview;
    code = Renderer::Core::Interop_marshal(cview, *env, mview);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_markDirty__J
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
    cview->getSurfaceRenderer().markDirty();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_markDirty__JDDDDZ
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble minX, jdouble minY, jdouble maxX, jdouble maxY, jboolean streaming)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
    cview->getSurfaceRenderer().markDirty(TAK::Engine::Feature::Envelope2(minX, minY, 0.0, maxX, maxY, 0.0), streaming);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_enableDrawMode
  (JNIEnv *env, jclass clazz, jlong ptr, jint tedm)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
    cview->enableDrawMode((TAK::Engine::Model::DrawMode)tedm);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_disableDrawMode
  (JNIEnv *env, jclass clazz, jlong ptr, jint tedm)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
    cview->disableDrawMode((TAK::Engine::Model::DrawMode)tedm);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_isDrawModeEnabled
  (JNIEnv *env, jclass clazz, jlong ptr, jint tedm)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
    return cview->isDrawModeEnabled((TAK::Engine::Model::DrawMode)tedm);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_getDrawModeColor
  (JNIEnv *env, jclass clazz, jlong ptr, jint tedm)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
    return cview->getColor((TAK::Engine::Model::DrawMode)tedm);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setDrawModeColor
  (JNIEnv *env, jclass clazz, jlong ptr, jint tedm, jint color)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
    return cview->setColor((TAK::Engine::Model::DrawMode)tedm, color, TAK::Engine::Renderer::Core::ColorControl::Modulate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_getSurfaceBounds
  (JNIEnv *env, jclass clazz, jlong ptr, jobject msurfaceBounds)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLGlobe *cview = static_cast<GLGlobe *>(cglobe);

    struct BoundsWrapper : public TAK::Engine::Port::Collection<TAK::Engine::Feature::Envelope2>
    {
    public :
        JNIEnv *env;
        jobject mimpl;
    public :
        virtual TAKErr add(TAK::Engine::Feature::Envelope2 elem) NOTHROWS override
        {
            Java::JNILocalRef mmbb(*env, NULL);
            Feature::Interop_marshal(mmbb, *env, elem);
            return Java::JNICollection_add(*env, mimpl, mmbb);
        }
        virtual TAKErr remove(TAK::Engine::Feature::Envelope2 &elem) NOTHROWS override
        {
            return TE_Unsupported;
        }
        virtual TAKErr contains(bool *value, TAK::Engine::Feature::Envelope2 &elem) NOTHROWS override
        {
            return TE_Unsupported;
        }
        virtual TAKErr clear() NOTHROWS
        {
            Java::JNILocalRef mmbb(*env, NULL);
            return Java::JNICollection_clear(*env, mimpl);
        }
        virtual std::size_t size() NOTHROWS override
        {
            return 0u;
        }
        virtual bool empty() NOTHROWS
        {
            return true;
        }
        virtual TAKErr iterator(IteratorPtr &iterator) NOTHROWS
        {
            return TE_Unsupported;
        }
    };
    BoundsWrapper csurfaceBounds;
    csurfaceBounds.env = env;
    csurfaceBounds.mimpl = msurfaceBounds;
    cview->getSurfaceBounds(csurfaceBounds);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setIlluminationEnabled
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean enabled)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    TAK::Engine::Renderer::Core::Controls::IlluminationControl *ctrl = cglobe->getIlluminationControl();
    if(!ctrl)
        return;
    ctrl->setEnabled(enabled);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_isIlluminationEnabled
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    TAK::Engine::Renderer::Core::Controls::IlluminationControl *ctrl = cglobe->getIlluminationControl();
    return !!ctrl && ctrl->getEnabled();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setIlluminationDateTime
  (JNIEnv *env, jclass clazz, jlong ptr, jint year, jint month, jint day, jint hours, jint minutes, jint seconds)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    IlluminationControl *ctrl = cglobe->getIlluminationControl();
    if(!ctrl)
        return;
    IlluminationControl::DateTime dateTime;
    dateTime.year = (short)year;
    dateTime.month = (short)month;
    dateTime.day = (short)day;
    dateTime.hour = (short)hours;
    dateTime.minute = (short)minutes;
    dateTime.second = (short)seconds;
    ctrl->setSimulatedDateTime(dateTime);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_getIlluminationDateTime
  (JNIEnv *env, jclass clazz, jlong ptr, jintArray mdateTime)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    IlluminationControl *ctrl = cglobe->getIlluminationControl();
    if(!ctrl)
        return;
    const IlluminationControl::DateTime cdateTime = ctrl->getSimulatedDateTime();
    JNIIntArray mdateTimeArr(*env, mdateTime, 0);
    mdateTimeArr[0] = cdateTime.year;
    mdateTimeArr[1] = cdateTime.month;
    mdateTimeArr[2] = cdateTime.day;
    mdateTimeArr[3] = cdateTime.hour;
    mdateTimeArr[4] = cdateTime.minute;
    mdateTimeArr[5] = cdateTime.second;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_getTerrainMeshElevation
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    double retval;
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        retval = 0.0;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        if (cview->getTerrainMeshElevation(&retval, lat, lng) != TE_Ok)
            return NAN;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        if (cview->getTerrainMeshElevation(&retval, lat, lng) != TE_Ok)
            return NAN;
    } else {
        return NAN;
    }
    return retval;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_intersectWithTerrain2
  (JNIEnv *env, jclass clazz, jlong viewptr, jlong sceneptr, jfloat x, jfloat y, jobject mresult, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, viewptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    MapSceneModel2 *cscene = JLONG_TO_INTPTR(MapSceneModel2, sceneptr);
    if(!cscene) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    GeoPoint2 cresult;
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        return false;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        if(cview->intersectWithTerrain2(&cresult, *cscene, x, y) != TE_Ok)
            return false;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        if(cview->intersectWithTerrain2(&cresult, *cscene, x, y) != TE_Ok)
            return false;
    } else {
        return false;
    }
    return (Core::Interop_copy(mresult, env, cresult) == TE_Ok);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_getTerrainTiles
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mtiles, jint type)
{
    TAKErr code(TE_Ok);
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(!mtiles) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    std::vector<std::shared_ptr<const TerrainTile>> ctiles;
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        return;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        code = cview->visitTerrainTiles(collectTiles, &ctiles);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        code = cview->visitTerrainTiles(collectTiles, &ctiles);
    } else {
        return;
    }
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
  (JNIEnv *env, jclass clazz, jlong ptr, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        return -1;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        return cview->getTerrainVersion();
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        return cview->getTerrainVersion();
    } else {
        return -1;
    }
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_GLMapView_getTerrainRenderService
  (JNIEnv *env, jclass clazz, jlong ptr, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        return NULL;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        return NewPointer<TAK::Engine::Renderer::Elevation::TerrainRenderService>(env, &cview->getTerrainRenderService(), true);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        return NewPointer<TAK::Engine::Renderer::Elevation::TerrainRenderService>(env, &cview->getTerrainRenderService(), true);
    } else {
        return NULL;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_forwardD
  (JNIEnv *env, jclass clazz, jlong ptr, jlong srcBufPtr, jint srcSize, jlong dstBufPtr, jint dstSize, jint count)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(count < 0)  {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLMapView2_forward(JLONG_TO_INTPTR(float, dstBufPtr), *cglobe, dstSize, JLONG_TO_INTPTR(double, srcBufPtr), srcSize, count);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_forwardF
  (JNIEnv *env, jclass clazz, jlong ptr, jlong srcBufPtr, jint srcSize, jlong dstBufPtr, jint dstSize, jint count)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(count < 0)  {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    GLMapView2_forward(JLONG_TO_INTPTR(float, dstBufPtr), *cglobe, dstSize, JLONG_TO_INTPTR(float, srcBufPtr), srcSize, count);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_estimateResolutionFromViewAABB
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble ullat, jdouble ullng, jdouble lrlat, jdouble lrlng, jobject mclosest)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    MapSceneModel2 cmodel(cview->renderPasses[0u].scene);
    cmodel.camera.target.y = cview->renderPasses[0].drawLat;
    cmodel.camera.target.x = cview->renderPasses[0].drawLng;
    cmodel.width = (cview->renderPasses[0].right-cview->renderPasses[0].left);
    cmodel.height = (cview->renderPasses[0].top-cview->renderPasses[0].bottom);
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
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
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
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat v, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        cview->terrainBlendFactor = v;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->terrainBlendFactor = v;
    } else {
        // no-op
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_set_1targeting
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean v)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    if(!checkInit(*env))
        return;

    cglobe->targeting = v;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1targeting
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean current)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    if(!checkInit(*env))
        return false;

    return current ? cglobe->renderPass->targeting : cglobe->renderPasses[0u].targeting;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1crossesIDL
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean current)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return current ? cview->renderPass->crossesIDL : cview->renderPasses[0u].crossesIDL;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1settled
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
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
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
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
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
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
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return cview->continuousScrollEnabled;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_get_1multiPartPass
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cview = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cview) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    if(!checkInit(*env))
        return false;

    return cview->multiPartPass;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_getDisplayMode
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    switch(cglobe->getDisplayMode()) {
        case MapRenderer::Flat :
            return 4326;
        case MapRenderer::Globe :
            return 4978;
        default :
            return -1;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setDisplayMode
  (JNIEnv *env, jclass clazz, jlong ptr, jint srid, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    switch(srid) {
        case 4326 :
            cglobe->setDisplayMode(MapRenderer::Flat);
            break;
        case 4978 :
            cglobe->setDisplayMode(MapRenderer::Globe);
            break;
        default :
            break;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        cview->view.setProjection(srid);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->view.setProjection(srid);
    } else {
        // no-op
    }
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_lookAt
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jdouble res, jdouble rot, jdouble tilt, jdouble collision, jint mcollide, jboolean animate, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    MapRenderer::CameraCollision ccollide;
    switch(mcollide) {
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_ABORT :
            ccollide = MapRenderer::Abort;
            break;
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_ADJUST_CAMERA :
            ccollide = MapRenderer::AdjustCamera;
            break;
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_ADJUST_FOCUS :
            ccollide = MapRenderer::AdjustFocus;
            break;
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_IGNORE :
            ccollide = MapRenderer::Ignore;
            break;
        default :
            return false;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        return cglobe->lookAt(
            TAK::Engine::Core::GeoPoint2(lat, lng, alt, TAK::Engine::Core::AltitudeReference::HAE),
            res,
            rot,
            tilt,
            ccollide,
            animate) == TE_Ok;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        const double mapScale = atakmap::core::AtakMapView_getMapScale(cview->view.getDisplayDpi(), res);
        cview->view.updateView(atakmap::core::GeoPoint(lat, lng, alt, atakmap::core::AltitudeReference::HAE), mapScale, rot, tilt, NAN, NAN, animate);
        return true;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        return GLGlobe_lookAt(*cview,
            TAK::Engine::Core::GeoPoint2(lat, lng, alt, TAK::Engine::Core::AltitudeReference::HAE),
            res,
            rot,
            tilt,
            collision,
            ccollide,
            animate) == TE_Ok;
    } else {
        return false;
    }
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_lookFrom
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jdouble rot, jdouble elevation, jdouble collision, jint mcollide, jboolean animate, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    MapRenderer::CameraCollision ccollide;
    switch(mcollide) {
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_ABORT :
            ccollide = MapRenderer::Abort;
            break;
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_ADJUST_CAMERA :
            ccollide = MapRenderer::AdjustCamera;
            break;
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_ADJUST_FOCUS :
            ccollide = MapRenderer::AdjustFocus;
            break;
        case com_atakmap_map_opengl_GLMapView_CAMERA_COLLISION_IGNORE :
            ccollide = MapRenderer::Ignore;
            break;
        default :
            return false;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2)
    {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        return GLGlobe_lookFrom(*cview,
            TAK::Engine::Core::GeoPoint2(lat, lng, alt, TAK::Engine::Core::AltitudeReference::HAE),
            rot,
            elevation,
            collision,
            ccollide,
            animate) == TE_Ok;
    }
    else
        return false;
}

/*
 * Class:     com_atakmap_map_opengl_GLMapView
 * Method:    getMapSceneModel
 * Signature: (JZZ)Lcom/atakmap/map/MapSceneModel;
 */
JNIEXPORT jobject JNICALL Java_com_atakmap_map_opengl_GLMapView_getMapSceneModel
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean instant, jboolean llOrigin, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    if(instant) {
        MapSceneModel2Ptr retval(NULL, NULL);
        {
            ReadLock rlock(cglobe->renderPasses0Mutex);
            retval = MapSceneModel2Ptr(new MapSceneModel2(cglobe->renderPasses[0].scene),
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
        double dpi;
        std::size_t width;
        std::size_t height;
        int srid;
        GeoPoint2 focus;
        float focusx, focusy;
        double rotation;
        double tilt;
        double resolution;
        MapCamera2::Mode mode;
        if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
            // XXX -
            return NULL;
        } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
            GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
            atakmap::math::Point<float> focusxy;
            cview->view.getController()->getFocusPoint(&focusxy);
            atakmap::core::GeoPoint geolegacy;
            cview->view.getPoint(&geolegacy);
            GeoPoint_adapt(&focus, geolegacy);
            dpi = cview->view.getDisplayDpi();
            width = (std::size_t)cview->view.getWidth();
            height = (std::size_t)cview->view.getHeight();
            srid = cview->view.getProjection();
            focusx = focusxy.x;
            focusy = focusxy.y;
            rotation = cview->view.getMapRotation();
            tilt = cview->view.getMapTilt();
            resolution = cview->view.getMapResolution();
            mode = MapCamera2::Scale;
        } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
            GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
            atakmap::math::Point<float> focusxy;
            cview->view.getController()->getFocusPoint(&focusxy);
            atakmap::core::GeoPoint geolegacy;
            cview->view.getPoint(&geolegacy);
            GeoPoint_adapt(&focus, geolegacy);
            dpi = cview->view.getDisplayDpi();
            width = (std::size_t)cview->view.getWidth();
            height = (std::size_t)cview->view.getHeight();
            srid = cview->view.getProjection();
            focusx = focusxy.x;
            focusy = focusxy.y;
            rotation = cview->view.getMapRotation();
            tilt = cview->view.getMapTilt();
            resolution = cview->view.getMapResolution();
            mode = MapCamera2::Perspective;
        } else {
            return NULL;
        }

        MapSceneModel2Ptr retval(new MapSceneModel2(dpi,
                                                    width,
                                                    height,
                                                    srid,
                                                    focus,
                                                    focusx, focusy,
                                                    rotation,
                                                    tilt,
                                                    resolution,
                                                    mode),
                                 Memory_deleter_const<MapSceneModel2>);
        // flip forward/inverse matrices
        if(llOrigin)
            GLGlobeBase_glScene(*retval);

        return NewPointer(env, std::move(retval));
    }
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_isAnimating
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    return !cglobe->settled;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setFocusPointOffset
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat x, jfloat y, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        cview->view.setFocusPointOffset(x, -1.0f*y);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->view.setFocusPointOffset(x, -1.0f*y);
    } else {
        // no-op
    }
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_opengl_GLMapView_getFocusPointOffsetX
  (JNIEnv *env, jclass clazz, jlong ptr, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0f;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        return 0.0f;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        atakmap::math::Point<float> focus;
        cview->view.getController()->getFocusPoint(&focus);
        return focus.x-(cview->view.getWidth()/2.0f);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        atakmap::math::Point<float> focus;
        cview->view.getController()->getFocusPoint(&focus);
        return focus.x-(cview->view.getWidth()/2.0f);
    } else {
        return 0.0f;
    }
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_opengl_GLMapView_getFocusPointOffsetY
  (JNIEnv *env, jclass clazz, jlong ptr, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0f;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        return 0.0f;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        atakmap::math::Point<float> focus;
        cview->view.getController()->getFocusPoint(&focus);
        return (cview->view.getHeight()/2.0f) - focus.y;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        atakmap::math::Point<float> focus;
        cview->view.getController()->getFocusPoint(&focus);
        return (cview->view.getHeight()/2.0f) - focus.y;
    } else {
        return 0.0f;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setSize
  (JNIEnv *env, jclass clazz, jlong ptr, jint width, jint height, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        cglobe->setSurfaceSize(width, height);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        cview->view.setSize(width, height);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->view.setSize(width, height);
    } else {
        // no-op
    }
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_opengl_GLMapView_addCameraChangedListener
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mview)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    std::unique_ptr<CameraChangedForwarder> clistener(new CameraChangedForwarder(*env, mview));
    cglobe->addOnCameraChangedListener(clistener.get());
    return INTPTR_TO_JLONG(clistener.release());
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_removeCameraChangedListener
  (JNIEnv *env, jclass clazz, jlong ptr, jlong cbptr)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    std::unique_ptr<CameraChangedForwarder> clistener(JLONG_TO_INTPTR(CameraChangedForwarder, cbptr));
    if(!clistener) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(cglobe)
        cglobe->removeOnCameraChangedListener(clistener.get());
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setDisplayDpi
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble dpi, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        cglobe->displayDpi = dpi;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        cview->view.setDisplayDpi(dpi);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->view.setDisplayDpi(dpi);
    } else {
        // no-op
    }
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_inverse
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble x, jdouble y, jdouble z, jint mmode, jobject mlla)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    MapSceneModel2 scene;
    {
        ReadLock lock(cglobe->renderPasses0Mutex);
        scene = cglobe->renderPasses[0u].scene;
    }

    Point2<double> xyz(x, y, z);
    GeoPoint2 clla;
    MapRenderer::InverseResult result;
    MapRenderer::InverseMode cmode;
    unsigned int hints = 0u;
    switch(mmode) {
        case com_atakmap_map_opengl_GLMapView_INVERSE_MODE_ABSOLUTE :
            cmode = MapRenderer::InverseMode::Transform;
            hints |= MapRenderer::IgnoreTerrainMesh|MapRenderer::IgnoreSurfaceMesh;
            break;
        case com_atakmap_map_opengl_GLMapView_INVERSE_MODE_MODEL :
            cmode = MapRenderer::InverseMode::RayCast;
            hints |= MapRenderer::IgnoreTerrainMesh|MapRenderer::IgnoreSurfaceMesh;
            break;
        case com_atakmap_map_opengl_GLMapView_INVERSE_MODE_TERRAIN :
            cmode = MapRenderer::InverseMode::RayCast;
            break;
        default :
            return false;
    }
    if(cglobe->inverse(&result, &clla, cmode, hints, xyz, MapRenderer::LowerLeft) != TE_Ok)
        return false;
    if(result == MapRenderer::None)
        return false;
    Core::Interop_copy(mlla, env, clla);
    return true;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setElevationExaggerationFactor
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble factor, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(factor <= 0.0) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        cglobe->elevationScaleFactor = factor;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        cview->view.setElevationExaggerationFactor(factor);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->view.setElevationExaggerationFactor(factor);
    } else {
        // no-op
    }
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_opengl_GLMapView_getElevationExaggerationFactor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 1.0;
    }
    return cglobe->elevationScaleFactor;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_isRenderDiagnosticsEnabled
  (JNIEnv *env, jclass clazz, jlong ptr, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
        return false;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        return cview->isRenderDiagnosticsEnabled();
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        return cview->isRenderDiagnosticsEnabled();
    } else {
        // no-op
        return false;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setRenderDiagnosticsEnabled
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean enabled, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        cview->setRenderDiagnosticsEnabled(enabled);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->setRenderDiagnosticsEnabled(enabled);
    } else {
        // no-op
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_setAtmosphereEnabled
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean enabled, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        // no-op
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        cview->setAtmosphereEnabled(enabled);
    } else {
        // no-op
    }
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLMapView_isAtmosphereEnabled
  (JNIEnv *env, jclass clazz, jlong ptr, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
        return false;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        // no-op
        return false;
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        return cview->isAtmosphereEnabled();
    } else {
        // no-op
        return false;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_addRenderDiagnostic
  (JNIEnv *env, jclass clazz, jlong ptr, jstring mmsg, jint type)
{
    GLGlobeBase *cglobe = JLONG_TO_INTPTR(GLGlobeBase, ptr);
    if(!cglobe) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(type == com_atakmap_map_opengl_GLMapView_IMPL_IFACE) {
        // no-op
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V1) {
        GLMapView2 *cview = static_cast<GLMapView2 *>(cglobe);
        JNIStringUTF cmsg(*env, mmsg);
        cview->addRenderDiagnosticMessage(cmsg);
    } else if(type == com_atakmap_map_opengl_GLMapView_IMPL_V2) {
        GLGlobe *cview = static_cast<GLGlobe *>(cglobe);
        JNIStringUTF cmsg(*env, mmsg);
        cview->addRenderDiagnosticMessage(cmsg);
    } else {
        // no-op
    }
}

namespace
{
    CameraChangedForwarder::CameraChangedForwarder(JNIEnv &env, jobject impl_) NOTHROWS :
        impl(env.NewWeakGlobalRef(impl_))
    {}
    CameraChangedForwarder::~CameraChangedForwarder() NOTHROWS
    {
        if(impl) {
            LocalJNIEnv env;
            env->DeleteWeakGlobalRef(impl);
            impl = NULL;
        }
    }
    TAKErr CameraChangedForwarder::onCameraChanged(const MapRenderer2 &renderer) NOTHROWS
    {
        if(!impl)
            return TE_Done;
        LocalJNIEnv env;
        // check if reference was cleared
        Java::JNILocalRef limpl(*env, env->NewLocalRef(impl));
        if(ATAKMapEngineJNI_equals(env, limpl, NULL)) {
            env->DeleteWeakGlobalRef(impl);
            impl = NULL;
            return TE_Done;
        }
        // dispatch camera changed
        env->CallVoidMethod(limpl, GLMapView_class.dispatchCameraChanged);
        return TE_Ok;
    }

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
        GLMapView_class.State_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/opengl/GLMapView$State");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, drawMapResolution, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, drawLat, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, drawLng, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, drawRotation, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, drawTilt, "D");
        SET_FIELD_DEFINITION(GLMapView_class, animationFactor, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, drawVersion, "I");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, targeting, "Z");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, westBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, southBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, northBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, eastBound, "D");
        SET_FIELD_DEFINITION(GLMapView_class, eastBoundUnwrapped, "D");
        SET_FIELD_DEFINITION(GLMapView_class, westBoundUnwrapped, "D");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, crossesIDL, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, idlHelper, "Lcom/atakmap/map/opengl/GLAntiMeridianHelper;");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, left, "I");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, right, "I");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, top, "I");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, bottom, "I");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, drawSrid, "I");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, focusx, "F");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, focusy, "F");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, upperLeft, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, upperRight, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, lowerRight, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, lowerLeft, "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        SET_FIELD_DEFINITION(GLMapView_class, settled, "Z");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, renderPump, "I");
        SET_FIELD_DEFINITION(GLMapView_class, rigorousRegistrationResolutionEnabled, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, animationLastTick, "J");
        SET_FIELD_DEFINITION(GLMapView_class, animationDelta, "J");
        SET_FIELD_DEFINITION(GLMapView_class, sceneModelVersion, "I");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, scene, "Lcom/atakmap/map/MapSceneModel;");
        SET_FIELD_DEFINITION(GLMapView_class, oscene, "Lcom/atakmap/map/MapSceneModel;");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, sceneModelForwardMatrix, "[F");
        SET_FIELD_DEFINITION(GLMapView_class, hardwareTransformResolutionThreshold, "D");
        SET_FIELD_DEFINITION(GLMapView_class, elevationScaleFactor, "D");
        SET_FIELD_DEFINITION(GLMapView_class, terrainBlendEnabled, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, terrainBlendFactor, "D");
        SET_FIELD_DEFINITION(GLMapView_class, continuousScrollEnabled, "Z");
        SET_FIELD_DEFINITION(GLMapView_class, currentPass, "Lcom/atakmap/map/opengl/GLMapView$State;");
        SET_FIELD_DEFINITION(GLMapView_class, currentScene, "Lcom/atakmap/map/opengl/GLMapView$State;");
        SET_FIELD_DEFINITION(GLMapView_class.State_class, relativeScaleHint, "F");
#undef SET_FIELD_DEFINITION

        GLMapView_class.dispatchCameraChanged = env.GetMethodID(GLMapView_class.id, "dispatchCameraChanged", "()V");

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
