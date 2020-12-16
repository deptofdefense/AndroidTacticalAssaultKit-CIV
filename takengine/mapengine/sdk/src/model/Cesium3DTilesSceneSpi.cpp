
#include "model/Cesium3DTilesSceneSpi.h"
#include "math/Matrix2.h"
#include "port/Collection.h"
#include "port/STLIteratorAdapter.h"
#include "formats/cesium3dtiles/C3DTTileset.h"
#include "port/StringBuilder.h"
#include "formats/cesium3dtiles/B3DM.h"
#include "port/STLVectorAdapter.h"
#include "model/SceneInfo.h"
#include "model/MeshTransformer.h"
#include "math/Utils.h"
#include "core/ProjectionFactory3.h"
#include "util/URI.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Formats::Cesium3DTiles;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Core;

namespace {
    class C3DTSceneNode : public SceneNode {
    public:
        struct LevelOfDetail {
            std::size_t levelOfDetail;
            int64_t meshDataOffset;
            std::size_t meshDataLength;
            std::shared_ptr<const Mesh> staticMesh;
            std::weak_ptr<const Mesh> streamingMesh;
            std::size_t instanceId;
        };
    public:
        C3DTSceneNode() NOTHROWS;
    public:
        bool isRoot() const NOTHROWS override;
        TAKErr getParent(const SceneNode** value) const NOTHROWS override;
        const Matrix2* getLocalFrame() const NOTHROWS override;
        TAKErr getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS override;
        bool hasChildren() const NOTHROWS override;
        bool hasMesh() const NOTHROWS override;
        const TAK::Engine::Feature::Envelope2& getAABB() const NOTHROWS override;
        std::size_t getNumLODs() const NOTHROWS override;
        TAKErr loadMesh(std::shared_ptr<const Mesh>& value, const std::size_t lod, ProcessingCallback *callback) NOTHROWS override;
        TAKErr getLevelOfDetail(std::size_t* value, const std::size_t lodIdx) const NOTHROWS override;
        TAKErr getLODIndex(std::size_t* value, const double clod, const int round) const NOTHROWS override;
        TAKErr getInstanceID(std::size_t* value, const std::size_t lodIdx) const NOTHROWS override;
        bool hasSubscene() const NOTHROWS override;
        TAKErr getSubsceneInfo(const SceneInfo** result) NOTHROWS override;
        bool hasLODNode() const NOTHROWS override;
        TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;
    public:
        const SceneNode *parent;
        std::vector<LevelOfDetail> lods;
        TAK::Engine::Feature::Envelope2 aabb;
        std::vector<std::shared_ptr<C3DTSceneNode>> children;
        std::unique_ptr<SceneInfo> subsceneInfo;
        Matrix2 localFrame;
        bool hasLocalFrame;
    };

    class C3DTScene : public Scene {
    public:
        ~C3DTScene() NOTHROWS override;
        SceneNode& getRootNode() const NOTHROWS override;
        const Envelope2& getAABB() const NOTHROWS override;
        unsigned int getProperties() const NOTHROWS override;
        std::shared_ptr<C3DTSceneNode> root;
    };

    struct ParseFrame {
        std::shared_ptr<C3DTSceneNode> sceneNode;
        const C3DTTile *tile;
    };

    struct ParseArgs {
        ParseArgs()
            : result(nullptr, nullptr)
        {}

        ProcessingCallback* callbacks;
        const Collection<ResourceAlias>* resourceAliases;        
        std::vector<ParseFrame> stack;
        String baseURI;
        ScenePtr result;
    };

    TAKErr parseVisitor(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) NOTHROWS;
    TAKErr spatiallyPartition(std::shared_ptr<C3DTSceneNode> &node, size_t perNodeLimit) NOTHROWS;
}

//
// Cesium3DTilesSceneSpi
//

Cesium3DTilesSceneSpi::Cesium3DTilesSceneSpi() NOTHROWS {

}

Cesium3DTilesSceneSpi::~Cesium3DTilesSceneSpi() NOTHROWS {

}

const char* Cesium3DTilesSceneSpi::getType() const NOTHROWS {
    return "Cesium3DTiles";
}

int Cesium3DTilesSceneSpi::getPriority() const NOTHROWS {
    return 1;
}

