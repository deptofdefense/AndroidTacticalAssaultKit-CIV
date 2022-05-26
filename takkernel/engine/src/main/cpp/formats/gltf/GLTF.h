#ifndef TAK_ENGINE_FORMATS_GLTF_GLTF_H_INCLUDED
#define TAK_ENGINE_FORMATS_GLTF_GLTF_H_INCLUDED

#include "util/Error.h"
#include "util/DataInput2.h"
#include "model/Scene.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace GLTF {                
                ENGINE_API Util::TAKErr GLTF_load(Model::ScenePtr &scene, Util::DataInput2 *input, const char *baseURI) NOTHROWS;
                ENGINE_API Util::TAKErr GLTF_load(Model::ScenePtr& scene, const uint8_t* binary, size_t len, const char* baseURI) NOTHROWS;
                ENGINE_API Util::TAKErr GLTF_loadV1(Model::ScenePtr& result, const uint8_t* binary, size_t len, const char* baseURI) NOTHROWS;
                ENGINE_API Util::TAKErr GLTF_loadV2(Model::ScenePtr& result, const uint8_t* binary, size_t len, const char* baseURI) NOTHROWS;
                ENGINE_API void GLTF_initMeshAABB(Feature::Envelope2& aabb) NOTHROWS;
            }
        }
    }
}

#endif
