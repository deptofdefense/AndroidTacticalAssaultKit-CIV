#include "util/ConfigOptions.h"

#include <map>
#include <string>

#include "thread/Lock.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    Mutex &getOptionsMapMutex();
    std::map<std::string, std::string> &getOptionsMap();
}

TAKErr TAK::Engine::Util::ConfigOptions_getOption(String &value, const char *key) NOTHROWS
{
    if (!key)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(getOptionsMapMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<std::string, std::string> &options = getOptionsMap();
    std::map<std::string, std::string>::iterator entry;

    entry = options.find(key);
    if (entry == options.end())
        return TE_InvalidArg;
    value = entry->second.c_str();
    return TE_Ok;
}

int TAK::Engine::Util::ConfigOptions_getIntOptionOrDefault(const char *option, int defVal) NOTHROWS
{
    Port::String sval;
    Util::TAKErr err = Util::ConfigOptions_getOption(sval, option);
    if (err == Util::TE_InvalidArg)
        return defVal;
    int n;
    if (Port::String_parseInteger(&n, sval) == Util::TE_Ok)
        return n;
    return defVal;
}

double TAK::Engine::Util::ConfigOptions_getDoubleOptionOrDefault(const char *option, double defVal) NOTHROWS
{
    Port::String sval;
    Util::TAKErr err = Util::ConfigOptions_getOption(sval, option);
    if (err == Util::TE_InvalidArg)
        return defVal;
    double n;
    if (Port::String_parseDouble(&n, sval) == Util::TE_Ok)
        return n;
    return defVal;
}

TAKErr TAK::Engine::Util::ConfigOptions_setOption(const char *key, const char *value) NOTHROWS
{
    if (!key)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(getOptionsMapMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<std::string, std::string> &options = getOptionsMap();
    if (value) {
        options[key] = value;
        return TE_Ok;
    } else {
        std::map<std::string, std::string>::iterator entry;
        entry = options.find(key);
        if (entry == options.end())
            return TE_InvalidArg;
        options.erase(entry);
        return TE_Ok;
    }
}

namespace
{
    Mutex &getOptionsMapMutex()
    {
        static Mutex optionsMapMutex;
        return optionsMapMutex;
    }

    std::map<std::string, std::string> &getOptionsMap()
    {
        static std::map<std::string, std::string> optionsMap;
        return optionsMap;
    }
}
