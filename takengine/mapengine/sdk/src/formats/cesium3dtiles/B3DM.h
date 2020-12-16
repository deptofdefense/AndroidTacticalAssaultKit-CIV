#ifndef TAK_ENGINE_FORMATS_CESIUM3DTILES_B3DM_H_INCLUDED
#define TAK_ENGINE_FORMATS_CESIUM3DTILES_B3DM_H_INCLUDED

#include "util/Error.h"
#include "port/String.h"
#include "util/DataInput2.h"
#include "model/Scene.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace Cesium3DTiles {
                ENGINE_API Util::TAKErr B3DM_parse(Model::ScenePtr &result, Util::DataInput2 *input, const char *baseURI) NOTHROWS;

                struct B3DMInfo {
                    TAK::Engine::Math::Point2<double> rtcCenter;
                };

                ENGINE_API Util::TAKErr B3DM_parseInfo(B3DMInfo *info, Util::DataInput2* input, const char* baseURI) NOTHROWS;
            }
        }
    }
}

#endif