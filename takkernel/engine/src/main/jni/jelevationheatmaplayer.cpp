#include "jelevationheatmaplayer.h"

#include <core/Layer.h>
#include <core/LegacyAdapters.h>
#include <elevation/ElevationHeatMapLayer.h>
#include <elevation/ElevationHeatMapLayer.h>
#include <port/String.h>
#include <renderer/core/GLLayerFactory2.h>
#include <renderer/core/GLLayerSpi2.h>
#include <renderer/elevation/GLElevationHeatMapLayer.h>
#include <renderer/elevation/GLElevationHeatMapLayer.h>
#include <util/Error.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/core/Interop.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    bool registerSpi() NOTHROWS;
    ElevationHeatMapLayer *getLayer(const atakmap::core::Layer &layer) NOTHROWS;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_newInstance
  (JNIEnv *env, jclass clazz, jstring mname)
{
    static bool clinit = registerSpi();

    TAKErr code(TE_Ok);

    TAK::Engine::Port::String cname;
    code = JNIStringUTF_get(cname, *env, mname);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    Layer2Ptr cretval(new(std::nothrow) ElevationHeatMapLayer(cname), Memory_deleter_const<Layer2, ElevationHeatMapLayer>);
    if(!cretval) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_OutOfMemory);
        return NULL;
    }

    std::shared_ptr<atakmap::core::Layer> clayer;
    code = LegacyAdapters_adapt(clayer, std::move(cretval));

    Java::JNILocalRef mretval(*env, NULL);
    code = Core::Interop_marshal(mretval, *env, clayer);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return mretval.release();
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_getAlpha
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.f;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.f;
    }
    return impl->getAlpha();
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_getSaturation
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.f;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.f;
    }
    return impl->getSaturation();
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_getValue
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.f;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.f;
    }
    return impl->getValue();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_setAlpha
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat alpha)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    impl->setAlpha(alpha);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_setSaturation
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat saturation)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    impl->setSaturation(saturation);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_setValue
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat value)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    impl->setValue(value);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_getRangeMinElevation
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }
    double min = NAN;
    double max = NAN;
    impl->getAbsoluteRange(&min, &max);
    return min;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_getRangeMaxElevation
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }
    double min = NAN;
    double max = NAN;
    impl->getAbsoluteRange(&min, &max);
    return max;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_setAbsoluteRange
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble min, jdouble max)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    impl->setAbsoluteRange(min, max);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_setDynamicRange
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    impl->setDynamicRange();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_elevation_ElevationHeatmapLayer_isDynamicRange
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    auto impl = getLayer(*JLONG_TO_INTPTR(atakmap::core::Layer, ptr));
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    return impl->isDynamicRange();
}

namespace
{
    bool registerSpi() NOTHROWS
    {
        class SpiImpl : public GLLayerSpi2
        {
        public :
            TAKErr create(GLLayer2Ptr &value, GLGlobeBase &renderer, Layer2 &subject) NOTHROWS override
            {
                ElevationHeatMapLayer *impl = dynamic_cast<ElevationHeatMapLayer *>(&subject);
                if(!impl)
                    return TE_InvalidArg;
                value = GLLayer2Ptr(new(std::nothrow) GLElevationHeatMapLayer(*impl), Memory_deleter_const<GLLayer2, GLElevationHeatMapLayer>);
                if(!value)
                    return TE_OutOfMemory;
                return TE_Ok;
            }
        };
        GLLayerSpi2Ptr spi(new SpiImpl(), Memory_deleter_const<GLLayerSpi2, SpiImpl>);
        GLLayerFactory2_registerSpi(std::move(spi), 1);
        return true;
    }

    ElevationHeatMapLayer *getLayer(const atakmap::core::Layer &layer) NOTHROWS
    {
        std::shared_ptr<Layer2> impl;
        LegacyAdapters_find(impl, layer);
        return impl ? static_cast<ElevationHeatMapLayer *>(impl.get()) : nullptr;
    }
}
