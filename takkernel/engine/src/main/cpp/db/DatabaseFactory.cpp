#include "db/DatabaseFactory.h"

#include <map>
#include <vector>

#include "db/DefaultDatabaseProvider.h"
#include "util/CopyOnWrite.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

namespace {
class DatabaseFactoryRegistry {
   public:
    TAKErr registerProvider(const std::shared_ptr<DatabaseProvider> &provider) NOTHROWS;
    TAKErr unRegisterProvider(const DatabaseProvider *provider) NOTHROWS;
    std::vector<std::shared_ptr<DatabaseProvider>> priority_map = {std::make_shared<DefaultDatabaseProvider>()};
};

CopyOnWrite<DatabaseFactoryRegistry> &getGlobalDatabaseFactoryRegistry();
}  // namespace

TAKErr TAK::Engine::DB::DatabaseFactory_registerProvider(const std::shared_ptr<DatabaseProvider> &provider) NOTHROWS {
    return ::getGlobalDatabaseFactoryRegistry().invokeWrite(&DatabaseFactoryRegistry::registerProvider, provider);
}

TAKErr TAK::Engine::DB::DatabaseFactory_unRegisterProvider(const DatabaseProvider *provider) NOTHROWS {
    return ::getGlobalDatabaseFactoryRegistry().invokeWrite(&DatabaseFactoryRegistry::unRegisterProvider, provider);
}

TAKErr TAK::Engine::DB::DatabaseFactory_create(DatabasePtr &result, const DatabaseInformation &dbInformation) NOTHROWS {
    TAKErr code = TE_Unsupported;
    const char* tmpUri;
    dbInformation.getUri(&tmpUri);

    if(!tmpUri){
        DefaultDatabaseProvider defaultProvider;
        code = defaultProvider.create(result, dbInformation);
    } else{
        auto registry = getGlobalDatabaseFactoryRegistry().read();
        if(registry->priority_map.size())
            code = registry->priority_map[registry->priority_map.size()-1u]->create(result, dbInformation);
    }
    return code;
}

namespace {

TAKErr DatabaseFactoryRegistry::registerProvider(const std::shared_ptr<DatabaseProvider> &provider) NOTHROWS {
    if (!provider)
        return TE_InvalidArg;
    this->priority_map.emplace_back(provider);
    return TE_Ok;
}

TAKErr DatabaseFactoryRegistry::unRegisterProvider(const DatabaseProvider *provider) NOTHROWS {
    for (auto it = this->priority_map.begin(); it != this->priority_map.end();) {
        if ((*it).get() == provider)
            it = this->priority_map.erase(it);
        else
            ++it;
    }
    return TE_Ok;
}

CopyOnWrite<DatabaseFactoryRegistry> &getGlobalDatabaseFactoryRegistry() {
    static CopyOnWrite<DatabaseFactoryRegistry> inst;
    return inst;
}
}  // namespace