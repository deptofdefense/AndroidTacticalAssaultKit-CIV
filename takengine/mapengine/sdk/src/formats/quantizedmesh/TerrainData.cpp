#include "TerrainData.h"

#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "elevation/ElevationManager.h"
#include "formats/egm/EGM96.h"
#include "math/Triangle.h"
#include "math/Vector4.h"
#include "util/DataInput2.h"
#include "util/IO.h"
#include "util/IO2.h"

using namespace TAK::Engine::Util;

struct LastState {

    LastState() : v(0,0,0), nv(0,0,0), vm(0,0,0){
    }
    
    int idx = 0;
    TAK::Engine::Math::Vector4<double> v;
    TAK::Engine::Math::Vector4<double> nv;
    TAK::Engine::Math::Vector4<double> vm;

    void set(int otheridx, TAK::Engine::Math::Vector4<double> otherv) {
        this->idx = otheridx;
        this->v = otherv;
    }

    void set (int otheridx, TAK::Engine::Math::Vector4<double> otherv, TAK::Engine::Math::Vector4<double> othernv, TAK::Engine::Math::Vector4<double> othervm) {
        set(otheridx, otherv);
        this->nv = othernv;
        this->vm = othervm;
    }
};

TAKErr TAK::Engine::Formats::QuantizedMesh::TerrainData::parseTerrainFile(const char* filename, int zlevel) NOTHROWS 
{
    this->_level = zlevel;
    std::unique_ptr<FileInput2> finput = std::make_unique<FileInput2>();
    if(TE_PlatformEndian == TE_BigEndian)
        finput->setSourceEndian(atakmap::util::LITTLE_ENDIAN);
    
    finput->open(filename);

    header = std::make_unique<TileHeader>(finput.get());
    vertexData = std::make_unique<VertexData>(finput.get());
    indexData = std::make_unique<IndexData>(vertexData.get(), finput.get());
    edgeIndices = std::make_unique<EdgeIndicies>(vertexData.get(), indexData->is32bit, finput.get());

    Core::GeoPoint2 g(0.0, 0.0);

    Core::Projection2Ptr ecefProjection(nullptr, nullptr);
    ProjectionFactory3_create(ecefProjection, 4978);
    Math::Point2<double> center(header->centerX, header->centerY, header->centerZ);
    ecefProjection->inverse(&g, center);

    this->_x = getTileX(g.longitude);
    this->_y = getTileY(g.latitude);

    for(int i=0; i<4; i++) {
        isSeamResolved[i] = false;
    }
    
    return TE_Ok;
}

TAK::Engine::Formats::QuantizedMesh::TerrainData::TerrainData(int level)
{
    double div = 1;
    for (int i = 0; i < 32; i++) {
        geodetic_spacing[i] = 180 / div;
        div *= 2;
    }
}

TAKErr TAK::Engine::Formats::QuantizedMesh::TerrainData::getElevation(double lat, double lon, double *elevationHae) NOTHROWS
{
    if(!isValid()) *elevationHae = NAN;

    Math::Vector4<double> vec(0,0,-1);

    getTileCoord(lat, lon, &vec);
    getElevation(vec, elevationHae, false);
    if(!isnan(*elevationHae)) {
        double geoidHeight;
        Elevation::ElevationManager_getGeoidHeight(&geoidHeight, lat, lon);
        *elevationHae += geoidHeight;
    }

    return TE_Ok;
}

bool TAK::Engine::Formats::QuantizedMesh::TerrainData::seamsResolved() {
    for(int i=0; i<4; i++) {
        if(!isSeamResolved[i]) return false;
    }

    return true;
}

void TAK::Engine::Formats::QuantizedMesh::TerrainData::resolveSeams(std::vector<TerrainData*> neighbors) {
    for(int edge =0; edge < 4; edge++) {
        resolveSeams(edge, this, neighbors[edge]);
    }
}

TAKErr TAK::Engine::Formats::QuantizedMesh::TerrainData::getTileCoord(double lat, double lon, Math::Vector4<double>* vec)
{
    double tileX = (lon + 180) / geodetic_spacing[this->_level];
    double tileY = (lat + 90) / geodetic_spacing[this->_level];

    vec->x = (tileX - this->_x) * MAX_RANGE;
    vec->y = (tileY - this->_y) * MAX_RANGE;

    return TE_Ok;
}
TAKErr TAK::Engine::Formats::QuantizedMesh::TerrainData::getElevation(Math::Vector4<double> &v, double *elevationHae, bool ignoreSkirts)
{

    if(!ignoreSkirts) {
        for(auto skirt : this->skirts) {
            double hitz = rayIntersectsTriangle(v, skirt.v0, skirt.v1, skirt.v2);
            if(!isnan(hitz)) {
                *elevationHae = zLevelToElevation(hitz);
            }
        }
    }
    
    int x = (int)v.x;
    int y = (int)v.y;

    for(int level = TriangleIndices::MAX_LEVEL; level >= TriangleIndices::MIN_LEVEL; level--)
    {
        auto indices = indexData->getTriangleIndices(level, x, y);
        if(indices.empty()) continue;
        
        for(auto tidx = indices.begin(); tidx != indices.end(); ++tidx) {
            int i = *tidx * 3;
            int i0 = indexData->get(i);
            int i1 = indexData->get(i+1);
            int i2 = indexData->get(i+2);

            
            Math::Vector4<double> v0(0,0,0);
            Math::Vector4<double> v1(0,0,0);
            Math::Vector4<double> v2(0,0,0);
            vertexData->get(i0, &v0);
            vertexData->get(i1, &v1);
            vertexData->get(i2, &v2);

            double hitz = rayIntersectsTriangle(v, v0, v1, v2 );

            if(!isnan(hitz)) {
                *elevationHae = zLevelToElevation(hitz);
                return TE_Ok;
            }
        }
    }

    *elevationHae = NAN;

    return TE_Ok;
}


