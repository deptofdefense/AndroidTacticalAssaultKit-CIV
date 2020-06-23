#ifndef TAK_ENGINE_DB_CURSORWRAPPER2_H_INCLUDED
#define TAK_ENGINE_DB_CURSORWRAPPER2_H_INCLUDED

#include "db/Query.h"
#include "db/Database2.h"

namespace TAK {
    namespace Engine {
        namespace DB {

            class CursorWrapper2 : public Query
            {
            public :
                CursorWrapper2(QueryPtr &&filter);
            public:
                virtual ~CursorWrapper2() NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr moveToNext() NOTHROWS;
            public:
                virtual TAK::Engine::Util::TAKErr getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getColumnName(const char **value, const std::size_t columnIndex) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getColumnCount(std::size_t *value) NOTHROWS;

                virtual TAK::Engine::Util::TAKErr getBlob(const uint8_t **value, std::size_t *len, const std::size_t columnIndex) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getString(const char **value, const std::size_t columnIndex) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getInt(int32_t *value, const std::size_t columnIndex) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getLong(int64_t *value, const std::size_t columnIndex) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getDouble(double *value, const std::size_t columnIndex) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getType(FieldType *value, const std::size_t columnIndex) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr isNull(bool *value, const std::size_t columnIndex) NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr bindInt(const std::size_t idx, const int32_t value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr bindLong(const std::size_t idx, const int64_t value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr bindDouble(const std::size_t idx, const double value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr bindString(const std::size_t idx, const char *value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr bindNull(const std::size_t idx) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr clearBindings() NOTHROWS;
            private :
                QueryPtr filterPtr;
            protected :
                Query *filter;
            };
        }
    }
}


#endif
