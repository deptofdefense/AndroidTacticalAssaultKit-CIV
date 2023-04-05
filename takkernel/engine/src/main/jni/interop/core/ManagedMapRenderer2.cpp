#include "interop/core/ManagedMapRenderer2.h"

#include "interop/core/Interop.h"

using namespace TAKEngineJNI::Interop::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

namespace {
    struct {
        jclass id;
        jmethodID getRenderContext;
        jmethodID lookAt__GeoPoint__GeoPoint_Z;
        jmethodID lookAt__GeoPoint_DDDZ;
        jmethodID lookFrom;
        jmethodID isAnimating;
        jmethodID getMapSceneModel;
        jmethodID getDisplayMode;
        jmethodID setDisplayMode;
        jmethodID setFocusPointOffset;
        jmethodID getFocusPointOffsetX;
        jmethodID getFocusPointOffsetY;
        jmethodID getDisplayOrigin;
        jmethodID addOnCameraChangedListener;
        jmethodID removeOnCameraChangedListener;
        jmethodID forward;
        jmethodID inverse;
        jmethodID setElevationExaggerationFactor;
        jmethodID getElevationExaggerationFactor;
        jmethodID getControl;
    } MapRenderer3_class;

    bool MapRenderer3_init(JNIEnv &env) NOTHROWS;
}

ManagedMapRenderer3::ManagedMapRenderer3(JNIEnv &env, jobject impl_) NOTHROWS :
    impl(env.NewGlobalRef(impl_))
{
    static bool clinit = MapRenderer3_init(env);
}
ManagedMapRenderer3::~ManagedMapRenderer3() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}

TAKErr ManagedMapRenderer3::registerControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::unregisterControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::visitControls(bool *visited, void *opaque, TAK::Engine::Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer, const char *type) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::visitControls(bool *visited, void *opaque, TAK::Engine::Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::visitControls(void *opaque, TAK::Engine::Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl)) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::addOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::removeOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS
{
    return TE_Unsupported;
}
RenderContext &ManagedMapRenderer3::getRenderContext() const NOTHROWS
{
    RenderContext *ctx = nullptr;
    return *ctx;
}
TAKErr ManagedMapRenderer3::lookAt(const TAK::Engine::Core::GeoPoint2 &from, const TAK::Engine::Core::GeoPoint2 &at, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::lookAt(const TAK::Engine::Core::GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::lookFrom(const TAK::Engine::Core::GeoPoint2 &from, const double azimuth, const double elevation, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS
{
    return TE_Unsupported;
}
bool ManagedMapRenderer3::isAnimating() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, MapRenderer3_class.isAnimating);
}
TAKErr ManagedMapRenderer3::setDisplayMode(const TAK::Engine::Core::MapRenderer::DisplayMode cmode) NOTHROWS
{
    TAKErr code(TE_Ok);
    LocalJNIEnv env;
    TAKEngineJNI::Interop::Java::JNILocalRef mmode(*env, nullptr);
    code = TAKEngineJNI::Interop::Core::Interop_marshal(mmode, *env, cmode);
    TE_CHECKRETURN_CODE(code);
    env->CallVoidMethod(impl, MapRenderer3_class.setDisplayMode, mmode.get());
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_Err;
    }
    return code;
}
MapRenderer::DisplayMode ManagedMapRenderer3::getDisplayMode() const NOTHROWS
{
    TAKErr code(TE_Ok);
    LocalJNIEnv env;
    TAKEngineJNI::Interop::Java::JNILocalRef mmode(*env, env->CallObjectMethod(impl, MapRenderer3_class.getDisplayMode));
    MapRenderer::DisplayMode cmode;
    return TAKEngineJNI::Interop::Core::Interop_marshal(&cmode, *env, mmode) ? cmode : MapRenderer::DisplayMode::Globe;
}
MapRenderer::DisplayOrigin ManagedMapRenderer3::getDisplayOrigin() const NOTHROWS
{
    TAKErr code(TE_Ok);
    LocalJNIEnv env;
    TAKEngineJNI::Interop::Java::JNILocalRef morigin(*env, env->CallObjectMethod(impl, MapRenderer3_class.getDisplayOrigin));
    MapRenderer::DisplayOrigin corigin;
    return TAKEngineJNI::Interop::Core::Interop_marshal(&corigin, *env, morigin) ? corigin : MapRenderer::DisplayOrigin::LowerLeft;
}
TAKErr ManagedMapRenderer3::setFocusPoint(const float focusx, const float focusy) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::getFocusPoint(float *focusx, float *focusy) const NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::inverse(TAK::Engine::Core::MapRenderer::InverseResult *result, TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapRenderer::InverseMode mode, const unsigned int hints, const TAK::Engine::Math::Point2<double> &screen, const TAK::Engine::Core::MapRenderer::DisplayOrigin) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::setSurfaceSize(const std::size_t width, const std::size_t height) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::getMapSceneModel(TAK::Engine::Core::MapSceneModel2 *value, const bool instant, const DisplayOrigin origin) const NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::addOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::removeOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::addOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr ManagedMapRenderer3::removeOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS
{
    return TE_Unsupported;
}

