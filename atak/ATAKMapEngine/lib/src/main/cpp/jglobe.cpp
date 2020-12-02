#include "jglobe.h"

#include <core/AtakMapController.h>
#include <core/AtakMapView.h>
#include <core/GeoPoint2.h>
#include <core/LegacyAdapters.h>
#include <core/MapSceneModel2.h>
#include <util/Error.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNILongArray.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{

    class CallbackForwarder : public atakmap::core::AtakMapView::MapMovedListener,
                              public atakmap::core::AtakMapView::MapResizedListener,
                              public atakmap::core::AtakMapView::MapProjectionChangedListener,
                              public atakmap::core::AtakMapView::MapElevationExaggerationFactorListener,
                              public atakmap::core::AtakMapView::MapLayersChangedListener,
                              public atakmap::core::AtakMapView::MapContinuousScrollListener,
                              public atakmap::core::MapControllerFocusPointChangedListener
    {
    public :
        CallbackForwarder(JNIEnv *env, jobject impl) NOTHROWS;
        ~CallbackForwarder() NOTHROWS;
    public :
        virtual void mapMoved(atakmap::core::AtakMapView *view, const bool animate);
        virtual void mapResized(atakmap::core::AtakMapView *view);
        virtual void mapProjectionChanged(atakmap::core::AtakMapView *view);
        virtual void mapElevationExaggerationFactorChanged(atakmap::core::AtakMapView *view, const double factor);
        virtual void mapLayerAdded(atakmap::core::AtakMapView *view, atakmap::core::Layer *layer);
        virtual void mapLayerRemoved(atakmap::core::AtakMapView *view, atakmap::core::Layer *layer);
        virtual void mapLayerPositionChanged(atakmap::core::AtakMapView *view, atakmap::core::Layer *layer, const int oldPos, const int newPos);
        virtual void mapContinuousScrollEnabledChanged(atakmap::core::AtakMapView *view, const bool enabled);
        virtual void mapControllerFocusPointChanged(atakmap::core::AtakMapController *controller, const atakmap::math::Point<float> * const focus);
    public :
        jobject impl;
    };

    struct
    {
        jclass id;
        jmethodID onLayerAdded;
        jmethodID onLayerRemoved;
        jmethodID onLayerPositionChanged;
    } Globe_OnLayersChangedListener_class;

    struct
    {
        jclass id;
        jmethodID onTerrainExaggerationFactorChanged;
    } Globe_OnElevationExaggerationFactorChangedListener_class;

    struct
    {
        jclass id;
        jmethodID onContinuousScrollEnabledChanged;
    } Globe_OnContinuousScrollEnabledChangedListener_class;

    struct
    {
        jclass id;
        jmethodID onMapViewResized;
    } Globe_OnMapViewResizedListener_class;

    struct
    {
        jclass id;
        jmethodID onMapMoved;
    } Globe_OnMapMovedListener_class;

    struct
    {
        jclass id;
        jmethodID onMapProjectionChanged;
    } Globe_OnMapProjectionChangedListener_class;

    struct
    {
        jclass id;
        jmethodID onFocusPointChanged;
    } Globe_OnFocusPointChangedListener_class;

    bool checkInit(JNIEnv *env) NOTHROWS;
    bool Globe_class_init(JNIEnv *env) NOTHROWS;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_Globe_create
  (JNIEnv *env, jclass jclazz, jint width, jint height, jdouble displayDpi, jdouble minScale, jdouble maxScale)
{
    std::unique_ptr<atakmap::core::AtakMapView, void(*)(const atakmap::core::AtakMapView *)> retval(new(std::nothrow) atakmap::core::AtakMapView(width, height, displayDpi), Memory_deleter_const<atakmap::core::AtakMapView>);
    if(retval.get()) {
        retval->setMinMapScale(minScale);
        retval->setMaxMapScale(maxScale);
    }
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_destruct
  (JNIEnv *env, jclass jclazz, jobject mpointer)
{
    Pointer_destruct<atakmap::core::AtakMapView>(env, mpointer);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getFullEquitorialExtentPixels
  (JNIEnv *env, jclass jclazz, jdouble dpi)
{
    return atakmap::core::AtakMapView_getFullEquitorialExtentPixels(dpi);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getDisplayDpi
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getDisplayDpi();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_Globe_updateView
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble latitude, jdouble longitude, jdouble scale, jdouble rotation, jdouble tilt, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    view->updateView(atakmap::core::GeoPoint(latitude, longitude), scale, rotation, tilt, NAN, NAN, animate);
    return true;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_Globe_getProjection
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }
    return view->getProjection();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_Globe_setProjection
  (JNIEnv *env, jclass jclazz, jlong ptr, jint srid)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }
    view->setProjection(srid);
    return view->getProjection();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMaxMapScale
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMaxMapScale();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMinMapScale
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMinMapScale();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMapScaleImpl
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMapScale();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMapResolutionImpl__J
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMapResolution();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMapResolutionImpl__JD
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble scale)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMapResolution(scale);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_mapResolutionAsMapScale
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble resolution)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->mapResolutionAsMapScale(resolution);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_setElevationExaggerationFactor
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble factor)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    view->setElevationExaggerationFactor(factor);
    return view->getElevationExaggerationFactor();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getElevationExaggerationFactor
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getElevationExaggerationFactor();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_Globe_forward
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble latitude, jdouble longitude, jdouble alt, jboolean altIsHae, jobject mresult)
{
    return false;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMinLatitude
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMinLatitude();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMaxLatitude
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMaxLatitude();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMinLongitude
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMinLongitude();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMaxLongitude
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMaxLongitude();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_Globe_isAnimating
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    return view->isAnimating();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMapRotation
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMapRotation();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMapTilt
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return view->getMapTilt();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_Globe_getPoint
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    atakmap::core::GeoPoint cretval;
    view->getPoint(&cretval);
    TAK::Engine::Core::GeoPoint2 cretval2;
    atakmap::core::GeoPoint_adapt(&cretval2, cretval);
    return TAKEngineJNI::Interop::Core::Interop_create(env, cretval2);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_setMaxMapTilt
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble maxTilt)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    view->setMaxMapTilt(maxTilt);
    return view->getMaxMapTilt(0.0);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_setContinuousScrollEnabled
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean enabled)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    view->setContinuousScrollEnabled(enabled);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_Globe_isContinuousScrollEnabled
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    return view->isContinuousScrollEnabled();
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_Globe_getController
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    return INTPTR_TO_JLONG(view->getController());
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_setFocusPointOffset
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat x, jfloat y)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    view->setFocusPointOffset(x, y);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_setSize
  (JNIEnv *env, jclass clazz, jlong ptr, jint width, jint height)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    view->setSize(width, height);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_Globe_getWidth
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return view->getHeight();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_Globe_getHeight
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return view->getWidth();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_Globe_createSceneModel
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    atakmap::math::Point<float> focus;
    view->getController()->getFocusPoint(&focus);
    atakmap::core::GeoPoint geolegacy;
    view->getPoint(&geolegacy);
    GeoPoint2 geo;
    GeoPoint_adapt(&geo, geolegacy);
    MapSceneModel2Ptr retval(new MapSceneModel2(view->getDisplayDpi(),
                                                view->getWidth(),
                                                view->getHeight(),
                                                view->getProjection(),
                                                geo,
                                                focus.x, focus.y,
                                                view->getMapRotation(),
                                                view->getMapTilt(),
                                                view->getMapResolution()),
                             Memory_deleter_const<MapSceneModel2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_addLayer__JJ
  (JNIEnv *env, jclass jclazz, jlong ptr, jlong clayerPtr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, clayerPtr);
    view->addLayer(clayer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_addLayer__JIJ
  (JNIEnv *env, jclass jclazz, jlong ptr, jint pos, jlong clayerPtr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, clayerPtr);
    view->addLayer(pos, clayer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_setLayerPosition
  (JNIEnv *env, jclass jclazz, jlong ptr, jlong clayerPtr, jint pos)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, clayerPtr);
    view->setLayerPosition(clayer, pos);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_removeLayer
  (JNIEnv *env, jclass jclazz, jlong ptr, jlong clayerPtr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::Layer *clayer = JLONG_TO_INTPTR(atakmap::core::Layer, clayerPtr);
    view->removeLayer(clayer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_removeAllLayers
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    view->removeAllLayers();
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_Globe_getLayer
  (JNIEnv *env, jclass jclazz, jlong ptr, jint position)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    return INTPTR_TO_JLONG(view->getLayer(position));
}
JNIEXPORT jlongArray JNICALL Java_com_atakmap_map_Globe_getLayers
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    std::list<atakmap::core::Layer *> layers = view->getLayers();
    jlongArray mretval = env->NewLongArray(layers.size());
    JNILongArray retval(*env, mretval, 0);
    std::size_t idx = 0u;
    for(auto it = layers.begin(); it != layers.end(); it++)
        retval[idx++] = INTPTR_TO_JLONG(*it);
    return mretval;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_Globe_getNumLayers
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return view->getNumLayers();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_Globe_registerNativeCallbackForwarder
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mcbforwarder)
{
    atakmap::core::AtakMapView *mapView = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!mapView) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    using namespace atakmap::core;
    std::unique_ptr<CallbackForwarder, void(*)(const CallbackForwarder *)> clistener(new CallbackForwarder(env, mcbforwarder), Memory_deleter_const<CallbackForwarder>);
    mapView->addMapMovedListener(clistener.get());
    mapView->addMapResizedListener(clistener.get());
    mapView->addLayersChangedListener(clistener.get());
    mapView->addMapProjectionChangedListener(clistener.get());
    mapView->addMapElevationExaggerationFactorListener(clistener.get());
    mapView->addMapContinuousScrollListener(clistener.get());
    mapView->getController()->addFocusPointChangedListener(clistener.get());
    return NewPointer(env, std::move(clistener));

}

JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_unregisterNativeCallbackForwarder
  (JNIEnv *env, jclass clazz, jlong ptr, jobject mchforwarderPointer)
{
    atakmap::core::AtakMapView *mapView = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    CallbackForwarder *cb = Pointer_get<CallbackForwarder>(env, mchforwarderPointer);
    if(mapView) {
        mapView->removeMapMovedListener(cb);
        mapView->removeMapResizedListener(cb);
        mapView->removeLayersChangedListener(cb);
        mapView->removeMapProjectionChangedListener(cb);
        mapView->removeMapElevationExaggerationFactorListener(cb);
        mapView->removeMapContinuousScrollListener(cb);
        mapView->getController()->removeFocusPointChangedListener(cb);
    }
    Pointer_destruct_iface<CallbackForwarder>(env, mchforwarderPointer);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMapResolution
  (JNIEnv *env, jclass clazz, jdouble dpi, jdouble scale)
{
    return atakmap::core::AtakMapView_getMapResolution(dpi, scale);
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_Globe_getMapScale
  (JNIEnv *env, jclass clazz, jdouble dpi, jdouble resolution)
{
    return atakmap::core::AtakMapView_getMapScale(dpi, resolution);
}

//static native void panTo(long ptr, double lat, double lng, double alt, boolean hae, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_panTo__JDDDZZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jboolean hae, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::core::GeoPoint focus(lat, lng, alt, hae ? atakmap::core::AltitudeReference::HAE : atakmap::core::AltitudeReference::AGL);
    ctrl->panTo(&focus, animate);
}
//static native void panZoomTo(long ptr, double lat, double lng, double alt, boolean hae, double scale, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_panZoomTo
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jboolean hae, jdouble scale, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::core::GeoPoint focus(lat, lng, alt, hae ? atakmap::core::AltitudeReference::HAE : atakmap::core::AltitudeReference::AGL);
    ctrl->panZoomTo(&focus, scale, animate);
}
//static native void panZoomRotateTo(long ptr, double lat, double lng, double alt, boolean hae, double scale, double rotation, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_panZoomRotateTo
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jboolean hae, jdouble scale, jdouble rotation, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::core::GeoPoint focus(lat, lng, alt, hae ? atakmap::core::AltitudeReference::HAE : atakmap::core::AltitudeReference::AGL);
    ctrl->panZoomRotateTo(&focus, scale, rotation, animate);
}
//static native void panTo(long ptr, double lat, double lng, double alt, boolean hae, float viewx, float viewy, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_panTo__JDDDZFFZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble lat, jdouble lng, jdouble alt, jboolean hae, jfloat viewx, jfloat viewy, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::core::GeoPoint focus(lat, lng, alt, hae ? atakmap::core::AltitudeReference::HAE : atakmap::core::AltitudeReference::AGL);
    ctrl->panTo(&focus, viewx, viewy, animate);
}
//static native void panByAtScale(long ptr, float x, float y, double scale, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_panByAtScale
  (JNIEnv *env, jclass jclazz, jlong ptr, jfloat x, jfloat y, jdouble scale, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->panByAtScale(x, y, scale, animate);
}
//static native void panBy(long ptr, float x, float y, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_panBy
  (JNIEnv *env, jclass jclazz, jlong ptr, jfloat x, jfloat y, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->panBy(x, y, animate);
}
//static native void panByScaleRotate(long ptr, float x, float y, double scale, double rotate, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_panByScaleRotate
  (JNIEnv *env, jclass jclazz, jlong ptr, jfloat x, jfloat y, jdouble scale, jdouble rotate, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->panByScaleRotate(x, y, scale, rotate, animate);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_zoomTo
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble scale, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->zoomTo(scale, animate);
}
//static native void zoomBy(long ptr, double scale, float x, float y, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_zoomBy
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble scale, jfloat x, jfloat y, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->zoomBy(scale, x, y, animate);
}
//static native void rotateTo(long ptr, double rotation, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_rotateTo
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble rotation, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->rotateTo(rotation, animate);
}
//static native void rotateBy(long ptr, double theta, float xpos, float ypos, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_rotateBy__JDFFZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble theta, jfloat xpos, jfloat ypos, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->rotateBy(theta, xpos, ypos, animate);
}
//static native void tiltTo(long ptr, double tilt, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_tiltTo__JDZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble tilt, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->tiltTo(tilt, animate);
}
//static native void tiltTo(long ptr, double tilt, double rotation, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_tiltTo__JDDZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble tilt, jdouble rotation, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->tiltTo(tilt, rotation, animate);
}
//static native void tiltBy(long ptr, double tilt, float xpos, float ypos, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_tiltBy__JDFFZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble tilt, jfloat xpos, jfloat ypos, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->tiltBy(tilt, xpos, ypos, animate);
}
//static native void tiltBy(long ptr, double tilt, double latitude, double longitude, double alt, boolean hae, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_tiltBy__JDDDDZZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble tilt, jdouble lat, jdouble lng, jdouble alt, jboolean hae, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::core::GeoPoint focus(lat, lng, alt, hae ? atakmap::core::AltitudeReference::HAE : atakmap::core::AltitudeReference::AGL);
    ctrl->tiltBy(tilt, focus, animate);
}
//static native void rotateBy(long ptr, double theta, double latitude, double longitude, double alt, boolean hae, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_rotateBy__JDDDDZZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble theta, jdouble lat, jdouble lng, jdouble alt, jboolean hae, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::core::GeoPoint focus(lat, lng, alt, hae ? atakmap::core::AltitudeReference::HAE : atakmap::core::AltitudeReference::AGL);
    ctrl->rotateBy(theta, focus, animate);
}
//static native void updateBy(long ptr, double scale, double rotation, double tilt, float xpos, float ypos, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_updateBy__JDDDFFZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble scale, jdouble rotation, jdouble tilt, jfloat xpos, jfloat ypos, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    ctrl->updateBy(scale, rotation, tilt, xpos, ypos, animate);
}
//static native void updateBy(long ptr, double scale, double rotation, double tilt, double lat, double lng, double alt, boolean hae, boolean animate);
JNIEXPORT void JNICALL Java_com_atakmap_map_Globe_updateBy__JDDDDDDZZ
  (JNIEnv *env, jclass jclazz, jlong ptr, jdouble scale, jdouble rotation, jdouble tilt, jdouble lat, jdouble lng, jdouble alt, jboolean hae, jboolean animate)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::core::GeoPoint focus(lat, lng, alt, hae ? atakmap::core::AltitudeReference::HAE : atakmap::core::AltitudeReference::AGL);
    ctrl->updateBy(scale, rotation, tilt, focus, animate);
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_Globe_getFocusPointX
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();
    atakmap::math::Point<float> cfocus;
    ctrl->getFocusPoint(&cfocus);
    return cfocus.x;
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_Globe_getFocusPointY
  (JNIEnv *env, jclass jclazz, jlong ptr)
{
    atakmap::core::AtakMapView *view = JLONG_TO_INTPTR(atakmap::core::AtakMapView, ptr);
    if(!view) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    atakmap::core::AtakMapController *ctrl = view->getController();

    atakmap::math::Point<float> cfocus;
    ctrl->getFocusPoint(&cfocus);
    return cfocus.y;
}

