
#include "formats/gltf/GLTF.h"
#include "model/Scene.h"
#include "formats/gltf/GLTF.h"
#include "port/STLVectorAdapter.h"
#include "util/MemBuffer2.h"
#include "model/MeshBuilder.h"
#include "port/StringBuilder.h"

#define TINYGLTF_IMPLEMENTATION
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include <tinygltf/tiny_gltf.h>

#include <memory>
#include <set>

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Formats::GLTF;

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace GLTF {
                class GLTFSceneNode : public SceneNode {
                public:
                    GLTFSceneNode() NOTHROWS;
                    ~GLTFSceneNode() override;
                    bool isRoot() const NOTHROWS override;
                    TAKErr getParent(const SceneNode** value) const NOTHROWS override;
                    const Matrix2* getLocalFrame() const NOTHROWS override;
                    TAKErr getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS override;
                    bool hasChildren() const NOTHROWS override;
                    bool hasMesh() const NOTHROWS override;
                    const Envelope2& getAABB() const NOTHROWS override;
                    std::size_t getNumLODs() const NOTHROWS override;
                    TAKErr loadMesh(std::shared_ptr<const Mesh>& value, const std::size_t lodIdx = 0u, ProcessingCallback* callback = nullptr) NOTHROWS override;
                    TAKErr getLevelOfDetail(std::size_t* value, const std::size_t lodIdx) const NOTHROWS override;
                    TAKErr getLODIndex(std::size_t* value, const double clod, const int round = 0) const NOTHROWS override;
                    TAKErr getInstanceID(std::size_t* instanceId, const std::size_t lodIdx) const NOTHROWS override;
                    bool hasSubscene() const NOTHROWS override;
                    TAKErr getSubsceneInfo(const SceneInfo** result) NOTHROWS override;
                    bool hasLODNode() const NOTHROWS override;
                    TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;

                    GLTFSceneNode* parent;
                    Envelope2 aabb;
                    Matrix2 transform;
                    STLVectorAdapter<std::shared_ptr<SceneNode>> children;
                    std::shared_ptr<const Mesh> mesh;
                };

                class GLTFScene : public Scene {
                public:
                    ~GLTFScene() NOTHROWS override;
                    SceneNode& getRootNode() const NOTHROWS override;
                    const Envelope2& getAABB() const NOTHROWS override;
                    unsigned int getProperties() const NOTHROWS override;

                    std::shared_ptr<GLTFSceneNode> root;
                    std::vector<std::vector<uint8_t>> buffers;
                };

                std::pair<VertexAttribute, VertexArray*> GLTF_vertArrayForAttr(const std::string& attr, VertexDataLayout& layout) NOTHROWS;
                TAKErr GLTF_drawModeForPrimitiveMode(DrawMode& result, int mode) NOTHROWS;
                TAKErr GLTF_dataTypeForAccessorComponentTypeV2(DataType& result, int componentType) NOTHROWS;
                int GLTF_componentCountForAccessorType(int accessorType) NOTHROWS;
                TAKErr GLTFScene_create(ScenePtr &result, const std::shared_ptr<GLTFSceneNode> &root, std::vector<std::vector<uint8_t>> &&buffers) NOTHROWS;
                TAKErr GLTFSceneNode_create(std::shared_ptr<GLTFSceneNode> &node) NOTHROWS;
                TAKErr GLTFSceneNode_createMeshNode(std::shared_ptr<GLTFSceneNode>& node, const std::shared_ptr<Mesh> &mesh) NOTHROWS;
                TAKErr GLTFSceneNode_addChild(GLTFSceneNode &node, const std::shared_ptr<GLTFSceneNode> &child) NOTHROWS;
                TAKErr GLTFSceneNode_setTransform(GLTFSceneNode& node, const std::vector<double>& matrix, const std::vector<double>& translation, const std::vector<double>& scale,
                    const std::vector<double>& rotation) NOTHROWS;
                void GLTF_initMeshAABB(Envelope2 &aabb) NOTHROWS;
                void GLTF_setAABBMinMax(Envelope2 &aabb, const std::vector<double> &min, const std::vector<double> &max) NOTHROWS;
                TAKErr GLTF_createMesh(MeshPtr& result, const VertexDataLayout& vertLayout, DrawMode drawMode,
                    const void* verts, size_t vertCount,
                    const void* indices, size_t indexCount, DataType indexType,
                    const std::vector<Material>& materials,
                    const Envelope2& aabb,
                    std::vector<MemBufferArg>& buffers) NOTHROWS;
                void GLTF_setMaterialColor(Material &mat, const std::vector<double> &color) NOTHROWS;
                TAKErr GLTFSceneNode_setMesh(std::shared_ptr<GLTFSceneNode>& node, const std::shared_ptr<Mesh>& mesh) NOTHROWS;
            }
        }
    }
}

