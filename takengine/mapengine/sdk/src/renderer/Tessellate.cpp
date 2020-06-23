#include "renderer/Tessellate.h"

#include <cmath>
#include <list>
#include <vector>

#include "core/GeoPoint2.h"
#include "formats/glues/glues.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    struct TessCallback
    {
    public :
        TessCallback(const VertexData &layout, const std::size_t count) NOTHROWS;
    public :
        Point2<double> origin;
        bool error;
        std::size_t srcCount;
        std::size_t dstCount;
        std::size_t combineCount;
        std::vector<std::size_t> indices;
        std::vector<Point2<double>> combinedVertices;
    };

    void VertexData_deleter(const VertexData *value);

    Algorithm wgs84() NOTHROWS;
    double WGS84_distance(const Point2<double> &a, const Point2<double> &b);
    Point2<double> WGS84_direction(const Point2<double> &a, const Point2<double> &b);
    Point2<double> WGS84_interpolate(const Point2<double> &origin, const Point2<double> &dir, const double distance);

    Algorithm cartesian() NOTHROWS;
    double Cartesian_distance(const Point2<double> &a, const Point2<double> &b);
    Point2<double> Cartesian_direction(const Point2<double> &a, const Point2<double> &b);
    Point2<double> Cartesian_interpolate(const Point2<double> &origin, const Point2<double> &dir, const double distance);

    Point2<double> midpoint(const Point2<double> &a, const Point2<double> &b, Algorithm alg) NOTHROWS;
    Point2<double> midpoint2(const Point2<double> &a, const Point2<double> &dir, const double hdab, Algorithm alg) NOTHROWS
    {
        return alg.interpolate(a, dir, hdab);
    }

    void TessCallback_beginData(GLenum type, void *opaque);
    void TessCallback_edgeFlagData(GLboolean flag, void *opaque);
    void TessCallback_vertexData_count(void *vertexData, void *opaque);
    void TessCallback_vertexData_assemble(void *vertexData, void *opaque);
    void TessCallback_endData(void *opaque);
    void TessCallback_combinData_count(GLfloat coords[3], void *d[4], GLfloat w[4], void **outData, void *opaque);
    void TessCallback_combinData_assemble(GLfloat coords[3], void *d[4], GLfloat w[4], void **outData, void *opaque);
    void TessCallback_errorData(GLenum err, void *opaque);

    struct Triangle
    {
        Point2<double> a;
        Point2<double> b;
        Point2<double> c;

        bool subdivide;

        struct
        {
            double distance;
            Point2<double> direction;
        } ab;
        struct
        {
            double distance;
            Point2<double> direction;
        } bc;
        struct
        {
            double distance;
            Point2<double> direction;
        } ca;
    };

    Triangle Triangle_create(const Point2< double> &a, const Point2<double> &b, const Point2<double> &c, const double threshold, Algorithm &alg) NOTHROWS
    {
        Triangle t;
        t.a = a;
        t.b = b;
        t.c = c;
        t.ab.distance = alg.distance(a, b);
        t.ab.direction = alg.direction(a, b);
        t.bc.distance = alg.distance(b, c);
        t.bc.direction = alg.direction(b, c);
        t.ca.distance = alg.distance(c, a);
        t.ca.direction = alg.direction(c, a);
        t.subdivide = (t.ab.distance > threshold) || (t.bc.distance > threshold) || (t.ca.distance > threshold);
        return t;
    }

    void Triangle_create(Triangle *t, const Point2< double> &a, const Point2<double> &b, const Point2<double> &c, const double threshold, Algorithm &alg) NOTHROWS
    {
        t->a = a;
        t->b = b;
        t->c = c;
        t->ab.distance = alg.distance(a, b);
        t->ab.direction = alg.direction(a, b);
        t->bc.distance = alg.distance(b, c);
        t->bc.direction = alg.direction(b, c);
        t->ca.distance = alg.distance(c, a);
        t->ca.direction = alg.direction(c, a);
        t->subdivide = (t->ab.distance > threshold) || (t->bc.distance > threshold) || (t->ca.distance > threshold);
    }

    struct VertexSink
    {
#define __te_vertexsink_buffer_size 2048000u

        VertexSink(WriteVertexFn vertWrite_, const VertexData &layout_) NOTHROWS :
            buf(new MemBuffer2(__te_vertexsink_buffer_size)),
            vertWrite(vertWrite_),
            layout(layout_),
            count(0u)
        {}

        TAKErr writeTriangle(const Point2<double> &a, const Point2<double> &b, const Point2<double> &c) NOTHROWS
        {
            TAKErr code(TE_Ok);
            if (buf->remaining() < (layout.stride*3u)) {
                buf->flip();
                bufs.push_back(std::move(buf));
                buf.reset(new MemBuffer2(__te_vertexsink_buffer_size));
            }

            code = vertWrite(*buf, layout, a);
            TE_CHECKRETURN_CODE(code);
            count++;
            code = vertWrite(*buf, layout, b);
            TE_CHECKRETURN_CODE(code);
            count++;
            code = vertWrite(*buf, layout, c);
            TE_CHECKRETURN_CODE(code);
            count++;

            return code;
        }

        TAKErr transfer(MemBuffer2 &dst) NOTHROWS
        {
            TAKErr code(TE_Ok);
            for (auto it = bufs.begin(); it != bufs.end(); it++) {
                const uint8_t *data = (*it)->get();
                code = dst.put(data, (*it)->limit());
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);

            if (buf->position()) {
                const uint8_t *data = buf->get();
                code = dst.put(data, buf->position());
                TE_CHECKRETURN_CODE(code);
            }

            return code;
        }

        const VertexData &layout;
        std::unique_ptr<MemBuffer2> buf;
        WriteVertexFn vertWrite;
        std::list<std::unique_ptr<MemBuffer2>> bufs;
        std::size_t count;
    };

    TAKErr polygon(TessCallback *value, const VertexData &src, const std::size_t count, ReadVertexFn it, _GLUfuncptr vertexDataCallback, _GLUfuncptr combineDataCallback) NOTHROWS;

    TAKErr triangle_r(VertexSink &sink, const Point2<double> &a, const Point2<double> &b, const Point2<double> &c, const double dab, const double dbc, const double dca, const double threshold, Algorithm alg, std::size_t depth) NOTHROWS;

    TAKErr getVertex(Point2<double> *xyz, const VertexData &src, const TessCallback &cb, const std::size_t i, ReadVertexFn vertRead) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (i >= cb.indices.size())
            return TE_InvalidArg;
        const std::size_t idx = cb.indices[i];
        if (idx >= cb.srcCount) {
            const std::size_t cmbidx = idx - cb.srcCount;
            if (cmbidx >= cb.combinedVertices.size())
                return TE_IllegalState;

            // index exceeds source count, points to combined vertex
            *xyz = cb.combinedVertices[cmbidx];
        } else {
            // index point to source data

            // position source pointer at the vertex
            MemBuffer2 srcbuf((const uint8_t *)src.data, src.stride*cb.srcCount);
            code = srcbuf.position(idx*src.stride);
            TE_CHECKRETURN_CODE(code);
            // read the vertex data
            code = vertRead(xyz, srcbuf, src);
            TE_CHECKRETURN_CODE(code);
        }

        return code;
    }
}

