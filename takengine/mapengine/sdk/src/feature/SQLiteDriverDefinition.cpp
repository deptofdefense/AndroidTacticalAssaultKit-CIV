#include "feature/SQLiteDriverDefinition.h"

#include "formats/pfps/DrsDriverDefinition.h"
#include "port/String.h"
#include "util/Error.h"
#include "util/IO2.h"
#include "util/Memory.h"

#define SQLITE_DRIVER_NAME "SQLite"

using namespace atakmap::feature;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Formats::Pfps;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

TAKErr SQLiteDriverDefinition::Spi::create(OGRDriverDefinition2Ptr& value, const char* path) NOTHROWS {
    Port::String ext;
    TAKErr code = IO_getExt(ext, path);
    TE_CHECKRETURN_CODE(code);
    int drs = -1;
    String_compareIgnoreCase(&drs, ext, ".drs");
    const bool is_drs = (drs == 0);
    if (is_drs) {
        value = OGRDriverDefinition2Ptr(new DrsDriverDefinition(), Memory_deleter_const<OGRDriverDefinition2, DrsDriverDefinition>);
        return TE_Ok;
    }

    return TE_InvalidArg;
}

const char* SQLiteDriverDefinition::Spi::getType() const NOTHROWS { return SQLITE_DRIVER_NAME; }