namespace {
    struct BuildState {
        const char* baseURI;
        std::set<int> usedBuffers;
        std::set<int> usedImages;
        std::vector<MemBufferArg> memBufferArgs;
        std::vector<MemBufferArg> meshBufferArgs;
    };

    TAKErr buildScene(ScenePtr &result, const char *baseURI, tinygltf::Model &model) NOTHROWS;
    TAKErr buildNode(std::shared_ptr<GLTFSceneNode> &result, BuildState &state, GLTFSceneNode*parent, tinygltf::Model &model, tinygltf::Scene &scene, tinygltf::Node &node) NOTHROWS;
    TAKErr buildMesh(std::shared_ptr<Mesh> &result, BuildState& state, tinygltf::Model& model, tinygltf::Scene& scene, tinygltf::Node& node, tinygltf::Mesh &mesh, tinygltf::Primitive &prim) NOTHROWS;
    void mergeAABB(Envelope2& dst, const Envelope2& src) NOTHROWS;
}

TAKErr TAK::Engine::Formats::GLTF::GLTFScene_create(ScenePtr &result, const std::shared_ptr<GLTFSceneNode>& root, std::vector<std::vector<uint8_t>>&& buffers) NOTHROWS {
    TAKErr code = TE_Ok;
    TE_BEGIN_TRAP() {
        std::unique_ptr<GLTFScene> scene(new GLTFScene());
        scene->buffers = std::move(buffers);
        scene->root = root;
        result = ScenePtr(scene.release(), Memory_deleter_const<Scene, GLTFScene>);
    } TE_END_TRAP(code);
    return code;
}

TAKErr TAK::Engine::Formats::GLTF::GLTFSceneNode_create(std::shared_ptr<GLTFSceneNode>& result) NOTHROWS {
    TAKErr code = TE_Ok;
    TE_BEGIN_TRAP() {
        result = std::make_shared<GLTFSceneNode>();
    } TE_END_TRAP(code);
    return code;
}

TAKErr TAK::Engine::Formats::GLTF::GLTFSceneNode_createMeshNode(std::shared_ptr<GLTFSceneNode>& result, const std::shared_ptr<Mesh>& mesh) NOTHROWS {
    TAKErr code = TE_Ok;
    TE_BEGIN_TRAP() {
        result = std::make_shared<GLTFSceneNode>();
        result->mesh = mesh;
        result->aabb = mesh->getAABB();
    } TE_END_TRAP(code);
    return code;
}

TAKErr TAK::Engine::Formats::GLTF::GLTFSceneNode_setMesh(std::shared_ptr<GLTFSceneNode>& node, const std::shared_ptr<Mesh>& mesh) NOTHROWS {
    node->mesh = mesh;
    node->aabb = mesh->getAABB();
    return TE_Ok;
}

TAKErr TAK::Engine::Formats::GLTF::GLTFSceneNode_addChild(GLTFSceneNode& node, const std::shared_ptr<GLTFSceneNode>& child) NOTHROWS {
    TAKErr code = TE_Ok;
    TE_BEGIN_TRAP() {

        node.children.add(child);
        child->parent = &node;
    
        GLTFSceneNode* curr = child.get();
        while (curr->parent) {
            mergeAABB(curr->parent->aabb, curr->aabb);
            curr = curr->parent;
        }

    } TE_END_TRAP(code);
    return code;
}

