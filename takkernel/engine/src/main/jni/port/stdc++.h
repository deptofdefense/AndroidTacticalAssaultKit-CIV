#ifndef CESIUM3DTILES_PORT_STDCXX_H_INCLUDED
#define CESIUM3DTILES_PORT_STDCXX_H_INCLUDED

// required if using gnustl_static or gnustl_shared
#ifdef __ANDROID__

#include <cstdlib>
#include <sstream>
#include <string>

namespace std {
    template<class T>
    inline std::string to_string(const T &v)
    {
        std::ostringstream strm;
        strm << v;
        return strm.str();
    }
    inline float strtof(const char *str, char **endptr)
    {
        return ::strtof(str, endptr);
    }
    inline double strtold(const char *str, char **endptr)
    {
        return ::strtold(str, endptr);
    }
    inline long long int strtoll(const char *str, char **endptr, int base = 10)
    {
        return ::strtoll(str, endptr, base);
    }
    inline unsigned long long int strtoull(const char *str, char **endptr, int base = 10)
    {
        return ::strtoull(str, endptr, base);
    }
    inline int stoi(const std::string &str, std::size_t *numRead, int base = 10)
    {
        const char *cstr = str.c_str();
        char *endptr;
        const int result = strtol(cstr, &endptr, base);
        if(numRead)
            *numRead = (endptr-cstr);
        return result;
    }
    inline int snprintf(char *s, size_t m, const char *fmt, ...)
    {
        va_list args;
        va_start(args, fmt);
        int result = ::vsnprintf(s, m , fmt, args);
        va_end(args);
        return result;
    }
}

#endif

#endif
