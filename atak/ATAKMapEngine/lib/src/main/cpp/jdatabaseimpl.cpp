#include "jdatabaseimpl.h"

#include <sstream>

#include <db/Database2.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jobject JNICALL Java_com_atakmap_database_impl_DatabaseImpl_openImpl
  (JNIEnv *env, jclass clazz, jstring jpath, jstring mpassphrase, jint flags)
{
    TAKErr code(TE_Ok);
    DatabasePtr retval(NULL, NULL);

    TAK::Engine::Port::String cpath(":memory:");
    if(jpath) {
        code = JNIStringUTF_get(cpath, *env, jpath);
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return NULL;
    }
    TAK::Engine::Port::String ckey;
    std::uint8_t keylen = 0u;
    if(mpassphrase) {
        code = JNIStringUTF_get(ckey, *env, mpassphrase);
        if(code == TE_Ok) {

            keylen = strlen(ckey);
        } else {
            // failed to get the passphrase, but try to proceed anyway
        }
    }
    code = Databases_openDatabase(retval, cpath, reinterpret_cast<const uint8_t *>(ckey.get()), keylen, flags&com_atakmap_database_impl_DatabaseImpl_OPEN_READONLY);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_DatabaseImpl_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<Database2>(env, jpointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_database_impl_DatabaseImpl_compileStatement
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jsql)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    JNIStringUTF sql(*env, jsql);

    TAKErr code(TE_Ok);
    StatementPtr stmt(NULL, NULL);
    code = db->compileStatement(stmt, sql);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(stmt));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_database_impl_DatabaseImpl_compileQuery
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jsql)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    JNIStringUTF sql(*env, jsql);

    TAKErr code(TE_Ok);
    QueryPtr stmt(NULL, NULL);
    code = db->compileQuery(stmt, sql);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(stmt));
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_database_impl_DatabaseImpl_isReadOnly
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    bool retval;
    code = db->isReadOnly(&retval);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return retval;
}
JNIEXPORT jint JNICALL Java_com_atakmap_database_impl_DatabaseImpl_getVersion
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    TAKErr code(TE_Ok);
    int retval;
    code = db->getVersion(&retval);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return retval;
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_DatabaseImpl_setVersion
  (JNIEnv *env, jclass clazz, jlong ptr, jint version)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = db->setVersion(version);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_DatabaseImpl_beginTransaction
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    bool retval;
    code = db->beginTransaction();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_DatabaseImpl_setTransactionSuccessful
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = db->setTransactionSuccessful();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_database_impl_DatabaseImpl_endTransaction
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = db->endTransaction();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_database_impl_DatabaseImpl_inTransaction
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Database2 *db = JLONG_TO_INTPTR(Database2, ptr);
    if(!db) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    bool retval;
    code = db->inTransaction(&retval);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return retval;
}