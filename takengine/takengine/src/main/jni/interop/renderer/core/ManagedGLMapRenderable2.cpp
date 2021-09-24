#include "interop/renderer/core/ManagedGLMapRenderable2.h"

#include <core/LegacyAdapters.h>

#include "common.h"
#include "interop/core/Interop.h"
#include "interop/java/JNILocalRef.h"
#include "interop/renderer/core/Interop.h"

using namespace TAKEngineJNI::Interop::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer::Core;

using namespace TAKEngineJNI::Interop::Core;
using namespace TAKEngineJNI::Interop::Java;
using namespace TAKEngineJNI::Interop::Renderer::Core;

namespace
{
    struct
    {
        jclass id;
        jmethodID draw;
        jmethodID release;
        jmethodID getRenderPass;
    } GLMapRenderable2_class;

    struct
    {
        jclass id;
        jfieldID syncVersion;
        jfieldID syncPass;
        jmethodID sync;
    } GLMapView_class;

    bool ManagedGLRenderable2_class_init(JNIEnv &env) NOTHROWS;
}

ManagedGLMapRenderable2::ManagedGLMapRenderable2(JNIEnv &env_, jobject impl_, const bool requiresSync_) NOTHROWS :
    impl(env_.NewGlobalRef(impl_)),
    requiresSync(requiresSync_)
{
    static bool clinit = ManagedGLRenderable2_class_init(env_);
}
ManagedGLMapRenderable2::~ManagedGLMapRenderable2() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
void ManagedGLMapRenderable2::draw(const TAK::Engine::Renderer::Core::GLGlobeBase& cview, const int crenderPass) NOTHROWS
{
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return;

    jint mrenderPass;
    Interop_marshal(&mrenderPass, (GLMapView2::RenderPass)crenderPass);
    // obtain managed view instance
    JNILocalRef mview(*env, NULL);
    TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(mview, *env, cview);
    // if required, check state on managed and copy from native if dirty
    if(requiresSync &&
        (cview.drawVersion != env->GetIntField(mview, GLMapView_class.syncVersion) ||
         crenderPass != env->GetIntField(mview, GLMapView_class.syncPass))) {

        env->CallVoidMethod(mview, GLMapView_class.sync);
        env->SetIntField(mview, GLMapView_class.syncVersion, cview.drawVersion);
        env->SetIntField(mview, GLMapView_class.syncPass, crenderPass);
    }
    env->CallVoidMethod(impl, GLMapRenderable2_class.draw, mview.get(), mrenderPass);
}
void ManagedGLMapRenderable2::release() NOTHROWS
{
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return;
    env->CallVoidMethod(impl, GLMapRenderable2_class.release);
}
int ManagedGLMapRenderable2::getRenderPass() NOTHROWS
{
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return 0;
    const jint mrenderPass = env->CallIntMethod(impl, GLMapRenderable2_class.getRenderPass);
    GLMapView2::RenderPass crenderPass;
    Interop_marshal(&crenderPass, mrenderPass);
    return crenderPass;
}
void ManagedGLMapRenderable2::start() NOTHROWS
{}
void ManagedGLMapRenderable2::stop() NOTHROWS
{}

namespace
{
    bool ManagedGLRenderable2_class_init(JNIEnv &env) NOTHROWS
    {
#define SET_METHOD_DEFINITION(c, m, sig) \
    c.m = env.GetMethodID(c.id, #m, sig)

        GLMapRenderable2_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/opengl/GLMapRenderable2");
        SET_METHOD_DEFINITION(GLMapRenderable2_class, draw, "(Lcom/atakmap/map/opengl/GLMapView;I)V");
        SET_METHOD_DEFINITION(GLMapRenderable2_class, release, "()V");
        SET_METHOD_DEFINITION(GLMapRenderable2_class, getRenderPass, "()I");

        GLMapView_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/opengl/GLMapView");
        GLMapView_class.syncVersion = env.GetFieldID(GLMapView_class.id, "syncVersion", "I");
        GLMapView_class.syncPass = env.GetFieldID(GLMapView_class.id, "syncPass", "I");
        GLMapView_class.sync = env.GetMethodID(GLMapView_class.id, "sync", "()V");

        return true;
    }
}
