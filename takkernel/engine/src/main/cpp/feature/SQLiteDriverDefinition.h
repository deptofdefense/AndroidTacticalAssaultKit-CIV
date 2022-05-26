#ifndef TAK_ENGINE_FEATURE_SQLITEDRIVERDEFINITION_H_INCLUDED
#define TAK_ENGINE_FEATURE_SQLITEDRIVERDEFINITION_H_INCLUDED

#include "feature/DefaultDriverDefinition2.h"

namespace TAK {
namespace Engine {
namespace Feature {
class ENGINE_API SQLiteDriverDefinition : public DefaultDriverDefinition2 {
   public:
    class ENGINE_API Spi;
};

class ENGINE_API SQLiteDriverDefinition::Spi : public OGRDriverDefinition2Spi {
   public:
    virtual Util::TAKErr create(OGRDriverDefinition2Ptr& value, const char* path) NOTHROWS;
    virtual const char* getType() const NOTHROWS;
};

#define SQLITE_DRIVER_NAME "SQLite"

}  // namespace Feature
}  // namespace Engine
}  // namespace TAK

#endif
