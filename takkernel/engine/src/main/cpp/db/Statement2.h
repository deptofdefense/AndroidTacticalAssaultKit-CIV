#ifndef TAK_ENGINE_DB_STATEMENT2_H_INCLUDED
#define TAK_ENGINE_DB_STATEMENT2_H_INCLUDED

#include "util/NonCopyable.h"

#include "db/Bindable.h"
#include "db/DB_Error.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace DB {
            class Statement2 : public Bindable,
                               private TAK::Engine::Util::NonCopyable
            {
            protected:
                Statement2() NOTHROWS = default;
            protected:
                virtual ~Statement2() NOTHROWS = default;
            public:
                /**
                * Executes the statement.
                */
                virtual TAK::Engine::Util::TAKErr execute() NOTHROWS = 0;
            }; // Statement2
        }
    }
}

#endif // TAK_ENGINE_DB_STATEMENT2_H_INCLUDED
