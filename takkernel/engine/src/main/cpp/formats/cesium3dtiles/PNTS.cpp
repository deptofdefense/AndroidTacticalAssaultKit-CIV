#include <algorithm>
#include <deque>
#include "formats/cesium3dtiles/PNTS.h"
#include "formats/gltf/GLTF.h"
#include "util/Memory.h"
#include "math/Matrix2.h"
#include "model/SceneInfo.h"
#include "model/SceneBuilder.h"
#include "model/MeshBuilder.h"
#include "db/Query.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::Cesium3DTiles;
using namespace TAK::Engine::Formats::GLTF;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Core;

// Use tinygltf's copy of JSON for Modern C++
#include <tinygltf/json.hpp>
using json = nlohmann::json;

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
                extern void GLTF_setMaterialColor(Material& mat, const std::vector<double>& color) NOTHROWS;
            }
        }
    }
}

namespace
{
    enum class PNTS_ColorType 
    {
        RGBA, RGB, RGB565, NONE
    };
    TAKErr PNTS_getColorType(json& featureTable, PNTS_ColorType *colorType, std::size_t *byteOffset)
    {
        if (colorType == nullptr || byteOffset == nullptr)
            return TE_InvalidArg;

        auto color = featureTable.find("RGBA");
        *colorType = PNTS_ColorType::RGBA;
        if (color == featureTable.end())
        {
            color = featureTable.find("RGB");
            *colorType = PNTS_ColorType::RGB;
        }
        if (color == featureTable.end())
        {
            color = featureTable.find("RGB565");
            *colorType = PNTS_ColorType::RGB565;
        }
        if (color == featureTable.end())
        {
            *colorType = PNTS_ColorType::NONE;
            *byteOffset = 0;
            return TE_Ok;
        }

        auto COLOR_byteOffset = color->find("byteOffset");

        if (COLOR_byteOffset == color->end() || !COLOR_byteOffset->is_number())
            return TE_Unsupported;

        *byteOffset = COLOR_byteOffset->get<std::size_t>();
        return TE_Ok;
    }
    TAKErr PNTS_createInterleavedVertices(array_ptr<uint8_t>& interleavedVertices, std::size_t& pointCount, Envelope2& aabb, char* featureTableJson, char* featureTableBinary)
    {
        json featureTable(json::parse(featureTableJson, nullptr, false));
        if (featureTable.is_discarded())
            return TE_Err;

        auto POINTS_LENGTH = featureTable.find("POINTS_LENGTH");
        auto POSITION = featureTable.find("POSITION");

        if (POINTS_LENGTH == featureTable.end() || POSITION == featureTable.end())
            return TE_Unsupported;

        if (!POINTS_LENGTH->is_number())
            return TE_Unsupported;

        pointCount = POINTS_LENGTH->get<std::size_t>();

        auto POSITION_byteOffset = POSITION->find("byteOffset");
        if (POSITION_byteOffset == POSITION->end() || !POSITION_byteOffset->is_number())
            return TE_Unsupported;

        std::size_t positionByteOffset = POSITION_byteOffset->get<std::size_t>();

        PNTS_ColorType colorType;
        std::size_t colorByteOffset;
        TAKErr code = PNTS_getColorType(featureTable, &colorType, &colorByteOffset);
        TE_CHECKRETURN_CODE(code);

        float* positionIt = reinterpret_cast<float*>(featureTableBinary + positionByteOffset);
        interleavedVertices.reset(new uint8_t[pointCount * 4 * 4]);
        float* verts = reinterpret_cast<float*>(interleavedVertices.get());
        for (std::size_t i = 0; i < pointCount; ++i)
        {
            float x = *(positionIt++);
            float y = *(positionIt++);
            float z = *(positionIt++);

            aabb.maxX = std::max(aabb.maxX, (double)x);
            aabb.maxY = std::max(aabb.maxY, (double)y);
            aabb.maxZ = std::max(aabb.maxZ, (double)z);
            
            aabb.minX = std::min(aabb.minX, (double)x);
            aabb.minY = std::min(aabb.minY, (double)y);
            aabb.minZ = std::min(aabb.minZ, (double)z);

            *(verts++) = x;
            *(verts++) = y;
            *(verts++) = z;

        }
        
        uint8_t* colorIt = reinterpret_cast<uint8_t*>(featureTableBinary) + colorByteOffset;
        uint8_t* colors = reinterpret_cast<uint8_t*>(interleavedVertices.get() + pointCount * sizeof(float) * 3);
        for (std::size_t i = 0; i < pointCount; ++i)
        {
            if (colorType == PNTS_ColorType::NONE)
            {
                *(colors++) = 0xFF;
                *(colors++) = 0xFF;
                *(colors++) = 0xFF;
                *(colors++) = 0xFF;
            }
            else if (colorType == PNTS_ColorType::RGBA)
            {
                auto temp = *(colorIt++);
                *(colors++) = *(colorIt++);
                *(colors++) = *(colorIt++);
                *(colors++) = *(colorIt++);
                *(colors++) = temp;
            }
            else if (colorType == PNTS_ColorType::RGB)
            {
                *(colors++) = *(colorIt++);
                *(colors++) = *(colorIt++);
                *(colors++) = *(colorIt++);
                *(colors++) = 0xFF;
            }
            else if (colorType == PNTS_ColorType::RGB565)
            {
                constexpr uint16_t kRed5 = 0x001f;
                constexpr uint16_t kGreen6 = 0x07e0;
                constexpr uint16_t kBlue5 = 0xf800;

                uint16_t* color16 = reinterpret_cast<uint16_t*>(colorIt);
                uint16_t color = *color16;
                *(colors++) = (color & kBlue5) << 3;
                *(colors++) = (color & kGreen6) << 4;
                *(colors++) = (color & kRed5) << 3;
                *(colors++) = 0XFF;
            }
            else
            {
                return TE_InvalidArg;
            }
        }

        return TE_Ok;
    }
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::PNTS_write(const char* URI, std::size_t count, DB::Query& query) NOTHROWS {
    json featureTable;
    featureTable["POSITION"]["byteOffset"] = 0;
    featureTable["RGBA"]["byteOffset"] = count * 4 * 3;
    featureTable["POINTS_LENGTH"] = count;
    std::string sfeatureTable = featureTable.dump();

    std::uint32_t featureTableByteLength = (std::uint32_t)sfeatureTable.size();
    std::uint32_t featureTableBinaryByteLength = (std::uint32_t)count * sizeof(float) * 4;
    std::uint32_t batchTableByteLength = 0;
    std::uint32_t batchTableBinaryByteLength = 0;

    constexpr std::uint32_t MAGIC_PNTS_LE = 0x73746E70;

    TAK::Engine::Util::FileOutput2 fileout;
    fileout.open(URI);
    fileout.writeInt(MAGIC_PNTS_LE);
    fileout.writeInt(1);
    fileout.writeInt(featureTableByteLength + featureTableBinaryByteLength + batchTableByteLength + batchTableBinaryByteLength + 28);
    fileout.writeInt(featureTableByteLength);
    fileout.writeInt(featureTableBinaryByteLength);
    fileout.writeInt(batchTableByteLength);
    fileout.writeInt(batchTableBinaryByteLength);
    fileout.writeString(sfeatureTable.c_str());

    double x, y, z;
    int32_t color;

    std::vector< int32_t> colors;
    colors.reserve(count);

    while (query.moveToNext() == TE_Ok) {
        TAKErr code = query.getDouble(&x, 1);
        TE_CHECKRETURN_CODE(code);
        code = query.getDouble(&y, 2);
        TE_CHECKRETURN_CODE(code);
        code = query.getDouble(&z, 3);
        TE_CHECKRETURN_CODE(code);
        code = query.getInt(&color, 4);
        TE_CHECKRETURN_CODE(code);

        colors.push_back(color);

        fileout.writeFloat((float)x);
        fileout.writeFloat((float)y);
        fileout.writeFloat((float)z);

        if (colors.size() == count)
            break;
    }

    fileout.write(reinterpret_cast<uint8_t*>(colors.data()), sizeof(int32_t) * colors.size());
    fileout.close();

    return TE_Ok;
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::PNTS_parse(Model::ScenePtr& result, Util::DataInput2* input, const char* baseURI) NOTHROWS
{
    TAKErr code;
    int magic, version, byteLength;
    int featureTableJsonByteLength, featureTableBinaryByteLength, batchTableJsonByteLength, batchTableBinaryByteLength;
    code = input->readInt(&magic);
    TE_CHECKRETURN_CODE(code);
    if (magic != 0x73746E70)
        return TE_Unsupported;

    code = input->readInt(&version);
    TE_CHECKRETURN_CODE(code);

    code = input->readInt(&byteLength);
    TE_CHECKRETURN_CODE(code);

    code = input->readInt(&featureTableJsonByteLength);
    TE_CHECKRETURN_CODE(code);

    code = input->readInt(&featureTableBinaryByteLength);
    TE_CHECKRETURN_CODE(code);

    code = input->readInt(&batchTableJsonByteLength);
    TE_CHECKRETURN_CODE(code);

    code = input->readInt(&batchTableBinaryByteLength);
    TE_CHECKRETURN_CODE(code);

    array_ptr<char> featureTableJson, featureTableBinary, batchTableJson, batchTableBinary;
    std::size_t numRead;

    featureTableJson.reset(new char[featureTableJsonByteLength + 1]);
    code = input->readString(featureTableJson.get(), &numRead, featureTableJsonByteLength);
    TE_CHECKRETURN_CODE(code);

    featureTableBinary.reset(new char[featureTableBinaryByteLength]);
    code = input->read(reinterpret_cast<uint8_t*>(featureTableBinary.get()), &numRead, featureTableBinaryByteLength);
    TE_CHECKRETURN_CODE(code);

    if (batchTableJsonByteLength > 0) {
        batchTableJson.reset(new char[batchTableJsonByteLength + 1]);
        code = input->readString(batchTableJson.get(), &numRead, batchTableJsonByteLength);
        TE_CHECKRETURN_CODE(code);

        batchTableBinary.reset(new char[batchTableBinaryByteLength]);
        code = input->read(reinterpret_cast<uint8_t*>(batchTableBinary.get()), &numRead, batchTableBinaryByteLength);
        TE_CHECKRETURN_CODE(code);
    }

    std::size_t pointCount;
    array_ptr<uint8_t> interleavedVertices;
    Envelope2 aabb;
    GLTF_initMeshAABB(aabb);

    code = PNTS_createInterleavedVertices(interleavedVertices, pointCount, aabb, featureTableJson.get(), featureTableBinary.get());
    TE_CHECKRETURN_CODE(code);

    VertexDataLayout vertLayout;
    memset(&vertLayout, 0, sizeof(vertLayout));

    vertLayout.position.size = pointCount;
    vertLayout.position.type = TEDT_Float32;
    vertLayout.position.stride = 0;

    vertLayout.color.size = pointCount;
    vertLayout.color.type = TEDT_Int32;
    vertLayout.color.stride = 0;
    vertLayout.color.offset = 3 * sizeof(float) * pointCount;
    vertLayout.interleaved = true;

    vertLayout.attributes = TEVA_Position | TEVA_Color;

    MeshPtr mesh(nullptr, nullptr);

    code = MeshBuilder_buildInterleavedMesh(
        mesh,
        TEDM_Points,
        TEWO_CounterClockwise,
        vertLayout,
        0,
        nullptr,
        aabb,
        pointCount,
        std::unique_ptr<const void, void(*)(const void*)>((void*)interleavedVertices.release(), Memory_void_array_deleter_const<uint8_t>));

    Matrix2 mat;
    SceneBuilder sceneBuilder(mat, true);
    code = sceneBuilder.addMesh(std::move(mesh), &mat);
    TE_CHECKRETURN_CODE(code);

    code = sceneBuilder.build(result);
    TE_CHECKRETURN_CODE(code);

    return TE_Ok;
}
TAKErr TAK::Engine::Formats::Cesium3DTiles::PNTS_parseInfo(PNTSInfo* info, Util::DataInput2* input, const char* baseURI) NOTHROWS
{
    return TE_Ok;
}

/**
    pointData is packed
    {
        float x, float y, float z;
        uint32_t color;
    }
*/

TAKErr TAK::Engine::Formats::Cesium3DTiles::PNTS_write(const char* URI, uint8_t *pointData, std::size_t numPoints) NOTHROWS {
    json featureTable;
    featureTable["POSITION"]["byteOffset"] = 0;
    featureTable["RGBA"]["byteOffset"] = numPoints * 4 * 3;
    featureTable["POINTS_LENGTH"] = numPoints;
    std::string sfeatureTable = featureTable.dump();

    std::uint32_t featureTableByteLength = (std::uint32_t)sfeatureTable.size();
    std::uint32_t featureTableBinaryByteLength = (std::uint32_t)numPoints * sizeof(float) * 4;
    std::uint32_t batchTableByteLength = 0;
    std::uint32_t batchTableBinaryByteLength = 0;

    constexpr std::uint32_t MAGIC_PNTS_LE = 0x73746E70;

    TAK::Engine::Util::FileOutput2 fileout;
    fileout.open(URI);
    fileout.writeInt(MAGIC_PNTS_LE);
    fileout.writeInt(1);
    fileout.writeInt(featureTableByteLength + featureTableBinaryByteLength + batchTableByteLength + batchTableBinaryByteLength + 28);
    fileout.writeInt(featureTableByteLength);
    fileout.writeInt(featureTableBinaryByteLength);
    fileout.writeInt(batchTableByteLength);
    fileout.writeInt(batchTableBinaryByteLength);
    fileout.writeString(sfeatureTable.c_str());

    std::vector< int32_t> colors;
    colors.reserve(numPoints);

    float* pointsIt = reinterpret_cast<float*>(pointData);
    int32_t* colorsIt = reinterpret_cast<int32_t*>(pointData) + 3;

    for (std::size_t i = 0; i < numPoints; ++i) {
        colors.push_back(*colorsIt);

        fileout.writeFloat(pointsIt[0]);
        fileout.writeFloat(pointsIt[1]);
        fileout.writeFloat(pointsIt[2]);
        pointsIt += 4;
        colorsIt += 4;

    }

    for (auto color2 : colors) {
        fileout.writeInt(color2);
    }
    fileout.close();

    return TE_Ok;

}
