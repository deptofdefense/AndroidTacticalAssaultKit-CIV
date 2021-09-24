#ifndef TAK_ENGINE_DB_QUERY_H_INCLUDED
#define TAK_ENGINE_DB_QUERY_H_INCLUDED

#include <memory>

#include <util/NonCopyable.h>

#include "db/Bindable.h"
#include "db/RowIterator.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace DB {

            class Query : public virtual RowIterator,
                          public virtual Bindable,
                          private TAK::Engine::Util::NonCopyable
            {
            public:
                enum FieldType
                {
                    TEFT_Blob,
                    TEFT_Null,
                    TEFT_String,
                    TEFT_Integer,
                    TEFT_Float,
                };
            protected:
                virtual ~Query() NOTHROWS = default;
            public:
                virtual TAK::Engine::Util::TAKErr getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getColumnName(const char **value, const std::size_t columnIndex) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getColumnCount(std::size_t *value) NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr getBlob(const uint8_t **value, std::size_t *len, const std::size_t columnIndex) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getString(const char **value, const std::size_t columnIndex) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getInt(int32_t *value, const std::size_t columnIndex) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getLong(int64_t *value, const std::size_t columnIndex) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getDouble(double *value, const std::size_t columnIndex) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getType(FieldType *value, const std::size_t columnIndex) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr isNull(bool *value, const std::size_t columnIndex) NOTHROWS = 0;

#if 0
                /**
                * Prepares for a new query. Note that this method does NOT clear the
                * existing bindings; bindings must always be explicitly cleared via
                * {@link #clearBindings()}.
                */
                virtual TAK::Engine::Util::TAKErr reset() NOTHROWS = 0;
#endif
            };
        }
    }
}

#endif
