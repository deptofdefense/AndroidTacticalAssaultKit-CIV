
#include "model/Scene.h"

#include <algorithm>
#include <map>

#include "model/MeshBuilder.h"
#include "model/SceneBuilder.h"
#include "port/STLIteratorAdapter.h"
#include "util/CopyOnWrite.h"


#define TE_SERIALIZED_MESH_HEADER_RESERVED 7u

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

typedef std::unique_ptr<void, void(*)(const void *)> VoidPtr;
typedef std::unique_ptr<const void, void(*)(const void *)> VoidPtr_const;

extern "C" void te_free_v(const void *buf);
extern "C" void *te_alloc_v(const std::size_t size);

Scene::~Scene() NOTHROWS
{}

SceneSpi::~SceneSpi() NOTHROWS
{}

namespace {
    class SceneSpiRegistry {
    public:
        TAK::Engine::Util::TAKErr registerSpi(const std::shared_ptr<SceneSpi> &spiPtr);
        TAK::Engine::Util::TAKErr unregisterSpi(const std::shared_ptr<SceneSpi> &spiPtr);
        TAK::Engine::Util::TAKErr create(ScenePtr &scene, const char *URI, const char *hint, ProcessingCallback *callbacks,
            const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) const NOTHROWS;

    private:
        // multiset::find() with arbitrary key types is C++14, so use multimap workaround
        struct CStringLess {
            bool operator()(const char *lhs, const char *rhs) const NOTHROWS {
                return strcmp(lhs, rhs) < 0;
            }
        };

        std::multimap<const char *, std::shared_ptr<SceneSpi>, CStringLess> hint_sorted;
        std::multimap<int, std::shared_ptr<SceneSpi>> priority_sorted;
    };

