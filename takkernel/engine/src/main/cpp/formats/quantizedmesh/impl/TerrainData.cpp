#include "formats/quantizedmesh/impl/TerrainData.h"
#include "formats/quantizedmesh/TileCoord.h"

#include "thread/Lock.h"

#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "util/IO2.h"
#include "elevation/ElevationManager.h"
#include "util/Logging2.h"


using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;

namespace {
    const double EPSILON = 0.0000001;

    struct LastState {
        int idx = 0;
        Math::Vector4<double> v;
        Math::Vector4<double> nv;
        Math::Vector4<double> vm;

        LastState() : idx(0), v(0,0,0), nv(0,0,0), vm(0,0,0)
        {
        }

        void set(int otheridx, const Math::Vector4<double> &otherv)
        {
            this->idx = otheridx;
            this->v = otherv;
        }

        void set(int otheridx, const Math::Vector4<double> &otherv, 
            const Math::Vector4<double> &othernv, const Math::Vector4<double> &othervm)
        {
            set(otheridx, otherv);
            this->nv = othernv;
            this->vm = othervm;
        }
    };


    /**
     * Triangle intersection test using the Moller-Trumbore intersection algorithm
     * Optimized to only find the z-value at a given point
     *
     * @param p Origin of ray
     * @param v0 First vector defining triangle to test
     * @param v1 Second vector defining triangle to test
     * @param v2 Third vector defining triangle to test
     * @return Z-value that the ray intersects the triangle, or NAN if no intersection found
     */

    double rayIntersectsTriangle(const Math::Vector4<double> &p, const Math::Vector4<double> &v0, const Math::Vector4<double> &v1, const Math::Vector4<double> &v2) NOTHROWS
    {
        // Ignore points outside of the triangle
        if (p.x > v0.x && p.x > v1.x && p.x > v2.x
            || p.x < v0.x && p.x < v1.x && p.x < v2.x
            || p.y > v0.y && p.y > v1.y && p.y > v2.y
            || p.y < v0.y && p.y < v1.y && p.y < v2.y)
            return NAN;

        double a, f, u, v;
        Math::Vector4<double> edge1(0, 0, 0);
        Math::Vector4<double> edge2(0, 0, 0);

        v1.subtract(&v0, &edge1);
        v2.subtract(&v0, &edge2);
        Math::Vector4<double> h(-edge2.y, edge2.x, 0);
        a = edge1.dot(&h);
        if (a > -EPSILON && a < EPSILON)
            return NAN; // This ray is parallel to this triangle.

        f = 1.0 / a;
        Math::Vector4<double> s(0, 0, 0);
        p.subtract(&v0, &s);
        u = f * (s.dot(&h));
        if (u < 0.0 || u > 1.0)
            return NAN;

        Math::Vector4<double> q(0, 0, 0);
        s.cross(&edge1, &q);
        v = f * q.z;
        if (v < 0.0 || u + v > 1.0)
            return NAN;

        // At this stage we can compute t to find out where the intersection point is on the line.
        double t = f * edge2.dot(&q);
        return t >= 0 ? t : NAN;

    }

    double getMajor(int edge, const Math::Vector4<double> &v) NOTHROWS
    {
        return (edge == EdgeIndices::NORTH || edge == EdgeIndices::SOUTH) ? v.x : v.y;
    }

}


TerrainData::TerrainData(int level) NOTHROWS : 
    mutex(Thread::TEMT_Recursive), totalSize(0), level(level), x(0), y(0),
    spacing(TileCoord_getSpacing(level)), header(), vertexData(), 
    indexData(), edgeIndices(), seamsResolved{ false, false, false, false }, skirts()
{
}