double TAK::Engine::Formats::QuantizedMesh::TerrainData::rayIntersectsTriangle(Math::Vector4<double>& p, Math::Vector4<double> v0, Math::Vector4<double> v1, Math::Vector4<double> v2) {

    // Ignore points outside of the triangle
    if (p.x > v0.x && p.x> v1.x && p.x> v2.x
            || p.x < v0.x && p.x < v1.x && p.x < v2.x
            || p.y > v0.y && p.y > v1.y && p.y > v2.y
            || p.y < v0.y && p.y < v1.y && p.y < v2.y)
                return NAN;
    double a, f, u, v;
    Math::Vector4<double> edge1(0,0,0);
    Math::Vector4<double> edge2(0,0,0);
    
    v1.subtract(&v0, &edge1);
    v2.subtract(&v0, &edge2);

    Math::Vector4<double> h(-edge2.y,edge2.x,0);
    a = edge1.dot(&h);
    if (a > -DBL_EPSILON && a < DBL_EPSILON)
        return NAN;

    f = 1.0 / a;
    Math::Vector4<double> s(0,0,0);
    p.subtract(&v0, &s);
    
    u = f * (s.dot(&h));
    if (u < 0.0 || u > 1.0)
        return NAN;

    Math::Vector4<double> q(0,0,0);
    s.cross(&edge1, &q);
    
    v = f * q.z;
    if (v < 0.0 || u + v > 1.0)
        return NAN;

    // At this stage we can compute t to find out where the intersection point is on the line.
    double t = f * edge2.dot(&q);
    return t >= 0 ? t : NAN;
}

double TAK::Engine::Formats::QuantizedMesh::TerrainData::getMajor(int edge, Math::Vector4<double>* v) {
    if(edge == EdgeIndicies::NORTH || edge == EdgeIndicies::SOUTH) return v->x;
    else return v->y;
}

int TAK::Engine::Formats::QuantizedMesh::TerrainData::findEdgeTriangle(int i1, int i2) {
    int match = 0;
    int tri = 0;
    int otherIndex = -1;

    for(int i=0; i< indexData->getLength(); i++) {
        int index = indexData->get(i);
        if (i1 == index || i2 == index)
            match++;
        else
            otherIndex = index;

        if(++tri == 3) {
            if(match == 2 && otherIndex != -1)
                return otherIndex;

            match = 0;
            tri = 0;
        }
    }

    return -1;
}

int TAK::Engine::Formats::QuantizedMesh::TerrainData::findEdgeTriangle(int d, int i1, int i2, Math::Vector4<double>* v1,
    Math::Vector4<double>* v2) {
    int x = (int) (v1->x + v2->x) / 2;
    int y = (int) (v1->y + v2->y) / 2;

    if (d == EdgeIndicies::NORTH)
        y--;
    else if (d == EdgeIndicies::EAST)
        x--;
    else if (d == EdgeIndicies::SOUTH)
        y++;
    else if (d == EdgeIndicies::WEST)
        x++;

    for (int level = TriangleIndices::MAX_LEVEL; level >= TriangleIndices::MIN_LEVEL; level--) {

        // Get all triangle indices within the same quadrant as this point
        auto indices = indexData->getTriangleIndices(level, x, y);
        if (indices.empty())
            continue;

        for (int tIndex : indices) {
            int i = tIndex * 3;
            int end = i + 3;
            int match = 0;
            int other = -1;
            for (; i < end; i++) {
                if (i == i1 || i == i2)
                    match++;
                else
                    other = i;
            }
            if (match == 2 && other != -1)
                return other;
        }
    }

    return -1;
 
}

double TAK::Engine::Formats::QuantizedMesh::TerrainData::elevationToZLevel(double elev) {
    return ((elev - header->minimumHeight) / header->getHeight()) * MAX_RANGE;
}

void TAK::Engine::Formats::QuantizedMesh::TerrainData::mirror(int dir, TerrainData* other, Math::Vector4<double>* vSrc,
                                                              Math::Vector4<double>* vDst) {

    if(dir == EdgeIndicies::NORTH || dir == EdgeIndicies::SOUTH) {
        vDst->x = vSrc->x;
        vDst->y = MAX_RANGE - vSrc->y;
    }
    else {
        vDst->x = MAX_RANGE - vSrc->x;
        vDst->y = vSrc->y;
    }

    double elev = zLevelToElevation(vSrc->z);
    vDst->z = other->elevationToZLevel(elev);
}

