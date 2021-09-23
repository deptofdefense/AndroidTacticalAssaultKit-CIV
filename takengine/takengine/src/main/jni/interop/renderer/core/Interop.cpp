#include "interop/renderer/core/Interop.h"

#include "jglmapview.h"

#include "interop/renderer/core/ManagedGLLayer2.h"
#include "interop/renderer/core/ManagedGLLayerSpi2.h"

#include "interop/InterfaceMarshalContext.h"
#include "interop/ImplementationMarshalContext.h"

using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } NativeGLMapRenderable2_class;

    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } NativeGLLayer3_class;

    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } NativeGLLayerSpi2_class;

    struct
    {
        jclass id;
        jfieldID pointer;
    } GLMapView_class;

    InterfaceMarshalContext<GLMapRenderable2> GLMapRenderable2_interop;
    InterfaceMarshalContext<GLLayer2> GLLayer2_interop;
    InterfaceMarshalContext<GLLayerSpi2> GLLayerSpi2_interop;
    ImplementationMarshalContext<GLGlobeBase> GLGlobeBase_interop;

    // NOTE: pairs of C++ GLMapView2::RenderPass, Java GLMapView.RENDER_PASS_XXX
    // array is length 64 as there is a maximum of 32 bit-mask values
    unsigned int renderPassMapping[64u];

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool Interop_class_init(JNIEnv &env) NOTHROWS;
}

// GLMapView interop
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(std::shared_ptr<TAK::Engine::Renderer::Core::GLGlobeBase> &value, JNIEnv &env, jobject mview) NOTHROWS
{
    return GLGlobeBase_interop.marshal(value, env, mview);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Renderer::Core::GLGlobeBase &cview) NOTHROWS
{
    return GLGlobeBase_interop.marshal(value, env, cview);
}

