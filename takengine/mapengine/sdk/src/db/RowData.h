#ifndef TAK_ENGINE_DB_ROWDATA_H_INCLUDED
#define TAK_ENGINE_DB_ROWDATA_H_INCLUDED

#include <cstdint>
#include <cstddef>
#include <map>
#include <memory>

#include "util/NonCopyable.h"

#include "db/Query.h"

namespace TAK {
    namespace Engine {
        namespace DB {
            class RowData : private TAK::Engine::Util::NonCopyable
            {
            private :
                struct ColumnData
                {
                    Query::FieldType type;
                    union
                    {
                        const char *s;
                        struct
                        {
                            const uint8_t *data;
                            std::size_t len;
                        } b;
                    } value;
                };
                typedef std::unique_ptr<void *, void(*)(const void *)> BackerPtr;
            public :
                RowData(Query &cursor);
            public :
                void reset();
            private :
                Query &cursor;
                std::map<std::size_t, ColumnData> row;
                std::map<std::size_t, BackerPtr> backer;
            };
        }
    }
}
#endif
