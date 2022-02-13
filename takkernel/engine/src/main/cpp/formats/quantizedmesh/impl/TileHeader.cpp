#include "formats/quantizedmesh/impl/TileHeader.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;


TileHeader::TileHeader(double cx, double cy, double cz, 
    float minH, float maxH, double bscX, double bscY, 
    double bscZ, double bsr, double hoX, double hoY, double hoZ) :
    centerX(cx), centerY(cy), centerZ(cz), minimumHeight(minH),
    maximumHeight(maxH), boundingSphereCenterX(bscX), 
    boundingSphereCenterY(bscY), boundingSphereCenterZ(bscZ), 
    boundingSphereRadius(bsr), horizonOcclusionPointX(hoX),
    horizonOcclusionPointY(hoY), horizonOcclusionPointZ(hoZ)
{
}

Util::TAKErr TAK::Engine::Formats::QuantizedMesh::Impl::TileHeader_deserialize(std::unique_ptr<TileHeader> &result, Util::DataInput2 &input)
{
    Util::TAKErr code = Util::TE_Ok;

    double cx, cy, cz;
    float minH, maxH;
    double bscX, bscY, bscZ, bsr, hoX, hoY, hoZ;

    code = input.readDouble(&cx);
    TE_CHECKRETURN_CODE(code);
    code = input.readDouble(&cy);
    TE_CHECKRETURN_CODE(code);
    code = input.readDouble(&cz);
    TE_CHECKRETURN_CODE(code);

    code = input.readFloat(&minH);
    TE_CHECKRETURN_CODE(code);
    code = input.readFloat(&maxH);
    TE_CHECKRETURN_CODE(code);

    code = input.readDouble(&bscX);
    TE_CHECKRETURN_CODE(code);
    code = input.readDouble(&bscY);
    TE_CHECKRETURN_CODE(code);
    code = input.readDouble(&bscZ);
    TE_CHECKRETURN_CODE(code);
    code = input.readDouble(&bsr);
    TE_CHECKRETURN_CODE(code);

    code = input.readDouble(&hoX);
    TE_CHECKRETURN_CODE(code);
    code = input.readDouble(&hoY);
    TE_CHECKRETURN_CODE(code);
    code = input.readDouble(&hoZ);
    TE_CHECKRETURN_CODE(code);

    result.reset(new TileHeader(cx, cy, cz, minH, maxH, bscX, bscY, bscZ, bsr, hoX, hoY, hoZ));
    return Util::TE_Ok;
}

float TileHeader::getHeight() const
{
    return maximumHeight - minimumHeight;
}