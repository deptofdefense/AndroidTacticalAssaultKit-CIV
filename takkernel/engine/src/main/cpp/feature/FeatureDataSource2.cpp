#include "feature/FeatureDataSource2.h"

#include <map>
#include <queue>
#include <set>

#include "feature/FeatureDataSource.h"
#include "feature/LegacyAdapters.h"
#include "feature/Style.h"
#include "thread/RWMutex.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    typedef std::map<TAK::Engine::Port::String, std::shared_ptr<FeatureDataSource2>, TAK::Engine::Port::StringLess> FeatureDataSourceMap;
    typedef std::vector<std::pair<int,std::shared_ptr<FeatureDataSource2>>>  FeatureDataSourcePriorityVector;


    RWMutex &providerMapMutex() NOTHROWS;
    FeatureDataSourceMap& getProviderMap() NOTHROWS;
    FeatureDataSourcePriorityVector& getProviderPriorityVector() NOTHROWS;
}

FeatureDataSource2::~FeatureDataSource2() NOTHROWS
{}

FeatureDataSource2::Content::~Content() NOTHROWS
{}

TAKErr TAK::Engine::Feature::FeatureDataSourceFactory_parse(FeatureDataSource2::ContentPtr &content, const char *path, const char *hint) NOTHROWS
{
    TAKErr code(TE_Ok);
    ReadLock lock(providerMapMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    // return ContentPtr for current FeatureDataSource implemenations
    FeatureDataSourceMap featureDataSourceMap(getProviderMap());
    FeatureDataSourceMap::iterator it;

    if (hint) {
        std::shared_ptr<FeatureDataSource2> spi;

        // look up provider directly if a hint is specified
        it = featureDataSourceMap.find(hint);
        if (it != featureDataSourceMap.end())
        {
            spi = it->second;
            if (spi.get() != nullptr)
                return spi->parse(content, path);
        }
    } 
    else {
        FeatureDataSourcePriorityVector &providers = getProviderPriorityVector();
        // iterate all available providers. choose the first that supports
        for (auto iter = providers.begin(); iter != providers.end(); iter++)
        {
            auto source  = iter->second;
            code = source->parse(content, path);
            if (code == TE_Ok)
                return code;
        }
    }

    // fall-through to legacy

    // return ContentPtr for legacy FeatureDataSource implemenations
    std::unique_ptr<atakmap::feature::FeatureDataSource::Content, void(*)(const atakmap::feature::FeatureDataSource::Content *)> parsed(nullptr, nullptr);
    try {
        parsed = std::unique_ptr<atakmap::feature::FeatureDataSource::Content, void(*)(const atakmap::feature::FeatureDataSource::Content *)>(atakmap::feature::FeatureDataSource::parse(path, hint), Memory_deleter_const<atakmap::feature::FeatureDataSource::Content>);
    } catch (...) {}
    if (!parsed.get())
        return TE_InvalidArg;
    return LegacyAdapters_adapt(content, std::move(parsed));
}

TAKErr TAK::Engine::Feature::FeatureDataSourceFactory_getProvider(std::shared_ptr<FeatureDataSource2> &spi, const char *hint) NOTHROWS
{
    if (!hint)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    ReadLock map_lock(providerMapMutex());
    code = map_lock.status;
    TE_CHECKRETURN_CODE(code);

    // check if feature data source can found amongst current implementations
    FeatureDataSourceMap featureDataSourceMap(getProviderMap());
    auto it = featureDataSourceMap.find(hint);
    if (it != featureDataSourceMap.end())
    {
        spi = it->second;
    }
    else
    {
        // check legacy feature data source implemenations
        static std::map<const atakmap::feature::FeatureDataSource *, std::shared_ptr<FeatureDataSource2>> legacyToAdapted;
        static Mutex mutex; // guards 'legacyToAdapted' and 'adaptedSpis'

        const atakmap::feature::FeatureDataSource *legacy = atakmap::feature::FeatureDataSource::getProvider(hint);
        if (!legacy)
            return TE_InvalidArg;

        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        std::map<const atakmap::feature::FeatureDataSource *, std::shared_ptr<FeatureDataSource2>>::iterator entry;
        entry = legacyToAdapted.find(legacy);
        if (entry != legacyToAdapted.end()) {
            spi = entry->second;
            return TE_Ok;
        }

        FeatureDataSourcePtr adapter(nullptr, nullptr);
        code = LegacyAdapters_adapt(adapter, *legacy);
        TE_CHECKRETURN_CODE(code);

        spi = std::move(adapter);
        legacyToAdapted[legacy] = spi;
    }
    return TE_Ok;
}

TAKErr TAK::Engine::Feature::FeatureDataSourceFactory_registerProvider(FeatureDataSourcePtr &&provider_, const int priority) NOTHROWS
{
    std::shared_ptr<FeatureDataSource2> provider = std::move(provider_);
    return FeatureDataSourceFactory_registerProvider(provider, priority);
}

TAKErr TAK::Engine::Feature::FeatureDataSourceFactory_registerProvider(const std::shared_ptr<FeatureDataSource2> &provider, const int priority) NOTHROWS
{
    if (!provider.get())
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    WriteLock lock(providerMapMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    FeatureDataSourceMap &featureDataSourceMap = getProviderMap();
    featureDataSourceMap[provider->getName()] = provider;

    const std::pair <int, std::shared_ptr<FeatureDataSource2>> prioritySource(priority, provider);
    FeatureDataSourcePriorityVector &priorityQueue = getProviderPriorityVector();
    priorityQueue.push_back(prioritySource);
    std::sort(priorityQueue.begin(), priorityQueue.end(), std::greater<std::pair<int, std::shared_ptr<FeatureDataSource2>>>());

    return TE_Ok;
}

TAKErr TAK::Engine::Feature::FeatureDataSourceFactory_unregisterProvider(const FeatureDataSource2 &provider) NOTHROWS
{
    TAKErr code(TE_Ok);
    WriteLock lock(providerMapMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    FeatureDataSourceMap &featureDataSourceMap = getProviderMap();
    FeatureDataSourcePriorityVector &featureDatasourcesByPriority = getProviderPriorityVector();
    FeatureDataSourceMap::iterator entry;
    for (entry = featureDataSourceMap.begin(); entry != featureDataSourceMap.end(); entry++) {
        if (entry->second.get() == &provider) {

            featureDataSourceMap.erase(entry);
			for (FeatureDataSourcePriorityVector::iterator it = featureDatasourcesByPriority.begin(); it != featureDatasourcesByPriority.end(); it++) {
				if (it->second.get() == &provider) {
					featureDatasourcesByPriority.erase(it);
					return TE_Ok;
				}
			}
            return TE_Ok;
        }
    }


    return TE_InvalidArg;
}

namespace
{
    RWMutex &providerMapMutex() NOTHROWS
    {
        static RWMutex providerMapMutex;
        return providerMapMutex;
    }

    FeatureDataSourceMap& getProviderMap() NOTHROWS
    {
        static FeatureDataSourceMap providerMap;
        return providerMap;
    }

    FeatureDataSourcePriorityVector& getProviderPriorityVector() NOTHROWS
    {
        static FeatureDataSourcePriorityVector providerPriorityVector;
        return providerPriorityVector;
    }
}
