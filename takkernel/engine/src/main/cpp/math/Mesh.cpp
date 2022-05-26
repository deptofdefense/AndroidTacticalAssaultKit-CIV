#include "math/Mesh.h"

#include <limits>

#include "math/AABB.h"
#include "math/PackedRay.h"
#include "math/PackedVector.h"
#include "math/Triangle.h"
#include "model/MeshBuilder.h"
#include "model/MeshTransformer.h"
#include "util/Memory.h"

using namespace TAK::Engine::Math;

using namespace TAK::Engine;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace {
template <class V, class I>
bool intersectImpl(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;

bool intersectGeneric(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;

AABB aabb(const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;

template <class V, class I>
bool intersectImpl_simd(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;

bool intersectGeneric_simd(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS;

void packed_triangle_intersect_simd(const PackedRay &packedRay, const PackedVector &vPack0, const PackedVector &vPack1,
                                    const PackedVector &vPack2, __m128 &tPack, __m128 &maskValid, PackedVector &out);

TAKErr triangle_intersect_simd(Point2<double> *value, const double V0x, const double V0y, const double V0z, const double V1x,
                               const double V1y, const double V1z, const double V2x, const double V2y, const double V2z,
                               const Ray2<double> &ray) NOTHROWS;
}  // namespace

Mesh::Mesh(const double *vertices, const std::size_t numVertexColumns, const std::size_t numVertexRows) NOTHROWS :
    simdEnabled(false)
{
    if ((numVertexRows * numVertexColumns) > 1u) {
        Model::MeshBuilder builder(Model::TEDM_Triangles, Model::TEVA_Position);
        builder.reserveVertices(((numVertexColumns - 1u) * (numVertexRows - 1u)) * 6u);

        for (std::size_t row = 0; row < numVertexRows - 1; row++) {
            for (std::size_t col = 0; col < numVertexColumns - 1; col++) {
                const std::size_t ulIdx = ((row * numVertexColumns) + col) * 3;
                const std::size_t urIdx = ((row * numVertexColumns) + col + 1) * 3;
                const std::size_t lrIdx = (((row + 1) * numVertexColumns) + col + 1) * 3;
                const std::size_t llIdx = (((row + 1) * numVertexColumns) + col) * 3;

                const double ulx = vertices[ulIdx];
                const double uly = vertices[ulIdx + 1u];
                const double ulz = vertices[ulIdx + 2u];
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

Mesh::Mesh(Model::MeshPtr_const &&data_, const Matrix2 *localFrame_, const bool simdEnabled_) NOTHROWS :
    Mesh(std::shared_ptr<const Model::Mesh>(std::move(data_)), localFrame_, simdEnabled_)
{}

Mesh::Mesh(const std::shared_ptr<const Model::Mesh> &data_, const Matrix2 *localFrame_, const bool simdEnabled_) NOTHROWS :
    data(data_), hasLocalFrame(!!localFrame_), simdEnabled(simdEnabled_)
{
    if (hasLocalFrame) localFrame.set(*localFrame_);
}

Mesh::Mesh(const Mesh &other) NOTHROWS {
    Model::MeshPtr model(nullptr, nullptr);
    Model::Mesh_transform(model, *data, data->getVertexDataLayout());

    data = std::move(model);
    localFrame.set(other.localFrame);
    hasLocalFrame = other.hasLocalFrame;
    simdEnabled = other.simdEnabled;
}

Mesh::~Mesh() NOTHROWS {}

bool Mesh::intersect(Point2<double> *value, const Ray2<double> &ray) const NOTHROWS {
    const Matrix2 *pLocalFrame = hasLocalFrame ? &localFrame : nullptr;

    const Math::AABB aabb_wcs = aabb(*data, pLocalFrame);
    const bool aabb_isect = aabb_wcs.intersect(value, ray);
    const bool aabb_contains = (ray.origin.x >= aabb_wcs.minX && ray.origin.x <= aabb_wcs.maxX && ray.origin.y >= aabb_wcs.minY &&
                                ray.origin.y <= aabb_wcs.maxY && ray.origin.z >= aabb_wcs.minZ && ray.origin.z <= aabb_wcs.maxZ);

    // screen for AABB intersection
    if (!aabb_isect && !aabb_contains) return false;

    switch (data->getVertexDataLayout().position.type) {
#define VT_CASE(tedt, vt, useSimd)                                                                           \
    case tedt:                                                                                               \
        if (data->isIndexed()) {                                                                             \
            DataType indexType;                                                                              \
            data->getIndexType(&indexType);                                                                  \
            switch (indexType) {                                                                             \
                case TEDT_UInt8:  { return useSimd ?                                                         \
                    intersectImpl_simd<vt, uint8_t>(value, ray, *data, pLocalFrame) :                        \
                    intersectImpl<vt, uint8_t>(value, ray, *data, pLocalFrame); }                            \
                case TEDT_UInt16:  { return useSimd ?                                                        \
                    intersectImpl_simd<vt, uint16_t>(value, ray, *data, pLocalFrame) :                       \
                    intersectImpl<vt, uint16_t>(value, ray, *data, pLocalFrame); }                           \
                case TEDT_UInt32:  { return useSimd ?                                                        \
                    intersectImpl_simd<vt, uint32_t>(value, ray, *data, pLocalFrame) :                       \
                    intersectImpl<vt, uint32_t>(value, ray, *data, pLocalFrame); }                           \
                default: break;                                                                              \
            }                                                                                                \
        } else {                                                                                             \
            if(useSimd)                                                                                      \
                return intersectImpl_simd<vt, bool>(value, ray, *data, pLocalFrame);                         \
            else                                                                                             \
                return intersectImpl<vt, bool>(value, ray, *data, pLocalFrame);                              \
        }                                                                                                    \
        break;

        VT_CASE(TEDT_Int8, int8_t, simdEnabled)
        VT_CASE(TEDT_UInt8, uint8_t, simdEnabled)
        VT_CASE(TEDT_Int16, int16_t, simdEnabled)
        VT_CASE(TEDT_UInt16, uint16_t, simdEnabled)
        VT_CASE(TEDT_Int32, int32_t, simdEnabled)
        VT_CASE(TEDT_UInt32, uint32_t, simdEnabled)
        VT_CASE(TEDT_Float32, float, simdEnabled)
        // double precision position data, never use SIMD
        VT_CASE(TEDT_Float64, double, false)
    }

    // don't use SIMD for generic mesh processing
    return intersectGeneric(value, ray, *data, pLocalFrame);
}

void Mesh::clone(std::unique_ptr<GeometryModel2, void (*)(const GeometryModel2 *)> &value) const NOTHROWS {
    value = GeometryModel2Ptr(new Mesh(*this), Memory_deleter_const<GeometryModel2, Mesh>);
}

GeometryModel2::GeometryClass Mesh::getGeomClass() const NOTHROWS { return GeometryModel2::MESH; }

namespace {

bool intersectGeneric(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS {
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
    if (localFrame) {
        Matrix2 invLocalFrame;
        localFrame->createInverse(&invLocalFrame);
        Point2<double> localRayOrg;
        invLocalFrame.transform(&localRayOrg, ray.origin);
        Point2<double> localRayDir(ray.origin.x + ray.direction.x, ray.origin.y + ray.direction.y, ray.origin.z + ray.direction.z);
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

        Point2<double> p;
#ifndef __ANDROID__
        Point2<double> abc[3] = {a, b, c};

        AABB aabb(abc, 3u);
        if (!aabb.intersect(&p, localRay)) continue;

        if (haveCandidate) {
            const double dx = (p.x - localRay.origin.x);
            const double dy = (p.y - localRay.origin.y);
            const double dz = (p.z - localRay.origin.z);
            const double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq > isectDistSq) continue;
        }
#endif
        int code = Triangle_intersect(&p, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, localRay);
        if (code != TE_Ok) continue;

        const double dx = (p.x - localRay.origin.x);
        const double dy = (p.y - localRay.origin.y);
        const double dz = (p.z - localRay.origin.z);
        const double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        if (!haveCandidate || (distSq < isectDistSq)) {
            *value = p;
            isectDistSq = distSq;
            haveCandidate = true;
        }
    }

    if (!haveCandidate) return false;

    // transform the intersect point from the local CS (if applicable
    if (localFrame) localFrame->transform(value, *value);
    return true;
}

template <class V, class I>
bool intersectImpl(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS {
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
    if (localFrame) {
        Matrix2 invLocalFrame;
        localFrame->createInverse(&invLocalFrame);
        Point2<double> localRayOrg;
        invLocalFrame.transform(&localRayOrg, ray.origin);
        Point2<double> localRayDir(ray.origin.x + ray.direction.x, ray.origin.y + ray.direction.y, ray.origin.z + ray.direction.z);
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
            if ((aidx == bidx) || (aidx == cidx) || (bidx == cidx)) continue;
        }

        const V *pos;

        pos = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) + position_offset + (aidx * position_stride));
        Point2<double> a(pos[0], pos[1], pos[2]);
        pos = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) + position_offset + (bidx * position_stride));
        Point2<double> b(pos[0], pos[1], pos[2]);
        pos = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) + position_offset + (cidx * position_stride));
        Point2<double> c(pos[0], pos[1], pos[2]);

        Point2<double> p;
#ifndef __ANDROID__
        Point2<double> abc[3] = {a, b, c};

        AABB aabb(abc, 3u);
        if (!aabb.intersect(&p, localRay)) continue;

        if (haveCandidate) {
            const double dx = (p.x - localRay.origin.x);
            const double dy = (p.y - localRay.origin.y);
            const double dz = (p.z - localRay.origin.z);
            const double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq > isectDistSq) continue;
        }
#endif
        int code = Triangle_intersect(&p, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, localRay);
        if (code != TE_Ok) continue;

        const double dx = (p.x - localRay.origin.x);
        const double dy = (p.y - localRay.origin.y);
        const double dz = (p.z - localRay.origin.z);
        const double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        if (!haveCandidate || (distSq < isectDistSq)) {
            *value = p;
            isectDistSq = distSq;
            haveCandidate = true;
        }
    }

    if (!haveCandidate) return false;

    // transform the intersect point from the local CS (if applicable
    if (localFrame) localFrame->transform(value, *value);

    return true;
}

AABB aabb(const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS {
    const Feature::Envelope2 &aabb_lcs = mesh.getAABB();
    Point2<double> p;
    Point2<double> min;
    Point2<double> max;

    Point2<double> corners[8] = {
        Point2<double>(aabb_lcs.minX, aabb_lcs.minY, aabb_lcs.minZ), Point2<double>(aabb_lcs.maxX, aabb_lcs.minY, aabb_lcs.minZ),
        Point2<double>(aabb_lcs.maxX, aabb_lcs.maxY, aabb_lcs.minZ), Point2<double>(aabb_lcs.minX, aabb_lcs.maxY, aabb_lcs.minZ),
        Point2<double>(aabb_lcs.minX, aabb_lcs.minY, aabb_lcs.maxZ), Point2<double>(aabb_lcs.maxX, aabb_lcs.minY, aabb_lcs.maxZ),
        Point2<double>(aabb_lcs.maxX, aabb_lcs.maxY, aabb_lcs.maxZ), Point2<double>(aabb_lcs.minX, aabb_lcs.maxY, aabb_lcs.maxZ),
    };

    p = corners[0u];
    if (localFrame) localFrame->transform(&p, p);
    min = p;
    max = p;

    for (std::size_t i = 1u; i < 8u; i++) {
        p = corners[i];
        if (localFrame) localFrame->transform(&p, p);
        if (p.x < min.x)
            min.x = p.x;
        else if (p.x > max.x)
            max.x = p.x;
        if (p.y < min.y)
            min.y = p.y;
        else if (p.y > max.y)
            max.y = p.y;
        if (p.z < min.z)
            min.z = p.z;
        else if (p.z > max.z)
            max.z = p.z;
    }

    return AABB(min, max);
}

template <class V, class I>
bool intersectImpl_simd(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS {
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

    const std::size_t numFaces = mesh.getNumFaces();
    const I *indices = reinterpret_cast<const I *>(static_cast<const uint8_t *>(mesh.getIndices()) + mesh.getIndexOffset());
    const void *positions;
    mesh.getVertices(&positions, Model::TEVA_Position);
    const std::size_t position_offset = mesh.getVertexDataLayout().position.offset;
    const std::size_t position_stride = mesh.getVertexDataLayout().position.stride;

    Ray2<double> localRay(ray);
    if (localFrame) {
        Matrix2 invLocalFrame;
        localFrame->createInverse(&invLocalFrame);
        Point2<double> localRayOrg;
        invLocalFrame.transform(&localRayOrg, ray.origin);
        Point2<double> localRayDir(ray.origin.x + ray.direction.x, ray.origin.y + ray.direction.y, ray.origin.z + ray.direction.z);
        invLocalFrame.transform(&localRayDir, localRayDir);
        localRayDir.x -= localRayOrg.x;
        localRayDir.y -= localRayOrg.y;
        localRayDir.z -= localRayOrg.z;
        localRay = Ray2<double>(localRayOrg, Vector4<double>(localRayDir.x, localRayDir.y, localRayDir.z));
    }
    PackedRay packedRay(localRay);

    static float maxDistance = std::numeric_limits<float>::max();
    float minDistance = maxDistance;

    PackedVector isectPoints;
    PackedVector vPack0;
    PackedVector vPack1;
    PackedVector vPack2;
    PackedVector* packedVectors[3] { &vPack0, &vPack1, &vPack2 };

    std::size_t ids[12];
    const V *v0, *v1, *v2, *v3;
    unsigned int numVerts = (unsigned int)numFaces * 3;
    unsigned int remainder = numVerts % 12;

    // We are working on 4 faces per iteration
    for (std::size_t face = 0; face < (numVerts + remainder) / 3; face += 4) {

        // Gather indices for the faces
        // If we exceed the actual number of vertices, reuse previous IDs for cache purpose.
        for (int i = 0; i < 4 && (face + i) < numFaces; i++) {
            std::size_t aidx = (i + face) * s;
            std::size_t bidx = (i + face) * s + 1;
            std::size_t cidx = (i + face) * s + 2;

            if (indices) {
                aidx = indices[aidx];
                bidx = indices[bidx];
                cidx = indices[cidx];
            }

            ids[3 * i + 0] = aidx;
            ids[3 * i + 1] = bidx;
            ids[3 * i + 2] = cidx;
        }

        // Each packed vertex has an xyz component for 4 faces
        // So, 3 packed vertices creates 4 faces, e.g.,
        // PackedVertex 0 : {Face0, Vertex0}, {Face1, Vertex0}, {Face2, Vertex0}, {Face3, Vertex0}
        // PackedVertex 1 : {Face0, Vertex1}, {Face1, Vertex1}, {Face2, Vertex1}, {Face3, Vertex1}
        // PackedVertex 2 : {Face0, Vertex2}, {Face1, Vertex2}, {Face2, Vertex2}, {Face3, Vertex2}
        for(int i = 0; i<3; i++) {
            v0 = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) +
                                             position_offset + (ids[i] * position_stride));
            v1 = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) +
                                             position_offset + (ids[3 + i] * position_stride));
            v2 = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) +
                                             position_offset + (ids[6 + i] * position_stride));
            v3 = reinterpret_cast<const V *>(static_cast<const uint8_t *>(positions) +
                                             position_offset + (ids[9 + i] * position_stride));

            packedVectors[i]->packedX_ = _mm_set_ps((float) v3[0], (float) v2[0], (float) v1[0], (float) v0[0]);
            packedVectors[i]->packedY_ = _mm_set_ps((float) v3[1], (float) v2[1], (float) v1[1], (float) v0[1]);
            packedVectors[i]->packedZ_ = _mm_set_ps((float) v3[2], (float) v2[2], (float) v1[2], (float) v0[2]);
        }

        // Initialize valid to if the face is not degenerate.
        __m128 v0v1_valid = _mm_add_ps(_mm_cmpneq_ps(vPack0.packedX_, vPack1.packedX_), _mm_add_ps(_mm_cmpneq_ps(vPack0.packedY_, vPack1.packedY_), _mm_cmpneq_ps(vPack0.packedZ_, vPack1.packedZ_)));
        __m128 v0v2_valid = _mm_add_ps(_mm_cmpneq_ps(vPack0.packedX_, vPack2.packedX_), _mm_add_ps(_mm_cmpneq_ps(vPack0.packedY_, vPack2.packedY_), _mm_cmpneq_ps(vPack0.packedZ_, vPack2.packedZ_)));
        __m128 v1v2_valid = _mm_add_ps(_mm_cmpneq_ps(vPack1.packedX_, vPack2.packedX_), _mm_add_ps(_mm_cmpneq_ps(vPack1.packedY_, vPack2.packedY_), _mm_cmpneq_ps(vPack1.packedZ_, vPack2.packedZ_)));
        __m128 validPack = _mm_and_ps(v0v1_valid, _mm_and_ps(v0v2_valid, v1v2_valid));

        //Calculate intersections and if the intersection is valid
        __m128 distPack = _mm_set1_ps(maxDistance);
        packed_triangle_intersect_simd(packedRay, vPack0, vPack1, vPack2, distPack, validPack, isectPoints);

        // Convert simd data back into data we can use
        float distArray[4], validArray[4], xVals[4], yVals[4], zVals[4];
        _mm_store_ps(distArray, distPack);
        _mm_store_ps(validArray, validPack);
        _mm_store_ps(xVals, isectPoints.packedX_);
        _mm_store_ps(yVals, isectPoints.packedY_);
        _mm_store_ps(zVals, isectPoints.packedZ_);

        // Look through results, checking if the intersection is valid and less than previous closest intersection
        for (int i = 0; i < 4; i++) {
            if ((bool)validArray[i] && distArray[i] < minDistance) {
                minDistance = distArray[i];
                value->x = xVals[i];
                value->y = yVals[i];
                value->z = zVals[i];
            }
        }
    }

    if (localFrame)
        localFrame->transform(value, *value);

    return (minDistance != maxDistance);
}

