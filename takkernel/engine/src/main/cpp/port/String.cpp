

#include "port/String.h"

#include <new>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <sstream>

#include "util/Memory.h"

using namespace TAK::Engine::Port;

using namespace TAK::Engine::Util;

String::String() NOTHROWS :
    String(nullptr, 0)
{ }

String::String(const char* s) NOTHROWS :
    String(s, !!s ? strlen(s) : 0u)
{ }

String::String(const char* s, std::size_t len) NOTHROWS
{
    if(!s) {
        data.heapAlloc = true;
        data.heap.reset();
        data.stack[0] = '\0';
    } else {
        if(len < stackStorageSize-1u) {
            data.heapAlloc = false;
        } else {
            data.heap.reset(new char[len + 1u]);
            data.heapAlloc = true;
        }
        char* value = data.heapAlloc ? data.heap.get() : data.stack;
        if (len)
            memcpy(value, s, len);
        value[len] = '\0';
    }
}

String::String(const String& rhs) NOTHROWS
{
    *this = rhs;
}

String::~String() NOTHROWS
{}


String& String::operator= (const String& rhs) NOTHROWS
{
    *this = rhs.get();
    return *this;
}

String& String::operator= (const char* rhs) NOTHROWS
{
    if (get() != rhs)
    {
        if(!rhs) {
            data.heapAlloc = true;
            data.heap.reset();
            data.stack[0] = '\0';
        } else {
            const std::size_t len = strlen(rhs);
            if(len < stackStorageSize-1u) {
                data.heapAlloc = false;
            } else {
                data.heap.reset(new char[len + 1u]);
                data.heapAlloc = true;
            }
            char* value = data.heapAlloc ? data.heap.get() : data.stack;
            if (len)
                memcpy(value, rhs, len);
            value[len] = '\0';
        }
    }

    return *this;
}

bool String::operator== (const String& rhs) const NOTHROWS
{
    return (*this) == (const char *)rhs;
}

bool String::operator!= (const String& rhs) const NOTHROWS
{
    return !(*this == rhs);
}

bool String::operator== (const char *rhs) const NOTHROWS
{
    return !!get()
        ? rhs && !std::strcmp(get(), rhs) :
        !rhs;
}

bool String::operator!= (const char *rhs) const NOTHROWS
{
    return !(*this == rhs);
}

String::operator const char* () const NOTHROWS
{
    return get();
}

char& String::operator[] (int index) NOTHROWS
{
    return get()[index];
}

char String::operator[] (int index) const NOTHROWS
{
    return get()[index];
}

char* String::get() NOTHROWS
{
    return data.heapAlloc ? data.heap.get() : data.stack;
}

const char *String::get() const NOTHROWS
{
    return data.heapAlloc ? data.heap.get() : data.stack;
}

TAKErr TAK::Engine::Port::String_parseDouble(double *value, const char *str) NOTHROWS
{
    char *end;
    const double result = std::strtod(str, &end);

    if(!result && end == str)
        return TE_InvalidArg;
    *value = result;
    return TE_Ok;
}
TAKErr TAK::Engine::Port::String_parseInteger(int *value, const char *str, const int base) NOTHROWS
{
    char *end;
    const int result = std::strtol(str, &end, base);

    if(!result && end == str)
        return TE_InvalidArg;
    *value = result;
    return TE_Ok;
}

char* TAK::Engine::Port::String_strcasestr(const char* lhs, const char* rhs) NOTHROWS
{
    const char* p1 = lhs;
    const char* p2 = rhs;
    const char* r = *p2 == 0 ? lhs : 0;

    while (*p1 != 0 && *p2 != 0)
    {
        if (tolower((unsigned char)*p1) == tolower((unsigned char)*p2))
        {
            if (r == 0)
            {
                r = p1;
            }

            p2++;
        }
        else
        {
            p2 = rhs;
            if (r != 0)
            {
                p1 = r + 1;
            }

            if (tolower((unsigned char)*p1) == tolower((unsigned char)*p2))
            {
                r = p1;
                p2++;
            }
            else
            {
                r = 0;
            }
        }

        p1++;
    }

    return *p2 == 0 ? (char*)r : 0;
}


TAKErr TAK::Engine::Port::String_compareIgnoreCase(int *result, const char *lhs, const char *rhs) NOTHROWS
{
    if (!lhs || !rhs || !result)
        return TE_InvalidArg;

#if _WIN32
    *result = _strcmpi(lhs, rhs);
#else
    *result = strcasecmp(lhs, rhs);
#endif
    return TE_Ok;
}

int TAK::Engine::Port::String_strcasecmp(const char *lhs, const char *rhs) NOTHROWS
{
    if(!lhs && !rhs)
        return 0;
    else if(!lhs)
        return 1;
    else if(!rhs)
        return -1;

#if _WIN32
    return _strcmpi(lhs, rhs);
#else
    return strcasecmp(lhs, rhs);
#endif
}

int TAK::Engine::Port::String_strcmp(const char *lhs, const char *rhs) NOTHROWS
{
    if(!lhs && !rhs)
        return 0;
    else if(!lhs)
        return 1;
    else if(!rhs)
        return -1;

    return strcmp(lhs, rhs);
}

bool TAK::Engine::Port::String_less(const char *a, const char *b) NOTHROWS
{
    if(!a && !b)
        return false;
    else if(!a)
        return false;
    else if(!b)
        return true;
    else
        return strcmp(a, b)<0;
}
bool TAK::Engine::Port::String_equal(const char *a, const char *b) NOTHROWS
{
    if(!a && !b)
        return true;
    else if(!a)
        return false;
    else if(!b)
        return true;
    else
        return strcmp(a, b)==0;
}
bool TAK::Engine::Port::String_endsWith(const char *str, const char *suffix) NOTHROWS
{
    if (!str && !suffix)
        return true;
    else if (!str || !suffix)
        return false;
    const std::size_t srclen = strlen(str);
    const std::size_t suffixlen = strlen(suffix);
    if (srclen < suffixlen)
        return false;
    for (std::size_t i = suffixlen; i > 0; i--) {
        if (str[(srclen-suffixlen)+i-1u] != suffix[i-1u])
            return false;
    }
    return true;

}
bool TAK::Engine::Port::String_trim(TAK::Engine::Port::String &value, const char *str) NOTHROWS
{
    if (!str)
        return false;
    const std::size_t srclen = strlen(str);
    std::ostringstream strm;
    for (std::size_t i = 0; i < srclen; i++)
        if (!isspace(str[i]))
            strm << str[i];
    if (strm.str().empty())
        return false;
    value = strm.str().c_str();
    return true;
}
