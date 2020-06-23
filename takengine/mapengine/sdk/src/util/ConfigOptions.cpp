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
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getOptionsMapMutex());
    TE_CHECKRETURN_CODE(code);

    std::map<std::string, std::string> &options = getOptionsMap();
    std::map<std::string, std::string>::iterator entry;

    entry = options.find(key);
    if (entry == options.end())
        return TE_InvalidArg;
    value = entry->second.c_str();
    return TE_Ok;
}

TAKErr TAK::Engine::Util::ConfigOptions_setOption(const char *key, const char *value) NOTHROWS
{
    if (!key)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getOptionsMapMutex());
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
