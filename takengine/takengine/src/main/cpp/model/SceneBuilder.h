#ifndef TAK_ENGINE_MODEL_SCENEBUILDER_H_INCLUDED
#define TAK_ENGINE_MODEL_SCENEBUILDER_H_INCLUDED

#include "math/Matrix2.h"
#include "model/Mesh.h"
#include "model/Scene.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API SceneBuilder
            {
            public :
                explicit SceneBuilder(const TAK::Engine::Math::Matrix2 &rootTransform, const bool direct) NOTHROWS;
                SceneBuilder(const bool direct) NOTHROWS;
                ~SceneBuilder() NOTHROWS;
            public :
                Util::TAKErr addMesh(MeshPtr_const &&mesh, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addMesh(MeshPtr &&mesh, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addMesh(const std::shared_ptr<Mesh> &mesh, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addMesh(const std::shared_ptr<const Mesh> &mesh, const Math::Matrix2 *localFrame) NOTHROWS;
                
                // instanced meshes
                Util::TAKErr addMesh(MeshPtr_const &&mesh, const std::size_t instanceId, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addMesh(MeshPtr &&mesh, const std::size_t instanceId, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addMesh(const std::shared_ptr<Mesh> &mesh, const std::size_t instanceId, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addMesh(const std::shared_ptr<const Mesh> &mesh, const std::size_t instanceId, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addMesh(const std::size_t instanceId, const Math::Matrix2 *localFrame) NOTHROWS;

                Util::TAKErr build(ScenePtr &value) NOTHROWS;

                /**
                 * Push a new node
                 */
                Util::TAKErr push(const Math::Matrix2 *localFrame) NOTHROWS;

                /**
                 * Pop the current node
                 */
                Util::TAKErr pop() NOTHROWS;

                int nodeDepth() const NOTHROWS;
            private :
                ScenePtr impl;
            };

            ENGINE_API Util::TAKErr SceneBuilder_build(ScenePtr &value, SceneNodePtr &&root, const bool direct) NOTHROWS;
        }
    }
}

#endif
