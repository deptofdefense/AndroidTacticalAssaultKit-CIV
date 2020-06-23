#pragma once

#include "commologger.h"
#include "commologger_cx.h"

namespace TAK {
    namespace Commo {
        namespace impl {
            class LoggerImpl : public atakmap::commoncommo::CommoLogger
            {
            public:
                LoggerImpl(TAK::Commo::ICommoLogger ^logger);

                virtual void log(atakmap::commoncommo::CommoLogger::Level level, const char *message);

            private:
                TAK::Commo::ICommoLogger^ _loggerCx;
            };
        }
    }
}