    class StreamingSceneNode : public SceneNode
    {
    public :
        struct LevelOfDetail
        {
            std::size_t levelOfDetail {0};
            int64_t meshDataOffset {0};
            std::size_t meshDataLength {0};
            std::shared_ptr<const Mesh> staticMesh;
            std::weak_ptr<const Mesh> streamingMesh;
            std::size_t instanceId {0};
        };
    public :
        StreamingSceneNode() NOTHROWS;
    public :
        bool isRoot() const NOTHROWS override;
        TAKErr getParent(const SceneNode **value) const NOTHROWS override;
        const Matrix2 *getLocalFrame() const NOTHROWS override;
        TAKErr getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS override;
        bool hasChildren() const NOTHROWS override;
        bool hasMesh() const NOTHROWS override;
        const TAK::Engine::Feature::Envelope2 &getAABB() const NOTHROWS override;
        std::size_t getNumLODs() const NOTHROWS override;
        TAKErr loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lod, ProcessingCallback *callback) NOTHROWS override;
        TAKErr getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS override;
        TAKErr getLODIndex(std::size_t *value, const double clod, const int round) const NOTHROWS override;
        TAKErr getInstanceID(std::size_t *value, const std::size_t lodIdx) const NOTHROWS override;
        bool hasSubscene() const NOTHROWS override;
        TAKErr getSubsceneInfo(const SceneInfo** result) NOTHROWS override;
        bool hasLODNode() const NOTHROWS override;
        TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;
    public :
        const SceneNode *parent;
        std::vector<LevelOfDetail> lods;
        TAK::Engine::Feature::Envelope2 aabb;
        std::vector<std::shared_ptr<StreamingSceneNode>> children;
        Matrix2 localFrame;
        bool hasLocalFrame;
        std::size_t headerRecordLength;
    };

    template<class T>
    class FileMark
    {
    public :
        FileMark(T &file_) NOTHROWS :
            file(file_)
        {
            valid = file.tell(&mark);
        }
        ~FileMark() NOTHROWS
        {
            if(valid == TE_Ok)
                file.seek(mark);
        }
    private :
        T &file;
        TAKErr valid;
        int64_t mark;
    };
    CopyOnWrite<SceneSpiRegistry> &sharedSceneRegistry() {
        static CopyOnWrite<SceneSpiRegistry> registry;
        return registry;
    }

    TAKErr prepareEncode(std::unique_ptr<StreamingSceneNode> &value, int64_t &dataOff, SceneNode &src, std::map<std::size_t, int64_t> &instanceOffsets, const StreamingSceneNode *parent) NOTHROWS;

    TAKErr encodeMesh(DataOutput2 &dst, const Mesh &mesh) NOTHROWS;
    TAKErr computeMeshEncodeLength(std::size_t *value, const Mesh &mesh) NOTHROWS;
    TAKErr decodeMesh(MeshPtr_const &value, DataInput2 &src) NOTHROWS;

    TAKErr encodeSceneNode(FileOutput2 &dst, std::map<std::size_t, bool> &meshInstanceEncoded, const StreamingSceneNode &node) NOTHROWS;
    TAKErr decodeSceneNode(std::unique_ptr<StreamingSceneNode> &value, FileInput2 &src, std::map<std::size_t, std::shared_ptr<const Mesh>> &instanceMeshes, const uint8_t version) NOTHROWS;

    int64_t computeMeshDataOffsetShift(StreamingSceneNode &node) NOTHROWS;
    void shiftMeshDataOffsets(StreamingSceneNode &node, const int64_t shift) NOTHROWS;

    std::size_t getDataTypeSize(const DataType &type) NOTHROWS
    {
        switch(type) {
        case TEDT_Int8 :
        case TEDT_UInt8 :
            return 1u;
        case TEDT_Int16 :
        case TEDT_UInt16 :
            return 1u;
        case TEDT_Int32 :
        case TEDT_UInt32 :
        case TEDT_Float32 :
            return 4u;
        case TEDT_Float64 :
            return 8u;
        }

        return 0u;
    }
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneFactory_registerSpi(const std::shared_ptr<SceneSpi> &spiPtr) NOTHROWS {
    return sharedSceneRegistry().invokeWrite(&SceneSpiRegistry::registerSpi, spiPtr);
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneFactory_unregisterSpi(const std::shared_ptr<SceneSpi> &spiPtr) NOTHROWS {
    return sharedSceneRegistry().invokeWrite(&SceneSpiRegistry::unregisterSpi, spiPtr);
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneFactory_create(ScenePtr &scene, const char *URI, const char *hint, ProcessingCallback *callbacks,
    const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) NOTHROWS {
    std::shared_ptr<const SceneSpiRegistry> registry = sharedSceneRegistry().read();
    if (URI && registry) {
        return registry->create(scene, URI, hint, callbacks, resourceAliases);
    }
    return TE_InvalidArg;
}

TAKErr TAK::Engine::Model::SceneFactory_encode(const char *path, const Scene &scene) NOTHROWS
{
    TAKErr code(TE_Ok);

    // compute header
    // we will traverse the source graph, populating the streaming
    // representation (records basic data model data and offset/length for
    // meshes).
    std::unique_ptr<StreamingSceneNode> stream;
    int64_t dataOff = 0LL;
    std::map<std::size_t, int64_t> instanceOffsets;
    code = prepareEncode(stream, dataOff, scene.getRootNode(), instanceOffsets, nullptr);

    // write header
    FileOutput2 sink;
    code = sink.open(path);
    TE_CHECKRETURN_CODE(code);
    
    uint8_t header[7u] = { /*magic*/ 'T', 'A', 'K', 'B', 'S', 'G', /*version*/ 0x2u };
    // header
    sink.write(header, 7u);

    // recurse stream nodes, writing data records
    std::map<std::size_t, bool> meshInstanceEncoded;
    code = encodeSceneNode(sink, meshInstanceEncoded, *stream);
    TE_CHECKRETURN_CODE(code);

    return TE_Ok;
}
TAKErr TAK::Engine::Model::SceneFactory_decode(ScenePtr &value, const char *file, const bool streaming) NOTHROWS
{
    TAKErr code(TE_Ok);
    FileInput2 src;
    code = src.open(file);
    TE_CHECKRETURN_CODE(code);

    uint8_t header[7u];
    std::size_t numRead;
    code = src.read(header, &numRead, 7u);
    TE_CHECKRETURN_CODE(code);

    uint8_t v1header[7u] = { /*magic*/ 'T', 'A', 'K', 'B', 'S', 'G', /*version*/ 0x1u };
    uint8_t v2header[7u] = { /*magic*/ 'T', 'A', 'K', 'B', 'S', 'G', /*version*/ 0x2u };

    if (memcmp(header, v1header, 7u) != 0 && memcmp(header, v2header, 7u) != 0)
        return TE_InvalidArg;

    std::unique_ptr<StreamingSceneNode> root;
    std::map<std::size_t, std::shared_ptr<const Mesh>> instanceMeshes;
    code = decodeSceneNode(root, src, instanceMeshes, header[6u]);
    TE_CHECKRETURN_CODE(code);

    code = SceneBuilder_build(value, std::move(SceneNodePtr(root.release(), Memory_deleter_const<SceneNode, StreamingSceneNode>)), true);
    TE_CHECKRETURN_CODE(code);

    return code;
}

namespace {
    template <typename Container, typename K>
    void eraseIf(Container &m, const K &key, const std::shared_ptr<SceneSpi> &spi) {
        auto range = m.equal_range(key);
        for (auto it = range.first; it != range.second; ++it) {
            if (it->second == spi) {
                m.erase(it);
                break;
            }
        }
    }

    template <typename Iter>
    TAKErr createImpl(Iter begin, Iter end, ScenePtr &scene, const char *URI, ProcessingCallback *callbacks,
        const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) NOTHROWS {
        while (begin != end) {
            if (ProcessingCallback_isCanceled(callbacks))
                return TE_Canceled;
            TAKErr code;
            if ((code = begin->second->create(scene, URI, callbacks, resourceAliases)) != TE_Unsupported) {
                return code;
            }
            ++begin;
        }
        return TE_Unsupported;
    }

    TAK::Engine::Util::TAKErr SceneSpiRegistry::registerSpi(const std::shared_ptr<SceneSpi> &spiPtr) {
        hint_sorted.insert(std::make_pair(spiPtr->getType(), spiPtr));
        priority_sorted.insert(std::make_pair(spiPtr->getPriority(), spiPtr));
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr SceneSpiRegistry::unregisterSpi(const std::shared_ptr<SceneSpi> &spiPtr) {
        eraseIf(priority_sorted, spiPtr->getPriority(), spiPtr);
        eraseIf(hint_sorted, spiPtr->getType(), spiPtr);
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr SceneSpiRegistry::create(ScenePtr &scene, const char *URI, const char *hint, ProcessingCallback *callbacks,
        const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) const NOTHROWS {
        if (hint) {
            auto range = hint_sorted.equal_range(hint);
            return createImpl(range.first, range.second, scene, URI, callbacks, resourceAliases);
        }
        return createImpl(priority_sorted.begin(), priority_sorted.end(), scene, URI, callbacks, resourceAliases);
    }

    StreamingSceneNode::StreamingSceneNode() NOTHROWS :
        parent(nullptr),
        hasLocalFrame(false),
        headerRecordLength(1u + //hasLocalFrame
                           (6u*sizeof(double)) + // aabb
                           4u + // numLods
                           4u) // numChildren
    {}
    bool StreamingSceneNode::isRoot() const NOTHROWS
    {
        return !!parent;
    }
    TAKErr StreamingSceneNode::getParent(const SceneNode **value) const NOTHROWS
    {
        *value = parent;
        return TE_Ok;
    }
    const Matrix2 *StreamingSceneNode::getLocalFrame() const NOTHROWS
    {
        const Matrix2 *retval = nullptr;
        if (hasLocalFrame)
            retval = &localFrame;
        return retval;
    }
    TAKErr StreamingSceneNode::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS
    {
        value = Collection<std::shared_ptr<SceneNode>>::IteratorPtr(
            new STLIteratorAdapter_const<std::shared_ptr<SceneNode>, const std::vector<std::shared_ptr<StreamingSceneNode>>>(children),
            Memory_deleter_const<Iterator2<std::shared_ptr<SceneNode>>, STLIteratorAdapter_const<std::shared_ptr<SceneNode>, const std::vector<std::shared_ptr<StreamingSceneNode>>>>);
        return TE_Ok;
    }
    bool StreamingSceneNode::hasChildren() const NOTHROWS
    {
        return !children.empty();
    }
    bool StreamingSceneNode::hasMesh() const NOTHROWS
    {
        return !lods.empty() && !!lods[0].meshDataLength;
    }
    const TAK::Engine::Feature::Envelope2 &StreamingSceneNode::getAABB() const NOTHROWS
    {
        return aabb;
    }
    std::size_t StreamingSceneNode::getNumLODs() const NOTHROWS
    {
        return lods.size();
    }
    TAKErr StreamingSceneNode::loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lod, ProcessingCallback *callback) NOTHROWS
    {
        if (lods.empty())
            return TE_IllegalState;
        if (lod >= lods.size())
            return TE_InvalidArg;
        if (!lods[lod].meshDataLength)
            return TE_IllegalState;

        // XXX - should support mesh loading abstraction
        if (lods[lod].staticMesh.get()) {
            value = lods[lod].staticMesh;
        } else {
            value = lods[lod].streamingMesh.lock();
            if (lods[lod].streamingMesh.expired())
                return TE_Err;
        } 
        return TE_Ok;
    }
    TAKErr StreamingSceneNode::getLevelOfDetail(std::size_t *value, const std::size_t lod) const NOTHROWS
    {
        if (lods.empty())
            return TE_IllegalState;
        if (lod >= lods.size())
            return TE_InvalidArg;
        *value = lods[lod].levelOfDetail;
        return TE_Ok;
    }
    TAKErr StreamingSceneNode::getLODIndex(std::size_t *value, const double clod, const int round) const NOTHROWS
    {
        if (lods.empty())
            return TE_IllegalState;
        if(clod < lods[0].levelOfDetail) {
            *value = 0u;
            return TE_Ok;
        } else if (clod > lods[lods.size() - 1u].levelOfDetail) {
            *value = lods.size() - 1u;
            return TE_Ok;
        } else if(round > 0) {
            for (std::size_t i = lods.size(); i > 0; i--) {
                if (clod >= lods[i - 1u].levelOfDetail) {
                    *value = i - 1u;
                    return TE_Ok;
                }
            }
            return TE_IllegalState;
        } else if (round < 0) {
            for (std::size_t i = 0; i < lods.size(); i++) {
                if (clod <= lods[i].levelOfDetail) {
                    *value = i;
                    return TE_Ok;
                }
            }
            return TE_IllegalState;
        } else { // round == 0
            for (std::size_t i = 0; i < lods.size()-1u; i++) {
                if (clod >= lods[i].levelOfDetail && clod <= lods[i+1u].levelOfDetail) {
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
    TAKErr StreamingSceneNode::getInstanceID(std::size_t *value, const std::size_t lod) const NOTHROWS
    {
        if (lods.empty())
            return TE_IllegalState;
        if (lod >= lods.size())
            return TE_InvalidArg;
        *value = lods[lod].instanceId;
        return TE_Ok;
    }
    bool StreamingSceneNode::hasSubscene() const NOTHROWS 
    {
        return false;
    }
    TAKErr StreamingSceneNode::getSubsceneInfo(const SceneInfo** result) NOTHROWS
    {
        return TE_IllegalState;
    }
    bool StreamingSceneNode::hasLODNode() const NOTHROWS {
        return false;
    }
    TAKErr StreamingSceneNode::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
        return TE_Unsupported;
    }

    TAKErr prepareEncode(std::unique_ptr<StreamingSceneNode> &value, int64_t &dataOff, SceneNode &src, std::map<std::size_t, int64_t> &instanceOffsets, const StreamingSceneNode *parent) NOTHROWS
    {
        TAKErr code(TE_Ok);
        value.reset(new StreamingSceneNode());
        value->parent = parent;
        value->hasLocalFrame = !!src.getLocalFrame();
        if (value->hasLocalFrame) {
            value->localFrame.set(*src.getLocalFrame());
            value->headerRecordLength += 16u * sizeof(double);
        }
        const std::size_t numLods = src.getNumLODs();
        value->lods.reserve(numLods);
        for (std::size_t i = 0u; i < numLods; i++) {
            StreamingSceneNode::LevelOfDetail lod;
            code = src.getInstanceID(&lod.instanceId, i);
            TE_CHECKBREAK_CODE(code);
            code = src.getLevelOfDetail(&lod.levelOfDetail, i);
            TE_CHECKBREAK_CODE(code);
            value->headerRecordLength += 9u; // LOD + instanceID + hasMesh bit
            if (src.hasMesh()) {
                std::shared_ptr<const Mesh> mesh;
                code = src.loadMesh(mesh, i, nullptr);
                TE_CHECKRETURN_CODE(code);

                code = computeMeshEncodeLength(&lod.meshDataLength, *mesh);
                TE_CHECKRETURN_CODE(code);

                if (lod.instanceId == SceneNode::InstanceID_None || (instanceOffsets.find(lod.instanceId) == instanceOffsets.end())) {
                    lod.meshDataOffset = dataOff;
                    dataOff += lod.meshDataLength;
                    instanceOffsets[lod.instanceId] = lod.meshDataOffset;
                } else {
                    // instance is mesh data recorded, point to same offset
                    lod.meshDataOffset = instanceOffsets[lod.instanceId];
                }

                // XXX - abstraction that provides "stream in" functionality from src
                lod.staticMesh = mesh;
                value->headerRecordLength += 12u; // offset+length
            } else {
                lod.meshDataLength = 0u;
                lod.meshDataOffset = 0LL;
            }
            value->lods.push_back(lod);
        }
        TE_CHECKRETURN_CODE(code);

        value->aabb = src.getAABB();

        if (src.hasChildren()) {
            Collection<std::shared_ptr<SceneNode>>::IteratorPtr iter(nullptr, nullptr);
            code = src.getChildren(iter);
            TE_CHECKRETURN_CODE(code);

            do {
                std::shared_ptr<SceneNode> srcChild;
                code = iter->get(srcChild);
                TE_CHECKBREAK_CODE(code);

                std::unique_ptr<StreamingSceneNode> dstChild;
                code = prepareEncode(dstChild, dataOff, *srcChild, instanceOffsets, value.get());
                TE_CHECKBREAK_CODE(code);

                value->children.push_back(std::move(dstChild));

                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
            TE_CHECKRETURN_CODE(code);
        }

        // if completing recursion the root, apply the header data offset to the mesh data offsets
        if (!parent) {
            const int64_t headerDataLen = computeMeshDataOffsetShift(*value);
            // XXX - do we want to align the mesh data???
            shiftMeshDataOffsets(*value, TE_SERIALIZED_MESH_HEADER_RESERVED+headerDataLen);
        }

        return code;
    }

    int64_t computeMeshDataOffsetShift(StreamingSceneNode &node) NOTHROWS
    {
        int64_t retval = node.headerRecordLength;
        for (std::size_t i = 0u; i < node.children.size(); i++)
            retval += computeMeshDataOffsetShift(*node.children[i]);
        return retval;
    }
    void shiftMeshDataOffsets(StreamingSceneNode &node, const int64_t shift) NOTHROWS
    {
        for (std::size_t i = 0u; i < node.lods.size(); i++)
            if(node.lods[i].meshDataLength)
                node.lods[i].meshDataOffset += shift;
        for (std::size_t i = 0u; i < node.children.size(); i++)
            shiftMeshDataOffsets(*node.children[i], shift);
    }

    TAKErr encodeMesh(DataOutput2 &dst, const Mesh &mesh) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const VertexDataLayout &srcLayout = mesh.getVertexDataLayout();
        VertexDataLayout dstLayout = srcLayout;
        if (srcLayout.interleaved) {
            if (!(srcLayout.attributes&TEVA_Position))
                return TE_InvalidArg;

            std::size_t baseOffset = srcLayout.position.offset;
#define TE_DISCOVER_SRCVA_BASEOFFSET(teva, arr) \
    if(srcLayout.attributes&teva) \
        baseOffset = std::min(baseOffset, srcLayout.arr.offset);

            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_Normal, normal);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_Color, color);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord0, texCoord0);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord1, texCoord1);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord2, texCoord2);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord3, texCoord3);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord4, texCoord4);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord5, texCoord5);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord6, texCoord6);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord7, texCoord7);
