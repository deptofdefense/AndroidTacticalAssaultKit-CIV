#include "model/Mesh.h"

#include "model/MeshBuilder.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

Mesh::~Mesh() NOTHROWS
{}

TAKErr TAK::Engine::Model::Mesh_transform(MeshPtr &value, const Mesh &src, const VertexDataLayout &dstLayout) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::unique_ptr<MeshBuilder> dst;
    if(src.isIndexed()) {
        DataType indexType;
        code = src.getIndexType(&indexType);
        TE_CHECKRETURN_CODE(code);
        dst.reset(new MeshBuilder(src.getDrawMode(), dstLayout, indexType));
    } else {
        dst.reset(new MeshBuilder(src.getDrawMode(), dstLayout));
    }
    const std::size_t numVertices = src.getNumVertices();
    code = dst->reserveVertices(numVertices);
    dst->setWindingOrder(src.getFaceWindingOrder());
    for (unsigned int i = 0u; i < src.getNumMaterials(); i++) {
        Material material;
        code = src.getMaterial(&material, i);
        TE_CHECKBREAK_CODE(code);
        code = dst->addMaterial(material);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    const VertexDataLayout &srcLayout = src.getVertexDataLayout();
    for(std::size_t i = 0u; i < src.getNumVertices(); i++) {
        Point2<double> pos;
        if(srcLayout.attributes&TEVA_Position) {
            code = src.getPosition(&pos, i);
            TE_CHECKBREAK_CODE(code);
        }
        Point2<float> normal;
        if(srcLayout.attributes&TEVA_Normal) {
            code = src.getNormal(&normal, i);
            TE_CHECKBREAK_CODE(code);
        }
        float texCoord[16];
        float *pTexCoord = texCoord;
#define FETCH_TEXCOORD(teva) \
    if(srcLayout.attributes&teva) { \
        Point2<float> uv; \
        code = src.getTextureCoordinate(&uv, teva, i); \
        TE_CHECKBREAK_CODE(code); \
        *pTexCoord++ = uv.x; \
        *pTexCoord++ = uv.y; \
    }
        FETCH_TEXCOORD(TEVA_TexCoord0);
        FETCH_TEXCOORD(TEVA_TexCoord1);
        FETCH_TEXCOORD(TEVA_TexCoord2);
        FETCH_TEXCOORD(TEVA_TexCoord3);
        FETCH_TEXCOORD(TEVA_TexCoord4);
        FETCH_TEXCOORD(TEVA_TexCoord5);
        FETCH_TEXCOORD(TEVA_TexCoord6);
        FETCH_TEXCOORD(TEVA_TexCoord7);
#undef FETCH_TEXCOORD
        unsigned int argb(0);
        if(srcLayout.attributes&TEVA_Color) {
            code = src.getColor(&argb, i);
            TE_CHECKBREAK_CODE(code);
        }
        code = dst->addVertex(pos.x, pos.y, pos.z,
                              texCoord,
                              normal.x, normal.y, normal.z,
                              (float)((argb>>16u)&0xFFu) / (float)255.0,
                              (float)((argb>>8u)&0xFFu) / (float)255.0,
                              (float)(argb&0xFFu) / (float)255.0,
                              (float)((argb>>24u)&0xFFu) / (float)255.0);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    if(src.isIndexed()) {
        const std::size_t numIndices = src.getNumIndices();
        dst->reserveIndices(numIndices);
        for(std::size_t i = 0u; i < numIndices; i++) {
            std::size_t index;
            code = src.getIndex(&index, i);
            TE_CHECKBREAK_CODE(code);
            code = dst->addIndex(index);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }
    code = dst->build(value);
    TE_CHECKRETURN_CODE(code);
    return code;
}
