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

#include <cstring>

using namespace TAK::Engine::Port;

template <typename S, typename D>
struct Convert {
    static void func(void* dst, const void* src, size_t count) NOTHROWS {
        D* dp = static_cast<D *>(dst);
        const S* sp = static_cast<const S *>(src);
        while (count > 0) {
            *dp++ = static_cast<D>(*sp++);
            --count;
        }
    }
};

#define MEMCPY_SPEC(t, t2) \
template <> \
struct Convert<t, t2> { \
    static void func(void* dst, const void* src, size_t count) NOTHROWS { \
        memcpy(dst, src, count); \
    } \
};

MEMCPY_SPEC(uint8_t, uint8_t);
MEMCPY_SPEC(uint8_t, int8_t);
MEMCPY_SPEC(int8_t, int8_t);
MEMCPY_SPEC(int8_t, uint8_t);

MEMCPY_SPEC(uint16_t, uint16_t);
MEMCPY_SPEC(uint16_t, int16_t);
MEMCPY_SPEC(int16_t, uint16_t);
MEMCPY_SPEC(int16_t, int16_t);

MEMCPY_SPEC(uint32_t, uint32_t);
MEMCPY_SPEC(uint32_t, int32_t);
MEMCPY_SPEC(int32_t, int32_t);
MEMCPY_SPEC(int32_t, uint32_t);

MEMCPY_SPEC(uint64_t, uint64_t);
MEMCPY_SPEC(uint64_t, int64_t);
MEMCPY_SPEC(int64_t, int64_t);
MEMCPY_SPEC(int64_t, uint64_t);

MEMCPY_SPEC(float, float);

MEMCPY_SPEC(double, double);

typedef void (*ConvertFunc)(void*, const void*, size_t);

/*
TEDT_UInt8,
TEDT_Int8,
TEDT_UInt16,
TEDT_Int16,
TEDT_UInt32,
TEDT_Int32,
TEDT_UInt64,
TEDT_Int64,
TEDT_Float32,
TEDT_Float64
*/

#define CONV_ROW(t) \
    Convert<t, uint8_t>::func, \
    Convert<t, int8_t>::func, \
    Convert<t, uint16_t>::func, \
    Convert<t, int16_t>::func, \
    Convert<t, uint32_t>::func, \
    Convert<t, int32_t>::func, \
    Convert<t, uint64_t>::func, \
    Convert<t, int64_t>::func, \
    Convert<t, float>::func, \
    Convert<t, double>::func \

ConvertFunc convertMatrix[] = {
    CONV_ROW(uint8_t),
    CONV_ROW(int8_t),
    CONV_ROW(uint16_t),
    CONV_ROW(int16_t),
    CONV_ROW(uint32_t),
    CONV_ROW(int32_t),
    CONV_ROW(uint64_t),
    CONV_ROW(int64_t),
    CONV_ROW(float),
    CONV_ROW(double)
};

size_t TAK::Engine::Port::DataType_convert(void* dst, const void* src, size_t count, DataType dstType, DataType srcType) NOTHROWS {

    if (dstType < 0 || dstType > TEDT_Float64)
        return 0;
    if (srcType < 0 || srcType > TEDT_Float64)
        return 0;

    if (count != 0 && (dst == nullptr || src == nullptr))
        return 0;

    void (*cvt)(void *, const void *, size_t) = convertMatrix[srcType * 10 + dstType];
    cvt(dst, src, count);
    return count;
}

size_t TAK::Engine::Port::DataType_size(DataType dataType) NOTHROWS {
    switch (dataType) {
    case TEDT_UInt8:
    case TEDT_Int8:
        return 1;
    case TEDT_UInt16:
    case TEDT_Int16:
        return 2;
    case TEDT_UInt32:
    case TEDT_Int32:
    case TEDT_Float32:
        return 4;
    case TEDT_UInt64:
    case TEDT_Int64:
    case TEDT_Float64:
        return 8;
    }
    return 0;
}

bool TAK::Engine::Port::DataType_isFloating(DataType dataType) NOTHROWS {
    return dataType == TEDT_Float32 ||
        dataType == TEDT_Float64;
}

bool TAK::Engine::Port::DataType_isInteger(DataType dataType) NOTHROWS {
    switch (dataType) {
    case TEDT_UInt8:
    case TEDT_Int8:
    case TEDT_UInt16:
    case TEDT_Int16:
    case TEDT_UInt32:
    case TEDT_Int32:
    case TEDT_UInt64:
    case TEDT_Int64:
        return true;
    }
    return false;
}

bool TAK::Engine::Port::DataType_isUnsignedInteger(DataType dataType) NOTHROWS {
    switch (dataType) {
    case TEDT_UInt8:
    case TEDT_UInt16:
    case TEDT_UInt32:
    case TEDT_UInt64:
        return true;
    }
    return false;
}



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