#include "port/Platform.h"

#define TE_HAVE_CXX_CHRONO 1

#ifdef MSVC
#define TE_HAVE_WINDOWS_TIME 1
#endif

#if TE_HAVE_CXX_CHRONO
#include <chrono>
#elif TE_HAVE_POSIX_SYSTIME
#include <sys/tiim.h>
#elif TE_HAVE_WINDOWS_TIME
#include <Windows.h>
#endif

using namespace TAK::Engine::Port;

int64_t TAK::Engine::Port::Platform_systime_millis() NOTHROWS
{
#if TE_HAVE_CXX_CHRONO
    using namespace std::chrono;

    // derived from https://stackoverflow.com/questions/19555121/how-to-get-current-timestamp-in-milliseconds-since-1970-just-the-way-java-gets
    milliseconds ms = duration_cast<milliseconds>(system_clock::now().time_since_epoch());
    return ms.count();
#elif TE_HAVE_POSIX_SYSTIME
    // derived from above
    struct timeval tp;
    gettimeofday(&tp, NULL);
    long int ms = tp.tv_sec * 1000 + tp.tv_usec / 1000;
#elif TE_HAVE_WINDOWS_TIME
    // derived from https://stackoverflow.com/questions/1695288/getting-the-current-time-in-milliseconds-from-the-system-clock-in-windows
    FILETIME ft_now;
    GetSystemTimeAsFileTime(&ft_now);
    int64_t ll_now;
    ll_now = (LONGLONG)ft_now.dwLowDateTime + ((LONGLONG)(ft_now.dwHighDateTime) << 32LL);

    // convert from 100ns intervals to milliseconds
    ll_now /= 10000LL;

    // convert from Windows Epoch to UNIX epoch
    ll_now -= 116444736000000000LL;

    return ll_now;
#else
    you_must_define_a_system_time_function_for_your_platform();
#endif
}

char TAK::Engine::Port::Platform_pathSep() NOTHROWS {
#if _WIN32
    return '\\';
#else
    return '/';
#endif
}