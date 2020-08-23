#ifndef TAK_ENGINE_DB_DEFAULTDATABASEPROVIDER_H_INCLUDED
#define TAK_ENGINE_DB_DEFAULTDATABASEPROVIDER_H_INCLUDED

#include "db/DatabaseProvider.h"
#include "db/Database2.h"
#include "db/DatabaseInformation.h"
#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace DB {
class ENGINE_API DefaultDatabaseProvider : public DatabaseProvider {
   public:
    DefaultDatabaseProvider() NOTHROWS = default;
    ~DefaultDatabaseProvider() NOTHROWS override = default;
    Util::TAKErr create(DatabasePtr& result, const DatabaseInformation& information) NOTHROWS override;
    Util::TAKErr getType(const char** value) NOTHROWS override;
};
}  // namespace DB
}  // namespace Engine
}  // namespace TAK
#endif  // TAK_ENGINE_DB_DEFAULTDATABASEPROVIDER_H_INCLUDED