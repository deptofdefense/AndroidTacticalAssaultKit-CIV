#pragma once

#include <memory>

#include "port/Platform.h"

struct hexdump;

namespace TAK {
    namespace Engine {
        namespace Tests {

            class HexdumpLogger {
            public:
                HexdumpLogger() NOTHROWS;
                void log(const char* message, void *buf, const size_t &len) NOTHROWS;

            private:

                class HexdumpDeleter {
                public:
                    inline void operator()(hexdump *ptr) const NOTHROWS;
                };

                typedef std::unique_ptr<hexdump, HexdumpDeleter> HexdumpPtr;

                HexdumpPtr           hexDump;
            };
        }
    }
}
