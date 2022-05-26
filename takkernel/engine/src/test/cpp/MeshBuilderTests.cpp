#include "pch.h"

#include <memory>

#include <model/MeshBuilder.h>
#include <port/Platform.h>
#include <util/Memory.h>

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace takenginetests {

	TEST(MeshBuilderTests, testBuildMoveBuffer) {
        VertexDataLayout layout;
        layout.attributes = TEVA_Position | TEVA_TexCoord0;
        layout.interleaved = true;
        layout.position.offset = 0u;
        layout.position.stride = 20u;
        layout.position.type = TEDT_Float32;
        layout.texCoord0.offset = 8u;
        layout.texCoord0.stride = 20u;
        layout.texCoord0.type = TEDT_Float32;

        const std::size_t numVertices = 4u;
        const std::size_t numIndices = 60u;

        std::unique_ptr<void, void(*)(const void *)> data(new float[numVertices*(layout.position.stride/4u)], Memory_void_array_deleter_const<float>);
        for (std::size_t i = 0u; i < (numVertices*(layout.position.stride/4u)); i++)
            static_cast<float *>(data.get())[i] = (float)i;
        std::unique_ptr<void, void(*)(const void *)> indices(new uint16_t[numIndices], Memory_void_array_deleter_const<uint16_t>);
        for (std::size_t i = 0u; i < numIndices; i++)
            static_cast<uint16_t *>(indices.get())[i] = (uint16_t)i;

        MeshPtr mesh(nullptr, nullptr);


        TAKErr code = MeshBuilder_buildInterleavedMesh(mesh, TEDM_Triangles, TEWO_CounterClockwise, layout, 0, nullptr, Envelope2(0, 0, 0, 0, 0, 0), numVertices, std::move(data), TEDT_UInt16, numIndices, std::move(indices));
        ASSERT_EQ((int)TE_Ok, (int)code);

        ASSERT_EQ(numVertices, mesh->getNumVertices());
        ASSERT_EQ(numIndices, mesh->getNumIndices());

        for (std::size_t i = 0u; i < (numVertices*(layout.position.stride/4u)); i++) {
            const void *blob;
            code = mesh->getVertices(&blob, TEVA_Position);
            ASSERT_EQ((int)TE_Ok, (int)code);
            const float expected = (float)i;
            const float actual = static_cast<const float*>(blob)[i];
            ASSERT_EQ(expected, actual);
        }

        for (std::size_t i = 0u; i < numIndices; i++) {
            const uint16_t expected = (uint16_t)i;
            const uint16_t actual = static_cast<const uint16_t *>(mesh->getIndices())[i];
            ASSERT_EQ(expected, actual);
        }
	}
}