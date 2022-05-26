#pragma once

#include "util/Logging2.h"

using namespace TAK::Engine::Util;

namespace TAK {
    namespace Engine {
        namespace Tests {

            class TestLogger : public Logger2 {
            public:

                int print(const LogLevel lvl, const char *fmt, va_list arg) NOTHROWS;
            };

        }
    }
}