#undef TE_DISCOVER_SRCVA_BASEOFFSET

#define TE_NORMALIZE_DSTVA_BASEOFFSET(teva, arr) \
            if (srcLayout.attributes&teva) { \
                if (dstLayout.arr.offset < baseOffset) \
                    return TE_IllegalState; \
                dstLayout.arr.offset -= baseOffset; \
            }

            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Position, position);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Normal, normal);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Color, color);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord0, texCoord0);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord1, texCoord1);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord2, texCoord2);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord3, texCoord3);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord4, texCoord4);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord5, texCoord5);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord6, texCoord6);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord7, texCoord7);
#undef TE_NORMALIZE_DSTVA_BASEOFFSET
        } else {
            // the data is not interleaved
#define TE_NORMALIZE_DSTVA_BASEOFFSET(teva, arr) \
    if (srcLayout.attributes&teva) \
        dstLayout.arr.offset = 0u; \

            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Position, position);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Normal, normal);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Color, color);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord0, texCoord0);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord1, texCoord1);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord2, texCoord2);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord3, texCoord3);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord4, texCoord4);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord5, texCoord5);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord6, texCoord6);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord7, texCoord7);
#undef TE_NORMALIZE_DSTVA_BASEOFFSET
        }

        // write layout
        code = dst.writeInt(dstLayout.attributes);
        TE_CHECKRETURN_CODE(code);
