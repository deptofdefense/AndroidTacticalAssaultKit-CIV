#include "interop/renderer/core/ManagedGLLayer2.h"

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
        jmethodID start;
        jmethodID stop;
        jmethodID getSubject;
    } GLLayer3_class;

    struct
    {
        jclass id;
        jfieldID syncVersion;
        jfieldID syncPass;
        jmethodID sync;
    } GLMapView_class;

    bool ManagedGLLayer2_class_init(JNIEnv &env) NOTHROWS;
}

ManagedGLLayer2::ManagedGLLayer2(JNIEnv &env_, jobject impl_) NOTHROWS :
    renderable(env_, impl_, true),
    impl(renderable.impl)
{
    static bool clinit = ManagedGLLayer2_class_init(env_);
    std::shared_ptr<atakmap::core::Layer> csub;
    Java::JNILocalRef msub(env_, env_.CallObjectMethod(impl, GLLayer3_class.getSubject));
    TAKEngineJNI::Interop::Core::Interop_marshal(csub, env_, msub);
    TAK::Engine::Core::LegacyAdapters_adapt(csubject, csub);

    gllayer3 = env_.IsInstanceOf(impl, GLLayer3_class.id);
}
ManagedGLLayer2::~ManagedGLLayer2() NOTHROWS
{}
void ManagedGLLayer2::draw(const TAK::Engine::Renderer::Core::GLGlobeBase& cview, const int crenderPass) NOTHROWS
{
    if(gllayer3) {
        renderable.draw(cview, crenderPass);
    } else {
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return;
        // obtain managed view instance
        Java::JNILocalRef mview(*env, NULL);
        TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(mview, *env, cview);
        // if required, check state on managed and copy from native if dirty
        if((cview.drawVersion != env->GetIntField(mview, GLMapView_class.syncVersion) ||
            crenderPass != env->GetIntField(mview, GLMapView_class.syncPass))) {

            env->CallVoidMethod(mview, GLMapView_class.sync);
            env->SetIntField(mview, GLMapView_class.syncVersion, cview.drawVersion);
            env->SetIntField(mview, GLMapView_class.syncPass, crenderPass);
        }
        env->CallVoidMethod(impl, GLLayer3_class.draw, mview.get());
    }
}
void ManagedGLLayer2::release() NOTHROWS
{
    if(gllayer3) {
        renderable.release();
    } else {
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return;
        env->CallVoidMethod(impl, GLLayer3_class.release);
    }

}
int ManagedGLLayer2::getRenderPass() NOTHROWS
{
    if(!gllayer3)
        return GLMapView2::Surface;
    else
        return renderable.getRenderPass();
}
void ManagedGLLayer2::start() NOTHROWS
{
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return;
    env->CallVoidMethod(impl, GLLayer3_class.start);
}
void ManagedGLLayer2::stop() NOTHROWS
{
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return;
    env->CallVoidMethod(impl, GLLayer3_class.stop);
}
Layer2 &ManagedGLLayer2::getSubject() NOTHROWS
{
    return *csubject;
}

namespace
{
    bool ManagedGLLayer2_class_init(JNIEnv &env) NOTHROWS
    {
#define SET_METHOD_DEFINITION(c, m, sig) \
    c.m = env.GetMethodID(c.id, #m, sig)

        GLLayer3_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/opengl/GLLayer3");
        SET_METHOD_DEFINITION(GLLayer3_class, draw, "(Lcom/atakmap/map/opengl/GLMapView;)V");
        SET_METHOD_DEFINITION(GLLayer3_class, release, "()V");
        SET_METHOD_DEFINITION(GLLayer3_class, start, "()V");
        SET_METHOD_DEFINITION(GLLayer3_class, stop, "()V");
        SET_METHOD_DEFINITION(GLLayer3_class, getSubject, "()Lcom/atakmap/map/layer/Layer;");

        GLMapView_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/opengl/GLMapView");
        GLMapView_class.syncVersion = env.GetFieldID(GLMapView_class.id, "syncVersion", "I");
        GLMapView_class.syncPass = env.GetFieldID(GLMapView_class.id, "syncPass", "I");
        GLMapView_class.sync = env.GetMethodID(GLMapView_class.id, "sync", "()V");

        return true;
    }
}
