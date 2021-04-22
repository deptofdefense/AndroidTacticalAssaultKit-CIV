#ifndef TAK_ENGINE_FORMATS_MSACCESS_MSACCESSDATABASEFACTORY_H_INCLUDED
#define TAK_ENGINE_FORMATS_MSACCESS_MSACCESSDATABASEFACTORY_H_INCLUDED

#include "db/Database2.h"
#include "db/DatabaseInformation.h"
#include "db/DatabaseProvider.h"
#include "port/Platform.h"

using namespace TAK::Engine::DB;

namespace TAK {
namespace Engine {
namespace Formats {
namespace MsAccess {
ENGINE_API Util::TAKErr MsAccessDatabaseFactory_registerProvider(const std::shared_ptr<DatabaseProvider> &provider) NOTHROWS;
ENGINE_API Util::TAKErr MsAccessDatabaseFactory_unRegisterProvider(const DatabaseProvider *provider) NOTHROWS;
ENGINE_API Util::TAKErr MsAccessDatabaseFactory_create(DatabasePtr &result, const DatabaseInformation &dbInformation) NOTHROWS;
}  // namespace MsAccess
}  // namespace Formats
}  // namespace Engine
}  // namespace TAK
#endif  // TAK_ENGINE_FORMATS_MSACCESS_MSACCESSDATABASEFACTORY_H_INCLUDED