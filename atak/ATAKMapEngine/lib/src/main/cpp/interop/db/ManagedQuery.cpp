#include "ManagedQuery.h"

#include "common.h"
#include <jni.h>
#include <db/Query.h>
#include <util/Error.h>
#include <db/BindArgument.h>

#include "interop/JNIByteArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/db/Interop.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;
using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::DB;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct {
        jclass id;
        jmethodID resetMethodId;
        jmethodID getColumnIndexMethodId;
        jmethodID getColumnNameMethodId;
        jmethodID getColumnNamesMethodId;
        jmethodID getColumnCountMethodId;
        jmethodID getBlobMethodId;
        jmethodID getStringMethodId;
        jmethodID getIntMethodId;
        jmethodID getLongMethodId;
        jmethodID getDoubleMethodId;
        jmethodID getTypeMethodId;
        jmethodID isNullMethodId;

        int fieldTypeBlob;
        int fieldTypeNull;
        int fieldTypeString;
        int fieldTypeInteger;
        int fieldTypeFloat;
    } QueryIface_class;

    bool QueryIface_Managed_class_init(JNIEnv &env) NOTHROWS;
}

ManagedQuery::ManagedQuery(JNIEnv &env, jobject instance) NOTHROWS {
    static bool clinit = QueryIface_Managed_class_init(env);
    m_instance = env.NewGlobalRef(instance);
}

ManagedQuery::~ManagedQuery() NOTHROWS {
    LocalJNIEnv env;

    if(m_instance) {
        RowIterator_close(env, m_instance);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteGlobalRef(m_instance);
    }
}

TAK::Engine::Util::TAKErr ManagedQuery::moveToNext() NOTHROWS {
    LocalJNIEnv env;

    TAKErr err = RowIterator_moveToNext(env, m_instance);
    m_bindValues.clear();
    return err;
}

TAK::Engine::Util::TAKErr
ManagedQuery::getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS {
    LocalJNIEnv env;
    JNILocalRef jcolumnName(*env, env->NewStringUTF(columnName));
    jint col = env->CallIntMethod(m_instance, QueryIface_class.getColumnIndexMethodId, jcolumnName.get());
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TE_Err;
    }
    *value = col;
    return TE_Ok;
}

TAK::Engine::Util::TAKErr
ManagedQuery::getColumnName(const char **value, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;
    auto entry = m_columnNames.find(columnIndex);
    if(entry == m_columnNames.end())
    {
        JNIStringUTF jname(*env, (jstring)env->CallObjectMethod(m_instance, QueryIface_class.getColumnNameMethodId, columnIndex));
        if(env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TE_Err;
        }
        std::string name(jname);
        m_columnNames[columnIndex] = name;
    }

    *value = m_columnNames[columnIndex].c_str();
    return TE_Ok;
}

TAK::Engine::Util::TAKErr ManagedQuery::getColumnCount(std::size_t *value) NOTHROWS {
    LocalJNIEnv env;
    jint count = env->CallIntMethod(m_instance, QueryIface_class.getColumnCountMethodId);
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TE_Err;
    }
    *value = count;
    return TE_Ok;
}

TAK::Engine::Util::TAKErr
ManagedQuery::getBlob(const uint8_t **value, std::size_t *len, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;

    auto entry = m_bindValues.find(columnIndex);
    if(entry == m_bindValues.end())
    {
        JNILocalRef blob(*env, env->CallObjectMethod(m_instance, QueryIface_class.getBlobMethodId, (jint)columnIndex));
        if(env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TE_Err;
        }
        if(blob) {
            auto blobLen = (std::size_t) env->GetArrayLength((jbyteArray) blob.get());

            JNIByteArray blobarr(*env, (jbyteArray)blob.get(), JNI_ABORT);
            m_bindValues[columnIndex].set(blobarr.get<const uint8_t>(), blobLen);
            m_bindValues[columnIndex].own();
        } else {
            // set to NULL
            m_bindValues[columnIndex].clear();

        }
    }
    const BindArgument &columnData = m_bindValues[columnIndex];
    if(columnData.getType() == TEFT_Null) {
        *value = NULL;
        *len = 0;
    } else if(columnData.getType() != FieldType::TEFT_Blob) {
        return TE_IllegalState;
    } else { // TEFT_Blob
        *value = columnData.getValue().b.data;
        *len = columnData.getValue().b.len;
    }

    return TE_Ok;
}

TAK::Engine::Util::TAKErr
ManagedQuery::getString(const char **value, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;

    auto entry = m_bindValues.find(columnIndex);
    if(entry == m_bindValues.end())
    {
        JNIStringUTF jstrValue(*env, (jstring)env->CallObjectMethod(m_instance, QueryIface_class.getStringMethodId, (jint)columnIndex));
        if(env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TE_Err;
        }

        m_bindValues[columnIndex].set(jstrValue);
        m_bindValues[columnIndex].own();
    }
    const BindArgument &columnData = m_bindValues[columnIndex];
    if(columnData.getType() == TEFT_Null) {
        *value = NULL;
    } else if(columnData.getType() != FieldType::TEFT_String) {
        return TE_IllegalState;
    } else { // TEFT_String
        *value = columnData.getValue().s;
    }

    return TE_Ok;
}

TAK::Engine::Util::TAKErr ManagedQuery::getInt(int32_t *value, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;
    jint jintValue = env->CallIntMethod(m_instance, QueryIface_class.getIntMethodId, (jint)columnIndex);
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TE_Err;
    }
    *value = jintValue;
    return TE_Ok;
}