void TAK::Engine::Formats::QuantizedMesh::TerrainData::addSkirt(Math::Vector4<double>* v1, Math::Vector4<double>* v2,
    Math::Vector4<double>* v3) {
    
    this->skirts.push_back(Triangle(v1, v2, v3));
}

void TAK::Engine::Formats::QuantizedMesh::TerrainData::resolveSeams(int d1, TerrainData *t1, TerrainData *t2) {
    if(!t1) return;
    if(!t2) return;
    if(t1->isSeamResolved[d1]) return;

    int d2 = (d1 + 2 ) % 4;
    if(t2->isSeamResolved[d2]) return;

    auto e1 = t1->edgeIndices->get(d1);
    auto e2 = t2->edgeIndices->get(d2);

    // Minimum number of edge points is 2 and corners must touch - ignore
    if(e1->length == 2 && e2->length == 2) {
        t1->isSeamResolved[d1] = t2->isSeamResolved[d2] = true;
        return;
    }

    Math::Vector4<double> v1(0.0, 0,0);
    Math::Vector4<double> v2(0.0, 0,0);
    Math::Vector4<double> nv(0, 0,0);
    Math::Vector4<double> vm(0, 0,0);
    
    TerrainData *major = t1;
    TerrainData *minor = t2;
    
    LastState last1;
    LastState last2;
    
    bool lastOverlapped = true;
    bool skirtOpen = false;
    int curIdx;
    int d = 0;
    int i1 = 0;
    int i2 = 0;
    
    Math::Vector4<double> *v;
    LastState last = last1;
    
    while(i1 < e1->length && i2 < e2->length) {
        int ind1 = e1->get(i1);
        int ind2 = e2->get(i2);
    
        t1->vertexData->get(ind1, &v1);
        t2->vertexData->get(ind2, &v2);
    
        double m1 = getMajor(d1, &v1);
        double m2 = getMajor(d2, &v2);
    
        bool overlapping = m1 == m2;
        if(lastOverlapped && overlapping) {
            skirtOpen = false;
            i1++;
            i2++;
    
            last1.set(ind1, v1);
            last2.set(ind2, v2);
            
            continue;
        }
    
        if(lastOverlapped) {
            if(m1 < m2) {
                major = t1;
                minor = t2;
            }
            else {
                major = t2;
                minor = t1;
            }
        }
    
        if(major == t1) {
            v = &v1;
            curIdx = ind1;
            d = d1;
            last = last1;
        }
        else {
            v = &v2;
            curIdx = ind2;
            d = d2;
            last = last2;
        }
    
        int ind3 = major->findEdgeTriangle(curIdx, last.idx);
        if(ind3 == -1 ) {
            break;
        }
    
        major->vertexData->get(ind3, &nv);
        int ind4 = major->findEdgeTriangle(d, curIdx, last.idx, v, &last.v);
        if (ind3 != ind4) {
            ind4 = major->findEdgeTriangle(d, curIdx, last.idx, v, &last.v);
        }
    
        major->mirror(d, minor, &nv, &nv);
        major->mirror(d, minor, v, &vm);
        major->mirror(d, minor, &last.v, &last.vm);
    
        nv.z = -1;
        double elevation;
    
        minor->getElevation(nv, &elevation, true);
        nv.z = minor->elevationToZLevel(elevation);
    
        minor->addSkirt(&vm, &last.vm, &nv);
        if(skirtOpen) {
            minor->addSkirt(&last.vm, &last.nv, &nv);
        }
    
        skirtOpen = true;
        lastOverlapped = overlapping;
        last.set(curIdx, *v, nv, vm);
        last1.set(ind1, v1);
        last2.set(ind2, v2);
    
        if(!overlapping) {
            if (t1 == major)
                i1++;
            else
                i2++;
        }
        
    }
    
    t1->isSeamResolved[d1] = t2->isSeamResolved[d2] = true;
}

double TAK::Engine::Formats::QuantizedMesh::TerrainData::zLevelToElevation(double z) {
    return header->minimumHeight + (( z / MAX_RANGE) * header->getHeight());
}

bool TAK::Engine::Formats::QuantizedMesh::TerrainData::isValid()
{
    if(vertexData && indexData) return true;
    else return false;
}

int TAK::Engine::Formats::QuantizedMesh::TerrainData::getTileX(double lon)
{
    return (int)((lon + 180) / geodetic_spacing[_level]);
}

int TAK::Engine::Formats::QuantizedMesh::TerrainData::getTileY(double lat)
{
    return (int)((lat+90) / geodetic_spacing[_level]);
}

TAKErr TAK::Engine::Formats::QuantizedMesh::TerrainData::getElevation(double lat, double lon, Math::Vector4<double> &v,
                                                                      Math::Triangle &t, double* elevationHae)
{
    *elevationHae = 0;
    return TE_Ok;
}