TAKErr TAK::Engine::Formats::GLTF::GLTFSceneNode_setTransform(GLTFSceneNode& node, const std::vector<double>& matrix, const std::vector<double>& translation, const std::vector<double> &scale,
    const std::vector<double> &rotation) NOTHROWS {
    if (matrix.size() == 16) {
        for (size_t i = 0; i < 16; ++i)
            node.transform.set(i % 4, i / 4, matrix[i]);
        /*for (size_t r = 0; r < 4; ++r) {
            for (size_t c = 0; c < 4; ++c) {
                node.transform.set(r, c, matrix[c * 4 + r]);
            }
        }*/
    } else {
        if (scale.size() == 3)
            node.transform.scale(scale[0], scale[1], scale[2]);
        if (rotation.size() == 4)
            node.transform.rotate(rotation[0], rotation[1], rotation[2], rotation[3]);
        if (translation.size() == 3)
            node.transform.translate(translation[0], translation[1], translation[2]);
    }
    return TE_Ok;
}

void TAK::Engine::Formats::GLTF::GLTF_initMeshAABB(Envelope2& aabb) NOTHROWS {
    aabb.minX = std::numeric_limits<double>::max();
    aabb.minY = std::numeric_limits<double>::max();
    aabb.minZ = std::numeric_limits<double>::max();
    aabb.maxX = std::numeric_limits<double>::lowest();
    aabb.maxY = std::numeric_limits<double>::lowest();
    aabb.maxZ = std::numeric_limits<double>::lowest();
}

void TAK::Engine::Formats::GLTF::GLTF_setAABBMinMax(Envelope2& aabb, const std::vector<double>& minValues, const std::vector<double>& maxValues) NOTHROWS {
    aabb.minX = minValues[0];
    if (minValues.size() > 1)
        aabb.minY = minValues[1];
    if (minValues.size() > 2)
        aabb.minZ = minValues[2];
    aabb.maxX = maxValues[0];
    if (maxValues.size() > 1)
        aabb.maxY = maxValues[1];
    if (maxValues.size() > 2)
        aabb.maxZ = maxValues[2];
}

TAKErr TAK::Engine::Formats::GLTF::GLTF_createMesh(MeshPtr &result, const VertexDataLayout &vertLayout, DrawMode drawMode,
    const void *verts, size_t vertCount, 
    const void *indices, size_t indexCount, DataType indexType,
    const std::vector<Material> &materials,
    const Envelope2 &aabb,
    std::vector<MemBufferArg> &buffers) NOTHROWS {
    
    TAKErr code = TE_Ok;
    if (vertLayout.interleaved) {
        if (indices) {
            code = MeshBuilder_buildInterleavedMesh(
                result,
                drawMode,
                TEWO_Undefined,
                vertLayout,
                materials.size(),
                materials.size() ? &materials[0] : nullptr,
                aabb,
                vertCount,
                std::unique_ptr<const void, void(*)(const void*)>(verts, Memory_leaker_const<void>),
                indexType,
                indexCount,
                std::unique_ptr<const void, void(*)(const void*)>(indices, Memory_leaker_const<void>),
                buffers.size(), buffers.size() ? &buffers[0] : nullptr);
        } else {
            code = MeshBuilder_buildInterleavedMesh(
                result,
                drawMode,
                TEWO_CounterClockwise,
                vertLayout,
                materials.size(),
                materials.size() ? &materials[0] : nullptr,
                aabb,
                vertCount,
                std::unique_ptr<const void, void(*)(const void*)>(verts, Memory_leaker_const<void>),
                buffers.size(), buffers.size() ? &buffers[0] : nullptr);
        }
    }
    return code;
}

void TAK::Engine::Formats::GLTF::GLTF_setMaterialColor(Material& mat, const std::vector<double>& color) NOTHROWS {

    double r = 1.0;
    double g = 1.0;
    double b = 1.0; 
    double a = 1.0; 
    if (color.size() == 4) {
        r = std::max(0.0, std::min(1.0, color[0]));
        g = std::max(0.0, std::min(1.0, color[1]));
        b = std::max(0.0, std::min(1.0, color[2]));
        a = std::max(0.0, std::min(1.0, color[3]));
    }
    mat.color = (static_cast<uint32_t>(r * 255.0) << 24)
        | (static_cast<uint32_t>(g * 255.0) << 16)
        | (static_cast<uint32_t>(b * 255.0) << 8)
        | static_cast<uint32_t>(a * 255.0);
}


