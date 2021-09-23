#include "jstatementimpl.h"

#include <db/Statement2.h>

#include "common.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<Statement2>(env, jpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_execute
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = stmt->execute();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_bindBinary
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jbyteArray jvalue)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    JNIByteArray valueArr(*env, jvalue, JNI_ABORT);
    const jbyte *bytes = valueArr;
    code = stmt->bindBlob(index, reinterpret_cast<const uint8_t *>(bytes), valueArr.length());
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_bindInt
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jint value)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = stmt->bindInt(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_bindLong
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jlong value)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = stmt->bindLong(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_bindDouble
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jdouble value)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = stmt->bindDouble(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_bindString
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jstring jvalue)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    JNIStringUTF value(*env, jvalue);
    TAKErr code(TE_Ok);
    code = stmt->bindString(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_bindNull
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = stmt->bindNull(index);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_StatementImpl_clearBindings
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Statement2 *stmt = JLONG_TO_INTPTR(Statement2, ptr);
    if(!stmt) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = stmt->clearBindings();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
