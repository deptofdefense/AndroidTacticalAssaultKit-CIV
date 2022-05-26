#include "LASSceneNode.h"
#include "port/STLVectorAdapter.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::LAS;

LASSceneNode::LASSceneNode() NOTHROWS :
parent(nullptr),
hasLocalFrame(false)
{}

LASSceneNode::~LASSceneNode() noexcept = default;

bool LASSceneNode::isRoot() const NOTHROWS{
    return parent != nullptr;
}

TAKErr LASSceneNode::getParent(const SceneNode **value) const NOTHROWS {
    *value = parent;
    return TE_Ok;
}

const Matrix2* LASSceneNode::getLocalFrame() const NOTHROWS{
    const Matrix2* retval = nullptr;
    if (hasLocalFrame)
        retval = &localFrame;
    return retval;
}

TAKErr LASSceneNode::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS {
    value = Collection<std::shared_ptr<SceneNode>>::IteratorPtr(
            new STLIteratorAdapter_const<std::shared_ptr<SceneNode>, const std::vector<std::shared_ptr<LASSceneNode>>>(children),
            Memory_deleter_const<Iterator2<std::shared_ptr<SceneNode>>, STLIteratorAdapter_const<std::shared_ptr<SceneNode>, const std::vector<std::shared_ptr<LASSceneNode>>>>);
    return TE_Ok;
}

bool LASSceneNode::hasChildren() const NOTHROWS {
    return !children.empty();
}

bool LASSceneNode::hasMesh() const NOTHROWS {
    return !lods.empty() && !!lods[0].meshDataLength;
}

const Envelope2& LASSceneNode::getAABB() const NOTHROWS {
    return aabb;
}

std::size_t LASSceneNode::getNumLODs() const NOTHROWS {
    return 1;
    //    return lods.size();
}

TAKErr LASSceneNode::loadMesh(std::shared_ptr<const Mesh>& value, const std::size_t lod, ProcessingCallback* callback) NOTHROWS {
    if (lods.empty())
        return TE_IllegalState;
    if (lod >= lods.size())
        return TE_InvalidArg;
    if (!lods[lod].meshDataLength)
        return TE_IllegalState;

    // XXX - should support mesh loading abstraction
    if (lods[lod].staticMesh.get()) {
        value = lods[lod].staticMesh;
    }
    else {
        value = lods[lod].streamingMesh.lock();
        if (lods[lod].streamingMesh.expired())
            return TE_Err;
    }
    return TE_Ok;
}

TAKErr LASSceneNode::getLevelOfDetail(std::size_t* value, const std::size_t lod) const NOTHROWS {
    if (lods.empty())
        return TE_IllegalState;
    if (lod >= lods.size())
        return TE_InvalidArg;
    *value = lods[lod].levelOfDetail;
    return TE_Ok;
}

TAKErr LASSceneNode::getLODIndex(std::size_t* value, const double clod, const int round) const NOTHROWS {
    if (lods.empty())
        return TE_IllegalState;
    if (clod < lods[0].levelOfDetail) {
        *value = 0u;
        return TE_Ok;
    }
    else if (clod > lods[lods.size() - 1u].levelOfDetail) {
        *value = lods.size() - 1u;
        return TE_Ok;
    }
    else if (round > 0) {
        for (std::size_t i = lods.size(); i > 0; i--) {
            if (clod >= lods[i - 1u].levelOfDetail) {
                *value = i - 1u;
                return TE_Ok;
            }
        }
        return TE_IllegalState;
    }
    else if (round < 0) {
        for (std::size_t i = 0; i < lods.size(); i++) {
            if (clod <= lods[i].levelOfDetail) {
                *value = i;
                return TE_Ok;
            }
        }
        return TE_IllegalState;
    }
    else { // round == 0
        for (std::size_t i = 0; i < lods.size() - 1u; i++) {
            if (clod >= lods[i].levelOfDetail && clod <= lods[i + 1u].levelOfDetail) {
                const double a = clod - (double)lods[i].levelOfDetail;
                const double b = (double)lods[i].levelOfDetail - clod;
                if (a <= b)
                    *value = i;
                else // b < a
                    *value = i + 1u;
                return TE_Ok;
            }
        }
        return TE_IllegalState;
    }
}

TAKErr LASSceneNode::getInstanceID(std::size_t* value, const std::size_t lod) const NOTHROWS {
    if (lods.empty())
        return TE_IllegalState;
    if (lod >= lods.size())
        return TE_InvalidArg;
    *value = lods[lod].instanceId;
    return TE_Ok;
}

bool LASSceneNode::hasSubscene() const NOTHROWS {
    return false; //subsceneInfo.get() != nullptr;
}

enum LASSubsceneType {
    LASSubsceneType_B3DM,
    LASSubsceneType_Tileset
};

TAKErr subsceneType(LASSubsceneType *result, const char* URI) NOTHROWS {
    *result = LASSubsceneType_B3DM;
    return TE_Ok;
}

TAKErr LASSceneNode::getSubsceneInfo(const SceneInfo** result) NOTHROWS {
    if (!hasSubscene())
        return TE_IllegalState;
    //*result = subsceneInfo.get();
    return TE_Ok;
}

bool LASSceneNode::hasLODNode() const NOTHROWS {
    return false;
}

TAKErr LASSceneNode::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
    return TE_Unsupported;
}