namespace {
    bool MapRenderer3_init(JNIEnv &env) NOTHROWS {
        MapRenderer3_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/MapRenderer3");
        MapRenderer3_class.getRenderContext = env.GetMethodID(MapRenderer3_class.id, "getRenderContext", "()Lcom/atakmap/map/RenderContext;");
        MapRenderer3_class.lookAt__GeoPoint__GeoPoint_Z = env.GetMethodID(MapRenderer3_class.id, "lookAt", "(Lcom/atakmap/coremap/coords/GeoPoint;Lcom/atakmap/coremap/coords/GeoPoint;Lgov/tak/api/map/IMapRendererEnums$CameraCollision;Z)Z");
        MapRenderer3_class.lookAt__GeoPoint_DDDZ = env.GetMethodID(MapRenderer3_class.id, "lookAt", "(Lcom/atakmap/coremap/coords/GeoPoint;DDDLgov/tak/api/map/IMapRendererEnums$CameraCollision;Z)Z");
        MapRenderer3_class.lookFrom = env.GetMethodID(MapRenderer3_class.id, "lookFrom", "(Lcom/atakmap/coremap/coords/GeoPoint;DDLgov/tak/api/map/IMapRendererEnums$CameraCollision;Z)Z");
        MapRenderer3_class.isAnimating = env.GetMethodID(MapRenderer3_class.id, "isAnimating", "()Z");
        MapRenderer3_class.getMapSceneModel = env.GetMethodID(MapRenderer3_class.id, "getMapSceneModel", "(ZLgov/tak/api/map/IMapRendererEnums$DisplayOrigin;)Lcom/atakmap/map/MapSceneModel;");
        MapRenderer3_class.getDisplayMode = env.GetMethodID(MapRenderer3_class.id, "getDisplayMode", "()Lgov/tak/api/map/IMapRendererEnums$DisplayMode;");
        MapRenderer3_class.setDisplayMode = env.GetMethodID(MapRenderer3_class.id, "setDisplayMode", "(Lgov/tak/api/map/IMapRendererEnums$DisplayMode;)V");
        MapRenderer3_class.setFocusPointOffset = env.GetMethodID(MapRenderer3_class.id, "isAnimating", "(FF)V");
        MapRenderer3_class.getFocusPointOffsetX = env.GetMethodID(MapRenderer3_class.id, "getFocusPointX", "()F");
        MapRenderer3_class.getFocusPointOffsetY = env.GetMethodID(MapRenderer3_class.id, "getFocusPointY", "()F");
        MapRenderer3_class.getDisplayOrigin = env.GetMethodID(MapRenderer3_class.id, "getDisplayOrigin", "()Lgov/tak/api/map/IMapRendererEnums$DisplayOrigin;");
        MapRenderer3_class.addOnCameraChangedListener = env.GetMethodID(MapRenderer3_class.id, "addOnCameraChangedListener", "(Lcom/atakmap/map/MapRenderer3$OnCameraChangedListener)V");
        MapRenderer3_class.removeOnCameraChangedListener = env.GetMethodID(MapRenderer3_class.id, "removeOnCameraChangedListener", "(Lcom/atakmap/map/MapRenderer3$OnCameraChangedListener)V");
        MapRenderer3_class.forward = env.GetMethodID(MapRenderer3_class.id, "forward", "(Lcom/atakmap/coremap/coords/GeoPoint;Lcom/atakmap/math/PointD;Lgov/tak/api/map/IMapRendererEnums$DisplayOrigin;)Z");
        MapRenderer3_class.inverse = env.GetMethodID(MapRenderer3_class.id, "inverse", "(Lcom/atakmap/math/PointD;Lcom/atakmap/coremap/coords/GeoPoint;Lgov/tak/api/map/IMapRendererEnums$InverseMode;ILgov/tak/api/map/IMapRendererEnums$DisplayOrigin;)Lgov/tak/api/map/IMapRendererEnums$InverseResult;");
        MapRenderer3_class.setElevationExaggerationFactor = env.GetMethodID(MapRenderer3_class.id, "setElevationExaggerationFactor", "(D)V");
        MapRenderer3_class.getElevationExaggerationFactor = env.GetMethodID(MapRenderer3_class.id, "getElevationExaggerationFactor", "()D");
        MapRenderer3_class.getControl = env.GetMethodID(MapRenderer3_class.id, "getControl", "(Ljava/lang/Class;)Ljava/lang/Object;");

        return true;
    }
}