#define TE_WRITE_VERTEXARRAY(arr) \
    code = dst.writeInt(static_cast<int32_t>(arr.type)); \
    TE_CHECKRETURN_CODE(code); \
    code = dst.writeInt(static_cast<int32_t>(arr.offset)); \
    TE_CHECKRETURN_CODE(code); \
    code = dst.writeInt(static_cast<int32_t>(arr.stride)); \
    TE_CHECKRETURN_CODE(code);

        TE_WRITE_VERTEXARRAY(dstLayout.position);
        TE_WRITE_VERTEXARRAY(dstLayout.normal);
        TE_WRITE_VERTEXARRAY(dstLayout.color);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord0);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord1);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord2);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord3);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord4);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord5);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord6);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord7);
#undef TE_WRITE_VERTEXARRAY
        code = dst.writeByte(dstLayout.interleaved ? 0x1u : 0x0u);
        TE_CHECKRETURN_CODE(code);

        // AABB
        double aabb[6] =
        {
            mesh.getAABB().minX,
            mesh.getAABB().minY,
            mesh.getAABB().minZ,
            mesh.getAABB().maxX,
            mesh.getAABB().maxY,
            mesh.getAABB().maxZ,
        };
        code = dst.write(reinterpret_cast<uint8_t *>(aabb), 6u * sizeof(double));
        TE_CHECKRETURN_CODE(code);

        // draw mode and winding
        code = dst.writeInt(mesh.getDrawMode());
        TE_CHECKRETURN_CODE(code);
        code = dst.writeInt(mesh.getFaceWindingOrder());
        TE_CHECKRETURN_CODE(code);

        // materials
        code = dst.writeInt(static_cast<int32_t>(mesh.getNumMaterials()));
        TE_CHECKRETURN_CODE(code);
        for (std::size_t i = 0u; i < mesh.getNumMaterials(); i++) {
            Material mat;
            code = mesh.getMaterial(&mat, i);
            TE_CHECKBREAK_CODE(code);

            code = dst.writeInt(mat.color);
            TE_CHECKBREAK_CODE(code);
            code = dst.writeInt(mat.propertyType);
            TE_CHECKBREAK_CODE(code);
            code = dst.writeByte(mat.twoSided ? 0x1u : 0x0u);
            TE_CHECKBREAK_CODE(code);
            code = dst.writeInt(mat.textureCoordIndex);
            TE_CHECKBREAK_CODE(code);
            code = dst.writeInt(mat.textureUri ? static_cast<int32_t>(strlen(mat.textureUri)) : 0);
            TE_CHECKBREAK_CODE(code);
            if (mat.textureUri) {
                code = dst.writeString(mat.textureUri);
                TE_CHECKBREAK_CODE(code);
            }
        }
        TE_CHECKRETURN_CODE(code);

        // write vertex data
        code = dst.writeInt(static_cast<int32_t>(mesh.getNumVertices()));
        TE_CHECKRETURN_CODE(code);
        if (dstLayout.interleaved) {
            std::size_t dataLen;
            code = VertexDataLayout_requiredInterleavedDataSize(&dataLen, dstLayout, mesh.getNumVertices());
            TE_CHECKRETURN_CODE(code);

            code = dst.writeLong(dataLen);
            TE_CHECKRETURN_CODE(code);

            const void *data;
            code = mesh.getVertices(&data, TEVA_Position);
            TE_CHECKRETURN_CODE(code);

            code = dst.write((const uint8_t *)data + (srcLayout.position.offset-dstLayout.position.offset), dataLen);
            TE_CHECKRETURN_CODE(code);
        } else {
            struct VertexDataRecord
            {
                VertexAttribute attr;
                VertexArray srcArr;
                VertexArray dstArr;
            };

            VertexDataRecord records[11u]
            {
                VertexDataRecord{ TEVA_Position, srcLayout.position, dstLayout.position },
                VertexDataRecord{ TEVA_Normal, srcLayout.normal, dstLayout.normal },
                VertexDataRecord{ TEVA_Color, srcLayout.color, dstLayout.color },
                VertexDataRecord{ TEVA_TexCoord0, srcLayout.texCoord0, dstLayout.texCoord0 },
                VertexDataRecord{ TEVA_TexCoord1, srcLayout.texCoord1, dstLayout.texCoord1 },
                VertexDataRecord{ TEVA_TexCoord2, srcLayout.texCoord2, dstLayout.texCoord2 },
                VertexDataRecord{ TEVA_TexCoord3, srcLayout.texCoord3, dstLayout.texCoord3 },
                VertexDataRecord{ TEVA_TexCoord4, srcLayout.texCoord4, dstLayout.texCoord4 },
                VertexDataRecord{ TEVA_TexCoord5, srcLayout.texCoord5, dstLayout.texCoord5 },
                VertexDataRecord{ TEVA_TexCoord6, srcLayout.texCoord6, dstLayout.texCoord6 },
                VertexDataRecord{ TEVA_TexCoord7, srcLayout.texCoord7, dstLayout.texCoord7 },
            };
            for (std::size_t i = 0u; i < 11u; i++) {
                if (!(dstLayout.attributes & records[i].attr))
                    continue;

                std::size_t dataLen;
                code = VertexDataLayout_requiredDataSize(&dataLen, dstLayout, records[i].attr, mesh.getNumVertices());
                TE_CHECKBREAK_CODE(code);

                code = dst.writeLong(dataLen);
                TE_CHECKBREAK_CODE(code);

                const void *data;
                code = mesh.getVertices(&data, records[i].attr);
                TE_CHECKBREAK_CODE(code);

                code = dst.write((const uint8_t *)data+records[i].srcArr.offset, dataLen);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);
        }

        // indices
        const void *indices = mesh.getIndices();
        code = dst.writeByte(indices ? 0x1u : 0x0u);
        TE_CHECKRETURN_CODE(code);
        if (indices) {
            DataType indexType;
            code = mesh.getIndexType(&indexType);
            TE_CHECKRETURN_CODE(code);
            code = dst.writeInt(indexType);
            TE_CHECKRETURN_CODE(code);
            code = dst.writeInt(static_cast<int32_t>(mesh.getNumIndices()));
            TE_CHECKRETURN_CODE(code);
            code = dst.write((const uint8_t *)indices + mesh.getIndexOffset(), mesh.getNumIndices()*getDataTypeSize(indexType));
            TE_CHECKRETURN_CODE(code);
        }

        return code;
    }
    TAKErr computeMeshEncodeLength(std::size_t *value, const Mesh &mesh) NOTHROWS
    {
        TAKErr code(TE_Ok);

        *value = 0;

        const VertexDataLayout &srcLayout = mesh.getVertexDataLayout();
        VertexDataLayout dstLayout = srcLayout;
        if (srcLayout.interleaved) {
            if (!(srcLayout.attributes&TEVA_Position))
                return TE_InvalidArg;

            std::size_t baseOffset = srcLayout.position.offset;
#define TE_DISCOVER_SRCVA_BASEOFFSET(teva, arr) \
    if(srcLayout.attributes&teva) \
        baseOffset = std::min(baseOffset, srcLayout.arr.offset);

            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_Normal, normal);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_Color, color);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord0, texCoord0);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord1, texCoord1);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord2, texCoord2);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord3, texCoord3);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord4, texCoord4);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord5, texCoord5);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord6, texCoord6);
            TE_DISCOVER_SRCVA_BASEOFFSET(TEVA_TexCoord7, texCoord7);
