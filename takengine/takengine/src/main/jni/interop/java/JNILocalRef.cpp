#include "interop/java/JNILocalRef.h"

using namespace TAKEngineJNI::Interop::Java;

JNILocalRef::JNILocalRef(JNIEnv &env_, jobject obj_) NOTHROWS :
    env(env_),
    obj(obj_)
{}
JNILocalRef::JNILocalRef(JNILocalRef &&other_) NOTHROWS :
    env(other_.env),
    obj(other_.obj)
{
    other_.obj = NULL;
}
JNILocalRef::~JNILocalRef() NOTHROWS
{
    if(obj) {
        env.DeleteLocalRef(obj);
        obj = NULL;
    }
}
jobject JNILocalRef::get() NOTHROWS
{
    return obj;
}
jobject JNILocalRef::release() NOTHROWS
{
    jobject retval = obj;
    obj = NULL;
    return retval;
}
JNILocalRef::operator bool () const NOTHROWS
{
    return !!obj;
}
JNILocalRef::operator jobject () const NOTHROWS
{
    return obj;
}
JNILocalRef::operator jstring () const NOTHROWS
{
    return (jstring)obj;
}
JNILocalRef::operator jclass () const NOTHROWS
{
    return (jclass)obj;
}
JNILocalRef &JNILocalRef::operator=(JNILocalRef &&other) NOTHROWS
{
    if(obj) {
        env.DeleteLocalRef(obj);
        obj = NULL;
    }
    env = other.env;
    obj = other.obj;
    other.obj = NULL;
    return *this;
}
