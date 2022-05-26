#ifndef TAK_ENGINE_FORMATS_LASSCENENODE_H
#define TAK_ENGINE_FORMATS_LASSCENENODE_H

#include "model/SceneNode.h"
#include "math/Matrix2.h"
#include "port/Collection.h"
#include "model/SceneInfo.h"

/**
 * This is just a placeholder for a SceneNode implementation if we need.
 * At this point it's just a copy of the 3D tiles class.
 */

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace LAS {
                class LASSceneNode : public Model::SceneNode {
                public:
                    struct LevelOfDetail {
                        std::size_t levelOfDetail;
                        int64_t meshDataOffset;
                        std::size_t meshDataLength;
                        std::shared_ptr<const Model::Mesh> staticMesh;
                        std::weak_ptr<const Model::Mesh> streamingMesh;
                        std::size_t instanceId;
                    };
                public:
                    LASSceneNode() NOTHROWS;
                    ~LASSceneNode() NOTHROWS;
                public:
                    bool isRoot() const NOTHROWS override;
                    Util::TAKErr getParent(const SceneNode** value) const NOTHROWS override;
                    const Math::Matrix2* getLocalFrame() const NOTHROWS override;
                    Util::TAKErr getChildren(Port::Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS override;
                    bool hasChildren() const NOTHROWS override;
                    bool hasMesh() const NOTHROWS override;
                    const TAK::Engine::Feature::Envelope2& getAABB() const NOTHROWS override;
                    std::size_t getNumLODs() const NOTHROWS override;
                    Util::TAKErr loadMesh(std::shared_ptr<const Model::Mesh>& value, const std::size_t lod, Util::ProcessingCallback *callback) NOTHROWS override;
                    Util::TAKErr getLevelOfDetail(std::size_t* value, const std::size_t lodIdx) const NOTHROWS override;
                    Util::TAKErr getLODIndex(std::size_t* value, const double clod, const int round) const NOTHROWS override;
                    Util::TAKErr getInstanceID(std::size_t* value, const std::size_t lodIdx) const NOTHROWS override;
                    bool hasSubscene() const NOTHROWS override;
                    Util::TAKErr getSubsceneInfo(const Model::SceneInfo** result) NOTHROWS override;
                    bool hasLODNode() const NOTHROWS override;
                    Util::TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;
                private:
                    const SceneNode *parent;
                    std::vector<LevelOfDetail> lods;
                    TAK::Engine::Feature::Envelope2 aabb;
                    std::vector<std::shared_ptr<LASSceneNode>> children;
                    //std::unique_ptr<SceneInfo> subsceneInfo;
                    Math::Matrix2 localFrame;
                    bool hasLocalFrame;
                };
            }
        }
    }
}

#endif //TAK_ENGINE_FORMATS_LASSCENENODE_H