Util::TAKErr TAK::Engine::Formats::QuantizedMesh::Impl::TerrainData_deserialize(std::unique_ptr<TerrainData> &result, const char *filename, int level) NOTHROWS
{
    Util::TAKErr code = Util::TE_Ok;
    Util::DataInput2Ptr input(nullptr, nullptr);
    
    code = Util::IO_openFile(input, filename);
    TE_CHECKRETURN_CODE(code);
    
    input->setSourceEndian2(Util::TAKEndian::TE_LittleEndian);
    
    result.reset(new TerrainData(level));

    code = TileHeader_deserialize(result->header, *input);
    TE_CHECKRETURN_CODE(code);
    code = VertexData_deserialize(result->vertexData, *input);
    TE_CHECKRETURN_CODE(code);
    code = IndexData_deserialize(result->indexData, *(result->vertexData), *input);
    TE_CHECKRETURN_CODE(code);
    code = EdgeIndices_deserialize(result->edgeIndices, *(result->vertexData), result->indexData->is32Bit(), *input);
    TE_CHECKRETURN_CODE(code);

    Core::GeoPoint2 g(0.0, 0.0);

    Core::Projection2Ptr ecefProjection(nullptr, nullptr);
    ProjectionFactory3_create(ecefProjection, 4978);
    Math::Point2<double> center(result->header->centerX, result->header->centerY, result->header->centerZ);
    ecefProjection->inverse(&g, center);

    result->x = (int)TileCoord_getTileX(g.longitude, level);
    result->y = (int)TileCoord_getTileY(g.latitude, level);

    result->totalSize = 32 + TileHeader::TOTAL_SIZE + result->vertexData->totalSize + result->indexData->getTotalSize() + result->edgeIndices->getTotalSize();

    return Util::TE_Ok;
}

bool TerrainData::areSeamsResolved() NOTHROWS
{
    Thread::Lock lock(mutex);
    for (size_t i = 0; i < 4; ++i) {
        if (!seamsResolved[i])
            return false;
    }
    return true;
}


void TerrainData::resolveSeams(const std::vector<TerrainData *> &neighbors) NOTHROWS
{
    Thread::Lock lock(mutex);

    for (size_t edge = 0; edge < neighbors.size(); edge++)
        resolveSeams(static_cast<int>(edge), this, neighbors[edge]);
}

int64_t TerrainData::getTotalSize() NOTHROWS
{
    return totalSize;
}


double TerrainData::getElevation(double lat, double lon, bool convertToHAE) NOTHROWS
{
    Thread::Lock lock(mutex);
    Math::Vector4<double> vec(0, 0, -1);
    return getElevationNoSync(lat, lon, convertToHAE, &vec);
}


Util::TAKErr TerrainData::getElevation(double *value, const std::size_t count, const double *srcLat, const double *srcLng,
    const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride, bool convertToHAE) NOTHROWS
{
    Thread::Lock lock(mutex);
    Util::TAKErr ret = Util::TE_Ok;
    Math::Vector4<double> vec(0, 0, -1);

    for (std::size_t i = 0; i < count; ++i) {
        double alt =  getElevationNoSync(srcLat[i * srcLatStride],
                srcLng[i * srcLngStride], convertToHAE, &vec);
        if (std::isnan(alt))
            ret = Util::TE_Done;
        value[i * dstStride] = alt;
    }

    return ret;
}


void TerrainData::addSkirt(const Math::Vector4<double> &v1, const Math::Vector4<double> &v2, const Math::Vector4<double> &v3) NOTHROWS
{
    Thread::Lock lock(mutex);
    skirts.push_back(TriVec(v1, v2, v3));
}

int TerrainData::findEdgeTriangle(int i1, int i2) NOTHROWS
{
    int match = 0;
    int tri = 0;
    int otherIndex = -1;
    for (int i = 0; i < indexData->getLength(); i++) {
        int index = indexData->get(i);
        if (i1 == index || i2 == index)
            match++;
        else
            otherIndex = index;
        if (++tri == 3) {
            if (match == 2 && otherIndex != -1)
                return otherIndex;
            match = 0;
            tri = 0;
        }
    }
    return -1;
}