#undef TE_DISCOVER_SRCVA_BASEOFFSET

#define TE_NORMALIZE_DSTVA_BASEOFFSET(teva, arr) \
            if (srcLayout.attributes&teva) { \
                if (dstLayout.arr.offset < baseOffset) \
                    return TE_IllegalState; \
                dstLayout.arr.offset -= baseOffset; \
            }

            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Position, position);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Normal, normal);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Color, color);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord0, texCoord0);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord1, texCoord1);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord2, texCoord2);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord3, texCoord3);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord4, texCoord4);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord5, texCoord5);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord6, texCoord6);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord7, texCoord7);
#undef TE_NORMALIZE_DSTVA_BASEOFFSET
        } else {
            // the data is not interleaved
#define TE_NORMALIZE_DSTVA_BASEOFFSET(teva, arr) \
    if (srcLayout.attributes&teva) \
        dstLayout.arr.offset = 0u; \

            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Position, position);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Normal, normal);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_Color, color);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord0, texCoord0);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord1, texCoord1);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord2, texCoord2);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord3, texCoord3);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord4, texCoord4);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord5, texCoord5);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord6, texCoord6);
            TE_NORMALIZE_DSTVA_BASEOFFSET(TEVA_TexCoord7, texCoord7);
#undef TE_NORMALIZE_DSTVA_BASEOFFSET
        }

        // write layout
        //code = dst.writeInt(dstLayout.attributes);
        *value += 4u;
        TE_CHECKRETURN_CODE(code);
#define TE_WRITE_VERTEXARRAY(arr) \
    *value += 12u;

        TE_WRITE_VERTEXARRAY(dstLayout.position);
        TE_WRITE_VERTEXARRAY(dstLayout.normal);
        TE_WRITE_VERTEXARRAY(dstLayout.color);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord0);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord1);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord2);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord3);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord4);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord5);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord6);
        TE_WRITE_VERTEXARRAY(dstLayout.texCoord7);
