#ifndef TAK_ENGINE_DB_DATABASEPROVIDER_H_INCLUDED
#define TAK_ENGINE_DB_DATABASEPROVIDER_H_INCLUDED

#include "db/Database2.h"
#include "db/DatabaseInformation.h"
#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace DB {
class ENGINE_API DatabaseProvider {
   protected:
    virtual ~DatabaseProvider() NOTHROWS = default;
   public :
    virtual Util::TAKErr create(DatabasePtr& result, const DatabaseInformation& information) NOTHROWS = 0;
    virtual Util::TAKErr getType(const char** value) NOTHROWS = 0;
};
typedef std::unique_ptr<DatabaseProvider, void (*)(const DatabaseProvider*)> DatabaseProviderPtr;
}
}  // namespace Engine
}  // namespace TAK
#endif  // TAK_ENGINE_DB_DATABASEPROVIDER_H_INCLUDED