int TerrainData::findEdgeTriangle(int d, int i1, int i2, const Math::Vector4<double> &v1, const Math::Vector4<double> &v2) NOTHROWS
{
    int xx = (int) (v1.x + v2.x) / 2;
    int yy = (int) (v1.y + v2.y) / 2;

    if (d == EdgeIndices::NORTH)
        yy--;
    else if (d == EdgeIndices::EAST)
        xx--;
    else if (d == EdgeIndices::SOUTH)
        yy++;
    else if (d == EdgeIndices::WEST)
        xx++;

    for (int llevel = IndexData::QUAD_MAX_LEVEL; llevel >= IndexData::QUAD_MIN_LEVEL; llevel--) {

        // Get all triangle indices within the same quadrant as this point
        const std::vector<int> *indices = indexData->getTriangleIndices(llevel, xx, yy);
        if (indices == nullptr)
            continue;

        std::vector<int>::const_iterator iter;
        for (iter = indices->cbegin(); iter != indices->cend(); iter++) {
            int tIndex = *iter;
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

void TerrainData::mirror(int dir, const TerrainData &other, const Math::Vector4<double> &vSrc, Math::Vector4<double>* vDst) NOTHROWS
{
    // Translate X/Y
    if (dir == EdgeIndices::NORTH || dir == EdgeIndices::SOUTH) {
        vDst->x = vSrc.x;
        vDst->y = MAX_RANGE - vSrc.y;
    } else {
        vDst->x = MAX_RANGE - vSrc.x;
        vDst->y = vSrc.y;
    }

    // Translate elevation (non-mirrored)
    double elev = zToElev(vSrc.z);
    vDst->z = other.elevToZ(elev);

}

double TerrainData::zToElev(double z) const NOTHROWS
{
    return header->minimumHeight + ((z / MAX_RANGE) * header->getHeight());
}

double TerrainData::elevToZ(double elev) const NOTHROWS
{
    return ((elev - header->minimumHeight) / header->getHeight()) * MAX_RANGE;
}

void TerrainData::getTileCoord(Math::Vector4<double> *result, double lat, double lon) NOTHROWS
{
    double tileX = (lon + 180) / spacing;
    double tileY = (lat + 90) / spacing;
    result->x = ((tileX - this->x) * TerrainData::MAX_RANGE);
    result->y = ((tileY - this->y) * TerrainData::MAX_RANGE);
}

double TerrainData::getElevationNoSync(double lat, double lng, bool convertToHAE,
    Math::Vector4<double> *vec) NOTHROWS
{
    getTileCoord(vec, lat, lng);
    double altMSL = getElevationNoSync(*vec, false);

    if (convertToHAE && !std::isnan(altMSL)) {
        double offset;
        Util::TAKErr code = Elevation::ElevationManager_getGeoidHeight(&offset, lat, lng);
        if (code != Util::TE_Ok)
            return NAN;
        return altMSL + offset;
    } else {
        return altMSL;
    }
}

double TerrainData::getElevationNoSync(const Math::Vector4<double> &vec, bool ignoreSkirts) NOTHROWS
{
    // First hit-test any skirts
    if (!ignoreSkirts) {
        
        for (auto iter = skirts.begin(); iter != skirts.end(); ++iter) {
            TriVec &skirt = *iter;
            double hitZ = rayIntersectsTriangle(vec, skirt.v0, skirt.v1, skirt.v2);
            if (!std::isnan(hitZ)) {
                //return 0;
                return zToElev(hitZ);
            }
        }
    }

    // Hit-test the rest of the triangles
    int xx = (int) vec.x, yy = (int) vec.y;
    for (int llevel = IndexData::QUAD_MAX_LEVEL; llevel >= IndexData::QUAD_MIN_LEVEL; llevel--) {

        // Get all triangle indices within the same quadrant as this point
        const std::vector<int> *indices = indexData->getTriangleIndices(llevel, xx, yy);
        if (indices == nullptr)
            continue;

        // Triangle indices
        for (auto idxIter = indices->begin(); idxIter != indices->end(); ++idxIter) {
            int tIndex = *idxIter;

            // Vertex indices that make up this triangle
            int i = tIndex * 3;
            int i0 = indexData->get(i);
            int i1 = indexData->get(i + 1);
            int i2 = indexData->get(i + 2);

            // Get the 3 vertices and build the test triangle
            Math::Vector4<double> v0(0, 0, 0);
            Math::Vector4<double> v1(0, 0, 0);
            Math::Vector4<double> v2(0, 0, 0);
            vertexData->get(&v0, i0);
            vertexData->get(&v1, i1);
            vertexData->get(&v2, i2);

            // Test if the point is within this triangle
            double hitZ = rayIntersectsTriangle(vec, v0, v1, v2);
            if (!std::isnan(hitZ))
                return zToElev(hitZ);
        }
    }

    return NAN;

}

void TerrainData::resolveSeams(int d1, TerrainData *t1, TerrainData *t2) NOTHROWS
{
    if (t1 == nullptr || t2 == nullptr || t1->seamsResolved[d1])
        return;

    int d2 = (d1 + 2) % 4;
    if (t2->seamsResolved[d2])
        return;

    const Indices *e1 = t1->edgeIndices->getIndicesForEdge(d1);
    const Indices *e2 = t2->edgeIndices->getIndicesForEdge(d2);

    // Minimum number of edge points is 2 and corners must touch - ignore
    if (e1->getLength() == 2 && e2->getLength() == 2) {
        t1->seamsResolved[d1] = t2->seamsResolved[d2] = true;
        return;
    }

    TerrainData *major = t1, *minor = t2;
    LastState last1;
    LastState last2;
    Math::Vector4<double> v1(0, 0, 0);
    Math::Vector4<double> v2(0, 0, 0);
    Math::Vector4<double> nv(0, 0, 0);
    Math::Vector4<double> vm(0, 0, 0);
    //Triangle hitTriangle = new Triangle(new PointD(0, 0),
    //    new PointD(0, 0), new PointD(0, 0));
    bool lastOverlapped = true;
    bool skirtOpen = false;
    int curIdx, d;
    Math::Vector4<double> v(0, 0, 0);
    LastState *last = &last1;
    int i1 = 0, i2 = 0;
    while (i1 < e1->getLength() && i2 < e2->getLength()) {

        // Get each vertex along both edges
        int ind1 = e1->get(i1);
        int ind2 = e2->get(i2);
        t1->vertexData->get(&v1, ind1);
        t2->vertexData->get(&v2, ind2);

        // If both vertices overlap in 1D space along a given edge
        // then no patching is required - there isn't a case (presently)
        // where 2 vertices overlap with different heights,
        // so we only care about x/y
        double m1 = getMajor(d1, v1);
        double m2 = getMajor(d2, v2);
        bool overlapping = m1 == m2;
        if (lastOverlapped && overlapping) {
            skirtOpen = false;
            last1.set(ind1, v1);
            last2.set(ind2, v2);
            i1++;
            i2++;
            continue;
        }

        // Vertices not equal - create triangle over lower-res tile

        // Find lower-res tile for this pair
        if (lastOverlapped) {
            if (m1 < m2) {
                major = t1;
                minor = t2;
            } else {
                major = t2;
                minor = t1;
            }
        }
        if (major == t1) {
            v = v1;
            curIdx = ind1;
            d = d1;
            last = &last1;
        } else {
            v = v2;
            curIdx = ind2;
            d = d2;
            last = &last2;
        }

        // First need the 3rd point in this triangle
        int ind3 = major->findEdgeTriangle(curIdx, last->idx);
        if (ind3 == -1) {
            Util::Logger_log(Util::LogLevel::TELL_Error, "Failed to find 3rd vertex for edge triangle [%d, %d] in tile %d, %d, %d", curIdx, last->idx, major->level, major->y, major->x);
            break;
        }

        // Overlay 3rd point on opposing tile
        major->vertexData->get(&nv, ind3);

        // TODO: Figure out why this faster method of finding indices doesn't work
        int ind4 = major->findEdgeTriangle(d, curIdx, last->idx, v, last->v);
        if (ind3 != ind4) {
            ind4 = major->findEdgeTriangle(d, curIdx, last->idx, v, last->v);
        }

        // Mirror onto other tile
        major->mirror(d, *minor, nv, &nv);
        major->mirror(d, *minor, v, &vm);
        major->mirror(d, *minor, last->v, &last->vm);
        nv.z = -1;
        nv.z = minor->elevToZ(minor->getElevationNoSync(nv, true));

        // Add new skirt over other tile
        minor->addSkirt(vm, last->vm, nv);

        // Add another triangle to connect this triangle to the last one
        if (skirtOpen)
            minor->addSkirt(last->vm, last->nv, nv);

        skirtOpen = true;
        lastOverlapped = overlapping;
        last->set(curIdx, v, nv, vm);
        last1.set(ind1, v1);
        last2.set(ind2, v2);
        if (!overlapping) {
            if (t1 == major)
                i1++;
            else
                i2++;
        }
    }

    t1->seamsResolved[d1] = t2->seamsResolved[d2] = true;

}



TerrainData::TriVec::TriVec(const Math::Vector4<double> &v0, const Math::Vector4<double> &v1, const Math::Vector4<double> &v2) : v0(v0), v1(v1), v2(v2)
{
}