bool intersectGeneric_simd(Point2<double> *value, const Ray2<double> &ray, const Model::Mesh &mesh, const Matrix2 *localFrame) NOTHROWS {
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

    Ray2<double> localRay(ray);
    if (localFrame) {
        Matrix2 invLocalFrame;
        localFrame->createInverse(&invLocalFrame);
        Point2<double> localRayOrg;
        invLocalFrame.transform(&localRayOrg, ray.origin);
        Point2<double> localRayDir(ray.origin.x + ray.direction.x, ray.origin.y + ray.direction.y,
                                   ray.origin.z + ray.direction.z);
        invLocalFrame.transform(&localRayDir, localRayDir);
        localRayDir.x -= localRayOrg.x;
        localRayDir.y -= localRayOrg.y;
        localRayDir.z -= localRayOrg.z;
        localRay = Ray2<double>(localRayOrg,
                                Vector4<double>(localRayDir.x, localRayDir.y, localRayDir.z));
    }
    PackedRay packedRay(localRay);

    static float maxDistance = std::numeric_limits<float>::max();
    float minDistance = maxDistance;

    PackedVector isectPoints;
    PackedVector vPack0;
    PackedVector vPack1;
    PackedVector vPack2;
    PackedVector *packedVectors[3]{&vPack0, &vPack1, &vPack2};

    const std::size_t numFaces = mesh.getNumFaces();
    const bool indices = mesh.isIndexed();
    unsigned int numVerts = (unsigned int) numFaces * 3;
    unsigned int remainder = numVerts % 12;
    std::size_t ids[12];

    // We are working on 4 faces per iteration
    for (std::size_t face = 0; face < (numVerts + remainder) / 3; face += 4) {

        // Gather indices for the faces
        // If we exceed the actual number of vertices, reuse previous IDs for cache purpose.
        for (int i = 0; i < 4 && (face + i) < numFaces; i++) {
            std::size_t aidx = (i + face) * s;
            std::size_t bidx = (i + face) * s + 1;
            std::size_t cidx = (i + face) * s + 2;

            if (indices) {
                mesh.getIndex(&aidx, aidx);
                mesh.getIndex(&bidx, bidx);
                mesh.getIndex(&cidx, cidx);
            }

            ids[3 * i + 0] = aidx;
            ids[3 * i + 1] = bidx;
            ids[3 * i + 2] = cidx;
        }

        // Each packed vertex has an xyz component for 4 faces
        // So, 3 packed vertices creates 4 faces, e.g.,
        // PackedVertex 0 : {Face0, Vertex0}, {Face1, Vertex0}, {Face2, Vertex0}, {Face3, Vertex0}
        // PackedVertex 1 : {Face0, Vertex1}, {Face1, Vertex1}, {Face2, Vertex1}, {Face3, Vertex1}
        // PackedVertex 2 : {Face0, Vertex2}, {Face1, Vertex2}, {Face2, Vertex2}, {Face3, Vertex2}
        for (int i = 0; i < 3; i++) {
            Point2<double> p0, p1, p2, p3;
            mesh.getPosition(&p0, ids[0 + i]);
            mesh.getPosition(&p1, ids[3 + i]);
            mesh.getPosition(&p2, ids[6 + i]);
            mesh.getPosition(&p3, ids[9 + i]);

            packedVectors[i]->packedX_ = _mm_set_ps((float) p3.x, (float) p2.x, (float) p1.x,
                                                    (float) p0.x);
            packedVectors[i]->packedY_ = _mm_set_ps((float) p3.y, (float) p2.y, (float) p1.y,
                                                    (float) p0.y);
            packedVectors[i]->packedZ_ = _mm_set_ps((float) p3.z, (float) p2.z, (float) p1.z,
                                                    (float) p0.z);
        }

        // Initialize valid to if the face is not degenerate.
        __m128 v0v1_valid = _mm_add_ps(_mm_cmpneq_ps(vPack0.packedX_, vPack1.packedX_),
                                       _mm_add_ps(_mm_cmpneq_ps(vPack0.packedY_, vPack1.packedY_),
                                                  _mm_cmpneq_ps(vPack0.packedZ_, vPack1.packedZ_)));
        __m128 v0v2_valid = _mm_add_ps(_mm_cmpneq_ps(vPack0.packedX_, vPack2.packedX_),
                                       _mm_add_ps(_mm_cmpneq_ps(vPack0.packedY_, vPack2.packedY_),
                                                  _mm_cmpneq_ps(vPack0.packedZ_, vPack2.packedZ_)));
        __m128 v1v2_valid = _mm_add_ps(_mm_cmpneq_ps(vPack1.packedX_, vPack2.packedX_),
                                       _mm_add_ps(_mm_cmpneq_ps(vPack1.packedY_, vPack2.packedY_),
                                                  _mm_cmpneq_ps(vPack1.packedZ_, vPack2.packedZ_)));
        __m128 validPack = _mm_and_ps(v0v1_valid, _mm_and_ps(v0v2_valid, v1v2_valid));

        //Calculate intersections and if the intersection is valid
        __m128 distPack = _mm_set1_ps(maxDistance);
        packed_triangle_intersect_simd(packedRay, vPack0, vPack1, vPack2, distPack, validPack,
                                       isectPoints);

        // Convert simd data back into data we can use
        float distArray[4], validArray[4], xVals[4], yVals[4], zVals[4];
        _mm_store_ps(distArray, distPack);
        _mm_store_ps(validArray, validPack);
        _mm_store_ps(xVals, isectPoints.packedX_);
        _mm_store_ps(yVals, isectPoints.packedY_);
        _mm_store_ps(zVals, isectPoints.packedZ_);

        // Look through results, checking if the intersection is valid and less than previous closest intersection
        for (int i = 0; i < 4; i++) {
            if ((bool) validArray[i] && distArray[i] < minDistance) {
                minDistance = distArray[i];
                value->x = xVals[i];
                value->y = yVals[i];
                value->z = zVals[i];
            }
        }
    }

    if (localFrame)
        localFrame->transform(value, *value);

    return (minDistance != maxDistance);
}