TAKErr Cesium3DTilesSceneSpi::create(ScenePtr& scene, const char* URI, ProcessingCallback* callbacks, const Collection<ResourceAlias>* resourceAliases) const NOTHROWS {

    TAKErr code = TE_Unsupported;
    TAK::Engine::Port::String tilesetURI;
    TAK::Engine::Port::String fileURI;
    TAK::Engine::Port::String baseURI;
    C3DTFileType type;
    bool isStreamingValue = false;

    // catches nullptr URI
    code = C3DT_probeSupport(&type, &fileURI, &tilesetURI, &baseURI, &isStreamingValue, URI);
    if (code != TE_Ok)
        return code;

    DataInput2Ptr input(nullptr, nullptr);
    code = URI_open(input, fileURI);
    if (code != TE_Ok)
        return code;

    if (type == C3DTFileType_TilesetJSON) {
        ParseArgs args;
        args.callbacks = callbacks;
        args.resourceAliases = resourceAliases;
        args.baseURI = baseURI;

        code = C3DTTileset_parse(input.get(), &args, parseVisitor);
        if (code == TE_Ok) {
            code = spatiallyPartition(static_cast<C3DTScene*>(args.result.get())->root, 10);
            TE_CHECKRETURN_CODE(code);
            scene = std::move(args.result);
        }
    } else if (type == C3DTFileType_B3DM) {
        code = B3DM_parse(scene, input.get(), baseURI.get());
    } else {
        code = TE_Unsupported;
    }

    if (input) {
        input->close();
    }

    return code;
}

//
//
//

namespace {
    C3DTSceneNode::C3DTSceneNode() NOTHROWS :
        parent(nullptr),
        hasLocalFrame(false)
    {}
    
    bool C3DTSceneNode::isRoot() const NOTHROWS {
        return !!parent;
    }

    TAKErr C3DTSceneNode::getParent(const SceneNode** value) const NOTHROWS {
        *value = parent;
        return TE_Ok;
    }

    const Matrix2* C3DTSceneNode::getLocalFrame() const NOTHROWS {
        const Matrix2* retval = nullptr;
        if (hasLocalFrame)
            retval = &localFrame;
        return retval;
    }

    TAKErr C3DTSceneNode::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS {
        value = Collection<std::shared_ptr<SceneNode>>::IteratorPtr(
            new STLIteratorAdapter_const<std::shared_ptr<SceneNode>, const std::vector<std::shared_ptr<C3DTSceneNode>>>(children),
            Memory_deleter_const<Iterator2<std::shared_ptr<SceneNode>>, STLIteratorAdapter_const<std::shared_ptr<SceneNode>, const std::vector<std::shared_ptr<C3DTSceneNode>>>>);
        return TE_Ok;
    }

    bool C3DTSceneNode::hasChildren() const NOTHROWS {
        return !children.empty();
    }

    bool C3DTSceneNode::hasMesh() const NOTHROWS {
        return !lods.empty() && !!lods[0].meshDataLength;
    }
    
    const TAK::Engine::Feature::Envelope2& C3DTSceneNode::getAABB() const NOTHROWS {
        return aabb;
    }
    
    std::size_t C3DTSceneNode::getNumLODs() const NOTHROWS {
        return 1;
    //    return lods.size();
    }
    
