#include "db/DefaultDatabaseProvider.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

TAKErr DefaultDatabaseProvider::create(DatabasePtr& result, const DatabaseInformation& information) NOTHROWS {
    const char* uri;
    information.getUri(&uri);
    int options;
    information.getOptions(&options);
    bool read_only = (options & DATABASE_OPTIONS_READONLY) == DATABASE_OPTIONS_READONLY;
    return Databases_openDatabase(result, uri, read_only);
}

TAKErr DefaultDatabaseProvider::getType(const char** value) NOTHROWS {
    if (value)
        *value = "";
    return TE_Ok;
}
