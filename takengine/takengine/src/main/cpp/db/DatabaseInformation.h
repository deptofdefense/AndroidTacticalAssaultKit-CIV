#ifndef TAK_ENGINE_DB_DATABASEINFORMATION_H_INCLUDED
#define TAK_ENGINE_DB_DATABASEINFORMATION_H_INCLUDED

#include "db/Database2.h"
#include "port/Platform.h"
#include "port/String.h"

namespace TAK {
namespace Engine {
namespace DB {
enum {
    DATABASE_OPTIONS_NONE = 0,

    DATABASE_OPTIONS_READONLY = 0x000001
};
struct ENGINE_API DatabaseInformation {
   public:
    DatabaseInformation(const char* uri);
    DatabaseInformation(const char* uri, const char* passphrase, int options);

    Util::TAKErr getUri(const char** value) const NOTHROWS;
    Util::TAKErr getPassphrase(const char** value) const NOTHROWS;
    Util::TAKErr getOptions(int* value) const NOTHROWS;

   private:
    Port::String uri_;
    Port::String passphrase_;
    int options_;
};
}  // namespace DB
}  // namespace Engine
}  // namespace TAK
#endif  // TAK_ENGINE_DB_DATABASEINFORMATION_H_INCLUDED