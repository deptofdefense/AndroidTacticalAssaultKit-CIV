#include "currency/CurrencyRegistry2.h"

#include "thread/Lock.h"

#include "currency/Currency2.h"

using namespace TAK::Engine::Currency;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

CatalogCurrencyRegistry2::CatalogCurrencyRegistry2()
{}

void CatalogCurrencyRegistry2::registerCurrency(CatalogCurrency2 *instance) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("CatalogCurrencyRegistry2::registerCurrency: Failed to acquire mutex");
    this->registeredInstances[instance->getName()] = instance;
}

void CatalogCurrencyRegistry2::deregisterCurrency(CatalogCurrency2 *instance) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("CatalogCurrencyRegistry2::deregisterCurrency: Failed to acquire mutex");
    this->registeredInstances.erase(instance->getName());
}

void CatalogCurrencyRegistry2::deregisterCurrency(const char *name) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("CatalogCurrencyRegistry2::deregisterCurrency: Failed to acquire mutex");
    this->registeredInstances.erase(name);
}

CatalogCurrency2 *CatalogCurrencyRegistry2::getCurrency(const char *name) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("CatalogCurrencyRegistry2::getCurrency: Failed to acquire mutex");
    std::map<PGSC::String, CatalogCurrency2 *, PGSC::StringLess>::iterator entry;
    entry = this->registeredInstances.find(name);
    if (entry == this->registeredInstances.end())
        return NULL;
    return entry->second;
}