void packed_triangle_intersect_simd(const PackedRay &packedRay, const PackedVector &vPack0, const PackedVector &vPack1, const PackedVector &vPack2, __m128 &distanceMask, __m128 &validMask, PackedVector &intersections) {
    static const __m128 zeros = _mm_setzero_ps();
    static const __m128 ones = _mm_set1_ps(1.0f);
    static const __m128 SMALL_NUM = _mm_set1_ps((float) 1e-13);
    static const __m128 nSMALL_NUM = _mm_set1_ps((float) -1e-13);

    PackedVector u = vPack1 - vPack0;
    PackedVector v = vPack2 - vPack0;
    PackedVector n = u.crossProduct(v);

    // valid &= (n.xyz != 0)
    __m128 sum = _mm_add_ps(n.packedX_, _mm_add_ps(n.packedY_, n.packedZ_));
    validMask = _mm_and_ps(validMask,
                           _mm_or_ps(_mm_cmplt_ps(sum, nSMALL_NUM), _mm_cmpgt_ps(sum, SMALL_NUM)));

    PackedVector w0 = packedRay.origin - vPack0;
    __m128 a = _mm_sub_ps(zeros, n.dotProduct(w0));
    __m128 b = n.dotProduct(packedRay.direction);
    distanceMask = _mm_div_ps(a, b);

    // valid &= (b < -1e-13 || b > 1e-13)
    validMask = _mm_and_ps(validMask,
                           _mm_or_ps(_mm_cmplt_ps(b, nSMALL_NUM), _mm_cmpgt_ps(b, SMALL_NUM)));

    // valid &= (r >= 0)
    validMask = _mm_and_ps(validMask, _mm_cmpge_ps(distanceMask, zeros));

    // isect points = origin + direction * r
    intersections.packedX_ = _mm_add_ps(packedRay.origin.packedX_,
                                        _mm_mul_ps(packedRay.direction.packedX_, distanceMask));
    intersections.packedY_ = _mm_add_ps(packedRay.origin.packedY_,
                                        _mm_mul_ps(packedRay.direction.packedY_, distanceMask));
    intersections.packedZ_ = _mm_add_ps(packedRay.origin.packedZ_,
                                        _mm_mul_ps(packedRay.direction.packedZ_, distanceMask));

    PackedVector w = intersections - vPack0;

    __m128 uu = u.dotProduct(u);
    __m128 uv = u.dotProduct(v);
    __m128 vv = v.dotProduct(v);
    __m128 wu = w.dotProduct(u);
    __m128 wv = w.dotProduct(v);
    __m128 d = _mm_sub_ps(_mm_mul_ps(uv, uv), _mm_mul_ps(uu, vv));

    __m128 s = _mm_div_ps(_mm_sub_ps(_mm_mul_ps(uv, wv), _mm_mul_ps(vv, wu)), d);
    __m128 t = _mm_div_ps(_mm_sub_ps(_mm_mul_ps(uv, wu), _mm_mul_ps(uu, wv)), d);

    // valid &= (s >= 0 && s<= 1 && t >=0 && (s + t) <= 1)
    validMask = _mm_and_ps(validMask, _mm_cmpge_ps(s, zeros));
    validMask = _mm_and_ps(validMask, _mm_cmple_ps(s, ones));
    validMask = _mm_and_ps(validMask, _mm_cmpge_ps(t, zeros));
    validMask = _mm_and_ps(validMask, _mm_cmple_ps(_mm_add_ps(s, t), ones));
}

