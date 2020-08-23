#include "util/Logging2.h"

#include "thread/Mutex.h"
#include "thread/Lock.h"

#include "util/Memory.h"

using namespace TAK::Engine::Util;


#define LOG_SYNC() \
    if(destructed) \
        return; \
    TAK::Engine::Thread::Lock lock(logMutex());

namespace
{
    static int destructed = 0;

    class StdOutLogger : public Logger2
    {
    public :
        StdOutLogger() NOTHROWS;
        ~StdOutLogger() NOTHROWS override;
    public :
        int print(const LogLevel lvl, const char *fmt, va_list args) NOTHROWS override;
    };

    class LoggerContext
    {
    public:
        LoggerContext() {}
        ~LoggerContext()
        {
            destructed = 1;
        }
    public:
        TAK::Engine::Thread::Mutex mutex;
        StdOutLogger defaultLogger;
    };

    LoggerContext &context()
    {
        static LoggerContext c;
        return c;
    }
    TAK::Engine::Thread::Mutex &logMutex()
    {
        return context().mutex;
    }

    Logger2 &defaultLogger()
    {
        return context().defaultLogger;
    }

    LoggerPtr &logger()
    {
        static LoggerPtr loggerPtr(&defaultLogger(), Memory_leaker_const<Logger2>);
        return loggerPtr;
    }

    LogLevel logLevel = TELL_All;
    
}

Logger2::~Logger2() NOTHROWS
{}

void TAK::Engine::Util::Logger_setLogger(LoggerPtr &&l) NOTHROWS
{
    if (destructed) return;

    LOG_SYNC();
    logger() = std::move(l);
}

void TAK::Engine::Util::Logger_setLevel(const LogLevel lvl) NOTHROWS
{
    if (destructed) return;

    LOG_SYNC();
    logLevel = lvl;
}

void TAK::Engine::Util::Logger_log(const LogLevel lvl, const char *fmt, ...) NOTHROWS
{
#ifndef __ANDROID__
    try {
#endif
        if (destructed) return;
        LOG_SYNC();
        if (!logger().get()) return;
        if (lvl < logLevel) return;

        va_list args;
        va_start(args, fmt);
        const int err = logger()->print(lvl, fmt, args);
        va_end(args);
        if (err)
            return;
#ifndef __ANDROID__
    } catch (...) {
        // XXX - mutex is invalid sometimes during teardown if objects log
        //       during last pump of disposer.  need to resolve.
    }
#endif

}

void TAK::Engine::Util::Logger_logv(const LogLevel lvl, const char *fmt, va_list args) NOTHROWS
{
#ifndef __ANDROID__
    try {
#endif
        if (destructed) return;

        LOG_SYNC();
        if (!logger().get()) return;
        if (lvl < logLevel) return;

        const int err = logger()->print(lvl, fmt, args);
        if(err)
            return;
#ifndef __ANDROID__
    } catch (...) {
        // XXX - mutex is invalid sometimes during teardown if objects log
        //       during last pump of disposer.  need to resolve.
    }
#endif
}


namespace
{

StdOutLogger::StdOutLogger() NOTHROWS
{}

StdOutLogger::~StdOutLogger() NOTHROWS
{
    fflush(stdout);
}

int StdOutLogger::print(const LogLevel lvl, const char *fmt, va_list arg) NOTHROWS
{
    vfprintf(stdout, fmt, arg);
    fprintf(stdout, "\n");
    return 0;
}

}
