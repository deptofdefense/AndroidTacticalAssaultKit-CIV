
#include "formats/gltf/GLTF.h"
#include "model/Scene.h"
#include "formats/gltf/GLTF.h"
#include "port/STLVectorAdapter.h"
#include "util/MemBuffer2.h"
#include "model/MeshBuilder.h"
#include "port/StringBuilder.h"

#define TAK_TINYGLTFLOADER_MODS
#define TINYGLTF_LOADER_IMPLEMENTATION
#define tinygltf tinygltfloader
#include <tinygltfloader/tiny_gltf_loader.h>
#undef tinygltf

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
                class GLTFScene;
                class GLTFSceneNode;

                TAKErr GLTF_dataTypeForAccessorComponentTypeV1(DataType& result, int componentType) NOTHROWS;

                extern std::pair<VertexAttribute, VertexArray*> GLTF_vertArrayForAttr(const std::string& attr, VertexDataLayout& layout) NOTHROWS;
                extern TAKErr GLTF_drawModeForPrimitiveMode(DrawMode& result, int mode) NOTHROWS;
                extern int GLTF_componentCountForAccessorType(int accessorType) NOTHROWS;
                extern TAKErr GLTFScene_create(ScenePtr& result, const std::shared_ptr<GLTFSceneNode>& root, std::vector<std::vector<uint8_t>>&& buffers) NOTHROWS;
                extern TAKErr GLTFSceneNode_create(std::shared_ptr<GLTFSceneNode>& node) NOTHROWS;
                extern TAKErr GLTFSceneNode_createMeshNode(std::shared_ptr<GLTFSceneNode>& node, const std::shared_ptr<Mesh>& mesh) NOTHROWS;
                extern TAKErr GLTFSceneNode_addChild(GLTFSceneNode& node, const std::shared_ptr<GLTFSceneNode>& child) NOTHROWS;
                extern TAKErr GLTFSceneNode_setTransform(GLTFSceneNode& node, const std::vector<double>& matrix, const std::vector<double>& translation, const std::vector<double>& scale,
                    const std::vector<double>& rotation) NOTHROWS;
                extern void GLTF_initMeshAABB(Envelope2& aabb) NOTHROWS;
                extern void GLTF_setAABBMinMax(Envelope2& aabb, const std::vector<double>& min, const std::vector<double>& max) NOTHROWS;
                extern TAKErr GLTF_createMesh(MeshPtr& result, const VertexDataLayout& vertLayout, DrawMode drawMode,
                    const void* verts, size_t vertCount,
                    const void* indices, size_t indexCount, DataType indexType,
                    const std::vector<Material>& materials,
                    const Envelope2& aabb,
                    std::vector<MemBufferArg>& buffers) NOTHROWS;
                extern void GLTF_setMaterialColor(Material& mat, const std::vector<double>& color) NOTHROWS;
            }
        }
    }
}

namespace {
    struct BuildState {
        const char *baseURI;
        std::set<std::string> usedBuffers;
        std::set<std::string> usedImages;
        std::vector<MemBufferArg> memBufferArgs;
        std::vector<MemBufferArg> meshBufferArgs;
    };

    TAKErr buildScene(ScenePtr& result, const char *baseURI, tinygltfloader::Scene& scene) NOTHROWS;
    TAKErr buildNode(std::shared_ptr<GLTFSceneNode>& result, BuildState& sate, GLTFSceneNode*parent, const tinygltfloader::Scene& scene, const tinygltfloader::Node &node) NOTHROWS;
    TAKErr buildMesh(std::shared_ptr<Mesh> &result, BuildState& sate, const tinygltfloader::Scene& scene, const tinygltfloader::Node& node, const tinygltfloader::Mesh &mesh, const tinygltfloader::Primitive &prim) NOTHROWS;
}

TAKErr TAK::Engine::Formats::GLTF::GLTF_loadV1(ScenePtr& result, const uint8_t* binary, size_t len, const char* baseURI) NOTHROWS {

    std::unique_ptr<tinygltfloader::Scene> scene(new tinygltfloader::Scene());
    tinygltfloader::TinyGLTFLoader gltf;
    std::string err;
    std::string warn;
    
    if (gltf.LoadBinaryFromMemory(scene.get(), &err, binary, static_cast<unsigned int>(len), baseURI)) {
        return buildScene(result, baseURI, *scene);
    }
    //TODO-- __android_log_print(ANDROID_LOG_VERBOSE, "Cesium3DTiles", "err=%s warn=%s", err.c_str(), warn.c_str());
    return TE_Unsupported;
}

