#include "math/Mesh.h"

#include "math/AABB.h"
#include "math/Triangle.h"
#include "model/MeshBuilder.h"
#include "model/MeshTransformer.h"
#include "util/Memory.h"

using namespace TAK::Engine::Math;

using namespace TAK::Engine;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    template<class V, class I>
    bool intersectImpl(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;

    bool intersectGeneric(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;

    AABB aabb(const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;
}

Mesh::Mesh(const double *vertices, const std::size_t numVertexColumns, const std::size_t numVertexRows) NOTHROWS
{
    if ((numVertexRows*numVertexColumns) > 1u) {
        Model::MeshBuilder builder(Model::TEDM_Triangles, Model::TEVA_Position);
        builder.reserveVertices(((numVertexColumns - 1u)*(numVertexRows - 1u)) * 6u);

        for (std::size_t row = 0; row < numVertexRows - 1; row++) {
            for (std::size_t col = 0; col < numVertexColumns - 1; col++) {
                const std::size_t ulIdx = ((row*numVertexColumns) + col) * 3;
                const std::size_t urIdx = ((row*numVertexColumns) + col + 1) * 3;
                const std::size_t lrIdx = (((row + 1)*numVertexColumns) + col + 1) * 3;
                const std::size_t llIdx = (((row + 1)*numVertexColumns) + col) * 3;

                const double ulx = vertices[ulIdx];
                const double uly = vertices[ulIdx+1u];
                const double ulz = vertices[ulIdx+2u];
                const double urx = vertices[urIdx];
                const double ury = vertices[urIdx + 1u];
                const double urz = vertices[urIdx + 2u];
                const double lrx = vertices[lrIdx];
                const double lry = vertices[lrIdx + 1u];
                const double lrz = vertices[lrIdx + 2u];
                const double llx = vertices[llIdx];
                const double lly = vertices[llIdx + 1u];
                const double llz = vertices[llIdx + 2u];

                builder.addVertex(ulx, uly, ulz, nullptr, 0, 0, 0, 0, 0, 0, 0);
                builder.addVertex(llx, lly, llz, nullptr, 0, 0, 0, 0, 0, 0, 0);
                builder.addVertex(urx, ury, urz, nullptr, 0, 0, 0, 0, 0, 0, 0);

                builder.addVertex(urx, ury, urz, nullptr, 0, 0, 0, 0, 0, 0, 0);
                builder.addVertex(llx, lly, llz, nullptr, 0, 0, 0, 0, 0, 0, 0);
                builder.addVertex(lrx, lry, lrz, nullptr, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        Model::MeshPtr model(nullptr, nullptr);
        builder.build(model);

        data = std::move(model);
    }
}

Mesh::Mesh(Model::MeshPtr_const &&data_, const Matrix2 *localFrame_) NOTHROWS :
    data(std::move(data_)),
    hasLocalFrame(!!localFrame_)
{
    if(hasLocalFrame)
        localFrame.set(*localFrame_);
}
Mesh::Mesh(const std::shared_ptr<const Model::Mesh> &data_, const Matrix2 *localFrame_) NOTHROWS :
    data(data_),
    hasLocalFrame(!!localFrame_)
{
    if(hasLocalFrame)
        localFrame.set(*localFrame_);
}
Mesh::Mesh(const Mesh &other) NOTHROWS
{
    Model::MeshPtr model(nullptr, nullptr);
    Model::Mesh_transform(model, *data, data->getVertexDataLayout());

    data = std::move(model);
    localFrame.set(other.localFrame);
    hasLocalFrame = other.hasLocalFrame;
}

Mesh::~Mesh() NOTHROWS
{}

bool Mesh::intersect(Point2<double> *value, const Ray2<double> &ray) const NOTHROWS
{
    const Matrix2 *pLocalFrame = hasLocalFrame ? &localFrame : nullptr;
    const Math::AABB aabb_wcs = aabb(*data, pLocalFrame);
    const bool aabb_isect = aabb_wcs.intersect(value, ray);
    const bool aabb_contains = (ray.origin.x >= aabb_wcs.minX && ray.origin.x <= aabb_wcs.maxX &&
                                ray.origin.y >= aabb_wcs.minY && ray.origin.y <= aabb_wcs.maxY &&
                                ray.origin.z >= aabb_wcs.minZ && ray.origin.z <= aabb_wcs.maxZ);

    // screen for AABB intersection
    if (!aabb_isect && !aabb_contains)
        return false;

    switch (data->getVertexDataLayout().position.type) {
#define VT_CASE(tedt, vt) \
    case tedt : \
        if (data->isIndexed()) { \
            DataType indexType; \
            data->getIndexType(&indexType); \
            switch(indexType) { \
                case TEDT_UInt8 : return intersectImpl<vt, uint8_t>(value, ray, *data, pLocalFrame); \
                case TEDT_UInt16 : return intersectImpl<vt, uint16_t>(value, ray, *data, pLocalFrame); \
                case TEDT_UInt32 : return intersectImpl<vt, uint32_t>(value, ray, *data, pLocalFrame); \
                default : break; \
            } \
        } else { \
            return intersectImpl<vt, bool>(value, ray, *data, pLocalFrame); \
        } \
        break;

        VT_CASE(TEDT_Int8, int8_t)
        VT_CASE(TEDT_UInt8, uint8_t)
        VT_CASE(TEDT_Int16, int16_t)
        VT_CASE(TEDT_UInt16, uint16_t)
        VT_CASE(TEDT_Int32, int32_t)
        VT_CASE(TEDT_UInt32, uint32_t)
        VT_CASE(TEDT_Float32, float)
        VT_CASE(TEDT_Float64, double)
    }

    return intersectGeneric(value, ray, *data, pLocalFrame);
}

void Mesh::clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const NOTHROWS
{
    value = GeometryModel2Ptr(new Mesh(*this), Memory_deleter_const<GeometryModel2, Mesh>);
}

GeometryModel2::GeometryClass Mesh::getGeomClass() const NOTHROWS
{
    return GeometryModel2::MESH;
}

namespace
{

bool intersectGeneric(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS
{
    std::size_t s;
    switch (mesh.getDrawMode()) {
    case Model::TEDM_Triangles:
        s = 3u;
        break;
    case Model::TEDM_TriangleStrip:
        s = 1u;
        break;
    default:
        return false;
    }

    double isectDistSq = NAN;
    bool haveCandidate = false;

    // transform the ray into the local CS (if applicable)
    Ray2<double> localRay(ray);
    if(localFrame) {
        Matrix2 invLocalFrame;
        localFrame->createInverse(&invLocalFrame);
        Point2<double> localRayOrg;
        invLocalFrame.transform(&localRayOrg, ray.origin);
        Point2<double> localRayDir(ray.origin.x+ray.direction.x, ray.origin.y+ray.direction.y, ray.origin.z+ray.direction.z);
        invLocalFrame.transform(&localRayDir, localRayDir);
        localRayDir.x -= localRayOrg.x;
        localRayDir.y -= localRayOrg.y;
        localRayDir.z -= localRayOrg.z;
        localRay = Ray2<double>(localRayOrg, Vector4<double>(localRayDir.x, localRayDir.y, localRayDir.z));
    }

    const std::size_t faceCount = mesh.getNumFaces();
    const bool indices = mesh.isIndexed();
    for (std::size_t face = 0; face < faceCount; face++) {
        std::size_t aidx = face * s;
        std::size_t bidx = face * s + 1;
        std::size_t cidx = face * s + 2;

        if (indices) {
            mesh.getIndex(&aidx, aidx);
            mesh.getIndex(&bidx, bidx);
            mesh.getIndex(&cidx, cidx);
        }

        Point2<double> a;
        mesh.getPosition(&a, aidx);
        Point2<double> b;
        mesh.getPosition(&b, bidx);
        Point2<double> c;
        mesh.getPosition(&c, cidx);

        Point2<double> abc[3] = { a, b, c};

        AABB aabb(abc, 3u);
        Point2<double> p;
        if(!aabb.intersect(&p, localRay))
            continue;

        if(haveCandidate) {
            const double dx = (p.x - localRay.origin.x);
            const double dy = (p.y - localRay.origin.y);
            const double dz = (p.z - localRay.origin.z);
            const double distSq = (dx*dx) + (dy*dy) + (dz*dz);
            if(distSq > isectDistSq)
                continue;
        }

        int code = Triangle_intersect(&p,
            a.x, a.y, a.z,
            b.x, b.y, b.z,
            c.x, c.y, c.z,
            localRay);
        if (code != TE_Ok)
            continue;

        const double dx = (p.x - localRay.origin.x);
        const double dy = (p.y - localRay.origin.y);
        const double dz = (p.z - localRay.origin.z);
        const double distSq = (dx*dx) + (dy*dy) + (dz*dz);
        if (!haveCandidate || (distSq < isectDistSq)) {
            *value = p;
            isectDistSq = distSq;
            haveCandidate = true;
        }
    }

    if(!haveCandidate)
        return false;

    // transform the intersect point from the local CS (if applicable
    if(localFrame)
        localFrame->transform(value, *value);
    return true;
}

template<class V, class I>
bool intersectImpl(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS
{
    std::size_t s;
    switch (mesh.getDrawMode()) {
    case Model::TEDM_Triangles:
        s = 3u;
        break;
    case Model::TEDM_TriangleStrip:
        s = 1u;
        break;
    default:
        return false;
    }

    double isectDistSq = NAN;

    const std::size_t faceCount = mesh.getNumFaces();
    const I *indices = reinterpret_cast<const I *>(static_cast<const uint8_t *>(mesh.getIndices()) + mesh.getIndexOffset());
    const void *positions;
    mesh.getVertices(&positions, Model::TEVA_Position);

    // transform the ray into the local CS (if applicable)
    Ray2<double> localRay(ray);
    if(localFrame) {
        Matrix2 invLocalFrame;
        localFrame->createInverse(&invLocalFrame);
        Point2<double> localRayOrg;
        invLocalFrame.transform(&localRayOrg, ray.origin);
        Point2<double> localRayDir(ray.origin.x+ray.direction.x, ray.origin.y+ray.direction.y, ray.origin.z+ray.direction.z);
        invLocalFrame.transform(&localRayDir, localRayDir);
        localRayDir.x -= localRayOrg.x;
        localRayDir.y -= localRayOrg.y;
        localRayDir.z -= localRayOrg.z;
        localRay = Ray2<double>(localRayOrg, Vector4<double>(localRayDir.x, localRayDir.y, localRayDir.z));
    }

    const std::size_t position_offset = mesh.getVertexDataLayout().position.offset;
    const std::size_t position_stride = mesh.getVertexDataLayout().position.stride;
    bool haveCandidate = false;
    for (std::size_t face = 0; face < faceCount; face++) {
        std::size_t aidx = face * s;
        std::size_t bidx = face * s + 1;
        std::size_t cidx = face * s + 2;

        if (indices) {
            aidx = indices[aidx];
            bidx = indices[bidx];
            cidx = indices[cidx];

            // skip degenerates
            if((aidx == bidx) || (aidx == cidx) || (bidx == cidx))
                continue;
        }

        const V *pos;

        pos = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) + position_offset + (aidx*position_stride));
        Point2<double> a(pos[0], pos[1], pos[2]);
        pos = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) + position_offset + (bidx*position_stride));
        Point2<double> b(pos[0], pos[1], pos[2]);
        pos = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) + position_offset + (cidx*position_stride));
        Point2<double> c(pos[0], pos[1], pos[2]);

        Point2<double> abc[3] = { a, b, c};

        AABB aabb(abc, 3u);
        Point2<double> p;
        if(!aabb.intersect(&p, localRay))
            continue;

        if(haveCandidate) {
            const double dx = (p.x - localRay.origin.x);
            const double dy = (p.y - localRay.origin.y);
            const double dz = (p.z - localRay.origin.z);
            const double distSq = (dx*dx) + (dy*dy) + (dz*dz);
            if(distSq > isectDistSq)
                continue;
        }

        int code = Triangle_intersect(&p,
            a.x, a.y, a.z,
            b.x, b.y, b.z,
            c.x, c.y, c.z,
            localRay);
        if (code != TE_Ok)
            continue;

        const double dx = (p.x - localRay.origin.x);
        const double dy = (p.y - localRay.origin.y);
        const double dz = (p.z - localRay.origin.z);
        const double distSq = (dx*dx) + (dy*dy) + (dz*dz);
        if (!haveCandidate || (distSq < isectDistSq)) {
            *value = p;
            isectDistSq = distSq;
            haveCandidate = true;
        }
    }

    if(!haveCandidate)
        return false;

    // transform the intersect point from the local CS (if applicable
    if(localFrame)
        localFrame->transform(value, *value);
    return true;
}

AABB aabb(const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS
{
    const Feature::Envelope2 &aabb_lcs = mesh.getAABB();
    Point2<double> p;
    Point2<double> min;
    Point2<double> max;

    Point2<double> corners[8] = 
    {
        Point2<double>(aabb_lcs.minX, aabb_lcs.minY, aabb_lcs.minZ),
        Point2<double>(aabb_lcs.maxX, aabb_lcs.minY, aabb_lcs.minZ),
        Point2<double>(aabb_lcs.maxX, aabb_lcs.maxY, aabb_lcs.minZ),
        Point2<double>(aabb_lcs.minX, aabb_lcs.maxY, aabb_lcs.minZ),
        Point2<double>(aabb_lcs.minX, aabb_lcs.minY, aabb_lcs.maxZ),
        Point2<double>(aabb_lcs.maxX, aabb_lcs.minY, aabb_lcs.maxZ),
        Point2<double>(aabb_lcs.maxX, aabb_lcs.maxY, aabb_lcs.maxZ),
        Point2<double>(aabb_lcs.minX, aabb_lcs.maxY, aabb_lcs.maxZ),
    };

    p = corners[0u];
    if (localFrame)
        localFrame->transform(&p, p);
    min = p;
    max = p;

    for (std::size_t i = 1u; i < 8u; i++) {
        p = corners[i];
        if (localFrame)
            localFrame->transform(&p, p);
        if (p.x < min.x)        min.x = p.x;
        else if (p.x > max.x)   max.x = p.x;
        if (p.y < min.y)        min.y = p.y;
        else if (p.y > max.y)   max.y = p.y;
        if (p.z < min.z)        min.z = p.z;
        else if (p.z > max.z)   max.z = p.z;
    }

    return AABB(min, max);
}

}
