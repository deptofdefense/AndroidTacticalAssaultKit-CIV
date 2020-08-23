#ifndef TAK_ENGINE_DB_WHERECLAUSEBUILDER2_H_INCLUDED
#define TAK_ENGINE_DB_WHERECLAUSEBUILDER2_H_INCLUDED

#include <list>
#include <sstream>

#include "util/NonCopyable.h"

#include "port/Collection.h"
#include "port/String.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace DB {
            class BindArgument;

            class WhereClauseBuilder2 : TAK::Engine::Util::NonCopyable
            {
            public :
                WhereClauseBuilder2() NOTHROWS;
            public :
                Util::TAKErr beginCondition() NOTHROWS;
                Util::TAKErr append(const char *s) NOTHROWS;
                Util::TAKErr appendIn(const char *col, TAK::Engine::Port::Collection<TAK::Engine::Port::String> &vals) NOTHROWS;
                Util::TAKErr appendIn(const char *col, TAK::Engine::Port::Collection<BindArgument> &vals) NOTHROWS;
                Util::TAKErr appendIn(const char *col, const std::size_t numArgs) NOTHROWS;
                Util::TAKErr addArg(const BindArgument arg) NOTHROWS;
                Util::TAKErr addArgs(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &args) NOTHROWS;
                Util::TAKErr addArgs(TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS;
                Util::TAKErr getSelection(const char **selection) NOTHROWS;
                Util::TAKErr getBindArgs(TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS;
                Util::TAKErr clear() NOTHROWS;
            public :
                static bool isWildcard(const char *arg) NOTHROWS;
                static bool isWildcard(const BindArgument &arg) NOTHROWS;
            private :
                std::ostringstream selection_;
                std::list<BindArgument> args_;
                Port::String sql_;
            };
        }
    }
}

#endif
