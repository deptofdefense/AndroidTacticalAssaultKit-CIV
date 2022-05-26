#ifndef TAK_ENGINE_MODEL_SCENE_H_INCLUDED
#define TAK_ENGINE_MODEL_SCENE_H_INCLUDED

#include <memory>

#include "feature/Envelope2.h"
#include "math/Point2.h"
#include "model/Mesh.h"
#include "model/SceneNode.h"
#include "model/ResourceMapper.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"
#include "util/Error.h"
#include "util/ProcessingCallback.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API Scene
            {
            public :
                enum Properties
                {
                    /**
                     * if set, the source of the scene is streaming from a
                     * remote source
                     */
                    Streaming = 0x01,
                    /**
                     * if set, the Scene Graph is constructed directly from the
                     * serialized representation with little to no parsing
                     */
                    DirectSceneGraph = 0x02,
                    /**
                     * If set, mesh data is extracted directly from the
                     * serialized representation with little to no parsing
                     */
                    DirectMesh = 0x04,

                    /**
                     * If set, the Scene Graph is spatially
                     * patitioned for effiencient occlusion
                     */
                    SpatiallyPartitioned = 0x08,
                };
            public :
                virtual ~Scene() NOTHROWS;

                /**
                 * Returns the root note of the scene graph.
                 */
                virtual SceneNode &getRootNode() const NOTHROWS = 0;
                /**
                 * Returns the AABB for the scene.
                 */
                virtual const Feature::Envelope2 &getAABB() const NOTHROWS = 0;
                /**
                 * Returns the bitwise-OR of the various properties for the
                 * scene.
                 */
                virtual unsigned int getProperties() const NOTHROWS = 0;
            };

            typedef std::unique_ptr<Scene, void(*)(const Scene *)> ScenePtr;
            typedef std::unique_ptr<const Scene, void(*)(const Scene *)> ScenePtr_const;

            class ENGINE_API SceneSpi {
            public:
                virtual ~SceneSpi() NOTHROWS;

                virtual const char *getType() const NOTHROWS = 0;
                virtual int getPriority() const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr create(ScenePtr &scene, 
                    const char *URI, 
                    Util::ProcessingCallback *callbacks,
                    const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) const NOTHROWS = 0;

            };

            ENGINE_API TAK::Engine::Util::TAKErr SceneFactory_registerSpi(const std::shared_ptr<SceneSpi> &spiPtr) NOTHROWS;
            ENGINE_API TAK::Engine::Util::TAKErr SceneFactory_unregisterSpi(const std::shared_ptr<SceneSpi> &spiPtr) NOTHROWS;

            ENGINE_API TAK::Engine::Util::TAKErr SceneFactory_create(ScenePtr &scene, 
                const char *URI, 
                const char *hint, 
                Util::ProcessingCallback *callbacks,
                const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) NOTHROWS;

            ENGINE_API Util::TAKErr SceneFactory_encode(const char *path, const Scene &scene) NOTHROWS;
            ENGINE_API Util::TAKErr SceneFactory_decode(ScenePtr &scene, const char *path, const bool streaming) NOTHROWS;
        }
    }
}
#endif
