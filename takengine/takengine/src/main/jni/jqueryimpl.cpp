#include "jqueryimpl.h"

#include <db/Query.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIStringUTF.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<Query>(env, jpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_reset
  (JNIEnv *env, jclass clazz, jlong ptr)
{
#if 0
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = query->reset();
    ATAKMapEngineJNI_checkOrThrow(env, code);
#else
    ATAKMapEngineJNI_checkOrThrow(env, TE_Unsupported);
#endif
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_bindBinary
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jbyteArray jvalue)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    JNIByteArray valueArr(*env, jvalue, JNI_ABORT);
    const jbyte *bytes = valueArr;
    code = query->bindBlob(index, reinterpret_cast<const uint8_t *>(bytes), valueArr.length());
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_bindInt
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jint value)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = query->bindInt(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_bindLong
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jlong value)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = query->bindLong(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_bindDouble
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jdouble value)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = query->bindDouble(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_bindString
  (JNIEnv *env, jclass clazz, jlong ptr, jint index, jstring jvalue)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    JNIStringUTF value(*env, jvalue);
    TAKErr code(TE_Ok);
    code = query->bindString(index, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_bindNull
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = query->bindNull(index);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_QueryImpl_clearBindings
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = query->clearBindings();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getColumnIndex
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jcolName)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    TAKErr code(TE_Ok);
    JNIStringUTF colName(*env, jcolName);
    std::size_t retval;
    code = query->getColumnIndex(&retval, colName);
    if(code == TE_Ok)
        return retval;
    else if(code == TE_InvalidArg)
        return -1;

    ATAKMapEngineJNI_checkOrThrow(env, code);
    return -1;
}
JNIEXPORT jstring JNICALL Java_com_atakmap_database_impl_QueryImpl_getColumnName
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    const char *colName;
    code = query->getColumnName(&colName, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return colName ? env->NewStringUTF(colName) : NULL;
}
JNIEXPORT jobjectArray JNICALL Java_com_atakmap_database_impl_QueryImpl_getColumnNames
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    std::size_t count;
    code = query->getColumnCount(&count);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    jobjectArray retval = env->NewObjectArray(count, ATAKMapEngineJNI_findClass(env, "java/lang/String"), NULL);
    for(std::size_t i = 0u; i < count; i++) {
        const char *colName;
        code = query->getColumnName(&colName, i);
        TE_CHECKBREAK_CODE(code);
        env->SetObjectArrayElement(retval, i, env->NewStringUTF(colName));
    }
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return retval;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getColumnCount
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    TAKErr code(TE_Ok);
    std::size_t len;
    code = query->getColumnCount(&len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return len;
}
JNIEXPORT jbyteArray JNICALL Java_com_atakmap_database_impl_QueryImpl_getBlob
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    const uint8_t *value;
    std::size_t len;
    code = query->getBlob(&value, &len, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return value ? JNIByteArray_newByteArray(env, reinterpret_cast<const jbyte *>(value), len) : NULL;
}
JNIEXPORT jstring JNICALL Java_com_atakmap_database_impl_QueryImpl_getString
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    const char *value;
    code = query->getString(&value, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return value ? env->NewStringUTF(value) : NULL;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getInt
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    TAKErr code(TE_Ok);
    int value;
    code = query->getInt(&value, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return value;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_database_impl_QueryImpl_getLong
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    TAKErr code(TE_Ok);
    int64_t value;
    code = query->getLong(&value, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return value;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_database_impl_QueryImpl_getDouble
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    TAKErr code(TE_Ok);
    double value;
    code = query->getDouble(&value, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return value;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getType
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    TAKErr code(TE_Ok);
    Query::FieldType type;
    code = query->getType(&type, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return (int)type;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_database_impl_QueryImpl_isNull
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    bool value;
    code = query->isNull(&value, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return value;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_database_impl_QueryImpl_moveToNext
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Query *query = JLONG_TO_INTPTR(Query, ptr);
    if(!query) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    code = query->moveToNext();
    if(code == TE_Ok)
        return true;
    else if(code == TE_Done)
        return false;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return false;
}

JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getFieldType_1TEFTBlob
  (JNIEnv *env, jclass clazz)
{
    return Query::TEFT_Blob;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getFieldType_1TEFTNull
  (JNIEnv *env, jclass clazz)
{
    return Query::TEFT_Null;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getFieldType_1TEFTString
  (JNIEnv *env, jclass clazz)
{
    return Query::TEFT_String;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getFieldType_1TEFTInteger
  (JNIEnv *env, jclass clazz)
{
    return Query::TEFT_Integer;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_QueryImpl_getFieldType_1TEFTFloat
  (JNIEnv *env, jclass clazz)
{
    return Query::TEFT_Float;
}