TAKErr TAK::Engine::Formats::GLTF::GLTF_loadV2(ScenePtr &result, const uint8_t *binary, size_t len, const char *baseURI) NOTHROWS {

    TAKErr code = TE_Unsupported;
    tinygltf::TinyGLTF gltf;
    tinygltf::Model model;
    std::string err;
    std::string warn;
    if (gltf.LoadBinaryFromMemory(&model, &err, &warn, binary, static_cast<unsigned int>(len), baseURI)) {
        code = buildScene(result, baseURI, model);
    }
    //__android_log_print(ANDROID_LOG_VERBOSE, "Cesium3DTiles", "err=%s warn=%s", err.c_str(), warn.c_str());
    return code;
}


std::pair<VertexAttribute, VertexArray*> TAK::Engine::Formats::GLTF::GLTF_vertArrayForAttr(const std::string& attr, VertexDataLayout& layout) NOTHROWS {
    VertexArray* vertArray = nullptr;
    VertexAttribute a = TEVA_Position; // hush warning
    if (attr == "POSITION") {
        vertArray = &layout.position;
        layout.attributes |= (a = TEVA_Position);
    } else if (attr == "NORMAL") {
        vertArray = &layout.normal;
        layout.attributes |= (a = TEVA_Normal);
    } else if (attr == "COLOR") {
        vertArray = &layout.color;
    } else if (attr == "TEXCOORD" || attr == "TEXCOORD_0") {
        vertArray = &layout.texCoord0;
        layout.attributes |= (a = TEVA_TexCoord0);
    } else if (attr == "TEXCOORD_1") {
        vertArray = &layout.texCoord1;
        layout.attributes |= (a = TEVA_TexCoord1);
    } else if (attr == "TEXCOORD_2") {
        vertArray = &layout.texCoord2;
        layout.attributes |= (a = TEVA_TexCoord2);
    } else if (attr == "TEXCOORD_3") {
        vertArray = &layout.texCoord3;
        layout.attributes |= (a = TEVA_TexCoord3);
    } else if (attr == "TEXCOORD_4") {
        vertArray = &layout.texCoord4;
        layout.attributes |= (a = TEVA_TexCoord4);
    } else if (attr == "TEXCOORD_5") {
        vertArray = &layout.texCoord5;
        layout.attributes |= (a = TEVA_TexCoord5);
    } else if (attr == "TEXCOORD_6") {
        vertArray = &layout.texCoord6;
        layout.attributes |= (a = TEVA_TexCoord6);
    } else if (attr == "TEXCOORD_7") {
        vertArray = &layout.texCoord7;
        layout.attributes |= (a = TEVA_TexCoord7);
    }
    return std::make_pair(a, vertArray);
}

int TAK::Engine::Formats::GLTF::GLTF_componentCountForAccessorType(int accessorType) NOTHROWS {
    switch (accessorType) {
    case TINYGLTF_TYPE_SCALAR: return 1;
    case TINYGLTF_TYPE_VEC2: return 2;
    case TINYGLTF_TYPE_VEC3: return 3;
    case TINYGLTF_TYPE_VEC4: return 4;
    }
    return 0;
}

TAKErr TAK::Engine::Formats::GLTF::GLTF_dataTypeForAccessorComponentTypeV2(DataType& result, int componentType) NOTHROWS {
    switch (componentType) {
    case TINYGLTF_COMPONENT_TYPE_BYTE:
        result = TEDT_Int8; break;
    case TINYGLTF_COMPONENT_TYPE_UNSIGNED_BYTE:
        result = TEDT_UInt8;  break;
    case TINYGLTF_COMPONENT_TYPE_SHORT:
        result = TEDT_Int16; break;
    case TINYGLTF_COMPONENT_TYPE_UNSIGNED_SHORT:
        result = TEDT_UInt16; break;
    case TINYGLTF_COMPONENT_TYPE_INT:
        result = TEDT_Int32; break;
    case TINYGLTF_COMPONENT_TYPE_UNSIGNED_INT:
        result = TEDT_UInt32; break;
    case TINYGLTF_COMPONENT_TYPE_FLOAT:
        result = TEDT_Float32; break;
    case TINYGLTF_COMPONENT_TYPE_DOUBLE:
        result = TEDT_Float64; break;
    default:
        return TE_Unsupported;
    }
    return TE_Ok;
}

