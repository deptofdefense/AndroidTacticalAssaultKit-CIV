#ifndef TAK_ENGINE_MODEL_MODELBUILDER_H_INCLUDED
#define TAK_ENGINE_MODEL_MODELBUILDER_H_INCLUDED

#include "model/Mesh.h"
#include "model/VertexDataLayout.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API MeshBuilder
            {
            public:
                MeshBuilder(const DrawMode &mode, unsigned int attrs) NOTHROWS;
                MeshBuilder(const DrawMode &mode, unsigned int attrs, const Port::DataType &indexType) NOTHROWS;
                MeshBuilder(const DrawMode &mode, const VertexDataLayout &layout) NOTHROWS;
                MeshBuilder(const DrawMode &mode, const VertexDataLayout &layout, const Port::DataType &indexType) NOTHROWS;
            public :
                ~MeshBuilder() NOTHROWS;
            public :
                Util::TAKErr reserveVertices(const std::size_t count) NOTHROWS;
                Util::TAKErr reserveIndices(const std::size_t count) NOTHROWS;
                Util::TAKErr setVertexDataLayout(const VertexDataLayout &layout) NOTHROWS;
                Util::TAKErr setWindingOrder(const WindingOrder &windingOrder) NOTHROWS;
                Util::TAKErr addMaterial(const Material &material) NOTHROWS;
                Util::TAKErr addVertex(const double posx, const double posy, const double posz,
                                       const float texu, const float texv,
                                       const float nx, const float ny, const float nz,
                                       const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addVertex(const double posx, const double posy, const double posz,
                                       const float *texCoords,
                                       const float nx, const float ny, const float nz,
                                       const float r, const float g, const float b, const float a) NOTHROWS;

                Util::TAKErr addIndex(const std::size_t index) NOTHROWS;
                /**
                 *
                 * @param indices
                 * @param off
                 * @param count The number of indices
                 */
                Util::TAKErr addIndices(const uint32_t *indices, const std::size_t count) NOTHROWS;
                Util::TAKErr addIndices(const uint16_t *indices, const std::size_t count) NOTHROWS;
                Util::TAKErr addIndices(const uint8_t *indices, const std::size_t count) NOTHROWS;

                Util::TAKErr addBuffer(std::unique_ptr<const void, void(*)(const void*)>&& buffer, size_t bufferSize) NOTHROWS;

                Util::TAKErr build(MeshPtr &value) NOTHROWS;
            private :
                MeshPtr impl;
                Util::TAKErr initErr;
            };

            struct MemBufferArg {
                std::unique_ptr<const void, void (*)(const void*)> buffer;
                size_t bufferSize;
            };

            // creates copy of source mesh data
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *vertices) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *vertices, const Port::DataType indexType, const std::size_t numIndices, const void *indices) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *positions, const void **texCoords, const void *normals, const void *colors) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *positions, const void **texCoords, const void *normals, const void *colors, const Port::DataType indexType, const std::size_t numIndices, const void *indices) NOTHROWS;

            // creates mesh by moving data
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, std::unique_ptr<const void, void(*)(const void *)> &&vertices) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, std::unique_ptr<const void, void(*)(const void *)> &&vertices, const Port::DataType indexType, const std::size_t numIndices, std::unique_ptr<const void, void(*)(const void *)> &&indices) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, std::unique_ptr<const void, void(*)(const void *)> &&positions, std::unique_ptr<const void, void(*)(const void *)> &&texCoords0, std::unique_ptr<const void, void(*)(const void *)> &&texCoords1, std::unique_ptr<const void, void(*)(const void *)> &&texCoords2, std::unique_ptr<const void, void(*)(const void *)> &&texCoords3, std::unique_ptr<const void, void(*)(const void *)> &&texCoords4, std::unique_ptr<const void, void(*)(const void *)> &&texCoords5, std::unique_ptr<const void, void(*)(const void *)> &&texCoords6, std::unique_ptr<const void, void(*)(const void *)> &&texCoords7, std::unique_ptr<const void, void(*)(const void *)> &&normals, std::unique_ptr<const void, void(*)(const void *)> &&colors) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, std::unique_ptr<const void, void(*)(const void *)> &&positions, std::unique_ptr<const void, void(*)(const void *)> &&texCoords0, std::unique_ptr<const void, void(*)(const void *)> &&texCoords1, std::unique_ptr<const void, void(*)(const void *)> &&texCoords2, std::unique_ptr<const void, void(*)(const void *)> &&texCoords3, std::unique_ptr<const void, void(*)(const void *)> &&texCoords4, std::unique_ptr<const void, void(*)(const void *)> &&texCoords5, std::unique_ptr<const void, void(*)(const void *)> &&texCoords6, std::unique_ptr<const void, void(*)(const void *)> &&texCoords7, std::unique_ptr<const void, void(*)(const void *)> &&normals, std::unique_ptr<const void, void(*)(const void *)> &&colors, const Port::DataType indexType, const std::size_t numIndices, std::unique_ptr<const void, void(*)(const void *)> &&indices) NOTHROWS;

            // creates mesh from raw pointers, single struct for cleanup
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *vertices, std::unique_ptr<void, void(*)(const void *)> &&cleaner) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *vertices, const Port::DataType indexType, const std::size_t numIndices, const void *indices, std::unique_ptr<void, void(*)(const void *)> &&cleaner) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *positions, const void **texCoords, const void *normals, const void *colors, std::unique_ptr<void, void(*)(const void *)> &&cleaner) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, const void *positions, const void **texCoords, const void *normals, const void *colors, const Port::DataType indexType, const std::size_t numIndices, const void *indices, std::unique_ptr<void, void(*)(const void *)> &&cleaner) NOTHROWS;

            // creates mesh by moving data
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr& value, const DrawMode mode, const WindingOrder order, const VertexDataLayout& layout, const std::size_t numMaterials, const Material* materials, const Feature::Envelope2& aabb, const std::size_t numVertices, std::unique_ptr<const void, void(*)(const void*)>&& vertices,
                size_t numBuffers, MemBufferArg* buffers) NOTHROWS;
            ENGINE_API Util::TAKErr MeshBuilder_buildInterleavedMesh(MeshPtr& value, const DrawMode mode, const WindingOrder order, const VertexDataLayout& layout, const std::size_t numMaterials, const Material* materials, const Feature::Envelope2& aabb, const std::size_t numVertices, std::unique_ptr<const void, void(*)(const void*)>&& vertices, const Port::DataType indexType, const std::size_t numIndices, std::unique_ptr<const void, void(*)(const void*)>&& indices,
                size_t numBuffers, MemBufferArg* buffers) NOTHROWS;
        }
    }
}

#endif
