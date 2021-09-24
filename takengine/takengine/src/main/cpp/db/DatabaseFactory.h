#ifndef TAK_ENGINE_DB_DATABASEFACTORY_H_INCLUDED
#define TAK_ENGINE_DB_DATABASEFACTORY_H_INCLUDED

#include "db/DatabaseProvider.h"
#include "db/Database2.h"
#include "db/DatabaseInformation.h"
#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace DB {
ENGINE_API Util::TAKErr DatabaseFactory_registerProvider(const std::shared_ptr<DatabaseProvider> &provider) NOTHROWS;
ENGINE_API Util::TAKErr DatabaseFactory_unRegisterProvider(const DatabaseProvider *provider) NOTHROWS;
ENGINE_API Util::TAKErr DatabaseFactory_create(DatabasePtr &result, const DatabaseInformation &dbInformation) NOTHROWS;
}  // namespace DB
}  // namespace Engine
}  // namespace TAK
#endif  // TAK_ENGINE_DB_DATABASEFACTORY_H_INCLUDED