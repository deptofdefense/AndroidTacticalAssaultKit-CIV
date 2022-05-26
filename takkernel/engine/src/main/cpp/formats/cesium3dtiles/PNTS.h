#ifndef TAK_ENGINE_FORMATS_CESIUM3DTILES_PNTS_H_INCLUDED
#define TAK_ENGINE_FORMATS_CESIUM3DTILES_PNTS_H_INCLUDED

#include "util/Error.h"
#include "port/String.h"
#include "util/DataInput2.h"
#include "model/Scene.h"
#include "db/Database2.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace Cesium3DTiles {
                ENGINE_API Util::TAKErr PNTS_parse(Model::ScenePtr &result, Util::DataInput2 *input, const char *baseURI) NOTHROWS;

                struct PNTSInfo {
                };

                ENGINE_API Util::TAKErr PNTS_parseInfo(PNTSInfo*info, Util::DataInput2* input, const char* baseURI) NOTHROWS;
                Util::TAKErr PNTS_write(const char* URI, std::size_t count, DB::Query& query) NOTHROWS;
                Util::TAKErr PNTS_write(const char* URI, uint8_t* pointData, std::size_t numPoints) NOTHROWS;

            }
        }
    }
}

#endif