TAKErr TAK::Engine::Renderer::VertexData_allocate(VertexDataPtr &value, const std::size_t stride, const std::size_t size, const std::size_t count) NOTHROWS
{
    value = VertexDataPtr(new VertexData(), VertexData_deleter);
    value->data = new uint8_t[stride*count];
    value->stride = stride;
    value->size = size;
    return TE_Ok;
}

Algorithm &TAK::Engine::Renderer::Tessellate_CartesianAlgorithm() NOTHROWS
{
    static Algorithm a = cartesian();
    return a;
}
Algorithm &TAK::Engine::Renderer::Tessellate_WGS84Algorithm() NOTHROWS
{
    static Algorithm a = wgs84();
    return a;
}

TAKErr TAK::Engine::Renderer::Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead, WriteVertexFn vertWrite) NOTHROWS
{
    TAKErr code(TE_Ok);

    if(!dstCount)
        return TE_InvalidArg;
    if(!src.data)
        return TE_InvalidArg;
    if (src.size != 2u && src.size != 3u)
        return TE_InvalidArg;
    if (count < 3u)
        return TE_InvalidArg;

    // count the number of output vertices (original+combined)
    TessCallback cb(src, count);
    code = polygon(&cb, src, count, vertRead, (_GLUfuncptr)TessCallback_vertexData_count, (_GLUfuncptr)TessCallback_combinData_count);
    TE_CHECKRETURN_CODE(code);

    // store all output vertices (original+combined)
    cb.indices.reserve(cb.dstCount);
    cb.combinedVertices.reserve(cb.combineCount);
    cb.dstCount = 0u;
    cb.combineCount = 0u;
    code = polygon(&cb, src, count, vertRead, (_GLUfuncptr)TessCallback_vertexData_assemble, (_GLUfuncptr)TessCallback_combinData_assemble);
    TE_CHECKRETURN_CODE(code);

    // iterate tessellation indices, subdividing triangles as necessary and aggregating into output

    if (threshold) {
        VertexSink sink(vertWrite, src);

        for (std::size_t idx = 0u; idx < cb.indices.size(); idx += 3) {
            Point2<double> a;
            Point2<double> b;
            Point2<double> c;

            code = getVertex(&a, src, cb, idx, vertRead);
            TE_CHECKBREAK_CODE(code);
            code = getVertex(&b, src, cb, idx+1, vertRead);
            TE_CHECKBREAK_CODE(code);
            code = getVertex(&c, src, cb, idx+2, vertRead);
            TE_CHECKBREAK_CODE(code);

            code = triangle_r(sink, a, b, c, algorithm.distance(a,b), algorithm.distance(b,c), algorithm.distance(c, a), threshold, algorithm, 0u);
            TE_CHECKBREAK_CODE(code)
        }
        TE_CHECKRETURN_CODE(code);

        // generate output from 'finished'
        *dstCount = sink.count;
        code = VertexData_allocate(value, src.stride, src.size, *dstCount);
        TE_CHECKRETURN_CODE(code);

        MemBuffer2 dstbuf((uint8_t *)value->data, value->stride**dstCount);
        code = sink.transfer(dstbuf);
        TE_CHECKRETURN_CODE(code);
        return TE_Ok;
    } else {
        *dstCount = cb.indices.size();
        code = VertexData_allocate(value, src.stride, src.size, *dstCount);
        TE_CHECKRETURN_CODE(code);

        MemBuffer2 dstbuf((uint8_t *)value->data, value->stride**dstCount);
        for (std::size_t i = 0u; i < *dstCount; i++) {
            Point2<double> xyz;
            code = getVertex(&xyz, src, cb, i, vertRead);
            TE_CHECKBREAK_CODE(code);
            code = vertWrite(dstbuf, *value, xyz);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

namespace
{
    TessCallback::TessCallback(const VertexData &layout_, const std::size_t count_) NOTHROWS :
        origin(0.0, 0.0, 0.0),
        error(false),
        srcCount(count_),
        dstCount(0u),
        combineCount(0u)
    {}

    void VertexData_deleter(const VertexData *value)
    {
        if(value) {
            const uint8_t *data = static_cast<const uint8_t *>(value->data);
            delete [] data;
            delete value;
        }
    }

    Algorithm wgs84() NOTHROWS
    {
        Algorithm geo;
        geo.distance = WGS84_distance;
        geo.direction = WGS84_direction;
        geo.interpolate = WGS84_interpolate;
        return geo;
    }
    double WGS84_distance(const Point2<double> &a, const Point2<double> &b)
    {
        return GeoPoint2_distance(GeoPoint2(a.y, a.x), GeoPoint2(b.y, b.x), true);
    }
    Point2<double> WGS84_direction(const Point2<double> &a, const Point2<double> &b)
    {
        Point2<double> result;
        result.x = GeoPoint2_bearing(GeoPoint2(a.y, a.x), GeoPoint2(b.y, b.x), true);
        if(a.z != b.z) {
            // doing a cartesian slant angle for purposes of preserving relative height over ellipsoid surface rather than doing a slant line
            const double dz = b.z-a.z;
            const double distance = GeoPoint2_distance(GeoPoint2(a.y, a.x), GeoPoint2(b.y, b.x), true);
            result.y = dz / distance;
        }
        return result;
    }
    Point2<double> WGS84_interpolate(const Point2<double> &origin, const Point2<double> &dir, const double distance)
    {
        GeoPoint2 result = GeoPoint2_pointAtDistance(GeoPoint2(origin.y, origin.x), dir.x, distance, true);
        result.altitude = origin.z + (dir.z*distance);
        return Point2<double>(result.longitude, result.latitude, result.altitude);
    }

    Algorithm cartesian() NOTHROWS
    {
        Algorithm xyz;
        xyz.distance = Cartesian_distance;
        xyz.direction = Cartesian_direction;
        xyz.interpolate = Cartesian_interpolate;
        return xyz;
    }
    double Cartesian_distance(const Point2<double> &a, const Point2<double> &b)
    {
        const double dx = b.x-a.x;
        const double dy = b.y-a.y;
        const double dz = b.z-a.z;
        return ::sqrt(dx*dx + dy*dy + dz*dz);
    }
    Point2<double> Cartesian_direction(const Point2<double> &a, const Point2<double> &b)
    {
        const double dx = b.x-a.x;
        const double dy = b.y-a.y;
        const double dz = b.z-a.z;
        const double length = ::sqrt(dx*dx + dy*dy + dz*dz);
        return Point2<double>(dx/length, dy/length, dz/length);
    }
    Point2<double> Cartesian_interpolate(const Point2<double> &origin, const Point2<double> &dir, const double distance)
    {
        return Point2<double>(origin.x + (dir.x*distance),
                              origin.y + (dir.y*distance),
                              origin.z + (dir.z*distance));
    }

    Point2<double> midpoint(const Point2<double> &a, const Point2<double> &b, Algorithm alg) NOTHROWS
    {
        const double dist = alg.distance(a, b);
        const Point2<double> dir = alg.direction(a, b);
        return alg.interpolate(a, dir, dist / 2.0);
    }

    void TessCallback_beginData(GLenum type, void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
    }
    void TessCallback_edgeFlagData(GLboolean flag, void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
    }
    void TessCallback_vertexData_count(void *vertexData, void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
        cb.dstCount++;
    }
    void TessCallback_vertexData_assemble(void *vertexData, void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
        cb.indices.push_back((std::size_t)(intptr_t)vertexData);
        cb.dstCount++;
    }
    void TessCallback_endData(void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
    }
    void TessCallback_combinData_count(GLfloat coords[3], void *d[4], GLfloat w[4], void **outData, void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
        *outData = (void *)(intptr_t)(cb.srcCount+cb.combineCount);
        cb.combineCount++;
    }
    void TessCallback_combinData_assemble(GLfloat coords[3], void *d[4], GLfloat w[4], void **outData, void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
        *outData = (void *)(intptr_t)(cb.srcCount+cb.combineCount);
        cb.combinedVertices.push_back(Point2<double>(coords[0]+cb.origin.x, coords[1]+cb.origin.y, coords[2]+cb.origin.z));
        cb.combineCount++;
    }
    void TessCallback_errorData(GLenum err, void *opaque)
    {
        TessCallback &cb = *static_cast<TessCallback *>(opaque);
        cb.error = true;
    }

    TAKErr polygon(TessCallback *value, const VertexData &src, const std::size_t count, ReadVertexFn it, _GLUfuncptr vertexDataCallback, _GLUfuncptr combineCallback) NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (!value)
            return TE_InvalidArg;
        if (!it)
            return TE_InvalidArg;

        GLenum windingRule = GLU_TESS_WINDING_ODD;
        double tolerance = 0.0;
        std::unique_ptr<GLUtesselator, void(*)(GLUtesselator *)> tess(gluNewTess(), gluDeleteTess);
        if (!tess.get())
            return TE_OutOfMemory;

        // properties
        gluTessProperty(tess.get(), GLU_TESS_WINDING_RULE, windingRule);
        gluTessProperty(tess.get(), GLU_TESS_TOLERANCE, tolerance);
        // callbacks
        gluTessCallback(tess.get(), GLU_TESS_COMBINE_DATA, combineCallback);
        gluTessCallback(tess.get(), GLU_TESS_BEGIN_DATA, (_GLUfuncptr)TessCallback_beginData);
        gluTessCallback(tess.get(), GLU_TESS_EDGE_FLAG_DATA, (_GLUfuncptr)TessCallback_edgeFlagData);
        gluTessCallback(tess.get(), GLU_TESS_END_DATA, (_GLUfuncptr)TessCallback_endData);
        gluTessCallback(tess.get(), GLU_TESS_ERROR_DATA, (_GLUfuncptr)TessCallback_errorData);
        gluTessCallback(tess.get(), GLU_TESS_VERTEX_DATA, vertexDataCallback);

        MemBuffer2 data((const uint8_t *)src.data, src.stride*count);

        code = it(&value->origin, data, src);
        TE_CHECKRETURN_CODE(code);
        code = data.position(0u); // rewind
        TE_CHECKRETURN_CODE(code);

        gluTessBeginPolygon(tess.get(), value);
        gluTessBeginContour(tess.get());

        std::size_t idx = 0u;
        while (data.remaining() && !value->error) {
            Point2<double> xyz;
            code = it(&xyz, data, src);
            TE_CHECKBREAK_CODE(code);

            GLfloat xyzf[3];
            xyzf[0] = xyz.x - value->origin.x;
            xyzf[1] = xyz.y - value->origin.y;
            //xyzf[2] = xyz.z - value->origin.z;
            xyzf[2] = 0.0;
            gluTessVertex(tess.get(), xyzf, (void *)(intptr_t)idx);
            idx++;
        }
        gluTessEndContour(tess.get());
        gluTessEndPolygon(tess.get());
        tess.reset();

        TE_CHECKRETURN_CODE(code);

        return value->error ? TE_Err : code;
    }

    TAKErr triangle_r(VertexSink &sink, const Point2<double> &a, const Point2<double> &b, const Point2<double> &c, const double dab, const double dbc, const double dca, const double threshold, Algorithm alg, std::size_t depth) NOTHROWS
    {
        TAKErr code(TE_Ok);
        //Logger_log(TELL_Info, "triangle_r[%u] {a={%lf,%lf} b={%lf,%lf} c={%lf,%lf}}", depth, a.x, a.y, b.x, b.y, c.x, c.y);

        const bool reprocess = ((dab/threshold) > 2.0 || (dbc/threshold) > 2.0 || (dca/threshold) > 2.0);

        const std::size_t subAB = dab > threshold ? 1u : 0u;
        const std::size_t subBC = dbc > threshold ? 1u : 0u;
        const std::size_t subCA = dca > threshold ? 1u : 0u;

        const std::size_t subs = subAB+subBC+subCA;

        // XXX - winding order!!!

#define __te_emit_or_recurse(sa, sb, sc, dsab, dsbc, dsca) \
    if(reprocess && ((dsab) > threshold || (dsbc) > threshold || (dsca) > threshold))  {\
        code = triangle_r(sink, sa, sb, sc, dsab, dsbc, dsca, threshold, alg, depth+1); \
        TE_CHECKRETURN_CODE(code); \
    } else { \
        code = sink.writeTriangle(sa, sb, sc); \
        TE_CHECKRETURN_CODE(code); \
    }

        // no tessellation
        if(subs == 0) {
            code = sink.writeTriangle(a, b, c);
            TE_CHECKRETURN_CODE(code);
            return code;
        } else if(subs == 3u) {
            // full tessellation
            const double hdab = dab / 2.0;
            const double hdbc = dbc / 2.0;
            const double hdca = dca / 2.0;

            const Point2<double> d = midpoint2(a, alg.direction(a, b), hdab, alg);
            const Point2<double> e = midpoint2(b, alg.direction(b, c), hdbc, alg);
            const Point2<double> f = midpoint2(c, alg.direction(c, a), hdca, alg);

            //DoubleBuffer retval = Unsafe.allocateDirect(12*stride, DoubleBuffer.class);
            //put(retval, a, d, f, size);
            const double ddf = alg.distance(d, f);
            __te_emit_or_recurse(a, d, f, hdab, ddf, hdca);
            //put(retval, d, b, e, size);
            const double ded = alg.distance(e, d);
            __te_emit_or_recurse(d, b, e, hdab, hdbc, alg.distance(e, d));
            //put(retval, d, e, f, size);
            const double def = alg.distance(e, f);
            __te_emit_or_recurse(d, e, f, ded, def, ddf);
            //put(retval, f, e, c, size);
            __te_emit_or_recurse(f, e, c, def, hdbc, hdca);
        } else if(subs == 2u) {
            //DoubleBuffer retval = Unsafe.allocateDirect(9*stride, DoubleBuffer.class);

            if(!subBC) {
                const double hdab = dab / 2.0;
                const double hdca = dca / 2.0;

                const Point2<double> d = midpoint2(a, alg.direction(a, b), hdab, alg);
                const Point2<double> f = midpoint2(c, alg.direction(c, a), hdca, alg);

                const double ddf = alg.distance(d, f);
                const double dbf = alg.distance(b, f);

                //put(retval, a, d, f, size);
                __te_emit_or_recurse(a, d, f, hdab, ddf, hdca);
                //put(retval, f, d, b, size);
                __te_emit_or_recurse(f, d, b, ddf, hdab, dbf);
                //put(retval, f, b, c, size);
                __te_emit_or_recurse(f, b, c, dbf, dbc,hdca);
            } else if(!subCA) {
                const double hdab = dab / 2.0;
                const double hdbc = dbc / 2.0;

                const Point2<double> d = midpoint2(a, alg.direction(a, b), hdab, alg);
                const Point2<double> e = midpoint2(b, alg.direction(b, c), hdbc, alg);

                const double ded = alg.distance(e, d);
                const double dea = alg.distance(e, a);

                //put(retval, a, d, e, size);
                __te_emit_or_recurse(a, d, e, hdab, ded, dea);
                //put(retval, d, b, e, size);
                __te_emit_or_recurse(d, b, e, hdab, hdbc, ded);
                //put(retval, a, e, c, size);
                __te_emit_or_recurse(a, e, c, dea, hdbc, dca);
            } else if(!subAB) {
                const double hdbc = dbc / 2.0;
                const double hdca = dca / 2.0;

                const Point2<double> e = midpoint2(b, alg.direction(b, c), hdbc, alg);
                const Point2<double> f = midpoint2(c, alg.direction(c, a), hdca, alg);

                const double def = alg.distance(e, f);
                const double dea = alg.distance(e, a);

                //put(retval, c, e, f, size);
                __te_emit_or_recurse(c, e, f, hdbc, def, hdca);
                //put(retval, f, e, a, size);
                __te_emit_or_recurse(f, e, a, def, dea, hdca);
                //put(retval, e, a, b, size);
                __te_emit_or_recurse(e, a, b, dea, dab, hdbc);
            } else {
                return TE_IllegalState;
            }
        } else if(subs == 1u) {
            //DoubleBuffer retval = Unsafe.allocateDirect(6*stride, DoubleBuffer.class);
            if(subAB) {
                const double hdab = dab / 2.0;

                const Point2<double> d = midpoint2(a, alg.direction(a, b), hdab, alg);

                const double ddc = alg.distance(d, c);

                //put(retval, a, d, c, size);
                __te_emit_or_recurse(a, d, c, hdab, ddc, dca);
                //put(retval, c, b, d, size);
                __te_emit_or_recurse(c, b, d, dbc, hdab, ddc);
            } else if(subBC) {
                const double hdbc = dbc / 2.0;

                const Point2<double> e = midpoint2(b, alg.direction(b, c), hdbc, alg);

                const double dea = alg.distance(e, a);

                //put(retval, b, e, a, size);
                __te_emit_or_recurse(b, e, a, hdbc, dea, dab);
                //put(retval, a, e, c, size);
                __te_emit_or_recurse(a, e, c, dea, hdbc, dca);
            } else if(subCA) {
                const double hdca = dca / 2.0;

                const Point2<double> f = midpoint2(c, alg.direction(c, a), hdca, alg);

                const double dbf = alg.distance(b, f);

                //put(retval, a, b, f, size);
                __te_emit_or_recurse(a, b, f, dab, dbf, hdca);
                //put(retval, f, b, c, size);
                __te_emit_or_recurse(f, b, c, dbf, dbc, hdca);
            } else {
                return TE_IllegalState;
            }
        } else {
            return TE_IllegalState;
        }

        return code;
    }
}
