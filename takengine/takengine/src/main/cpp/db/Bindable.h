#ifndef TAK_ENGINE_DB_BINDABLE_H_INCLUDED
#define TAK_ENGINE_DB_BINDABLE_H_INCLUDED

#include <cstddef>
#include <cstdint>

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace DB {

            class Bindable
            {
            protected:
                virtual ~Bindable() NOTHROWS = default;
            public:
                /**
                * Binds the specified blob on the specified index.
                *
                * @param idx   The index (one based)
                * @param value The blob
                */
                virtual TAK::Engine::Util::TAKErr bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS = 0;

                /**
                * Binds the specified <code>int</code> on the specified index.
                *
                * @param idx   The index (one based)
                * @param value The value
                */
                virtual TAK::Engine::Util::TAKErr bindInt(const std::size_t idx, const int32_t value) NOTHROWS = 0;

                /**
                * Binds the specified <code>long</code> on the specified index.
                *
                * @param idx   The index (one based)
                * @param value The value
                */
                virtual TAK::Engine::Util::TAKErr bindLong(const std::size_t idx, const int64_t value) NOTHROWS = 0;

                /**
                * Binds the specified <code>double</code> on the specified index.
                *
                * @param idx   The index (one based)
                * @param value The value
                */
                virtual TAK::Engine::Util::TAKErr bindDouble(const std::size_t idx, const double value) NOTHROWS = 0;

                /**
                * Binds the specified {@link String} on the specified index.
                *
                * @param idx   The index (one based)
                * @param value The value
                */
                virtual TAK::Engine::Util::TAKErr bindString(const std::size_t idx, const char *value) NOTHROWS = 0;

                /**
                * Binds the specified <code>null</code> on the specified index.
                *
                * @param idx   The index (one based)
                */
                virtual TAK::Engine::Util::TAKErr bindNull(const std::size_t idx) NOTHROWS = 0;

                /**
                * Clears all bindings.
                */
                virtual TAK::Engine::Util::TAKErr clearBindings() NOTHROWS = 0;

            };
        }
    }
}

#endif // TAK_ENGINE_DB_BINDABLE_H_INCLUDED
