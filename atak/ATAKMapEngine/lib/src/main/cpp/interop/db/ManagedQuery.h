#ifndef ATAK_MANAGEDQUERY_H
#define ATAK_MANAGEDQUERY_H

#include <jni.h>
#include <map>

#include <db/BindArgument.h>
#include <db/Query.h>
#include <util/Error.h>


using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

class ManagedQuery : public Query
{
public :
    ManagedQuery(JNIEnv &env, jobject instance) NOTHROWS;
    ~ManagedQuery() NOTHROWS override;
public :
    TAK::Engine::Util::TAKErr moveToNext() NOTHROWS override;
public:
    TAK::Engine::Util::TAKErr getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS override;
    TAK::Engine::Util::TAKErr getColumnName(const char **value, const std::size_t columnIndex) NOTHROWS override;
    TAK::Engine::Util::TAKErr getColumnCount(std::size_t *value) NOTHROWS override;

    TAK::Engine::Util::TAKErr getBlob(const uint8_t **value, std::size_t *len, const std::size_t columnIndex) NOTHROWS override;
    TAK::Engine::Util::TAKErr getString(const char **value, const std::size_t columnIndex) NOTHROWS override;
    TAK::Engine::Util::TAKErr getInt(int32_t *value, const std::size_t columnIndex) NOTHROWS override;
    TAK::Engine::Util::TAKErr getLong(int64_t *value, const std::size_t columnIndex) NOTHROWS override;
    TAK::Engine::Util::TAKErr getDouble(double *value, const std::size_t columnIndex) NOTHROWS override;
    TAK::Engine::Util::TAKErr getType(FieldType *value, const std::size_t columnIndex) NOTHROWS override;
    TAK::Engine::Util::TAKErr isNull(bool *value, const std::size_t columnIndex) NOTHROWS override;
public:
    TAK::Engine::Util::TAKErr bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS override;
    TAK::Engine::Util::TAKErr bindInt(const std::size_t idx, const int32_t value) NOTHROWS override;
    TAK::Engine::Util::TAKErr bindLong(const std::size_t idx, const int64_t value) NOTHROWS override;
    TAK::Engine::Util::TAKErr bindDouble(const std::size_t idx, const double value) NOTHROWS override;
    TAK::Engine::Util::TAKErr bindString(const std::size_t idx, const char *value) NOTHROWS override;
    TAK::Engine::Util::TAKErr bindNull(const std::size_t idx) NOTHROWS override;
    TAK::Engine::Util::TAKErr clearBindings() NOTHROWS override;

private:
    jobject m_instance;

    std::map<std::size_t, std::string> m_columnNames;
    std::map<std::size_t, BindArgument> m_bindValues;
};
#endif //ATAK_MANAGEDQUERY_H
