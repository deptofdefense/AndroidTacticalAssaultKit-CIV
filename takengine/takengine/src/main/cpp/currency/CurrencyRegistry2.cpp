#include "currency/CurrencyRegistry2.h"

#include "currency/Currency2.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Currency;

using namespace TAK::Engine::Thread;

CatalogCurrencyRegistry2::CatalogCurrencyRegistry2()
{}

void CatalogCurrencyRegistry2::registerCurrency(CatalogCurrency2 *instance) NOTHROWS
{
    Lock lock(mutex);
    registeredInstances[instance->getName()] = instance;
}

void CatalogCurrencyRegistry2::deregisterCurrency(CatalogCurrency2 *instance) NOTHROWS
{
    Lock lock(mutex);
    registeredInstances.erase(instance->getName());
}

void CatalogCurrencyRegistry2::deregisterCurrency(const char *name) NOTHROWS
{
    Lock lock(mutex);
    registeredInstances.erase(name);
}
CatalogCurrency2 *CatalogCurrencyRegistry2::getCurrency(const char *name) NOTHROWS
{
    Lock lock(mutex);
    std::map<TAK::Engine::Port::String, CatalogCurrency2 *>::iterator entry;
    entry = registeredInstances.find(name);
    if (entry == registeredInstances.end())
        return nullptr;
    return entry->second;
}