TAKErr TAK::Engine::Formats::GLTF::GLTF_dataTypeForAccessorComponentTypeV1(DataType& result, int componentType) NOTHROWS {
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

namespace {
    TAKErr buildScene(ScenePtr& result, const char *baseURI, tinygltfloader::Scene& scene) NOTHROWS {

        TAKErr code = TE_Ok;
        BuildState state;
        state.baseURI = baseURI;

        if (scene.scenes.empty())
            return TE_Err;

        auto it = scene.scenes.find(scene.defaultScene);
        if (it == scene.scenes.end())
            it = scene.scenes.begin();

        std::shared_ptr<GLTFSceneNode> root;
        if (it->second.size() > 1) {
            code = GLTFSceneNode_create(root);
            TE_CHECKRETURN_CODE(code);
        }

        for (size_t i = 0; i < it->second.size(); i++) {
            auto nodeIt = scene.nodes.find((it->second)[i]);
            if (nodeIt != scene.nodes.end()) {
                std::shared_ptr<GLTFSceneNode> gltfNode;
                code = buildNode(gltfNode, state, nullptr, scene, nodeIt->second);
                if (code != TE_Ok)
                    return code;
                if (root) {
                    code = GLTFSceneNode_addChild(*root, gltfNode);
                    if (code != TE_Ok)
                        return code;
                } else
                    root = gltfNode;
            }

        }

        // Move buffers into scene's lifecycle, so pointers are the same and taking over management of memory
        std::vector<std::vector<uint8_t>> buffers;
        for (const std::string& buffer : state.usedBuffers) {
            buffers.push_back(std::move(scene.buffers[buffer].data));
        }
        for (const std::string& image : state.usedImages) {
            buffers.push_back(std::move(scene.images[image].image));
        }

        return GLTFScene_create(result, root, std::move(buffers));
    }


    TAKErr buildNode(std::shared_ptr<GLTFSceneNode>& result, BuildState& state, GLTFSceneNode*parent, const tinygltfloader::Scene& scene, const tinygltfloader::Node& node) NOTHROWS {

        std::shared_ptr<GLTFSceneNode> gltfNode;
        TAKErr code = GLTFSceneNode_create(gltfNode);
        TE_CHECKRETURN_CODE(code);

        code = GLTFSceneNode_setTransform(*gltfNode, node.matrix, node.translation, node.scale, node.rotation);
        TE_CHECKRETURN_CODE(code);

        // meshes
        for (size_t i = 0; i < node.meshes.size(); i++) {
            auto it = scene.meshes.find(node.meshes[i]);
            if (it != scene.meshes.end()) {
                const tinygltfloader::Mesh &mesh = it->second;

                for (auto primIt = mesh.primitives.begin(); primIt != mesh.primitives.end(); ++primIt) {
                    std::shared_ptr<Mesh> meshPtr;
                    code = buildMesh(meshPtr, state, scene, node, it->second, *primIt);
                    if (code != TE_Ok)
                        return code;

                    std::shared_ptr<GLTFSceneNode> meshNode;
                    code = GLTFSceneNode_createMeshNode(meshNode, meshPtr);
                    TE_CHECKRETURN_CODE(code);
                    code = GLTFSceneNode_addChild(*gltfNode, meshNode);
                    TE_CHECKRETURN_CODE(code);
                }
            }
        }

        // children
        for (size_t i = 0; i < node.children.size(); ++i) {
            auto it = scene.nodes.find(node.children[i]);
            if (it != scene.nodes.end()) {
                std::shared_ptr<GLTFSceneNode> childNode;
                code = buildNode(childNode, state, gltfNode.get(), scene, it->second);
                if (code != TE_Ok)
                    return code;
                code = GLTFSceneNode_addChild(*gltfNode, childNode);
                TE_CHECKRETURN_CODE(code);
            }
        }

        if (code == TE_Ok)
            result = gltfNode;

        return code;
    }

    TAKErr buildMaterials(std::vector<Material>& mats, BuildState& state, const tinygltfloader::Scene& scene, const tinygltfloader::Material& mat) {
        TAKErr code = TE_Ok;

        mats.push_back(Material());
        Material& diffuse = mats[0];
        
        auto diffuseIt = mat.values.find("diffuse");
        if (diffuseIt != mat.values.end()) {
            const tinygltfloader::Parameter& param = diffuseIt->second;
            GLTF_setMaterialColor(diffuse, param.number_array);

            if (param.string_value.empty())
                return TE_Ok;

            auto texIt = scene.textures.find(param.string_value);
            if (texIt == scene.textures.end())
                return TE_Ok;

            const tinygltfloader::Texture& tex = texIt->second;

            auto imageIt = scene.images.find(tex.source);
            if (imageIt == scene.images.end())
                return TE_Ok;

            const tinygltfloader::Image &texImage = imageIt->second;
            diffuse.textureCoordIndex = tex.target;

            if (!texImage.uri.empty()) {
                StringBuilder sb;
                StringBuilder_combine(sb, state.baseURI, Platform_pathSep(), texImage.uri.c_str());
                diffuse.textureUri = sb.c_str();
            } else if (texImage.image.size()) {
                const tinygltfloader::BufferView& bv = scene.bufferViews.find(texImage.bufferView)->second;
                const tinygltfloader::Buffer& b = scene.buffers.find(bv.buffer)->second;
                state.meshBufferArgs.push_back(MemBufferArg {
                        std::unique_ptr<const void, void (*)(const void *)>(&b.data[0] + bv.byteOffset, Memory_leaker_const<void>),
                        bv.byteLength
                    });
                Material_setBufferIndexTextureURI(&diffuse, state.meshBufferArgs.size() - 1);
                state.usedBuffers.insert(bv.buffer);;
            }
        }

        return code;
    }

    TAKErr buildMesh(std::shared_ptr<Mesh>& result, BuildState& state, const tinygltfloader::Scene& scene, const tinygltfloader::Node& node, const tinygltfloader::Mesh& mesh, const tinygltfloader::Primitive& prim) NOTHROWS {
        
        TAKErr code = TE_Ok;
        const void* verts = nullptr;
        DrawMode drawMode;
        DataType indexType;
        VertexDataLayout vertLayout;
        size_t vertCount = 0;
        std::vector<Material> materials;
        const void* indicesData = nullptr;
        size_t indexCount = 0;
        Envelope2 aabb;
        GLTF_initMeshAABB(aabb);

        memset(&vertLayout, 0, sizeof(vertLayout));

        //TODO--
        vertLayout.interleaved = true;

        auto matIt = scene.materials.find(prim.material);
        if (matIt != scene.materials.end()) {
            const tinygltfloader::Material& material = matIt->second;
            buildMaterials(materials, state, scene, material);
        } else {
            // Default diffuse
            materials.push_back(Material());
        }

        code = GLTF_drawModeForPrimitiveMode(drawMode, prim.mode);
        if (code != TE_Ok)
            return code;

        auto indicesIt = scene.accessors.find(prim.indices);
        if (indicesIt != scene.accessors.end()) {
            const tinygltfloader::Accessor& indices = indicesIt->second;
            const tinygltfloader::BufferView& bv = scene.bufferViews.find(indices.bufferView)->second;
            const tinygltfloader::Buffer& b = scene.buffers.find(bv.buffer)->second;
            state.usedBuffers.insert(bv.buffer);

            code = GLTF_dataTypeForAccessorComponentTypeV1(indexType, indices.componentType);
            if (code != TE_Ok)
                return code;

            indicesData = &b.data[0] + bv.byteOffset + indices.byteOffset;
            indexCount = indices.count;
        }

        for (auto pIt = prim.attributes.begin(); pIt != prim.attributes.end(); pIt++) {
            auto aIt = scene.accessors.find(pIt->second);
            if (aIt != scene.accessors.end()) {

                const tinygltfloader::Accessor& accessor = aIt->second;
                const tinygltfloader::BufferView& bv = scene.bufferViews.find(accessor.bufferView)->second;
                const tinygltfloader::Buffer& b = scene.buffers.find(bv.buffer)->second;
                state.usedBuffers.insert(bv.buffer);

                auto vertArrayPair = GLTF_vertArrayForAttr(pIt->first, vertLayout);
                VertexArray* vertArray = vertArrayPair.second;
                if (vertArray == nullptr)
                    continue;

                VertexAttribute vertAttr = vertArrayPair.first;
                code = GLTF_dataTypeForAccessorComponentTypeV1(vertArray->type, accessor.componentType);
                if (code != TE_Ok)
                    return code;

                int accessorComponentCount = GLTF_componentCountForAccessorType(accessor.type);
                vertArray->stride = accessor.byteStride;
                vertArray->offset = accessor.byteOffset;
                
                if (vertArray == &vertLayout.position) {
                    verts = &b.data[0] + bv.byteOffset;
                    vertCount = accessor.count;
                    GLTF_setAABBMinMax(aabb, accessor.minValues, accessor.maxValues);
                }
            }
        }

        MeshPtr meshPtr(nullptr, nullptr);
        code = GLTF_createMesh(meshPtr, vertLayout, drawMode,
            verts, vertCount,
            indicesData, indexCount, indexType,
            materials,
            aabb,
            state.meshBufferArgs);

        if (meshPtr) {
            result = MeshPtr(meshPtr.release(), meshPtr.get_deleter());
        }
        return code;
    }
}