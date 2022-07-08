#ifdef MSVC
#include "renderer/raster/tilereader/NodeContextResources.h"

#include <algorithm>

#include "renderer/GL.h"
#include "renderer/GLTexture2.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Renderer::Raster::TileReader;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define GLQuadTileNode3_MAX_GRID_SIZE 32

namespace
{
    struct {
        Mutex mutex;
        std::map<const RenderContext*, std::shared_ptr<NodeContextResources>> value;
    } cache;
}

NodeContextResources::NodeContextResources() NOTHROWS :
    coordStreamBuffer(std::max(
        std::max(
            // max indices
            GLTexture2_getNumQuadMeshIndices(GLQuadTileNode3_MAX_GRID_SIZE, GLQuadTileNode3_MAX_GRID_SIZE) * 2u,
            // max pos/texcoord
            GLTexture2_getNumQuadMeshVertices(GLQuadTileNode3_MAX_GRID_SIZE, GLQuadTileNode3_MAX_GRID_SIZE) * 12u),
        // max interleaved pox.xy,texcoord.uv for texture copy
        (std::size_t)(4u * (8u + 8u)))
    ),
    coordStreamBufferF(reinterpret_cast<float *>(const_cast<uint8_t *>(coordStreamBuffer.get())), coordStreamBuffer.size()/4u),
    coordStreamBufferS(reinterpret_cast<uint16_t *>(const_cast<uint8_t *>(coordStreamBuffer.get())), coordStreamBuffer.size()/2u)
{
    for (std::size_t i = 0u; i < GLQuadTileNode3_MAX_GRID_SIZE; i++) {
        uniformGriddedIndices[i] = GL_NONE;
        uniformGriddedTexCoords[i] = GL_NONE;
    }
}

bool NodeContextResources::isUniformGrid(const std::size_t gridWidth, const std::size_t gridHeight) const NOTHROWS
{
    return (gridWidth == gridHeight) &&
            (gridWidth > 0) &&
            ((gridWidth-1) < GLQuadTileNode3_MAX_GRID_SIZE);
}
GLuint NodeContextResources::getUniformGriddedTexCoords(const std::size_t gridSize) NOTHROWS
{
    if (gridSize >= GLQuadTileNode3_MAX_GRID_SIZE)
        return GL_NONE;
    GLuint uniformTexCoords = uniformGriddedTexCoords[gridSize-1];
    if(uniformTexCoords == GL_NONE) {
        const std::size_t numVerts = GLTexture2_getNumQuadMeshVertices(gridSize, gridSize);
        coordStreamBuffer.reset();
        coordStreamBufferF.reset();
        GLTexture2_createQuadMeshTexCoords(coordStreamBufferF,
                                            Point2<float>(0.f, 0.f),
                                            Point2<float>(1.f, 0.f),
                                            Point2<float>(1.f, 1.f),
                                            Point2<float>(0.f, 1.f),
                                            gridSize,
                                            gridSize);

        uniformTexCoords = genBuffer();
        glBindBuffer(GL_ARRAY_BUFFER, uniformTexCoords);
        glBufferData(GL_ARRAY_BUFFER, numVerts*2*4, coordStreamBuffer.get(), GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        uniformGriddedTexCoords[gridSize-1] = uniformTexCoords;
    }
    return uniformTexCoords;
}
GLuint NodeContextResources::getUniformGriddedIndices(const std::size_t gridSize) NOTHROWS
{
    int uniformIndices = uniformGriddedIndices[gridSize-1];
    if(uniformIndices == GL_NONE) {
        const std::size_t numIndices = GLTexture2_getNumQuadMeshIndices(gridSize, gridSize);
        uniformIndices = genBuffer();
        coordStreamBuffer.reset();
        coordStreamBufferS.reset();
        GLTexture2_createQuadMeshIndexBuffer(coordStreamBufferS, GL_UNSIGNED_SHORT, gridSize, gridSize);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, uniformIndices);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, numIndices * 2, coordStreamBuffer.get(), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);
        uniformGriddedIndices[gridSize-1] = uniformIndices;
    }
    return uniformIndices;
}
GLuint NodeContextResources::discardBuffer(const GLuint id) NOTHROWS
{
    if(id == GL_NONE)
        return GL_NONE;
    if(numNumDiscardBuffers == 16u) {
        glDeleteBuffers(numNumDiscardBuffers, discardBuffers);
        numNumDiscardBuffers = 0;
    }
    discardBuffers[numNumDiscardBuffers++] = id;
    return GL_NONE;

}
GLuint NodeContextResources::genBuffer() NOTHROWS
{
    // if no buffers are available, generate one
    if(numNumDiscardBuffers == 0) {
        glGenBuffers(1, discardBuffers);
        numNumDiscardBuffers++;
    }
    // pull buffer off of end of discard list
    return discardBuffers[--numNumDiscardBuffers];
}
void NodeContextResources::deleteBuffers() NOTHROWS
{
    // delete all discard buffers
    if(numNumDiscardBuffers > 0)
        glDeleteBuffers(numNumDiscardBuffers, discardBuffers);
    numNumDiscardBuffers = 0;
}

TAKErr TAK::Engine::Renderer::Raster::TileReader::NodeContextResources_get(std::shared_ptr<NodeContextResources>& value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(cache.mutex);
    TE_CHECKRETURN_CODE(lock.status);
    auto entry = cache.value.find(&ctx);
    // check cache
    if (entry != cache.value.end()) {
        value = entry->second;
        return code;
    }

    // create a new `NodeContextResources` and add to cache
    value = std::shared_ptr<NodeContextResources>(new NodeContextResources());
    cache.value[&ctx] = value;
    return code;
}
#endif