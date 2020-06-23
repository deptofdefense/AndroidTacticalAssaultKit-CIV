#include "commologger.h"
#include "commologger_cli.h"

#include <msclr/marshal.h>

#ifndef LOGGERIMPL_H
#define LOGGERIMPL_H

namespace TAK {
    namespace Commo {
        namespace impl {
            class LoggerImpl : public atakmap::commoncommo::CommoLogger
            {
            public:
                LoggerImpl(TAK::Commo::ICommoLogger ^logger);

                virtual void log(atakmap::commoncommo::CommoLogger::Level level, const char *message);

            private:
                gcroot<TAK::Commo::ICommoLogger ^> loggerCLI;
            };
        }
    }
}

#endif
