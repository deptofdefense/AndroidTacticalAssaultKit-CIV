
#include "pch.h"
#include "TakEngineTestsLogging.h"

using namespace TAK::Engine::Tests;


int TestLogger::print(const LogLevel lvl, const char *fmt, va_list arg) NOTHROWS {
    int v(0);
    char buf[512];
    try {
        v = vsnprintf(buf, 512, fmt, arg);
#if _MSC_VER < 1900
        // Per MSDN documentation, VC++ 2013 and prior deviate from
        // standard C in that vsnprintf returns -1 on truncation and does
        // not NULL terminate. The only other stated possible return
        // values is the number of characters written when no truncation
        // occurs.
        if (v == -1)
            buf[511] = '\0';
#endif
    } catch (...) {
        return -1;
    }

    //Logger::WriteMessage(buf);
    return v;
}
