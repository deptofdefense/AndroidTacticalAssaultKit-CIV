#include "util/Logging.h"

#include "util/Logging2.h"
#include "util/Memory.h"

using namespace atakmap::util;

using namespace TAK::Engine::Util;

namespace
{
    class LoggerAdapter : public Logger2
    {
    public :
        LoggerAdapter(Logger *impl) NOTHROWS;
    public :
        /** returns 0 on success, non-zero on error */
        int print(const LogLevel lvl, const char *fmt, va_list arg) NOTHROWS override;
    private :
        Logger *impl;
    };

    LogLevel adaptLevel(Logger::Level legacyLvl);
    Logger::Level adaptLevel(LogLevel level);
}

Logger::~Logger()
{}

void Logger::setLogger(Logger *l)
{
    if (!l)
        Logger_setLogger(std::move(LoggerPtr(nullptr, nullptr)));
    else
        Logger_setLogger(std::move(LoggerPtr(new LoggerAdapter(l), Memory_deleter_const<Logger2, LoggerAdapter>)));
}

void Logger::setLevel(const Level lvl)
{
    
    Logger_setLevel(adaptLevel(lvl));
}

void Logger::log(const Level lvl, const char *fmt, ...)
{
    // XXX - may not be thread-safe on windows
    va_list args;
    va_start(args, fmt);
    logv(lvl, fmt, args);
    va_end(args);
}

void Logger::logv(const Level lvl, const char *fmt, va_list args) {
    Logger_logv(adaptLevel(lvl), fmt, args);
}


namespace
{

LoggerAdapter::LoggerAdapter(Logger *impl_) NOTHROWS :
    impl(impl_)
{}

int LoggerAdapter::print(const LogLevel lvl, const char *fmt, va_list arg) NOTHROWS
{
    try {
        impl->print(adaptLevel(lvl), fmt, arg);
        return 0;
    } catch (...) {
        return -1;
    }
}

LogLevel adaptLevel(Logger::Level lvl)
{
    LogLevel level;
    switch (lvl) {
    case Logger::All:
        level = TELL_All;
        break;
    case Logger::Debug:
        level = TELL_Debug;
        break;
    case Logger::Error:
        level = TELL_Error;
        break;
    case Logger::Info:
        level = TELL_Info;
        break;
    case Logger::None:
        level = TELL_None;
        break;
    case Logger::Severe:
        level = TELL_Severe;
        break;
    case Logger::Warning:
        level = TELL_Warning;
        break;
    default:
        level = TELL_Severe;
        break;
    }

    return level;
}

Logger::Level adaptLevel(LogLevel lvl)
{
    Logger::Level legacyLvl;
    switch (lvl) {
    case TELL_All :
        legacyLvl = Logger::All;
        break;
    case TELL_Debug :
        legacyLvl = Logger::Debug;
        break;
    case TELL_Error :
        legacyLvl = Logger::Error;
        break;
    case TELL_Info :
        legacyLvl = Logger::Info;
        break;
    case TELL_None :
        legacyLvl = Logger::None;
        break;
    case TELL_Severe :
        legacyLvl = Logger::Severe;
        break;
    case TELL_Warning :
        legacyLvl = Logger::Warning;
        break;
    default:
        legacyLvl = Logger::Severe;
        break;
    }

    return legacyLvl;
}

}
