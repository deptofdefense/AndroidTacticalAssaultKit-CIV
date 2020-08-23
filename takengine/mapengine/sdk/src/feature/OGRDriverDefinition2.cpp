#include "feature/OGRDriverDefinition2.h"

#include <map>

#include "thread/Lock.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    struct LT_ignore_case
    {
        bool operator()(const std::string &a, const std::string &b) const
        {
            return TAK::Engine::Port::String_strcasecmp(a.c_str(), b.c_str()) < 0;
        }
    };

    typedef std::map<std::string, std::shared_ptr<OGRDriverDefinition2Spi>, LT_ignore_case> SpiRegistry;
    SpiRegistry &getSpis();
    Mutex &getSpisMutex();
}

OGRDriverDefinition2::~OGRDriverDefinition2() NOTHROWS
{}

OGRDriverDefinition2Spi::~OGRDriverDefinition2Spi() NOTHROWS
{}

TAKErr TAK::Engine::Feature::OGRDriverDefinition2_create(OGRDriverDefinition2Ptr &value, const char *path, const char *type) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(getSpisMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    SpiRegistry &spis = getSpis();

    SpiRegistry::iterator it;
    it = spis.find(type);
    if (it == spis.end())
        return TE_InvalidArg;
    return it->second->create(value, path);
}

TAKErr TAK::Engine::Feature::OGRDriverDefinition2_registerSpi(const std::shared_ptr<OGRDriverDefinition2Spi> &spi) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(getSpisMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    SpiRegistry &spis = getSpis();

    spis[spi->getType()] = spi;
    return TE_Ok;
}

TAKErr TAK::Engine::Feature::OGRDriverDefinition2_unregisterSpi(const OGRDriverDefinition2Spi *spi) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(getSpisMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    SpiRegistry &spis = getSpis();

    SpiRegistry::iterator it;
    for (it = spis.begin(); it != spis.end(); it++) {
        if (it->second.get() == spi) {
            spis.erase(it);
            return TE_Ok;
        }
    }
    return TE_InvalidArg;
}

namespace
{
    SpiRegistry &getSpis()
    {
        static SpiRegistry spis;
        return spis;
    }

    Mutex &getSpisMutex()
    {
        static Mutex spisMutex;
        return spisMutex;
    }
}
