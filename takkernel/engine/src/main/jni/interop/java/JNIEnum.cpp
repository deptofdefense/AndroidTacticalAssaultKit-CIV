#include "interop/java/JNIEnum.h"

#include <sstream>

#include "common.h"

using namespace TAKEngineJNI::Interop::Java;

using namespace TAK::Engine::Util;

TAKErr TAKEngineJNI::Interop::Java::JNIEnum_value(JNILocalRef &value, JNIEnv &env, const char *enumClass, const char *name) NOTHROWS
{
    jclass classid = ATAKMapEngineJNI_findClass(&env, enumClass);
    if(env.ExceptionCheck()) {
        env.ExceptionClear();
        return TE_Err;
    }
    std::ostringstream strm;
    strm << "L" << enumClass << ";";
    jfieldID fieldid = env.GetStaticFieldID(classid, name, strm.str().c_str());
    if(env.ExceptionCheck()) {
        env.ExceptionClear();
        return TE_Err;
    }
    if(!fieldid)
        return TE_InvalidArg;
    value = JNILocalRef(env, env.GetStaticObjectField(classid, fieldid));
    if(env.ExceptionCheck()) {
        env.ExceptionClear();
        return TE_Err;
    }
    return TE_Ok;
}