    TAKErr C3DTSceneNode::loadMesh(std::shared_ptr<const Mesh>& value, const std::size_t lod, ProcessingCallback* callback) NOTHROWS {
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
    
    TAKErr C3DTSceneNode::getLevelOfDetail(std::size_t* value, const std::size_t lod) const NOTHROWS {
        if (lods.empty())
            return TE_IllegalState;
        if (lod >= lods.size())
            return TE_InvalidArg;
        *value = lods[lod].levelOfDetail;
        return TE_Ok;
    }
    
    TAKErr C3DTSceneNode::getLODIndex(std::size_t* value, const double clod, const int round) const NOTHROWS {
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
    
    TAKErr C3DTSceneNode::getInstanceID(std::size_t* value, const std::size_t lod) const NOTHROWS {
        if (lods.empty())
            return TE_IllegalState;
        if (lod >= lods.size())
            return TE_InvalidArg;
        *value = lods[lod].instanceId;
        return TE_Ok;
    }

    bool C3DTSceneNode::hasSubscene() const NOTHROWS {
        return subsceneInfo.get() != nullptr;
    }

    enum C3DTSubsceneType {
        C3DTSubsceneType_B3DM,
        C3DTSubsceneType_Tileset
    };

    TAKErr subsceneType(C3DTSubsceneType *result, const char* URI) NOTHROWS {
        *result = C3DTSubsceneType_B3DM;
        return TE_Ok;
    }

    TAKErr C3DTSceneNode::getSubsceneInfo(const SceneInfo** result) NOTHROWS {
        if (!hasSubscene())
            return TE_IllegalState;
        *result = subsceneInfo.get();
        return TE_Ok;
    }

    bool C3DTSceneNode::hasLODNode() const NOTHROWS {
        return false;
    }
    
    TAKErr C3DTSceneNode::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
        return TE_Unsupported;
    }

    //
    // C3DTScene
    //

    C3DTScene::~C3DTScene() NOTHROWS {

    }

    SceneNode& C3DTScene::getRootNode() const NOTHROWS {
        return *root;
    }

    const Envelope2& C3DTScene::getAABB() const NOTHROWS {
        return root->aabb;
    }

    unsigned int C3DTScene::getProperties() const NOTHROWS {
        return DirectMesh | DirectSceneGraph | SpatiallyPartitioned;
    }


    bool isTileset(const char* URI) {
        return false;
    }

    TAKErr parseVisitor(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) NOTHROWS {

        ParseArgs *args = static_cast<ParseArgs *>(opaque);       
        std::shared_ptr<C3DTSceneNode> node = std::make_shared<C3DTSceneNode>();
        
        if (!args->result) {
            std::unique_ptr<C3DTScene> scene(new C3DTScene());
            scene->root = node;
            args->result = ScenePtr(scene.release(), Memory_deleter_const<Scene, C3DTScene>);
        }

        while (args->stack.size() && args->stack.back().tile != tile->parent) {
            args->stack.pop_back();
        }

        C3DTSceneNode* parent = args->stack.size() ? args->stack.back().sceneNode.get() : nullptr;
        node->children.reserve(tile->childCount);
        if (parent)
            parent->children.push_back(node);
        node->parent = parent;

        if (tile->hasTransform) {
            node->hasLocalFrame = true;
            for (size_t r = 0; r < 4; ++r)
                for (size_t c = 0; c < 4; ++c)
                    node->localFrame.set(r, c, tile->transform[r * 4 + c]);
        }

        C3DTTileset_approximateTileBounds(&node->aabb, tile);

        if (tile->content.uri.get() != nullptr && tile->content.uri != "") {
            TAK::Engine::Port::StringBuilder fullURI;
            
            if (TAK::Engine::Port::String_endsWith(args->baseURI, "/") || TAK::Engine::Port::String_endsWith(args->baseURI, "\\")) {
                if (TAK::Engine::Port::StringBuilder_combine(fullURI, args->baseURI, tile->content.uri) != TE_Ok)
                    return TE_Err;
            } else if (TAK::Engine::Port::StringBuilder_combine(fullURI, args->baseURI, TAK::Engine::Port::Platform_pathSep(), tile->content.uri) != TE_Ok) {
                return TE_Err;
            }

            node->subsceneInfo.reset(new SceneInfo());
            Envelope2 aabb;
            Matrix2 localFrame;
            GeoPoint2 locPoint;

            Matrix2 locTransform;
            C3DTTileset_accumulate(&locTransform, tile);

            Envelope2 node_aabb;
            B3DMInfo b3dmInfo;
            DataInput2Ptr inputPtr(nullptr, nullptr);
            TAKErr code = URI_open(inputPtr, fullURI.c_str());
            if (code != TE_Ok)
                return code;
            code = B3DM_parseInfo(&b3dmInfo, inputPtr.get(), args->baseURI);
            if (code != TE_Ok)
                return code;

            MeshTransformOptions aabb_src;
            aabb_src.srid = 4326;

            MeshTransformOptions aabb_dst;
            aabb_dst.srid = 4978;

            // XXX-- Mesh_transform for aabb's lla -> ECEF not as expected since lla is a polar coordinate system
            // and so the AABB volume isn't a uniform box, but instead is a cut out of a sphere. We really
            // just want the ground level x, y, and z of ECEF volume
            Envelope2 groundedAABB = node->aabb;
            groundedAABB.maxZ = groundedAABB.minZ;

            Mesh_transform(&node_aabb, groundedAABB, aabb_src, aabb_dst);

            Projection2Ptr proj(nullptr, nullptr);
            code = ProjectionFactory3_create(proj, 4978);
            TE_CHECKRETURN_CODE(code);

            proj->inverse(&locPoint, b3dmInfo.rtcCenter);

            node->subsceneInfo->srid = 4978;
            node->subsceneInfo->aabb = Envelope2Ptr(new Envelope2(node_aabb), Memory_deleter_const<Envelope2>);
            node->subsceneInfo->aabb = Envelope2Ptr(new Envelope2(aabb), Memory_deleter_const<Envelope2>);
            node->subsceneInfo->uri = fullURI.c_str();
            node->subsceneInfo->localFrame = Matrix2Ptr(new Matrix2(localFrame), Memory_deleter_const<Matrix2>);
            node->subsceneInfo->location = 
                TAK::Engine::Core::GeoPoint2Ptr(new TAK::Engine::Core::GeoPoint2(locPoint), Memory_deleter_const<TAK::Engine::Core::GeoPoint2>);
            node->subsceneInfo->type = "Cesium3DTiles";
        }

        if (tile->childCount)
            args->stack.push_back({
                node,
                tile
            });

        return TE_Ok;
    }

    TAKErr spatiallyPartition(std::shared_ptr<C3DTSceneNode>& node, size_t perNodeLimit) NOTHROWS {

        TAKErr code = TE_Ok;

        // Organize in quadrant based patitioning based on minX, minY

        std::shared_ptr<C3DTSceneNode> sw, nw, ne, se;
        std::vector<std::shared_ptr<C3DTSceneNode>> newChildren;

        if (node->children.size() > perNodeLimit) {

            double midX = (node->aabb.minX + node->aabb.maxX) / 2.0;
            double midY = (node->aabb.minY + node->aabb.maxY) / 2.0;

            for (std::shared_ptr<C3DTSceneNode>& child : node->children) {
                if (child->aabb.minX < midX) {
                    if (child->aabb.minY < midY) {
                        if (!sw) {
                            sw = std::make_shared<C3DTSceneNode>();
                            sw->aabb = Envelope2(node->aabb.minX, node->aabb.minY, node->aabb.minZ, midX, midY, node->aabb.maxZ);
                            sw->parent = node.get();
                            newChildren.push_back(sw);
                        }
                        sw->children.push_back(child);
                        child->parent = sw.get();
                    } else {
                        if (!nw) {
                            nw = std::make_shared<C3DTSceneNode>();
                            nw->aabb = Envelope2(node->aabb.minX, midY, node->aabb.minZ, midX, node->aabb.maxY, node->aabb.maxZ);
                            nw->parent = node.get();
                            newChildren.push_back(nw);
                        }
                        nw->children.push_back(child);
                        child->parent = nw.get();
                    }
                } else {
                    if (child->aabb.minY < midY) {
                        if (!se) {
                            se = std::make_shared<C3DTSceneNode>();
                            se->aabb = Envelope2(midX, node->aabb.minY, node->aabb.minZ, node->aabb.maxX, midY, node->aabb.maxZ);
                            se->parent = node.get();
                            newChildren.push_back(se);
                        }
                        se->children.push_back(child);
                        child->parent = se.get();
                    } else {
                        if (!ne) {
                            ne = std::make_shared<C3DTSceneNode>();
                            ne->aabb = Envelope2(midX, midY, node->aabb.minZ, node->aabb.maxX, node->aabb.maxY, node->aabb.maxZ);
                            ne->parent = node.get();
                            newChildren.push_back(ne);
                        }
                        ne->children.push_back(child);
                        child->parent = ne.get();
                    }
                }
            }
            node->children = std::move(newChildren);
        }
        
        for (std::shared_ptr<C3DTSceneNode>& child : node->children) {
            code = spatiallyPartition(child, perNodeLimit);
        }

        return code;
    }
}