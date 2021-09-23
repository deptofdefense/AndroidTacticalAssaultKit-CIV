#ifndef TAK_ENGINE_DB_BINDARGUMENT_H_INCLUDED
#define TAK_ENGINE_DB_BINDARGUMENT_H_INCLUDED

#include "db/Bindable.h"
#include "db/Database2.h"
#include "db/Query.h"

namespace TAK {
    namespace Engine {
        namespace DB {

            class ENGINE_API BindArgument
            {
            public :
                typedef union
                {
                    int64_t l;
                    double d;
                    const char *s;
                    struct
                    {
                        std::size_t len;
                        const uint8_t* data;
                    } b;
                } Value;
            public :
                BindArgument() NOTHROWS;
                BindArgument(int val) NOTHROWS;
                BindArgument(int64_t val) NOTHROWS;
                BindArgument(double val) NOTHROWS;
                BindArgument(const char *val) NOTHROWS;
                BindArgument(const uint8_t *val, const std::size_t valLen) NOTHROWS;
                BindArgument(const BindArgument &other) NOTHROWS;
            public :
                ~BindArgument() NOTHROWS;
            public :
                void set(int val) NOTHROWS;
                void set(int64_t val) NOTHROWS;
                void set(double val) NOTHROWS;
                void set(const char *val) NOTHROWS;
                void set(const uint8_t *val, const std::size_t len) NOTHROWS;
                void clear() NOTHROWS;
            public :
                /** this instance will take ownership of any blob/string value via copy */
                void own() NOTHROWS;
            public :
                BindArgument& operator=(const BindArgument &other);
            public :
                Query::FieldType getType() const NOTHROWS;
                Value getValue() const NOTHROWS;
            public :
                TAK::Engine::Util::TAKErr bind(Bindable &stmt, const std::size_t idx) const NOTHROWS;
            public :
                bool operator==(const BindArgument &other) const NOTHROWS;
            public :
                static TAK::Engine::Util::TAKErr query(QueryPtr &cursor, Database2 &database, const char *sql, TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS;
                static TAK::Engine::Util::TAKErr bind(Bindable &cursor, TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS;
            private :
                Query::FieldType type;
                Value value;
                bool owns;
            };
        }
    }
}

#endif