TAKErr triangle_intersect_simd(Point2<double> *value, const double V0x, const double V0y, const double V0z, const double V1x,
                               const double V1y, const double V1z, const double V2x, const double V2y, const double V2z,
                               const Ray2<double> &ray) NOTHROWS {
    __m128 u = _mm_set_ps((float) (V1z - V0z), (float) (V1y - V0y), (float) (V1x - V0x), 0.f);
    __m128 v = _mm_set_ps((float) (V2z - V0z), (float) (V2y - V0y), (float) (V2x - V0x), 0.f);

    __m128 n = Simd::cross_product(u, v);
    if (_mm_cvtss_f32(_mm_shuffle_ps(n, n, _MM_SHUFFLE(1, 1, 1, 1))) == 0.0
        && _mm_cvtss_f32(_mm_shuffle_ps(n, n, _MM_SHUFFLE(2, 2, 2, 2))) == 0.0
        && _mm_cvtss_f32(_mm_shuffle_ps(n, n, _MM_SHUFFLE(3, 3, 3, 3))) == 0.0)
        return TE_Err;

    __m128 dir = _mm_set_ps((float) ray.direction.z, (float) ray.direction.y,
                            (float) ray.direction.x, 0.f);
    __m128 w0 = _mm_set_ps((float) (ray.origin.z - V0z), (float) (ray.origin.y - V0y),
                           (float) (ray.origin.x - V0x), 0.f);

    double a = (double) -Simd::dot_product(n, w0);
    double b = (double) Simd::dot_product(n, dir);

    if (std::abs(b) < 1e-13) return TE_Err;

    double r = a / b;
    if (r < 0.0) return TE_Err;

    Vector4<double> I(ray.origin.x + (ray.direction.x * r), ray.origin.y + (ray.direction.y * r),
                      ray.origin.z + (ray.direction.z * r));
    float uu, uv, vv, wu, wv, D;
    uu = Simd::dot_product(u, u);
    uv = Simd::dot_product(u, v);
    vv = Simd::dot_product(v, v);

    __m128 w = _mm_set_ps((float) (I.z - V0z), (float) (I.y - V0y), (float) (I.x - V0x), 0.f);
    wu = Simd::dot_product(w, u);
    wv = Simd::dot_product(w, v);
    D = uv * uv - uu * vv;

    float s, t;
    s = (uv * wv - vv * wu) / D;
    if (s < 0.0 || s > 1.0) return TE_Err;
    t = (uv * wu - uu * wv) / D;
    if (t < 0.0 || (s + t) > 1.0) return TE_Err;

    value->x = I.x;
    value->y = I.y;
    value->z = I.z;

    return TE_Ok;
}
}  // namespace