#undef TE_WRITE_VERTEXARRAY
        //code = dst.writeByte(dstLayout.interleaved ? 0x1u : 0x0u);
        *value += 1u;

        // AABB
        //code = dst.write(reinterpret_cast<uint8_t *>(aabb), 6u * sizeof(double));
        *value += (6u * sizeof(double));
        TE_CHECKRETURN_CODE(code);

        //code = dst.writeInt(mesh.getDrawMode());
        //code = dst.writeInt(mesh.getFaceWindingOrder());
        *value += 8u;

        // materials
        //code = dst.writeInt(mesh.getNumMaterials());
        *value += 4u;
        for (std::size_t i = 0u; i < mesh.getNumMaterials(); i++) {
            Material mat;
            code = mesh.getMaterial(&mat, i);
            TE_CHECKBREAK_CODE(code);

            //code = dst.writeInt(mat.color);
            //code = dst.writeInt(mat.propertyType);
            //code = dst.writeByte(mat.twoSided ? 0x1u : 0x0u);
            //code = dst.writeInt(mat.textureCoordIndex);
            //code = dst.writeInt(mat.textureUri ? strlen(mat.textureUri) : 0u);
            *value += 17u;
            if (mat.textureUri) {
                //code = dst.writeString(mat.textureUri);
                *value += strlen(mat.textureUri);
            }
        }
        TE_CHECKRETURN_CODE(code);

        // write vertex data
        //code = dst.writeInt(mesh.getNumVertices());
        *value += 4u;
        if (dstLayout.interleaved) {
            std::size_t dataLen;
            code = VertexDataLayout_requiredInterleavedDataSize(&dataLen, dstLayout, mesh.getNumVertices());
            TE_CHECKRETURN_CODE(code);

            //code = dst.writeLong(dataLen);
            *value += 8u;
            //code = dst.write((const uint8_t *)data + (srcLayout.position.offset-dstLayout.position.offset), dataLen);
            *value += dataLen;
        } else {
            struct VertexDataRecord
            {
                VertexAttribute attr;
                VertexArray srcArr;
                VertexArray dstArr;
            };

            VertexDataRecord records[11u]
            {
                VertexDataRecord{ TEVA_Position, srcLayout.position, dstLayout.position },
                VertexDataRecord{ TEVA_Normal, srcLayout.normal, dstLayout.normal },
                VertexDataRecord{ TEVA_Color, srcLayout.color, dstLayout.color },
                VertexDataRecord{ TEVA_TexCoord0, srcLayout.texCoord0, dstLayout.texCoord0 },
                VertexDataRecord{ TEVA_TexCoord1, srcLayout.texCoord1, dstLayout.texCoord1 },
                VertexDataRecord{ TEVA_TexCoord2, srcLayout.texCoord2, dstLayout.texCoord2 },
                VertexDataRecord{ TEVA_TexCoord3, srcLayout.texCoord3, dstLayout.texCoord3 },
                VertexDataRecord{ TEVA_TexCoord4, srcLayout.texCoord4, dstLayout.texCoord4 },
                VertexDataRecord{ TEVA_TexCoord5, srcLayout.texCoord5, dstLayout.texCoord5 },
                VertexDataRecord{ TEVA_TexCoord6, srcLayout.texCoord6, dstLayout.texCoord6 },
                VertexDataRecord{ TEVA_TexCoord7, srcLayout.texCoord7, dstLayout.texCoord7 },
            };
            for (std::size_t i = 0u; i < 11u; i++) {
                if (!(dstLayout.attributes & records[i].attr))
                    continue;

                std::size_t dataLen;
                code = VertexDataLayout_requiredDataSize(&dataLen, dstLayout, records[i].attr, mesh.getNumVertices());
                TE_CHECKBREAK_CODE(code);

                //code = dst.writeLong(dataLen);
                *value += 8u;

                //code = dst.write((const uint8_t *)data+records[i].srcArr.offset, dataLen);
                *value += dataLen;
            }
            TE_CHECKRETURN_CODE(code);
        }

        // indices
        const void *indices = mesh.getIndices();
        //code = dst.writeByte(indices ? 0x1u : 0x0u);
        *value += 1u;
        if (indices) {
            DataType indexType;
            code = mesh.getIndexType(&indexType);
            TE_CHECKRETURN_CODE(code);

            //code = dst.writeInt(indexType);
            //code = dst.writeInt(mesh.getNumIndices());
            *value += 8u;

            //code = dst.write((const uint8_t *)indices + mesh.getIndexOffset(), mesh.getNumIndices());
            *value += mesh.getNumIndices()*getDataTypeSize(indexType);
        }

        return code;
    }
    TAKErr decodeMesh(MeshPtr_const &value, DataInput2 &src) NOTHROWS
    {
        TAKErr code(TE_Ok);
        int intval;
        uint8_t bit;
        std::size_t numRead;
        int64_t longval;

        VertexDataLayout layout;
        double aabb[6u];
        DrawMode drawMode;
        WindingOrder windingOrder;
        std::size_t numMaterials;
        std::vector<Material> materials;
        std::size_t numVertices;
        VoidPtr_const position(nullptr, nullptr);
        VoidPtr_const normal(nullptr, nullptr);
        VoidPtr_const color(nullptr, nullptr);
        VoidPtr_const texCoord0(nullptr, nullptr);
        VoidPtr_const texCoord1(nullptr, nullptr);
        VoidPtr_const texCoord2(nullptr, nullptr);
        VoidPtr_const texCoord3(nullptr, nullptr);
        VoidPtr_const texCoord4(nullptr, nullptr);
        VoidPtr_const texCoord5(nullptr, nullptr);
        VoidPtr_const texCoord6(nullptr, nullptr);
        VoidPtr_const texCoord7(nullptr, nullptr);

        MeshPtr retval(nullptr, nullptr);

        // read layout
        code = src.readInt(&intval);
        TE_CHECKRETURN_CODE(code);
        layout.attributes = intval;
#define TE_READ_VERTEXARRAY(arr) \
    code = src.readInt(&intval); \
    TE_CHECKRETURN_CODE(code); \
    arr.type = (DataType)intval; \
    code = src.readInt(&intval); \
    TE_CHECKRETURN_CODE(code); \
    arr.offset = intval; \
    code = src.readInt(&intval); \
    TE_CHECKRETURN_CODE(code); \
    arr.stride = intval;

        TE_READ_VERTEXARRAY(layout.position);
        TE_READ_VERTEXARRAY(layout.normal);
        TE_READ_VERTEXARRAY(layout.color);
        TE_READ_VERTEXARRAY(layout.texCoord0);
        TE_READ_VERTEXARRAY(layout.texCoord1);
        TE_READ_VERTEXARRAY(layout.texCoord2);
        TE_READ_VERTEXARRAY(layout.texCoord3);
        TE_READ_VERTEXARRAY(layout.texCoord4);
        TE_READ_VERTEXARRAY(layout.texCoord5);
        TE_READ_VERTEXARRAY(layout.texCoord6);
        TE_READ_VERTEXARRAY(layout.texCoord7);
#undef TE_READ_VERTEXARRAY
        code = src.readByte(&bit);
        TE_CHECKRETURN_CODE(code);
        layout.interleaved = !!bit;

        // AABB
        code = src.read(reinterpret_cast<uint8_t *>(aabb), &numRead, 6u * sizeof(double));
        TE_CHECKRETURN_CODE(code);
        if (numRead < (6u * sizeof(double)))
            return TE_EOF;

        // draw mode and winding
        code = src.readInt(&intval);
        TE_CHECKRETURN_CODE(code);
        drawMode = (DrawMode)intval;
        code = src.readInt(&intval);
        TE_CHECKRETURN_CODE(code);
        windingOrder = (WindingOrder)intval;
        // materials
        code = src.readInt(&intval);
        TE_CHECKRETURN_CODE(code);
        if (intval < 0)
            return TE_IllegalState;
        numMaterials = intval;
        materials.reserve(numMaterials);
        for (std::size_t i = 0u; i < numMaterials; i++) {
            Material mat;

            code = src.readInt(&intval);
            TE_CHECKBREAK_CODE(code);
            mat.color = intval;
            code = src.readInt(&intval);
            TE_CHECKBREAK_CODE(code);
            mat.propertyType = (Material::PropertyType)intval;
            code = src.readByte(&bit);
            TE_CHECKBREAK_CODE(code);
            mat.twoSided = !!bit;
            code = src.readInt(&intval);
            TE_CHECKBREAK_CODE(code);
            mat.textureCoordIndex = intval;
            code = src.readInt(&intval);
            TE_CHECKBREAK_CODE(code);
            if (intval < 0)
                return TE_IllegalState;
            if (intval) {
                std::size_t tex_num_read;
                array_ptr<char> textureUri(new char[intval+1]);
                code = src.readString(textureUri.get(), &tex_num_read, intval);
                TE_CHECKBREAK_CODE(code);
                if (tex_num_read < static_cast<std::size_t>(intval))
                    return TE_EOF;
                mat.textureUri = textureUri.get();
            }

            materials.push_back(mat);
        }
        TE_CHECKRETURN_CODE(code);

        // read vertex data
        code = src.readInt(&intval);
        TE_CHECKRETURN_CODE(code);
        if (intval < 0)
            return TE_IllegalState;
        numVertices = intval;
        if (layout.interleaved) {
            code = src.readLong(&longval);
            TE_CHECKRETURN_CODE(code);
            if (longval > 0xFFFFFFFFLL)
                return TE_IllegalState;
            const auto dataLen = static_cast<std::size_t>(longval);

            VoidPtr data(te_alloc_v(dataLen), te_free_v);
            if (!data.get())
                return TE_OutOfMemory;
            code = src.read((uint8_t *)data.get(), &numRead, dataLen);
            TE_CHECKRETURN_CODE(code);
            if (numRead < dataLen)
                return TE_EOF;
            position = VoidPtr_const(data.release(), data.get_deleter());
        } else {
            struct VertexDataRecord
            {
                VertexAttribute attr;
                VertexArray arr;
                VoidPtr_const &data;
            };

            VertexDataRecord records[11u]
            {
                VertexDataRecord{ TEVA_Position, layout.position, position },
                VertexDataRecord{ TEVA_Normal, layout.normal, normal },
                VertexDataRecord{ TEVA_Color, layout.color, color },
                VertexDataRecord{ TEVA_TexCoord0, layout.texCoord0, texCoord0 },
                VertexDataRecord{ TEVA_TexCoord1, layout.texCoord1, texCoord1 },
                VertexDataRecord{ TEVA_TexCoord2, layout.texCoord2, texCoord2 },
                VertexDataRecord{ TEVA_TexCoord3, layout.texCoord3, texCoord3 },
                VertexDataRecord{ TEVA_TexCoord4, layout.texCoord4, texCoord4 },
                VertexDataRecord{ TEVA_TexCoord5, layout.texCoord5, texCoord5 },
                VertexDataRecord{ TEVA_TexCoord6, layout.texCoord6, texCoord6 },
                VertexDataRecord{ TEVA_TexCoord7, layout.texCoord7, texCoord7 },
            };
            for (std::size_t i = 0u; i < 11u; i++) {
                if (!(layout.attributes & records[i].attr))
                    continue;

                code = src.readLong(&longval);
                TE_CHECKRETURN_CODE(code);
                if (longval > 0xFFFFFFFFLL)
                    return TE_IllegalState;
                const auto dataLen = static_cast<std::size_t>(longval);


                VoidPtr data(te_alloc_v(dataLen), te_free_v);
                if (!data.get())
                    return TE_OutOfMemory;
                code = src.read((uint8_t *)data.get(), &numRead, dataLen);
                TE_CHECKRETURN_CODE(code);
                if (numRead < dataLen)
                    return TE_EOF;
                records[i].data = VoidPtr_const(data.release(), data.get_deleter());
            }
            TE_CHECKRETURN_CODE(code);
        }

        // indices
        code = src.readByte(&bit);
        TE_CHECKRETURN_CODE(code);
        if (bit) {
            DataType indexType;
            code = src.readInt(&intval);
            TE_CHECKRETURN_CODE(code);
            indexType = (DataType)intval;
            code = src.readInt(&intval);
            TE_CHECKRETURN_CODE(code);
            if (intval < 0)
                return TE_IllegalState;
            const std::size_t numIndices = intval;

            array_ptr<uint8_t> indices(new uint8_t[numIndices*getDataTypeSize(indexType)]);
            code = src.read(indices.get(), &numRead, numIndices*getDataTypeSize(indexType));
            TE_CHECKRETURN_CODE(code);
            if (numRead < (numIndices*getDataTypeSize(indexType)))
                return TE_EOF;


            if(layout.interleaved)
                code = MeshBuilder_buildInterleavedMesh(
                    retval,
                    drawMode,
                    windingOrder,
                    layout,
                    numMaterials,
                    materials.data(),
                    TAK::Engine::Feature::Envelope2(aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]),
                    numVertices,
                    std::move(position),
                    indexType,
                    numIndices,
                    std::move(VoidPtr_const(indices.release(), Memory_void_array_deleter_const<uint8_t>)));
            else
                code = MeshBuilder_buildNonInterleavedMesh(
                    retval,
                    drawMode,
                    windingOrder,
                    layout,
                    numMaterials,
                    materials.data(),
                    TAK::Engine::Feature::Envelope2(aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]),
                    numVertices,
                    std::move(position),
                    std::move(texCoord0),
                    std::move(texCoord1),
                    std::move(texCoord2),
                    std::move(texCoord3),
                    std::move(texCoord4),
                    std::move(texCoord5),
                    std::move(texCoord6),
                    std::move(texCoord7),
                    std::move(normal),
                    std::move(color),
                    indexType,
                    numIndices,
                    std::move(VoidPtr_const(indices.release(), Memory_void_array_deleter_const<uint8_t>)));
            TE_CHECKRETURN_CODE(code);
        } else if (layout.interleaved) {
            code = MeshBuilder_buildInterleavedMesh(
                retval,
                drawMode,
                windingOrder,
                layout,
                numMaterials,
                materials.data(),
                TAK::Engine::Feature::Envelope2(aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]),
                numVertices,
                std::move(position));
            TE_CHECKRETURN_CODE(code);
        } else {
            code = MeshBuilder_buildNonInterleavedMesh(
                retval,
                drawMode,
                windingOrder,
                layout,
                numMaterials,
                materials.data(),
                TAK::Engine::Feature::Envelope2(aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]),
                numVertices,
                std::move(position),
                std::move(texCoord0),
                std::move(texCoord1),
                std::move(texCoord2),
                std::move(texCoord3),
                std::move(texCoord4),
                std::move(texCoord5),
                std::move(texCoord6),
                std::move(texCoord7),
                std::move(normal),
                std::move(color));
            TE_CHECKRETURN_CODE(code);
        }

        value = MeshPtr_const(retval.release(), retval.get_deleter());

        
        return code;
    }

    TAKErr encodeSceneNode(FileOutput2 &dst, std::map<std::size_t, bool> &meshInstanceEncoded, const StreamingSceneNode &node) NOTHROWS
    {
        TAKErr code(TE_Ok);
        // local frame
        code = dst.writeByte(node.hasLocalFrame ? 0x1u : 0x0u);
        TE_CHECKRETURN_CODE(code);
        if (node.hasLocalFrame) {
            double mx[16];
            code = node.localFrame.get(mx);
            TE_CHECKRETURN_CODE(code);
            code = dst.write(reinterpret_cast<uint8_t *>(mx), 16u * sizeof(double));
            TE_CHECKRETURN_CODE(code);
        }
        // AABB
        double aabb[6] =
        {
            node.aabb.minX,
            node.aabb.minY,
            node.aabb.minZ,
            node.aabb.maxX,
            node.aabb.maxY,
            node.aabb.maxZ,
        };
        code = dst.write(reinterpret_cast<uint8_t *>(aabb), 6u * sizeof(double));
        TE_CHECKRETURN_CODE(code);
        // LODs
        const std::size_t numLods = node.lods.size();
        code = dst.writeInt(static_cast<int32_t>(numLods));
        TE_CHECKRETURN_CODE(code);
        for (std::size_t i = 0u; i < numLods; i++) {
            // LOD info
            code = dst.writeInt(static_cast<int32_t>(node.lods[i].levelOfDetail));
            TE_CHECKBREAK_CODE(code);

            code = dst.writeInt(static_cast<int32_t>(node.lods[i].instanceId));
            TE_CHECKBREAK_CODE(code);

            const bool hasMesh = node.lods[i].meshDataLength != 0;
            code = dst.writeByte(hasMesh ? 0x1u : 0x0u);
            TE_CHECKBREAK_CODE(code);

            if (!hasMesh)
                continue;

            // LOD mesh
            code = dst.writeLong(node.lods[i].meshDataOffset);
            TE_CHECKRETURN_CODE(code);
            code = dst.writeInt(static_cast<int32_t>(node.lods[i].meshDataLength));
            TE_CHECKRETURN_CODE(code);

            if (node.lods[i].instanceId == SceneNode::InstanceID_None || (meshInstanceEncoded.find(node.lods[i].instanceId) == meshInstanceEncoded.end())) {
                // mark the current write pointer to automatically restore after we write the mesh
                FileMark<FileOutput2> mark(dst);
                // seek to the offset
                code = dst.seek(node.lods[i].meshDataOffset);
                TE_CHECKBREAK_CODE(code);
                code = encodeMesh(dst, *node.lods[i].staticMesh);
                TE_CHECKBREAK_CODE(code);

                if (node.lods[i].instanceId != SceneNode::InstanceID_None)
                    meshInstanceEncoded[node.lods[i].instanceId] = true;
            }
        }
        TE_CHECKRETURN_CODE(code);

        // children
        const std::size_t numChildren = node.children.size();
        code = dst.writeInt(static_cast<int32_t>(numChildren));
        TE_CHECKRETURN_CODE(code);

        for (std::size_t i = 0u; i < numChildren; i++) {
            code = encodeSceneNode(dst, meshInstanceEncoded, *node.children[i]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr decodeSceneNode(std::unique_ptr<StreamingSceneNode> &value, FileInput2 &src, std::map<std::size_t, std::shared_ptr<const Mesh>> &instanceMeshes, const uint8_t version) NOTHROWS
    {
        TAKErr code(TE_Ok);
        uint8_t bit;
        std::size_t numRead;

        std::unique_ptr<StreamingSceneNode> node(new StreamingSceneNode());

        // local frame
        code = src.readByte(&bit);
        TE_CHECKRETURN_CODE(code);
        node->hasLocalFrame = bit != 0;
        if (node->hasLocalFrame) {
            double mx[16];
            code = src.read(reinterpret_cast<uint8_t *>(mx), &numRead, 16u * sizeof(double));
            TE_CHECKRETURN_CODE(code);
            if (numRead < (16u * sizeof(double)))
                return TE_EOF;
            for (std::size_t i = 0u; i < 16u; i++)
                node->localFrame.set(i / 4u, i % 4u, mx[i]);
            TE_CHECKRETURN_CODE(code);
        }
        // AABB
        double aabb[6];
        code = src.read(reinterpret_cast<uint8_t *>(aabb), &numRead, 6u * sizeof(double));
        TE_CHECKRETURN_CODE(code);
        if (numRead < (6u * sizeof(double)))
            return TE_EOF;
        node->aabb = TAK::Engine::Feature::Envelope2(aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]);

        // LODs
        int numLods;
        code = src.readInt(&numLods);
        TE_CHECKRETURN_CODE(code);
        if (numLods < 0)
            return TE_IllegalState;

        node->lods.reserve(numLods);
        for (std::size_t i = 0u; i < static_cast<std::size_t>(numLods); i++) {
            // LOD info
            node->lods.push_back(StreamingSceneNode::LevelOfDetail());
            node->lods[i].instanceId = SceneNode::InstanceID_None;

            int lodv;
            code = src.readInt(&lodv);
            TE_CHECKBREAK_CODE(code);

            node->lods[i].levelOfDetail = lodv;

            if (version > 1u) {
                code = src.readInt(&lodv);
                TE_CHECKBREAK_CODE(code);

                node->lods[i].instanceId = lodv;
            }

            code = src.readByte(&bit);
            TE_CHECKBREAK_CODE(code);

            const bool hasMesh = !!bit;
            if (!hasMesh)
                continue;

            // LOD mesh
            code = src.readLong(&node->lods[i].meshDataOffset);
            TE_CHECKRETURN_CODE(code);
            int len;
            code = src.readInt(&len);
            TE_CHECKRETURN_CODE(code);
            node->lods[i].meshDataLength = len;

            // mark the current write pointer to automatically restore after we write the mesh
            if (node->lods[i].instanceId == SceneNode::InstanceID_None || (instanceMeshes.find(node->lods[i].instanceId) == instanceMeshes.end())) {
                FileMark<FileInput2> mark(src);
                // seek to the offset
                code = src.seek(node->lods[i].meshDataOffset);
                TE_CHECKBREAK_CODE(code);
                MeshPtr_const mesh(nullptr, nullptr);
                code = decodeMesh(mesh, src);
                TE_CHECKBREAK_CODE(code);

                // XXX - enable possibility to stream from file on demand rather than allocating
                node->lods[i].staticMesh = std::move(mesh);

                if (node->lods[i].instanceId != SceneNode::InstanceID_None)
                    instanceMeshes[node->lods[i].instanceId] = node->lods[i].staticMesh;
            } else {
                node->lods[i].staticMesh = instanceMeshes[node->lods[i].instanceId];
            }


        }
        TE_CHECKRETURN_CODE(code);

        // children
        int numChildren;
        code = src.readInt(&numChildren);
        TE_CHECKRETURN_CODE(code);
        if (numChildren < 0)
            return TE_IllegalState;

        node->children.reserve(numChildren);
        for (std::size_t i = 0u; i < static_cast<std::size_t>(numChildren); i++) {
            std::unique_ptr<StreamingSceneNode> child;
            code = decodeSceneNode(child, src, instanceMeshes, version);
            TE_CHECKBREAK_CODE(code);

            child->parent = node.get();
            node->children.push_back(std::move(child));
        }
        TE_CHECKRETURN_CODE(code);

        value = std::move(node);

        return code;
    }
}
