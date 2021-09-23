#ifndef ATAKMAP_UTIL_LOGGING_H_INCLUDED
#define ATAKMAP_UTIL_LOGGING_H_INCLUDED

#include <cstdarg>

#include "port/Platform.h"

namespace atakmap {
namespace util {

class ENGINE_API Logger
{
public :
    enum Level {
        All,
        Debug,
        Info,
        Warning,
        Error, 
        Severe,
        None
    };

public :
    virtual ~Logger() = 0;
public :
    virtual void print(const Level lvl, const char *fmt, va_list arg) = 0;
public :
    static void setLogger(Logger *logger);
    static void setLevel(const Level lvl);
    static void log(const Level lvl, const char *fmt, ...);
    static void logv(const Level lvl, const char *fmt, va_list args);
};


}
}

#endif // ATAKMAP_UTIL_LOGGING_H_INCLUDED
