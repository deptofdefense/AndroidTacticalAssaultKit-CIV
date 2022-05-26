#ifndef TAK_ENGINE_UTIL_LOGGING2_H_INCLUDED
#define TAK_ENGINE_UTIL_LOGGING2_H_INCLUDED

#include <cstdarg>
#include <memory>

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            enum LogLevel
            {
                TELL_All,
                TELL_Debug,
                TELL_Info,
                TELL_Warning,
                TELL_Error,
                TELL_Severe,
                TELL_None,
            };

            class ENGINE_API Logger2
            {
            protected :
                virtual ~Logger2() NOTHROWS = 0;
            public :
                /** returns 0 on success, non-zero on error */
                virtual int print(const LogLevel lvl, const char *fmt, va_list arg) NOTHROWS = 0;
            };

            typedef std::unique_ptr<Logger2, void(*)(const Logger2 *)> LoggerPtr;

			ENGINE_API void Logger_setLogger(LoggerPtr &&logger) NOTHROWS;
			ENGINE_API void Logger_setLevel(const LogLevel lvl) NOTHROWS;
			ENGINE_API void Logger_log(const LogLevel lvl, const char *fmt, ...) NOTHROWS;
			ENGINE_API void Logger_logv(const LogLevel lvl, const char *fmt, va_list args) NOTHROWS;
        }
    }
}

#endif // ATAKMAP_UTIL_LOGGING_H_INCLUDED
