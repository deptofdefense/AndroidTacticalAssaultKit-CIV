#pragma once
#include "EdgeIndicies.h"
#include "IndexData.h"
#include "TileHeader.h"
#include "VertexData.h"
#include "core/Projection2.h"
#include "math/Triangle.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

class Triangle {
    Triangle() : v0(0,0,0),
    v1(0,0,0),
    v2(0,0,0) {
        
    }

public:
    Triangle(Math::Vector4<double> *vv0, Math::Vector4<double> *vv1, Math::Vector4<double> *vv2) : Triangle(){
        v0.x = vv0->x;
        v0.y = vv0->y;
        v0.z = vv0->z;
        
        v1.x = vv1->x;
        v1.y = vv1->y;
        v1.z = vv1->z;
        
        v2.x = vv2->x;
        v2.y = vv2->y;
        v2.z = vv2->z;
    }

    Math::Vector4<double> v0;
    Math::Vector4<double> v1;
    Math::Vector4<double> v2;
};


class ENGINE_API TerrainData {
    
public:

    TerrainData(int level);
    
    Util::TAKErr parseTerrainFile(const char* filename, int level) NOTHROWS;
    Util::TAKErr getElevation(double lat, double lon, double *elevationHae) NOTHROWS;

    bool seamsResolved();
    void resolveSeams(std::vector<TerrainData*> neighbors);

private:

    int _level;
    int _x;
    int _y;
    static const int MAX_RANGE = 32767;

    std::vector<Triangle> skirts;

    std::unique_ptr<TileHeader> header;
    std::unique_ptr<VertexData> vertexData;
    std::unique_ptr<IndexData> indexData;
    std::unique_ptr<EdgeIndicies> edgeIndices;

    bool isValid();

    int getTileX(double lon);
    int getTileY(double lat);

    bool isSeamResolved[4];

    Util::TAKErr getTileCoord(double lat, double lon, Math::Vector4<double>* vec);
    Util::TAKErr getElevation(double lat, double lon, Math::Vector4<double> &v, Math::Triangle &t, double *elevationHae);
    Util::TAKErr getElevation(Math::Vector4<double> &v, double *elevationHae, bool ignoreSkirts);

    void mirror(int dir, TerrainData* other, Math::Vector4<double>* vSrc, Math::Vector4<double>* vDst);
    void addSkirt(Math::Vector4<double>* v1, Math::Vector4<double>* v2, Math::Vector4<double>* v3);
    
    double elevationToZLevel(double elev);
    double zLevelToElevation(double z);
    
    int findEdgeTriangle(int i1, int i2);
    int findEdgeTriangle(int d, int i1, int i2, Math::Vector4<double>* v1, Math::Vector4<double>* v2);
    
    double geodetic_spacing[32];
    
    static void resolveSeams(int d1, TerrainData *t1, TerrainData *t2);
    static double rayIntersectsTriangle(Math::Vector4<double> &p, Math::Vector4<double> v0, Math::Vector4<double> v1, Math::Vector4<double> v2);
    static double getMajor(int edge, Math::Vector4<double>* v);
};


}
}
}
}