TAKErr TAK::Engine::Formats::GLTF::GLTF_drawModeForPrimitiveMode(DrawMode& result, int mode) NOTHROWS {
    switch (mode) {
    case TINYGLTF_MODE_POINTS:
        result = TEDM_Points; break;
        result = TEDM_Points; break;
    case TINYGLTF_MODE_TRIANGLES:
        result = TEDM_Triangles; break;
    case TINYGLTF_MODE_TRIANGLE_STRIP:
        result = TEDM_TriangleStrip; break;
        //TODO--
    case TINYGLTF_MODE_TRIANGLE_FAN:
    case TINYGLTF_MODE_LINE:
    case TINYGLTF_MODE_LINE_LOOP:
    default:
        return TE_Unsupported;
    }
    return TE_Ok;
}

//
// GLTF2SceneNode
//

GLTFSceneNode::GLTFSceneNode() NOTHROWS
    : parent(nullptr),
    aabb(std::numeric_limits<double>::max(), std::numeric_limits<double>::max(), std::numeric_limits<double>::max(), 
         std::numeric_limits<double>::min(), std::numeric_limits<double>::min(), std::numeric_limits<double>::min())
{}

GLTFSceneNode::~GLTFSceneNode()
{}

bool GLTFSceneNode::isRoot() const NOTHROWS {
    return false;
}

TAKErr GLTFSceneNode::getParent(const SceneNode** value) const NOTHROWS {
    if (!value)
        return TE_InvalidArg;
    *value = parent;
    return TE_Ok;
}

const Matrix2* GLTFSceneNode::getLocalFrame() const NOTHROWS {
    return &transform;
}

TAKErr GLTFSceneNode::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS {
    return const_cast<GLTFSceneNode*>(this)->children.iterator(value);
}

bool GLTFSceneNode::hasChildren() const NOTHROWS {
    return const_cast<GLTFSceneNode*>(this)->children.size() != 0;
}

bool GLTFSceneNode::hasMesh() const NOTHROWS {
    return mesh != nullptr;
}

const Envelope2& GLTFSceneNode::getAABB() const NOTHROWS {
    return aabb;
}

std::size_t GLTFSceneNode::getNumLODs() const NOTHROWS {
    return 1;
}

TAKErr GLTFSceneNode::loadMesh(std::shared_ptr<const Mesh>& value, const std::size_t lodIdx, ProcessingCallback* callback) NOTHROWS {
    value = this->mesh;
    return TE_Ok;
}

TAKErr GLTFSceneNode::getLevelOfDetail(std::size_t* value, const std::size_t lodIdx) const NOTHROWS {
    return TE_Ok;
}

TAKErr GLTFSceneNode::getLODIndex(std::size_t* value, const double clod, const int round) const NOTHROWS {
    return TE_Ok;
}

TAKErr GLTFSceneNode::getInstanceID(std::size_t* instanceId, const std::size_t lodIdx) const NOTHROWS {
    return TE_Ok;
}

bool GLTFSceneNode::hasSubscene() const NOTHROWS {
    return false;
}

TAKErr GLTFSceneNode::getSubsceneInfo(const SceneInfo** result) NOTHROWS {
    return TE_IllegalState;
}

bool GLTFSceneNode::hasLODNode() const NOTHROWS {
    return false;
}

TAKErr GLTFSceneNode::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
    return TE_Unsupported;
}

//
// GLTF2Scene
//

GLTFScene::~GLTFScene() NOTHROWS
{}

SceneNode& GLTFScene::getRootNode() const NOTHROWS {
    return *root;
}

const Envelope2& GLTFScene::getAABB() const NOTHROWS {
    return getRootNode().getAABB();
}

unsigned int GLTFScene::getProperties() const NOTHROWS {
    return DirectMesh | DirectSceneGraph;
}

namespace {

    void mergeAABB(Envelope2& dst, const Envelope2& src) NOTHROWS {
        if (src.minX < dst.minX)
            dst.minX = src.minX;
        if (src.minY < dst.minY)
            dst.minY = src.minY;
        if (src.minZ < dst.minZ)
            dst.minZ = src.minZ;
        if (src.maxX > dst.maxX)
            dst.maxX = src.maxX;
        if (src.maxY > dst.maxY)
            dst.maxY = src.maxY;
        if (src.maxZ > dst.maxZ)
            dst.maxZ = src.maxZ;
    }

