#include "interop/math/Interop.h"

#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } Matrix_class;

    struct
    {
        jclass id;
        jmethodID ctor;
        jfieldID X;
        jfieldID Y;
        jfieldID Width;
        jfieldID Height;
    } Rectangle_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool Math_interop_init(JNIEnv &env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Math::Interop_copy(Matrix2 *value, JNIEnv *env, jobject mmatrix) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(!checkInit(*env))
        return TE_IllegalState;
    if(!mmatrix)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;

    JNILocalRef mpointer(*env, env->GetObjectField(mmatrix, Matrix_class.pointer));
    Matrix2 *cmatrix = Pointer_get<Matrix2>(env, mpointer);
    if(!cmatrix)
        return TE_IllegalState;

    value->set(*cmatrix);
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::Math::Interop_marshal(JNILocalRef &value, JNIEnv &env, const Matrix2 &cmatrix) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    Matrix2Ptr retval(new Matrix2(cmatrix), Memory_deleter_const<Matrix2>);
    value = JNILocalRef(env, env.NewObject(Matrix_class.id, Matrix_class.ctor, NewPointer(&env, std::move(retval)), NULL));
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Math::Interop_marshal(jobject value, JNIEnv &env, const TAK::Engine::Math::Matrix2 &cmatrix) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    JNILocalRef mpointer(env, env.GetObjectField(value, Matrix_class.pointer));
    Matrix2 *impl = Pointer_get<Matrix2>(env, mpointer);
    if(!impl)
        return TE_IllegalState;
    impl->set(cmatrix);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Math::Interop_marshal(TAK::Engine::Math::Rectangle2<double> *value, JNIEnv &env, jobject mrect) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    value->x = env.GetDoubleField(mrect, Rectangle_class.X);
    value->y = env.GetDoubleField(mrect, Rectangle_class.Y);
    value->width = env.GetDoubleField(mrect, Rectangle_class.Width);
    value->height = env.GetDoubleField(mrect, Rectangle_class.Height);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Math::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Math::Rectangle2<double> &crect) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    value = Java::JNILocalRef(env, env.NewObject(Rectangle_class.id, Rectangle_class.ctor, crect.x, crect.y, crect.width, crect.height));
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Math::Interop_marshal(jobject value, JNIEnv &env, const TAK::Engine::Math::Rectangle2<double> &crect) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    env.SetDoubleField(value, Rectangle_class.X, crect.x);
    env.SetDoubleField(value, Rectangle_class.Y, crect.y);
    env.SetDoubleField(value, Rectangle_class.Width, crect.width);
    env.SetDoubleField(value, Rectangle_class.Height, crect.height);

    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = Math_interop_init(env);
        return clinit;
    }
    bool Math_interop_init(JNIEnv &env) NOTHROWS
    {
        Matrix_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/math/Matrix");
        Matrix_class.pointer = env.GetFieldID(Matrix_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        Matrix_class.ctor = env.GetMethodID(Matrix_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");

        Rectangle_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/math/Rectangle");
        Rectangle_class.ctor = env.GetMethodID(Rectangle_class.id, "<init>", "(DDDD)V");
        Rectangle_class.X = env.GetFieldID(Rectangle_class.id, "X", "D");
        Rectangle_class.Y = env.GetFieldID(Rectangle_class.id, "Y", "D");
        Rectangle_class.Width = env.GetFieldID(Rectangle_class.id, "Width", "D");
        Rectangle_class.Height = env.GetFieldID(Rectangle_class.id, "Height", "D");

        return true;
    }
}
