#include "interop/renderer/core/ManagedGLLayerSpi2.h"

#include <core/LegacyAdapters.h>

#include "common.h"
#include "interop/core/Interop.h"
#include "interop/java/JNIPair.h"
#include "interop/renderer/core/Interop.h"

using namespace TAKEngineJNI::Interop::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop::Core;
using namespace TAKEngineJNI::Interop::Java;
using namespace TAKEngineJNI::Interop::Renderer::Core;

namespace
{
    struct
    {
        jclass id;
        jmethodID create;
    } GLLayerSpi2_class;

    bool GLLayerSpi2_class_init(JNIEnv &env) NOTHROWS;
}

ManagedGLLayerSpi2::ManagedGLLayerSpi2(JNIEnv &env_, jobject impl_) NOTHROWS :
    impl(env_.NewGlobalRef(impl_))
{
    static bool clinit = GLLayerSpi2_class_init(env_);
}
ManagedGLLayerSpi2::~ManagedGLLayerSpi2() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
TAKErr ManagedGLLayerSpi2::create(GLLayer2Ptr &value, GLGlobeBase& renderer, Layer2 &subject2) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::shared_ptr<Layer2> subject2Ptr = std::move(Layer2Ptr(&subject2, Memory_leaker_const<Layer2>));
    std::shared_ptr<atakmap::core::Layer> subject;
    code = LegacyAdapters_adapt(subject, subject2Ptr);
    TE_CHECKRETURN_CODE(code);
    LocalJNIEnv env;
    JNILocalRef msubject(*env, NULL);
    code = TAKEngineJNI::Interop::Core::Interop_marshal(msubject, *env, subject);
    TE_CHECKRETURN_CODE(code);
    JNILocalRef mview(*env, NULL);
    code = TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(mview, *env, renderer);
    TE_CHECKRETURN_CODE(code);
    JNILocalRef marg(*env, NULL);
    code = JNIPair_create(marg, *env, mview, msubject);
    TE_CHECKRETURN_CODE(code);
    JNILocalRef mretval(*env, env->CallObjectMethod(impl, GLLayerSpi2_class.create, marg.get()));
    if(!mretval)
        return TE_InvalidArg;
    return TAKEngineJNI::Interop::Renderer::Core::Interop_marshal(value, *env, mretval);
}

namespace
{
    bool GLLayerSpi2_class_init(JNIEnv &env) NOTHROWS
    {
        GLLayerSpi2_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/opengl/GLLayerSpi2");
        GLLayerSpi2_class.create = env.GetMethodID(GLLayerSpi2_class.id, "create", "(Ljava/lang/Object;)Ljava/lang/Object;");
        return true;
    }
}
