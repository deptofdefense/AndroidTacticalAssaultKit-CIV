#ifndef TAK_ENGINE_MODEL_SCENEGRAPHBUILDER_H_INCLUDED
#define TAK_ENGINE_MODEL_SCENEGRAPHBUILDER_H_INCLUDED

#include <set>

#include "feature/Envelope2.h"
#include "math/Matrix2.h"
#include "model/SceneNode.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API SceneGraphBuilder
            {
            public :
                SceneGraphBuilder(const Math::Matrix2 *rootLocalFrame) NOTHROWS;
                SceneGraphBuilder(const Feature::Envelope2 &aabb, const Math::Matrix2 *rootLocalFrame, MeshPtr_const &&rootMesh) NOTHROWS;
                SceneGraphBuilder(const Feature::Envelope2 &aabb, const Math::Matrix2 *rootLocalFrame, const std::shared_ptr<const Mesh> &rootMesh) NOTHROWS;
                ~SceneGraphBuilder() NOTHROWS;
            public :
                SceneNode &getRoot() const NOTHROWS;
                Util::TAKErr addNode(SceneNode **value, const SceneNode &parent, const Math::Matrix2 *localFrame) NOTHROWS;
                Util::TAKErr addNode(SceneNode **value, const SceneNode &parent, const Math::Matrix2 *localFrame, const Feature::Envelope2 &aabb, MeshPtr_const &&mesh) NOTHROWS;
                Util::TAKErr addNode(SceneNode **value, const SceneNode &parent, const Math::Matrix2 *localFrame, const Feature::Envelope2 &aabb, const std::shared_ptr<const Mesh> &mesh) NOTHROWS;

                Util::TAKErr addNode(SceneNode **value, const SceneNode &parent, const Math::Matrix2 *localFrame, const Feature::Envelope2 &aabb, const std::shared_ptr<const Mesh> &mesh, const std::size_t instanceId) NOTHROWS;

                Util::TAKErr build(SceneNodePtr &value) NOTHROWS;
            private :
                SceneNodePtr root;
                std::set<const SceneNode *> nodes;
            };

            
            ENGINE_API Util::TAKErr SceneGraphBuilder_build(SceneNodePtr &value, const Math::Matrix2 *localFrame, MeshPtr_const &&meshes) NOTHROWS;
            ENGINE_API Util::TAKErr SceneGraphBuilder_build(SceneNodePtr &value, const Math::Matrix2 *localFrame, const std::shared_ptr<const Mesh> &meshes) NOTHROWS;
            ENGINE_API Util::TAKErr SceneGraphBuilder_build(SceneNodePtr &value, const Math::Matrix2 *localFrame, Port::Collection<std::shared_ptr<const Mesh>> &meshes) NOTHROWS;
        }
    }
}
#endif