    TAKErr buildScene(ScenePtr& result, const char* baseURI, tinygltf::Model& model) NOTHROWS {

        TAKErr code = TE_Ok;
        BuildState state;
        state.baseURI = baseURI;

        if (model.scenes.empty())
            return TE_Err;

        int sceneIndex = model.defaultScene;
        if (sceneIndex < 0 || sceneIndex >= static_cast<int>(model.scenes.size()))
            sceneIndex = 0;

        tinygltf::Scene &scene = model.scenes[sceneIndex];
        std::shared_ptr<GLTFSceneNode> root;
        if (scene.nodes.size() > 1) {
            code = GLTFSceneNode_create(root);
            TE_CHECKRETURN_CODE(code);
        }

        for (int nodeIndex : scene.nodes) {
            std::shared_ptr<GLTFSceneNode> gltfNode;
            code = buildNode(gltfNode, state, nullptr, model, scene, model.nodes[nodeIndex]);
            if (code != TE_Ok)
                return code;
            if (root) {
                code = GLTFSceneNode_addChild(*root, gltfNode);
                if (code != TE_Ok)
                    return code;
            } else
                root = gltfNode;
        }

        // Move buffers into scene's lifecycle, so pointers are the same and taking over management of memory
        std::vector<std::vector<uint8_t>> buffers;
        for (int buffer : state.usedBuffers) {
            buffers.push_back(std::move(model.buffers[buffer].data));
        }
        for (int image : state.usedImages) {
            buffers.push_back(std::move(model.images[image].image));
        }

        return GLTFScene_create(result, root, std::move(buffers));
    }

    TAKErr buildNode(std::shared_ptr<GLTFSceneNode>& result, BuildState& state, GLTFSceneNode* parent, tinygltf::Model& model, tinygltf::Scene& scene, tinygltf::Node& node) NOTHROWS {

        std::shared_ptr<GLTFSceneNode> gltfNode;
        TAKErr code = GLTFSceneNode_create(gltfNode);
        TE_CHECKRETURN_CODE(code);

        code = GLTFSceneNode_setTransform(*gltfNode, node.matrix, node.translation, node.scale, node.rotation);
        TE_CHECKRETURN_CODE(code);

        // mesh
        tinygltf::Mesh &mesh = model.meshes[node.mesh];
        for (tinygltf::Primitive &primative : mesh.primitives) {
            std::shared_ptr<Mesh> meshPtr;
            code = buildMesh(meshPtr, state, model, scene, node, mesh, primative);
            if (code != TE_Ok)
                return code;

            std::shared_ptr<GLTFSceneNode> meshNode;
            code = GLTFSceneNode_createMeshNode(meshNode, meshPtr);
            TE_CHECKRETURN_CODE(code);
            code = GLTFSceneNode_addChild(*gltfNode, meshNode);
            TE_CHECKRETURN_CODE(code);
        }

        // children
        for (int childIndex : node.children) {
            std::shared_ptr<GLTFSceneNode> childNode;
            code = buildNode(childNode, state, gltfNode.get(), model, scene, model.nodes[childIndex]);
            if (code != TE_Ok)
                return code;
            code = GLTFSceneNode_addChild(*gltfNode, childNode);
            TE_CHECKRETURN_CODE(code);
        }

        if (code == TE_Ok)
            result = gltfNode;

        return code;
    }

    TAKErr buildMaterials(std::vector<Material>& mats, BuildState& state, const tinygltf::Model &model, const tinygltf::Material& mat) {
        TAKErr code = TE_Ok;

        mats.push_back(Material());
        Material &diffuse = mats[0];
        diffuse.propertyType = Material::TEPT_Diffuse;
        diffuse.twoSided = mat.doubleSided;
        GLTF_setMaterialColor(diffuse, mat.pbrMetallicRoughness.baseColorFactor);

        const tinygltf::TextureInfo &baseColorTex = mat.pbrMetallicRoughness.baseColorTexture;
        int textureIndex = baseColorTex.index;
        if (textureIndex < 0 || textureIndex >= static_cast<int>(model.textures.size()))
            return TE_Ok;
        
        const tinygltf::Texture &tex = model.textures[textureIndex];
        int imageIndex = tex.source;

        if (imageIndex < 0 || imageIndex >= static_cast<int>(model.images.size()))
            return TE_Ok;

        const tinygltf::Image &image = model.images[imageIndex];

        if (!image.uri.empty()) {
            StringBuilder sb;
            StringBuilder_combine(sb, state.baseURI, Platform_pathSep(), image.uri.c_str());
            diffuse.textureUri = sb.c_str();
        } else {
            const tinygltf::BufferView &bv = model.bufferViews[image.bufferView];
            const tinygltf::Buffer &b = model.buffers[bv.buffer];
            uint8_t *bufCopy = new uint8_t[bv.byteLength];
            memcpy(bufCopy, &b.data[0] + bv.byteOffset, bv.byteLength);
            state.meshBufferArgs.push_back(MemBufferArg{
                    std::unique_ptr<const void, void (*)(const void*)>(bufCopy, Memory_deleter_const<void>),
                    bv.byteLength
                });
            state.usedBuffers.insert(bv.buffer);
            Material_setBufferIndexTextureURI(&diffuse, state.meshBufferArgs.size() - 1);
        }

        diffuse.textureCoordIndex = baseColorTex.texCoord;
        return code;
    }