TAK::Engine::Util::TAKErr ManagedQuery::getLong(int64_t *value, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;
    jlong jlongValue = env->CallLongMethod(m_instance, QueryIface_class.getLongMethodId, (jint)columnIndex);
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TE_Err;
    }
    *value = jlongValue;
    return TE_Ok;
}

TAK::Engine::Util::TAKErr ManagedQuery::getDouble(double *value, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;
    jdouble jdoubleValue = env->CallDoubleMethod(m_instance, QueryIface_class.getDoubleMethodId, (jint)columnIndex);
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TE_Err;
    }
    *value = jdoubleValue;
    return TE_Ok;
}

TAK::Engine::Util::TAKErr
ManagedQuery::getType(FieldType *value, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;
    jint type = env->CallIntMethod(m_instance, QueryIface_class.getTypeMethodId, (jint)columnIndex);
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TE_Err;
    }

    if(type == QueryIface_class.fieldTypeBlob)
    {
        *value = FieldType::TEFT_Blob;
    }
    else if(type == QueryIface_class.fieldTypeNull)
    {
        *value = FieldType::TEFT_Null;
    }
    else if(type == QueryIface_class.fieldTypeString)
    {
        *value = FieldType::TEFT_String;
    }
    else if(type == QueryIface_class.fieldTypeInteger)
    {
        *value = FieldType::TEFT_Integer;
    }
    else if(type == QueryIface_class.fieldTypeFloat)
    {
        *value = FieldType::TEFT_Float;
    } else {
        return TE_IllegalState;
    }

    return TE_Ok;
}

TAK::Engine::Util::TAKErr ManagedQuery::isNull(bool *value, const std::size_t columnIndex) NOTHROWS {
    LocalJNIEnv env;
    jboolean isnull = env->CallBooleanMethod(m_instance, QueryIface_class.isNullMethodId, (jint)columnIndex);
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TE_Err;
    }
    *value = isnull;
    return TE_Ok;
}

TAK::Engine::Util::TAKErr
ManagedQuery::bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, blob, size);
}

TAK::Engine::Util::TAKErr ManagedQuery::bindInt(const std::size_t idx, const int32_t value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedQuery::bindLong(const std::size_t idx, const int64_t value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedQuery::bindDouble(const std::size_t idx, const double value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedQuery::bindString(const std::size_t idx, const char *value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedQuery::bindNull(const std::size_t idx) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bindNull(*env, m_instance, idx);
}

TAK::Engine::Util::TAKErr ManagedQuery::clearBindings() NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_clearBindings(*env, m_instance);
}

namespace
{
    bool QueryIface_Managed_class_init(JNIEnv &env) NOTHROWS
    {
        QueryIface_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/database/QueryIface");

        QueryIface_class.resetMethodId = env.GetMethodID(QueryIface_class.id, "reset", "()V");
        QueryIface_class.getColumnIndexMethodId = env.GetMethodID(QueryIface_class.id, "getColumnIndex", "(Ljava/lang/String;)I");
        QueryIface_class.getColumnNameMethodId = env.GetMethodID(QueryIface_class.id, "getColumnName", "(I)Ljava/lang/String;");
        QueryIface_class.getColumnNamesMethodId = env.GetMethodID(QueryIface_class.id, "getColumnNames", "()[Ljava/lang/String;");
        QueryIface_class.getColumnCountMethodId = env.GetMethodID(QueryIface_class.id, "getColumnCount", "()I");
        QueryIface_class.getBlobMethodId = env.GetMethodID(QueryIface_class.id, "getBlob", "(I)[B");
        QueryIface_class.getStringMethodId = env.GetMethodID(QueryIface_class.id, "getString", "(I)Ljava/lang/String;");
        QueryIface_class.getIntMethodId = env.GetMethodID(QueryIface_class.id, "getInt", "(I)I");
        QueryIface_class.getLongMethodId = env.GetMethodID(QueryIface_class.id, "getLong", "(I)J");
        QueryIface_class.getDoubleMethodId = env.GetMethodID(QueryIface_class.id, "getDouble", "(I)D");
        QueryIface_class.getTypeMethodId = env.GetMethodID(QueryIface_class.id, "getType", "(I)I");
        QueryIface_class.isNullMethodId = env.GetMethodID(QueryIface_class.id, "isNull", "(I)Z");

        jclass CursorIface_class = ATAKMapEngineJNI_findClass(&env, "com/atakmap/database/CursorIface");
        jfieldID blobType = env.GetStaticFieldID(CursorIface_class, "FIELD_TYPE_BLOB", "I");
        QueryIface_class.fieldTypeBlob = env.GetStaticIntField(CursorIface_class, blobType);
        jfieldID nullType = env.GetStaticFieldID(CursorIface_class, "FIELD_TYPE_NULL", "I");
        QueryIface_class.fieldTypeNull = env.GetStaticIntField(CursorIface_class, nullType);
        jfieldID stringType = env.GetStaticFieldID(CursorIface_class, "FIELD_TYPE_STRING", "I");
        QueryIface_class.fieldTypeString = env.GetStaticIntField(CursorIface_class, stringType);
        jfieldID integerType = env.GetStaticFieldID(CursorIface_class, "FIELD_TYPE_INTEGER", "I");
        QueryIface_class.fieldTypeInteger = env.GetStaticIntField(CursorIface_class, integerType);
        jfieldID floatType = env.GetStaticFieldID(CursorIface_class, "FIELD_TYPE_FLOAT", "I");
        QueryIface_class.fieldTypeFloat = env.GetStaticIntField(CursorIface_class, floatType);

        return true;
    }
}