namespace
{
    CallbackForwarder::CallbackForwarder(JNIEnv *env_, jobject impl_) NOTHROWS :
        impl(env_->NewGlobalRef(impl_))
    {}
    CallbackForwarder::~CallbackForwarder() NOTHROWS
    {
        LocalJNIEnv env;
        if(impl) {
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
    void CallbackForwarder::mapMoved(atakmap::core::AtakMapView *view, const bool animate)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        env->CallVoidMethod(impl, Globe_OnMapMovedListener_class.onMapMoved, NULL, animate);
    }
    void CallbackForwarder::mapResized(atakmap::core::AtakMapView *view)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        env->CallVoidMethod(impl, Globe_OnMapViewResizedListener_class.onMapViewResized, NULL);
    }
    void CallbackForwarder::mapProjectionChanged(atakmap::core::AtakMapView *view)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        env->CallVoidMethod(impl, Globe_OnMapProjectionChangedListener_class.onMapProjectionChanged, NULL);
    }
    void CallbackForwarder::mapElevationExaggerationFactorChanged(atakmap::core::AtakMapView *view, const double factor)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        env->CallVoidMethod(impl, Globe_OnElevationExaggerationFactorChangedListener_class.onTerrainExaggerationFactorChanged, NULL, factor);
    }
    void CallbackForwarder::mapLayerAdded(atakmap::core::AtakMapView *view, atakmap::core::Layer *layer)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        JNILocalRef mlayer(*env, NULL);
        Core::Interop_marshal(mlayer, *env, *layer);
        env->CallVoidMethod(impl, Globe_OnLayersChangedListener_class.onLayerAdded, NULL, mlayer.get());
    }
    void CallbackForwarder::mapLayerRemoved(atakmap::core::AtakMapView *view, atakmap::core::Layer *layer)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        JNILocalRef mlayer(*env, NULL);
        Core::Interop_marshal(mlayer, *env, *layer);
        env->CallVoidMethod(impl, Globe_OnLayersChangedListener_class.onLayerRemoved, NULL, mlayer.get());
    }
    void CallbackForwarder::mapLayerPositionChanged(atakmap::core::AtakMapView *view, atakmap::core::Layer *layer, const int oldPos, const int newPos)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        JNILocalRef mlayer(*env, NULL);
        Core::Interop_marshal(mlayer, *env, *layer);
        env->CallVoidMethod(impl, Globe_OnLayersChangedListener_class.onLayerPositionChanged, NULL, mlayer.get(), oldPos, newPos);
    }
    void CallbackForwarder::mapContinuousScrollEnabledChanged(atakmap::core::AtakMapView *view, const bool enabled)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        env->CallVoidMethod(impl, Globe_OnContinuousScrollEnabledChangedListener_class.onContinuousScrollEnabledChanged, NULL, enabled);
    }
    void CallbackForwarder::mapControllerFocusPointChanged(atakmap::core::AtakMapController *controller, const atakmap::math::Point<float> * const focus)
    {
        LocalJNIEnv env;
        if(!checkInit(env))
            return;
        env->CallVoidMethod(impl, Globe_OnFocusPointChangedListener_class.onFocusPointChanged, NULL, focus->x, focus->y);
    }

    bool checkInit(JNIEnv *env) NOTHROWS
    {
        static bool clinit = Globe_class_init(env);
        return clinit;
    }
    bool Globe_class_init(JNIEnv *env) NOTHROWS
    {
#define SET_METHOD_DEFINITION(c, m, sig) \
    c.m = env->GetMethodID(c.id, #m, sig)

        Globe_OnLayersChangedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/Globe$OnLayersChangedListener");
        SET_METHOD_DEFINITION(Globe_OnLayersChangedListener_class, onLayerAdded, "(Lcom/atakmap/map/Globe;Lcom/atakmap/map/layer/Layer;)V");
        SET_METHOD_DEFINITION(Globe_OnLayersChangedListener_class, onLayerRemoved,"(Lcom/atakmap/map/Globe;Lcom/atakmap/map/layer/Layer;)V");
        SET_METHOD_DEFINITION(Globe_OnLayersChangedListener_class, onLayerPositionChanged, "(Lcom/atakmap/map/Globe;Lcom/atakmap/map/layer/Layer;II)V");

        Globe_OnElevationExaggerationFactorChangedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/Globe$OnElevationExaggerationFactorChangedListener");
        SET_METHOD_DEFINITION(Globe_OnElevationExaggerationFactorChangedListener_class, onTerrainExaggerationFactorChanged, "(Lcom/atakmap/map/Globe;D)V");

        Globe_OnContinuousScrollEnabledChangedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/Globe$OnContinuousScrollEnabledChangedListener");
        SET_METHOD_DEFINITION(Globe_OnContinuousScrollEnabledChangedListener_class, onContinuousScrollEnabledChanged, "(Lcom/atakmap/map/Globe;Z)V");

        Globe_OnMapViewResizedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/Globe$OnMapViewResizedListener");
        SET_METHOD_DEFINITION(Globe_OnMapViewResizedListener_class, onMapViewResized, "(Lcom/atakmap/map/Globe;)V");

        Globe_OnMapMovedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/Globe$OnMapMovedListener");
        SET_METHOD_DEFINITION(Globe_OnMapMovedListener_class, onMapMoved, "(Lcom/atakmap/map/Globe;Z)V");

        Globe_OnMapProjectionChangedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/Globe$OnMapProjectionChangedListener");
        SET_METHOD_DEFINITION(Globe_OnMapProjectionChangedListener_class, onMapProjectionChanged, "(Lcom/atakmap/map/Globe;)V");

        Globe_OnFocusPointChangedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/Globe$OnFocusPointChangedListener");
        SET_METHOD_DEFINITION(Globe_OnFocusPointChangedListener_class, onFocusPointChanged, "(Lcom/atakmap/map/Globe;FF)V");

        return true;
    }
}