    TAKErr buildMesh(std::shared_ptr<Mesh> &result, BuildState& state, tinygltf::Model& model, tinygltf::Scene& scene, tinygltf::Node& node, tinygltf::Mesh& mesh, tinygltf::Primitive& prim) NOTHROWS {

        TAKErr code = TE_Ok;
        const void* verts = nullptr;
        DrawMode drawMode;
        DataType indexType = TEDT_UInt16;
        VertexDataLayout vertLayout;
        size_t vertCount = 0;
        std::vector<Material> materials;
        const void* indicesData = nullptr;
        size_t indexCount = 0;
        Envelope2 aabb;
        GLTF_initMeshAABB(aabb);

        memset(&vertLayout, 0, sizeof(vertLayout));
        

        if (prim.material == -1)
            return TE_IllegalState;
        const tinygltf::Material &mat = model.materials[prim.material];
        code = buildMaterials(materials, state, model, mat);
        TE_CHECKRETURN_CODE(code);

        code = GLTF_drawModeForPrimitiveMode(drawMode, prim.mode);
        if (code != TE_Ok)
            return code;

        if (prim.indices != -1) {
            tinygltf::Accessor &indices = model.accessors[prim.indices];
            tinygltf::BufferView &bv = model.bufferViews[indices.bufferView];
            tinygltf::Buffer &b = model.buffers[bv.buffer];
            state.usedBuffers.insert(bv.buffer);

            code = GLTF_dataTypeForAccessorComponentTypeV2(indexType, indices.componentType);
            if (code != TE_Ok)
                return code;

            indicesData = &b.data[0] + bv.byteOffset + indices.byteOffset;
            indexCount = indices.count;
        }

        size_t numBuffers = 0;

        for (const std::pair<std::string, int>& attr : prim.attributes) {

            const tinygltf::Accessor& accessor = model.accessors[attr.second];
            const tinygltf::BufferView& bv = model.bufferViews[accessor.bufferView];
            const tinygltf::Buffer& b = model.buffers[bv.buffer];

            size_t numUsedBuffers = state.usedBuffers.size();
            state.usedBuffers.insert(bv.buffer);
            if (numUsedBuffers < state.usedBuffers.size())
                ++numBuffers;

            auto vertArrayPair = GLTF_vertArrayForAttr(attr.first, vertLayout);
            VertexArray* vertArray = vertArrayPair.second;
            if (vertArray == nullptr)
                continue;

            VertexAttribute vertAttr = vertArrayPair.first;
            code = GLTF_dataTypeForAccessorComponentTypeV2(vertArray->type, accessor.componentType);
            if (code != TE_Ok)
                return code;

            int accessorComponentCount = GLTF_componentCountForAccessorType(accessor.type);
            vertArray->stride = accessor.ByteStride(bv);
            vertArray->offset = bv.byteOffset + accessor.byteOffset;

            if (vertArray == &vertLayout.position) {
                verts = &b.data[0];
                vertCount = accessor.count;
                GLTF_setAABBMinMax(aabb, accessor.minValues, accessor.maxValues);
            }
        }

        vertLayout.interleaved = true;
        
        MeshPtr meshPtr(nullptr, nullptr);
        code = GLTF_createMesh(meshPtr, vertLayout, drawMode,
            verts, vertCount,
            indicesData, indexCount, indexType,
            materials,
            aabb,
            state.meshBufferArgs);
        state.meshBufferArgs.clear();

        if (meshPtr) {
            result = MeshPtr(meshPtr.release(), meshPtr.get_deleter());
        }
        return code;
    }
}