// GLMapView::RenderPass interop
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(GLMapView2::RenderPass *value, const jint mpass) NOTHROWS
{
    unsigned int v = 0u;
    std::size_t idx = 0;
    while(renderPassMapping[idx] && idx < 64u) {
        if(mpass&renderPassMapping[idx+1u])
            v |= renderPassMapping[idx];
        idx += 2u;
    }
    *value = (GLMapView2::RenderPass)v;
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(jint *value, const GLMapView2::RenderPass cpass) NOTHROWS
{
    unsigned int v = 0u;
    std::size_t idx = 0;
    while(renderPassMapping[idx] && idx < 64u) {
        if(cpass&renderPassMapping[idx])
            v |= renderPassMapping[idx+1u];
        idx += 2u;
    }
    *value = v;
    return TE_Ok;
}

// GLMapRenderable2 interop
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(std::shared_ptr<GLMapRenderable2> &value, JNIEnv &env, jobject mgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLMapRenderable2_interop.marshal<ManagedGLMapRenderable2>(value, env, mgllayer);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(GLMapRenderable2Ptr &value, JNIEnv &env, jobject mgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLMapRenderable2_interop.marshal<ManagedGLMapRenderable2>(value, env, mgllayer);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<GLMapRenderable2> &cgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLMapRenderable2_interop.marshal<ManagedGLMapRenderable2>(value, env, cgllayer);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, GLMapRenderable2Ptr &&cgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLMapRenderable2_interop.marshal<ManagedGLMapRenderable2>(value, env, std::move(cgllayer));
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const GLMapRenderable2 &cgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLMapRenderable2_interop.marshal<ManagedGLMapRenderable2>(value, env, cgllayer);
}

template<>
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_isWrapper<GLMapRenderable2>(bool *value, JNIEnv &env, jobject mlayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = GLMapRenderable2_interop.isWrapper(env, mlayer);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_isWrapper(bool *value, JNIEnv &env, const GLMapRenderable2 &clayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = GLMapRenderable2_interop.isWrapper<ManagedGLMapRenderable2>(clayer);
    return TE_Ok;
}

// GLLayer2 interop
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(std::shared_ptr<GLLayer2> &value, JNIEnv &env, jobject mgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayer2_interop.marshal<ManagedGLLayer2>(value, env, mgllayer);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(GLLayer2Ptr &value, JNIEnv &env, jobject mgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayer2_interop.marshal<ManagedGLLayer2>(value, env, mgllayer);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<GLLayer2> &cgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayer2_interop.marshal<ManagedGLLayer2>(value, env, cgllayer);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, GLLayer2Ptr &&cgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayer2_interop.marshal<ManagedGLLayer2>(value, env, std::move(cgllayer));
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const GLLayer2 &cgllayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayer2_interop.marshal<ManagedGLLayer2>(value, env, cgllayer);
}

template<>
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_isWrapper<GLLayer2>(bool *value, JNIEnv &env, jobject mlayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = GLLayer2_interop.isWrapper(env, mlayer);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_isWrapper(bool *value, JNIEnv &env, const GLLayer2 &clayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = GLLayer2_interop.isWrapper<ManagedGLLayer2>(clayer);
    return TE_Ok;
}

// GLLayerSpi2 interop
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(std::shared_ptr<GLLayerSpi2> &value, JNIEnv &env, jobject mspi) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayerSpi2_interop.marshal<ManagedGLLayerSpi2>(value, env, mspi);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(GLLayerSpi2Ptr &value, JNIEnv &env, jobject mspi) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayerSpi2_interop.marshal<ManagedGLLayerSpi2>(value, env, mspi);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<GLLayerSpi2> &cspi) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayerSpi2_interop.marshal<ManagedGLLayerSpi2>(value, env, cspi);
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, GLLayerSpi2Ptr &&cspi) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayerSpi2_interop.marshal<ManagedGLLayerSpi2>(value, env, std::move(cspi));
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const GLLayerSpi2 &cspi) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return GLLayerSpi2_interop.marshal<ManagedGLLayerSpi2>(value, env, cspi);
}

template<>
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_isWrapper<GLLayerSpi2>(bool *value, JNIEnv &env, jobject mspi) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = GLLayerSpi2_interop.isWrapper(env, mspi);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Renderer::Core::Interop_isWrapper(bool *value, JNIEnv &env, const GLLayerSpi2 &cspi) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = GLLayerSpi2_interop.isWrapper<ManagedGLLayerSpi2>(cspi);
    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = Interop_class_init(env);
        return clinit;
    }
    bool Interop_class_init(JNIEnv &env) NOTHROWS
    {
        NativeGLMapRenderable2_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/opengl/NativeGLMapRenderable2");
        NativeGLMapRenderable2_class.pointer = env.GetFieldID(NativeGLMapRenderable2_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        NativeGLMapRenderable2_class.ctor = env.GetMethodID(NativeGLMapRenderable2_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");

        GLMapRenderable2_interop.init(env, NativeGLMapRenderable2_class.id, NativeGLMapRenderable2_class.pointer, NativeGLMapRenderable2_class.ctor);

        NativeGLLayer3_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/opengl/NativeGLLayer3");
        NativeGLLayer3_class.pointer = env.GetFieldID(NativeGLLayer3_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        NativeGLLayer3_class.ctor = env.GetMethodID(NativeGLLayer3_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");

        GLLayer2_interop.init(env, NativeGLLayer3_class.id, NativeGLLayer3_class.pointer, NativeGLLayer3_class.ctor);

        NativeGLLayerSpi2_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/opengl/NativeGLLayerSpi2");
        NativeGLLayerSpi2_class.pointer = env.GetFieldID(NativeGLLayerSpi2_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        NativeGLLayerSpi2_class.ctor = env.GetMethodID(NativeGLLayerSpi2_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");

        GLLayerSpi2_interop.init(env, NativeGLLayerSpi2_class.id, NativeGLLayerSpi2_class.pointer, NativeGLLayerSpi2_class.ctor);

        GLMapView_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/opengl/GLMapView");
        GLMapView_class.pointer = env.GetFieldID(GLMapView_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");

        GLGlobeBase_interop.init(env, GLMapView_class.id, GLMapView_class.pointer, NULL);

        {
            memset(renderPassMapping, 0u, sizeof(unsigned int)*64u);

            std::size_t idx = 0u;
            renderPassMapping[idx++] = GLMapView2::Surface;
            renderPassMapping[idx++] = com_atakmap_map_opengl_GLMapView_RENDER_PASS_SURFACE;
            renderPassMapping[idx++] = GLMapView2::Sprites;
            renderPassMapping[idx++] = com_atakmap_map_opengl_GLMapView_RENDER_PASS_SPRITES;
            renderPassMapping[idx++] = GLMapView2::Scenes;
            renderPassMapping[idx++] = com_atakmap_map_opengl_GLMapView_RENDER_PASS_SCENES;
            renderPassMapping[idx++] = GLMapView2::UserInterface;
            renderPassMapping[idx++] = com_atakmap_map_opengl_GLMapView_RENDER_PASS_UI;
        }

        return true;
    }
}
