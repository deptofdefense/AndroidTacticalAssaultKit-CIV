#include "renderer/feature/GLGeometryBatchBuilder.h"

#include "core/ProjectionFactory3.h"
#include "feature/GeometryCollection.h"
#include "feature/GeometryFactory.h"
#include "feature/ParseGeometry.h"
#include "renderer/core/GLMapRenderGlobals.h"

#include <vector>

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

// 512kb
#define BUILDER_BUF_SIZE (512u*1024u)

namespace
{
    BatchGeometryBufferLayout layout;

    struct _Color
    {
        uint8_t r{ 0x0u };
        uint8_t g{ 0x0u };
        uint8_t b{ 0x0u };
        uint8_t a{ 0x0u };
    };

    struct FeatureStyle {
        struct {
            _Color stroke;
            _Color fill;
        } color;
        struct
        {
            float u0{ 0.f };
            float v0{ 0.f };
            float u1{ 0.f };
            float v1{ 0.f };
        } texCoords;
        GLuint texid{ GL_NONE };
        struct {
            std::size_t width{ 0u };
            std::size_t height{ 0u };
            float rotation{ 0.0f };
            bool isAbsoluteRotation{ false };
        } icon;
        struct {
            TAK::Engine::Port::String text;
            struct {
                double angle{ 0.0 };
                bool absolute{ false };
            } rotation;
            struct {
                unsigned int foreground{ 0xFFFFFFFFu };
                unsigned int background{ 0u };
            } color;
            struct {
                TextAlignment horizontal{ TETA_Center };
                VerticalAlignment vertical{ TEVA_Top };
            } alignment;
            double maxResolution{ 13.0 };
            TAK::Engine::Math::Point2<double> offset;
            unsigned int hints{ 0u };
            TextFormatParams format{ TextFormatParams(nullptr, 0.f) };
        } label;
        struct {
            uint32_t pattern{ 0xFFFFFFFFu };
            uint16_t factor{ 0u };
            float width{ 1.0f };
        } stroke;
        struct {
            std::size_t basicFill{ 0u };
            std::size_t basicPoint{ 0u };
            std::size_t basicStroke{ 0u };
            std::size_t icon{ 0u };
            std::size_t pattern{ 0u };
            std::size_t label{ 0u };
        } composite;
    };

    TAK::Engine::Math::Point2<double> extrudeImpl(const TAK::Engine::Math::Point2<double> &p, const double extrude_, GLGeometryBatchBuilder::Callback& callback) NOTHROWS
    {
        double extrude = extrude_;
        if (extrude < 0.0) {
            double localel = NAN;
            callback.getElevation(&localel, p.y, p.x);
            if (TE_ISNAN(localel))
                localel = 0.0;
            extrude = localel - p.z;
        }
        return TAK::Engine::Math::Point2<double>(p.x, p.y, p.z + extrude);
    }

    // Point streaming

    /**
     * Streams the points of a geometry to the specified callback
     */
    class PointStream
    {
    public :
        class Callback
        {
        public :
            virtual void point(const TAK::Engine::Math::Point2<double>& value, const bool isExteriorVertex) NOTHROWS = 0;
        };
    public :
        /**
         * Begins streaming points to the specified callback.
         * @return TE_Ok on complete, TE_Done if no points were streamed; various codes on error
         */
        virtual TAKErr start(Callback &cb) NOTHROWS = 0;
        virtual TAKErr reset(PointStream& impl) NOTHROWS
        {
            return TE_Ok;
        }
    };
    // linestring
    class LineStringPointStream : public PointStream
    {
    public :
        LineStringPointStream(const atakmap::feature::LineString &linestring_) NOTHROWS :
            linestring(&linestring_)
        {}
        TAKErr start(Callback &cb) NOTHROWS override
        {
            const std::size_t pointCount = linestring->getPointCount();
            if (pointCount < 2u)
                return TE_Done;
            const std::size_t limit = pointCount - 1u;
            for (std::size_t i = 0u; i < limit; i++) {
                const atakmap::feature::Point p0 = linestring->getPoint(i);
                cb.point(TAK::Engine::Math::Point2<double>(p0.x, p0.y, p0.z), true);
                const atakmap::feature::Point p1 = linestring->getPoint(i+1u);
                cb.point(TAK::Engine::Math::Point2<double>(p1.x, p1.y, p1.z), true);
            }
            return TE_Ok;
        }
    public :
        void reset(const atakmap::feature::LineString &linestring_) NOTHROWS
        {
            linestring = &linestring_;
        }
    private :
        const atakmap::feature::LineString *linestring;
    };
    // linestring extrude (fill//triangles)
    class QuadStripPointStream : public PointStream
    {
    public :
        QuadStripPointStream(PointStream &impl_, const double extrude_, GLGeometryBatchBuilder::Callback &callback_) NOTHROWS :
            impl(impl_),
            extrude(extrude_),
            callback(callback_)
        {
            reset(impl);
        }
        TAKErr start(Callback &cb) NOTHROWS override
        {
            struct ExtrudeToQuad : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p, const bool isExteriorVertex) NOTHROWS
                {
                    // select based on value of emit flag
                    if (emit)
                        p0 = p;
                    else
                        p1 = p;
                    // toggle
                    emit = !emit;

                    // extrude and emit two triangles
                    if (emit) {
                        /*
                        * p0----p1
                        * |\     |
                        * | \    |
                        * |  \   |
                        * |   \  |
                        * |    \ |
                        * p0'---p1'
                        * 
                        * { p0',p1',p0, p0,p1',p1 } 
                        */
                        const TAK::Engine::Math::Point2<double> p0ex = extrudeImpl(p0, extrude, *elevationFetcher);
                        const TAK::Engine::Math::Point2<double> p1ex = extrudeImpl(p1, extrude, *elevationFetcher);

                        cb->point(p0ex, isExteriorVertex);
                        cb->point(p1ex, isExteriorVertex);
                        cb->point(p0, isExteriorVertex);

                        cb->point(p0, isExteriorVertex);
                        cb->point(p1ex, isExteriorVertex);
                        cb->point(p1, isExteriorVertex);
                    }
                }
                TAK::Engine::Math::Point2<double> p0;
                TAK::Engine::Math::Point2<double> p1;
                bool emit{ true };
                GLGeometryBatchBuilder::Callback *elevationFetcher{ nullptr };
                Callback *cb{ nullptr };
                double extrude{ 0.0 };
            } extruder;
            extruder.elevationFetcher = &callback;
            extruder.extrude = extrude;
            extruder.cb = &cb;

            return impl.start(extruder);
        }
    public :
        TAKErr reset(PointStream &impl_) NOTHROWS
        {
            impl = impl_;
            return TE_Ok;
        }
    private :
        PointStream &impl;
        double extrude;
        GLGeometryBatchBuilder::Callback &callback;
    };
    // polygon (stroke//lines)
    class StrokePolygonPointStream : public PointStream
    {
    public :
        StrokePolygonPointStream(const atakmap::feature::Polygon &polygon_) :
            polygon(&polygon_),
            impl(nullptr)
        {}
        StrokePolygonPointStream(const atakmap::feature::Polygon &polygon_, PointStream &impl_) :
            polygon(&polygon_),
            impl(&impl_)
        {}
    public :
        TAKErr start(Callback &cb) NOTHROWS override
        {
            const auto rings = polygon->getRings();
            if (rings.first == rings.second)
                return TE_Done;
            for (auto it = rings.first; it != rings.second; it++) {
                LineStringPointStream linestring(*it);
                PointStream *ps;
                if (impl) {
                    impl->reset(linestring);
                    ps = impl;
                } else {
                    ps = &linestring;
                }
                ps->start(cb);
            }
            return TE_Ok;
        }
    public :
        void reset(const atakmap::feature::Polygon& polygon_) NOTHROWS
        {
            polygon = &polygon_;
        }
    private :
        /**
         * Optional wrapper that may perform operations like segment extrusion,
         * relative alt adjustment, etc. May be set to `&ps` if none was
         * specified at construction.
         */
        PointStream *impl;
        const atakmap::feature::Polygon *polygon;
    };
    // polygon (fill//triangles)
    // polygon (stroke//lines)
    class FillPolygonPointStream : public PointStream
    {
    public :
        FillPolygonPointStream(const atakmap::feature::Polygon &polygon_) :
            polygon(&polygon_),
            impl(nullptr)
        {}
        FillPolygonPointStream(const atakmap::feature::Polygon& polygon_, PointStream& impl_) :
            polygon(&polygon_),
            impl(&impl_)
        {}
    public :
        TAKErr start(Callback &cb) NOTHROWS override
        {
            const auto rings = polygon->getRings();
            if (rings.first == rings.second)
                return TE_Done;

            struct Tessellate : public TessellateCallback
            {
                TAKErr point(const TAK::Engine::Math::Point2<double>& xyz) NOTHROWS override
                {
                    return point(xyz, false);
                }
                TAKErr point(const TAK::Engine::Math::Point2<double>& xyz, const bool isExteriorVertex) NOTHROWS /*override*/
                {
                    impl->point(xyz, isExteriorVertex);
                    return TE_Ok;
                }
                Callback *impl{ nullptr };
            } tessellate;
            tessellate.impl = &cb;

            const size_t vertexSize = (rings.first->getDimension() == atakmap::feature::Geometry::_2D) ? 2u : 3u;
            std::vector<VertexData> ringsToTessellate;
            std::vector<int> ringSizes;
            for (auto ring = rings.first; ring != rings.second; ring++) {
                VertexData src;
                src.data = (void*)ring->getPoints();
                src.size = vertexSize;
                src.stride = src.size * sizeof(double);
                ringsToTessellate.push_back(src);
                ringSizes.push_back((int)ring->getPointCount());
            }

            return Tessellate_polygon<double>(tessellate, ringsToTessellate.data(), ringSizes.data(), (int)ringsToTessellate.size(), 0.0, Tessellate_CartesianAlgorithm());
        }
    public :
        void reset(const atakmap::feature::Polygon& polygon_) NOTHROWS
        {
            polygon = &polygon_;
        }
    private :
        /**
         * Optional wrapper that may perform operations like segment extrusion,
         * relative alt adjustment, etc. May be set to `&ps` if none was
         * specified at construction.
         */
        PointStream *impl;
        const atakmap::feature::Polygon *polygon;
    };
    // polygon extruder (fill//triangles)
    // point
    class PointPointStream : public PointStream
    {
    public :
        PointPointStream(const atakmap::feature::Point& point_) NOTHROWS :
            point(point_)
        {}
    public :
        TAKErr start(Callback &cb) NOTHROWS override
        {
            cb.point(TAK::Engine::Math::Point2<double>(point.x, point.y, point.z), false);
            return TE_Ok;
        }
    private :
        const atakmap::feature::Point &point;
    };
    // utility
    class RelativeAltPointStream : public PointStream
    {
    public :
        RelativeAltPointStream(PointStream &impl_, GLGeometryBatchBuilder::Callback &callback_) NOTHROWS :
            impl(impl_),
            callback(callback_)
        {}
        TAKErr start(Callback &cb) NOTHROWS override
        {
            struct AltAdjuster : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p_, const bool isExteriorVertex) NOTHROWS
                {
                    TAK::Engine::Math::Point2<double> p(p_);
                    double localel;
                    if (elevationFetcher->getElevation(&localel, p.y, p.x) == TE_Ok) {
                        if (TE_ISNAN(p.z))
                            p.z = localel;
                        else if (!TE_ISNAN(localel))
                            p.z += localel;
                    }
                    cb->point(p, false);
                }
                GLGeometryBatchBuilder::Callback *elevationFetcher{ nullptr };
                Callback *cb{ nullptr };
            } altAdjuster;
            altAdjuster.elevationFetcher = &callback;
            altAdjuster.cb = &cb;

            return impl.start(altAdjuster);
        }
        TAKErr reset(PointStream& impl_) NOTHROWS override
        {
            impl = impl_;
            return TE_Ok;
        }
    private :
        PointStream &impl;
        GLGeometryBatchBuilder::Callback &callback;
    };
    class RelativeBasePointStream : public PointStream
    {
    public :
        RelativeBasePointStream(PointStream &impl_, GLGeometryBatchBuilder::Callback &callback_) NOTHROWS :
            impl(impl_),
            callback(callback_)
        {}
        TAKErr start(Callback &cb) NOTHROWS override
        {
            struct BoundsDiscovery : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p, const bool isExteriorVertex) NOTHROWS
                {
                    if (!n) {
                        mbb.minX = p.x;
                        mbb.minY = p.y;
                        mbb.minZ = p.z;
                        mbb.maxX = p.x;
                        mbb.maxY = p.y;
                        mbb.maxZ = p.z;
                    } else {
                        if (p.x < mbb.minX)     mbb.minX = p.x;
                        else if(p.x > mbb.maxX) mbb.maxX = p.x;
                        if (p.y < mbb.minY)     mbb.minY = p.y;
                        else if(p.y > mbb.maxY) mbb.maxY = p.y;
                        if (p.z < mbb.minZ)     mbb.minZ = p.z;
                        else if(p.z > mbb.maxZ) mbb.maxZ = p.z;
                    }
                    n++;
                }
                TAK::Engine::Feature::Envelope2 mbb;
                std::size_t n{ 0u };
            } bounds;

            // run first pass to discover the base altitude
            const TAKErr code = impl.start(bounds);
            TE_CHECKRETURN_CODE(code);
            
            struct AltAdjuster : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p_, const bool isExteriorVertex) NOTHROWS
                {
                    TAK::Engine::Math::Point2<double> p(p_);
                    if (TE_ISNAN(p.z))
                        p.z = baseElevation;
                    else if (!TE_ISNAN(baseElevation))
                        p.z += baseElevation;
                    cb->point(p, false);
                }
                double baseElevation{ 0.0 };
                Callback *cb{ nullptr };
            } altAdjuster;
            altAdjuster.cb = &cb;
            callback.getElevation(&altAdjuster.baseElevation, (bounds.mbb.minY+bounds.mbb.maxY)/2.0, (bounds.mbb.minX+bounds.mbb.maxX)/2.0);

            // run second pass to adjust by base elevation
            return impl.start(altAdjuster);
        }
        TAKErr reset(PointStream& impl_) NOTHROWS override
        {
            impl = impl_;
            return TE_Ok;
        }
    private :
        PointStream &impl;
        GLGeometryBatchBuilder::Callback &callback;
    };
    class TerrainCollisionPointStream : public PointStream
    {
    public :
        TerrainCollisionPointStream(PointStream &impl_, GLGeometryBatchBuilder::Callback &callback_) NOTHROWS :
            impl(impl_),
            callback(callback_)
        {}
        TAKErr start(Callback &cb) NOTHROWS override
        {
            struct AltAdjuster : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p_, const bool isExteriorVertex) NOTHROWS
                {
                    TAK::Engine::Math::Point2<double> p(p_);
                    double localel = NAN;
                    if (elevationFetcher->getElevation(&localel, p.y, p.x) != TE_Ok || TE_ISNAN(localel))
                        localel = 0.0;
                    if (TE_ISNAN(p.z) || (p.z < localel))
                        p.z = localel;
                    cb->point(p, false);
                }
                GLGeometryBatchBuilder::Callback *elevationFetcher{ nullptr };
                Callback *cb{ nullptr };
            } altAdjuster;
            altAdjuster.elevationFetcher = &callback;
            altAdjuster.cb = &cb;

            return impl.start(altAdjuster);
        }
        TAKErr reset(PointStream& impl_) NOTHROWS override
        {
            impl = impl_;
            return TE_Ok;
        }
    private :
        PointStream &impl;
        GLGeometryBatchBuilder::Callback &callback;
    };
    class EmptyPointStream : public PointStream
    {
    public :
        TAKErr start(Callback &cb) NOTHROWS override
        {
            return TE_Done;
        }
    };
    class SegmentExtruder : public PointStream
    {
    public :
        SegmentExtruder(PointStream &impl_, const double extrude_, GLGeometryBatchBuilder::Callback &callback_) NOTHROWS :
            impl(impl_),
            callback(callback_),
            extrude(extrude_)
        {
            reset(impl);
        }
        TAKErr start(Callback &cb) NOTHROWS override
        {
            struct ExtrudeLines : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p, const bool isExteriorVertex) NOTHROWS
                {
                    // select based on value of emit flag
                    if (emit)
                        p0 = p;
                    else
                        p1 = p;
                    // toggle
                    emit = !emit;

                    // extrude and emit two triangles
                    if (emit) {
                        /*
                        * p0-------p1
                        * |         |
                        * p0'------p1'
                        *
                        * {p0',p0, p0,p1, p1,p1', p1',p0'}
                        */
                        const TAK::Engine::Math::Point2<double> p0ex = extrudeImpl(p0, extrude, *elevationFetcher);
                        const TAK::Engine::Math::Point2<double> p1ex = extrudeImpl(p1, extrude, *elevationFetcher);

                        cb->point(p0ex, false);
                        cb->point(p0, false);

                        cb->point(p0, false);
                        cb->point(p1, false);

                        cb->point(p1, false);
                        cb->point(p1ex, false);
                        
                        cb->point(p1ex, false);
                        cb->point(p0ex, false);
                    }
                }
                TAK::Engine::Math::Point2<double> p0;
                TAK::Engine::Math::Point2<double> p1;
                bool emit{ true };
                GLGeometryBatchBuilder::Callback *elevationFetcher{ nullptr };
                Callback *cb{ nullptr };
                double extrude{ 0.0 };
            } linesExtruder;
            linesExtruder.elevationFetcher = &callback;
            linesExtruder.extrude = extrude;
            linesExtruder.cb = &cb;

            return impl.start(linesExtruder);
        }
    public :
        TAKErr reset(PointStream& impl_) NOTHROWS
        {
            impl = impl_;
            return TE_Ok;
        }
    private :
        PointStream &impl;
        GLGeometryBatchBuilder::Callback &callback;
        const double extrude;
    };
    class PointExtruder : public PointStream
    {
    public :
        PointExtruder(PointStream &impl_, const double extrude_, GLGeometryBatchBuilder::Callback &callback_) NOTHROWS :
            impl(impl_),
            callback(callback_),
            extrude(extrude_)
        {
            reset(impl);
        }
        TAKErr start(Callback &cb) NOTHROWS override
        {
            struct ExtrudePoint : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p, const bool isExteriorVertex) NOTHROWS
                {
                    const TAK::Engine::Math::Point2<double> pex = extrudeImpl(p, extrude, *elevationFetcher);

                    cb->point(p, false);
                    cb->point(pex, false);
                }
                GLGeometryBatchBuilder::Callback *elevationFetcher{ nullptr };
                Callback *cb{ nullptr };
                double extrude{ 0.0 };
            } linesExtruder;
            linesExtruder.elevationFetcher = &callback;
            linesExtruder.extrude = extrude;
            linesExtruder.cb = &cb;

            return impl.start(linesExtruder);
        }
    public :
        TAKErr reset(PointStream& impl_) NOTHROWS
        {
            impl = impl_;
            return TE_Ok;
        }
    private :
        PointStream &impl;
        GLGeometryBatchBuilder::Callback &callback;
        const double extrude;
    };

    class LabelingPointStream : public PointStream
    {
    public :
        LabelingPointStream(PointStream &impl_, GLGeometryBatchBuilder::Callback &callback_) :
            impl(&impl_),
            callback(callback_)
        {}
    public :
        TAKErr start(Callback &cb) NOTHROWS override
        {
            struct Labeler : public Callback
            {
                void point(const TAK::Engine::Math::Point2<double>& p, const bool isExteriorVertex) NOTHROWS
                {
                    if(geometry.numPoints < 2) {
                        geometry.points[geometry.numPoints] = p;
                    } else if(geometry.numPoints == 2u) {
                        // transfer to overflow
                        geometry.overflow = TAK::Engine::Feature::LineString2Ptr(new TAK::Engine::Feature::LineString2(), Memory_deleter_const<TAK::Engine::Feature::LineString2>);
                        geometry.overflow->setDimension(3);
                        for(std::size_t i = 0u; i < geometry.numPoints; i++)
                            geometry.overflow->addPoint(geometry.points[i].x, geometry.points[i].y, geometry.points[i].z);
                        geometry.overflow->addPoint(p.x, p.y, p.z);
                    } else {
                        geometry.overflow->addPoint(p.x, p.y, p.z);
                    }
                    geometry.numPoints++;
                    cb->point(p, isExteriorVertex);
                }
                TAKErr setGeometry(GLLabel &label) NOTHROWS
                {
                    if (!geometry.numPoints) {
                        return TE_Done;
                    } else if(geometry.numPoints == 1) {
                        label.setGeometry(
                            TAK::Engine::Feature::Point2(
                                geometry.points[0].x,
                                geometry.points[0].y,
                                geometry.points[0].z));
                    } else if (geometry.numPoints == 2) {
                        TAK::Engine::Feature::LineString2 segment;
                        segment.setDimension(3u);
                        for(std::size_t i = 0u; i < geometry.numPoints; i++)
                            segment.addPoint(geometry.points[i].x, geometry.points[i].y, geometry.points[i].z);
                        label.setGeometry(segment);
                    } else {
                        label.setGeometry(*geometry.overflow);
                    }
                    return TE_Ok;
                }
                Callback *cb{ nullptr };
                struct {
                    TAK::Engine::Math::Point2<double> points[2u];
                    std::size_t numPoints{ 0u };
                    TAK::Engine::Feature::LineString2Ptr overflow{nullptr, nullptr};
                } geometry;
            } labeler;
            labeler.cb = &cb;

            TAKErr code = impl->start(labeler);
            // flush labels
            do {
                if (labeler.setGeometry(labels.primary) != TE_Ok)
                    break;
                callback.addLabel(labels.primary);
                for(auto &secondary : labels.secondary) {
                    if (labeler.setGeometry(secondary) != TE_Ok)
                        break;
                    callback.addLabel(secondary);
                }
            } while (false);
            return code;
        }
        TAKErr reset(PointStream& impl_) NOTHROWS
        {
            impl = &impl_;
            return TE_Ok;
        }
    private :
        PointStream* impl{ nullptr };
        GLGeometryBatchBuilder::Callback &callback;
    public :
        struct {
            GLLabel primary;
            std::vector<GLLabel> secondary;
        } labels;
    };

    struct VertexWriterImpl
    {
        // TODO-JGM: Don't understand why we're using a function pointer instead of a virtual method?
        TAKErr(*emitPrimitives)(MemoryOutput2 &sink, const FeatureStyle &style, const TAK::Engine::Math::Point2<float> *primitive, const uint32_t hitid, const bool *exteriorVertices) NOTHROWS { nullptr };

        std::size_t vertexSize{ 0u };
        struct {
            std::size_t in{ 0u };
            std::size_t out{ 0u };
        } verticesPerEmitPrimitives;
        GLenum mode{ GL_NONE };
        GLBatchGeometryRenderer4::Program type{ GLBatchGeometryRenderer4::Points };
        TAK::Engine::Feature::AltitudeMode altmode{ TAK::Engine::Feature::TEAM_ClampToGround };
    };
    struct VertexWriterContext
    {
        GLGeometryBatchBuilder *owner{ nullptr };
        double resolution{ 0.0 };
        GLGeometryBatchBuilder::Callback *callback{ nullptr };
        VertexWriterImpl impl;
        FeatureStyle style;
        std::size_t bufsize{ BUILDER_BUF_SIZE };
        uint32_t hitid{ 0u };
    };

    struct AntiAliasVertexWriter : public VertexWriterImpl
    {
        AntiAliasVertexWriter()
        {
            vertexSize = 36u;
            verticesPerEmitPrimitives.in = 2u;
            verticesPerEmitPrimitives.out = 6u;
            mode = GL_TRIANGLES;
            emitPrimitives = emitPrimitivesImpl;
            type = GLBatchGeometryRenderer4::AntiAliasedLines;
        }
    private :
        using Point = TAK::Engine::Math::Point2<float>;
        static void writeLineVertex(MemoryOutput2 &sink, const FeatureStyle &style, const Point &start, const Point &end, const uint8_t normal, const uint8_t direction, const uint32_t hitid) 
        {
            sink.writeFloat(start.x);
            sink.writeFloat(start.y);
            sink.writeFloat(start.z);
            sink.writeFloat(end.x);
            sink.writeFloat(end.y);
            sink.writeFloat(end.z);
            sink.writeByte(style.color.stroke.r);
            sink.writeByte(style.color.stroke.g);
            sink.writeByte(style.color.stroke.b);
            sink.writeByte(style.color.stroke.a);
            sink.writeInt(style.stroke.factor ? static_cast<uint16_t>(style.stroke.pattern) : 0xFFFFu);
            sink.writeByte(normal);
            sink.writeByte(static_cast<uint8_t>(std::min(style.stroke.width/4.0f*GLMapRenderGlobals_getRelativeDisplayDensity(), 255.0f)));
            sink.writeByte(direction);
            sink.writeByte(style.stroke.factor ? static_cast<uint8_t>(style.stroke.factor) : 0x1u);
            sink.writeInt(hitid);
        }
        static TAKErr emitPrimitivesImpl(MemoryOutput2 &sink, const FeatureStyle &style, const TAK::Engine::Math::Point2<float> *primitive, const uint32_t hitid, const bool *exteriorVertices) NOTHROWS
        {
            const TAK::Engine::Math::Point2<float> a(primitive[0]);
            const TAK::Engine::Math::Point2<float> b(primitive[1]);

            writeLineVertex(sink, style, a, b, 0xFFu, 0xFFu, hitid);
            writeLineVertex(sink, style, b, a, 0xFFu, 0x00u, hitid);
            writeLineVertex(sink, style, a, b, 0x00u, 0xFFu, hitid);

            writeLineVertex(sink, style, a, b, 0xFFu, 0xFFu, hitid);
            writeLineVertex(sink, style, b, a, 0xFFu, 0x00u, hitid);
            writeLineVertex(sink, style, b, a, 0x00u, 0x00u, hitid);

            return TE_Ok;
        }
    };

    struct PointSpriteVertexWriter : public VertexWriterImpl
    {
    public :
        PointSpriteVertexWriter() NOTHROWS
        {
            vertexSize = POINT_VERTEX_SIZE;
            verticesPerEmitPrimitives.in =  1u;
            verticesPerEmitPrimitives.out =  1u;
            mode = GL_POINTS;
            emitPrimitives = emitPrimitivesImpl;
            type = GLBatchGeometryRenderer4::Points;
        }
    private :
        static TAKErr emitPrimitivesImpl(MemoryOutput2 &sink, const FeatureStyle &style, const TAK::Engine::Math::Point2<float> *primitive, const uint32_t hitid, const bool *exteriorVertices) NOTHROWS
        {
            sink.writeFloat(primitive[0].x);
            sink.writeFloat(primitive[0].y);
            sink.writeFloat(primitive[0].z);
            sink.writeShort((int16_t)(style.icon.rotation * 0x7FFF));
            sink.writeShort((int16_t)(style.icon.rotation * 0x7FFF));
            sink.writeShort((int16_t)(style.texCoords.u0*0xFFFFu));
            sink.writeShort((int16_t)(style.texCoords.v0*0xFFFFu));
            sink.writeShort((int16_t)(style.texCoords.u1*0xFFFFu));
            sink.writeShort((int16_t)(style.texCoords.v1*0xFFFFu));
            sink.writeFloat((float)std::max(style.icon.width, style.icon.height));
            sink.writeByte(style.color.fill.r);
            sink.writeByte(style.color.fill.g);
            sink.writeByte(style.color.fill.b);
            sink.writeByte(style.color.fill.a);
            sink.writeInt(hitid);
            sink.writeFloat((float)style.icon.isAbsoluteRotation);
            return TE_Ok;
        }
    };

    struct PolygonVertexWriter : public VertexWriterImpl
    {
    public :
        PolygonVertexWriter() NOTHROWS
        {
            vertexSize = POLYGON_VERTEX_SIZE;
            verticesPerEmitPrimitives.in = 3u;
            verticesPerEmitPrimitives.out = 3u;
            emitPrimitives = emitPrimitivesImpl;
            mode = GL_TRIANGLES;
            type = GLBatchGeometryRenderer4::Polygons;
        }
    private :
        static TAKErr emitPrimitivesImpl(MemoryOutput2 &sink, const FeatureStyle &style, const TAK::Engine::Math::Point2<float> *primitive, const uint32_t hitid, const bool *exteriorVertices) NOTHROWS
        {
            for (std::size_t i = 0; i < 3u; i++) {
                sink.writeFloat(primitive[i].x);
                sink.writeFloat(primitive[i].y);
                sink.writeFloat(primitive[i].z);
                sink.writeByte(style.color.fill.r);
                sink.writeByte(style.color.fill.g);
                sink.writeByte(style.color.fill.b);
                sink.writeByte(style.color.fill.a);
                //sink.writeFloat(style.stroke.width);
                sink.writeFloat(10.0f);
                // https://community.khronos.org/t/how-do-i-draw-a-polygon-with-a-1-2-or-n-pixel-inset-outline-in-opengl-4-1/104201
                // The aExteriorVertex attribute should be 0.0 for exterior vertices and 1.0 for interior vertices.
                // The opposite was assumed to be true, which is why the commented out line is inverted
                // TODO: rename `exteriorVertices` to `interiorVertices`
                // TODO: determine if this is an exterior vertex
                //sink.writeFloat((float)!exteriorVertices[i]);
                sink.writeFloat(1.0f);
                sink.writeInt(hitid);
            }

            return TE_Ok;
        }
    };

    const atakmap::feature::Style &getDefaultLinestringStyle() NOTHROWS;
    const atakmap::feature::Style &getDefaultPolygonStyle() NOTHROWS;
    const atakmap::feature::Style &getDefaultPointStyle() NOTHROWS;
    TAKErr push(VertexWriterContext &ctx, const char *featureName, const atakmap::feature::Geometry& geom, const double extrude, const TAK::Engine::Feature::AltitudeMode altmode, const atakmap::feature::Style *style, const bool processLabels) NOTHROWS;

    TAKErr flushPrimitives(VertexWriterContext &ctx, const atakmap::feature::Geometry &geom, const double extrude, const TAK::Engine::Feature::AltitudeMode altmode) NOTHROWS;
    bool GLGeometryBatchBuilder_init() NOTHROWS;

    class WKBLineString : public atakmap::feature::Geometry
    {
    public:
        Geometry* clone() const override
        {
            throw std::logic_error("The method or operation is not implemented.");
        }


        std::size_t computeWKB_Size() const override
        {
            throw std::logic_error("The method or operation is not implemented.");
        }


        atakmap::feature::Envelope getEnvelope() const override
        {
        }


        void toBlob(std::ostream&, BlobFormat = GEOMETRY) const override
        {
        }


        void toWKB(std::ostream&, bool includeHeader = true) const override
        {
        }

    private:
        void changeDimension(Dimension) override
        {
            throw std::logic_error("The method or operation is not implemented.");
        }

    };
}

class GLGeometryBatchBuilder::VertexWriter : public PointStream::Callback
{
public :
    void setError() NOTHROWS;
    void point(const TAK::Engine::Math::Point2<double>& p_, const bool isExteriorVertex) NOTHROWS override;
public :
    VertexWriterContext *ctx{ nullptr };
    TAK::Engine::Math::Point2<float> primitive[6u];
    bool exteriorVertex[6u];
    std::size_t idx{ 0u };
    bool error{ false };
};

GLGeometryBatchBuilder::GLGeometryBatchBuilder() NOTHROWS :
    callback(nullptr)
{
    static bool clinit = GLGeometryBatchBuilder_init();
}
GLGeometryBatchBuilder::~GLGeometryBatchBuilder() NOTHROWS
{}
                
TAKErr GLGeometryBatchBuilder::reset(const int surfaceSrid_, const int spritesSrid_, const GeoPoint2 &relativeToCenter_, const double resolution_, Callback &callback_) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = ProjectionFactory3_create(proj.surface, surfaceSrid_);
    TE_CHECKRETURN_CODE(code);
    code = ProjectionFactory3_create(proj.sprites, spritesSrid_);
    TE_CHECKRETURN_CODE(code);

    // unmap any mapped buffers (warn, none should be mapped); this gets done before callback is reassigned
    Builder *bs[10u]
    {
        &builders.sprites.antiAliasedLines,
        &builders.sprites.lines,
        &builders.sprites.polygons,
        &builders.sprites.strokedPolygons,
        &builders.sprites.points,
        &builders.surface.antiAliasedLines,
        &builders.surface.lines,
        &builders.surface.polygons,
        &builders.surface.strokedPolygons,
        &builders.surface.points,
    };
    bool warn = false;
    for (std::size_t i = 0u; i < 10u; i++) {
        if (bs[i]->pb.count) {
            warn |= true;
            callback->unmapBuffer(bs[i]->pb.vbo);
        }

        // reset
        bs[i]->pb = GLBatchGeometryRenderer4::PrimitiveBuffer();
    }

    if (warn) {
        Logger_log(TELL_Warning, "GLBatchGeometryBuilder::reset() encountered unflushed buffer data, VBO leaked.");
    }

    buffers.sprites.antiAliasedLines.clear();
    buffers.sprites.lines.clear();
    buffers.sprites.polygons.clear();
    buffers.sprites.strokedPolygons.clear();
    buffers.sprites.points.clear();
    buffers.surface.antiAliasedLines.clear();
    buffers.surface.lines.clear();
    buffers.surface.polygons.clear();
    buffers.surface.strokedPolygons.clear();
    buffers.surface.points.clear();

    proj.surface->forward(&relativeToCenter.surface, relativeToCenter_);
    proj.sprites->forward(&relativeToCenter.sprites, relativeToCenter_);
    resolution = resolution_;
    callback = &callback_;
    return TE_Ok;
}
TAKErr GLGeometryBatchBuilder::push(TAK::Engine::Feature::FeatureDefinition2 &def, const bool processLabels) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!this->callback)
        return TE_IllegalState;
    VertexWriterContext ctx;
    ctx.owner = this;
    ctx.resolution = resolution;
    ctx.callback = this->callback;
    ctx.bufsize = BUILDER_BUF_SIZE;
    ctx.hitid = callback->reserveHitId();
    TAK::Engine::Feature::FeatureDefinition2::RawData rawStyle;
    def.getRawStyle(&rawStyle);
    const atakmap::feature::Style *style = nullptr;
    switch (def.getStyleCoding()) {
    case TAK::Engine::Feature::FeatureDefinition2::StyleStyle :
        style = static_cast<const atakmap::feature::Style*>(rawStyle.object);
        break;
    case TAK::Engine::Feature::FeatureDefinition2::StyleOgr :
        if (rawStyle.text) {
            const auto entry = parsedStyles.find(rawStyle.text);
            if (entry != parsedStyles.end()) {
                style = entry->second.get();
            } else {
                TAK::Engine::Feature::StylePtr_const parsed(nullptr, nullptr);
                atakmap::feature::Style_parseStyle(parsed, rawStyle.text);
                style = parsed.get();
                parsedStyles.insert(std::make_pair(rawStyle.text, std::move(parsed)));
            }
        }
        break;
    default :
        break;
    }
    TAK::Engine::Feature::FeatureDefinition2::RawData rawGeom;
    def.getRawGeometry(&rawGeom);
    TAK::Engine::Feature::GeometryPtr_const geom(nullptr, nullptr);
    switch (def.getGeomCoding()) {
    case TAK::Engine::Feature::FeatureDefinition2::GeomGeometry :
        geom = TAK::Engine::Feature::GeometryPtr_const(static_cast<const atakmap::feature::Geometry*>(rawGeom.object), Memory_leaker_const<atakmap::feature::Geometry>);
        break;
    case TAK::Engine::Feature::FeatureDefinition2::GeomBlob :
        if (rawGeom.binary.len) {
            try {
                geom = TAK::Engine::Feature::GeometryPtr_const(
                    atakmap::feature::parseBlob(atakmap::feature::ByteBuffer(rawGeom.binary.value, rawGeom.binary.value + rawGeom.binary.len)),
                    atakmap::feature::destructGeometry);
            } catch (...) {}
        }
        break;
    case TAK::Engine::Feature::FeatureDefinition2::GeomWkb :
        if (rawGeom.binary.len) {
            try {
                geom = TAK::Engine::Feature::GeometryPtr_const(
                    atakmap::feature::parseWKB(atakmap::feature::ByteBuffer(rawGeom.binary.value, rawGeom.binary.value + rawGeom.binary.len)),
                    atakmap::feature::destructGeometry);
            } catch (...) {}
        }
        break;
    default :
        break;
    }
    if (!geom)
        return TE_Ok;
    const char* name = nullptr;
    def.getName(&name);
    return ::push(ctx, name, *geom, def.getExtrude(), def.getAltitudeMode(), style, processLabels);
}
TAKErr GLGeometryBatchBuilder::push(const TAK::Engine::Feature::Feature2 &def, const bool processLabels) NOTHROWS
{
    if (!callback)
        return TE_IllegalState;
    const atakmap::feature::Geometry *geom = def.getGeometry();
    if (!geom)
        return TE_Done;
    VertexWriterContext ctx;
    ctx.owner = this;
    ctx.resolution = resolution;
    ctx.callback = this->callback;
    ctx.bufsize = BUILDER_BUF_SIZE;
    ctx.hitid = callback->reserveHitId();
    return ::push(ctx, def.getName(), *geom, def.getExtrude(), def.getAltitudeMode(), def.getStyle(), processLabels);
}
TAKErr GLGeometryBatchBuilder::flush() NOTHROWS
{
    struct FlushContext
    {
        FlushContext(Builder &builder_, std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> &buffer_) NOTHROWS :
            builder(builder_),
            buffer(buffer_)
        {}

        Builder &builder;
        std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> &buffer;
    };
    FlushContext ctx[10u]
    {
        FlushContext(builders.sprites.antiAliasedLines, buffers.sprites.antiAliasedLines),
        FlushContext(builders.sprites.lines, buffers.sprites.lines),
        FlushContext(builders.sprites.polygons, buffers.sprites.polygons),
        FlushContext(builders.sprites.strokedPolygons, buffers.sprites.strokedPolygons),
        FlushContext(builders.sprites.points, buffers.sprites.points),
        FlushContext(builders.surface.antiAliasedLines, buffers.surface.antiAliasedLines),
        FlushContext(builders.surface.lines, buffers.surface.lines),
        FlushContext(builders.surface.polygons, buffers.surface.polygons),
        FlushContext(builders.surface.strokedPolygons, buffers.surface.strokedPolygons),
        FlushContext(builders.surface.points, buffers.surface.points),
    };
    for (std::size_t i = 0u; i < 10u; i++) {
        if (ctx[i].builder.pb.count) {
            callback->unmapBuffer(ctx[i].builder.pb.vbo);
            ctx[i].buffer.push_back(ctx[i].builder.pb);
        }

        // reset
        ctx[i].builder.pb = GLBatchGeometryRenderer4::PrimitiveBuffer();
        ctx[i].builder.buffer.close();
    }
    return TE_Ok;
}
TAKErr GLGeometryBatchBuilder::setBatch(GLBatchGeometryRenderer4 &sink) const NOTHROWS
{
    // verify flushed
    const Builder *bs[10u]
    {
        &builders.sprites.antiAliasedLines,
        &builders.sprites.lines,
        &builders.sprites.polygons,
        &builders.sprites.strokedPolygons,
        &builders.sprites.points,
        &builders.surface.antiAliasedLines,
        &builders.surface.lines,
        &builders.surface.polygons,
        &builders.surface.strokedPolygons,
        &builders.surface.points,
    };
    for (std::size_t i = 0u; i < 10u; i++) {
        if (bs[i]->pb.count)
            return TE_IllegalState;
    }

    // reset batch state for upload buffers
    GLBatchGeometryRenderer4::BatchState surface;
    GLBatchGeometryRenderer4::BatchState sprites;
    if(proj.surface) {
        surface.srid = proj.surface->getSpatialReferenceID();
        proj.surface->inverse(&surface.centroid, relativeToCenter.surface);
        surface.centroidProj = relativeToCenter.surface;
        surface.localFrame.setToTranslate(surface.centroidProj.x, surface.centroidProj.y, surface.centroidProj.z);
    }
    if(proj.sprites) {
        sprites.srid = proj.sprites->getSpatialReferenceID();
        proj.sprites->inverse(&surface.centroid, relativeToCenter.sprites);
        sprites.centroidProj = relativeToCenter.sprites;
        sprites.localFrame.setToTranslate(sprites.centroidProj.x, sprites.centroidProj.y, sprites.centroidProj.z);
    }
    sink.setBatchState(surface, sprites);

    // sprites
    for (const auto& pb : buffers.sprites.lines)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::Lines, pb, layout, GLGlobeBase::Sprites);
    for (const auto& pb : buffers.sprites.antiAliasedLines)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::AntiAliasedLines, pb, layout, GLGlobeBase::Sprites);
    for (const auto& pb : buffers.sprites.polygons)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::Polygons, pb, layout, GLGlobeBase::Sprites);
    for (const auto& pb : buffers.sprites.strokedPolygons)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::StrokedPolygons, pb, layout, GLGlobeBase::Sprites);
    for (const auto& pb : buffers.sprites.points)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::Points, pb, layout, GLGlobeBase::Sprites);

    // surface
    for (const auto& pb : buffers.surface.lines)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::Lines, pb, layout, GLGlobeBase::Surface);
    for (const auto& pb : buffers.surface.antiAliasedLines)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::AntiAliasedLines, pb, layout, GLGlobeBase::Surface);
    for (const auto& pb : buffers.surface.polygons)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::Polygons, pb, layout, GLGlobeBase::Surface);
    for (const auto& pb : buffers.surface.strokedPolygons)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::StrokedPolygons, pb, layout, GLGlobeBase::Surface);
    for (const auto& pb : buffers.surface.points)
        sink.addBatchBuffer(GLBatchGeometryRenderer4::Points, pb, layout, GLGlobeBase::Surface);

    return TE_Ok;
}

GLGeometryBatchBuilder::Callback::~Callback() NOTHROWS
{}

void GLGeometryBatchBuilder::VertexWriter::setError() NOTHROWS
{
    error = true;
}
void GLGeometryBatchBuilder::VertexWriter::point(const TAK::Engine::Math::Point2<double> &p_, const bool isExteriorVertex) NOTHROWS
{
    if (error)
        return;
    if (!ctx) {
        error = true;
        return;
    }

    const bool sprites = (ctx->impl.altmode != TAK::Engine::Feature::TEAM_ClampToGround);

    TAK::Engine::Core::GeoPoint2 lla(p_.y, p_.x, p_.z, TAK::Engine::Core::HAE);
    TAK::Engine::Math::Point2<double> p;
    const Projection2 &lla2xyz = sprites ? *ctx->owner->proj.sprites : *ctx->owner->proj.surface;
    const TAK::Engine::Math::Point2<double> &rtc = sprites ? ctx->owner->relativeToCenter.sprites : ctx->owner->relativeToCenter.surface;
    if (lla2xyz.forward(&p, lla) != TE_Ok) {
        error = true;
        return;
    }
    p.x -= rtc.x;
    p.y -= rtc.y;
    p.z -= rtc.z;

    struct {
        GLGeometryBatchBuilder::Builder* builder{ nullptr };
        std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> *buffers{ nullptr };
    } sink;
    switch (ctx->impl.type) {
    case GLBatchGeometryRenderer4::AntiAliasedLines :
        sink.builder = sprites ? &ctx->owner->builders.sprites.antiAliasedLines : &ctx->owner->builders.surface.antiAliasedLines;
        sink.buffers = sprites ? &ctx->owner->buffers.sprites.antiAliasedLines : &ctx->owner->buffers.surface.antiAliasedLines;
        break;
    case GLBatchGeometryRenderer4::Lines :
        sink.builder = sprites ? &ctx->owner->builders.sprites.lines : &ctx->owner->builders.surface.lines;
        sink.buffers = sprites ? &ctx->owner->buffers.sprites.lines : &ctx->owner->buffers.surface.lines;
        break;
    case GLBatchGeometryRenderer4::Polygons :
        sink.builder = sprites ? &ctx->owner->builders.sprites.polygons : &ctx->owner->builders.surface.polygons;
        sink.buffers = sprites ? &ctx->owner->buffers.sprites.polygons : &ctx->owner->buffers.surface.polygons;
        break;
    case GLBatchGeometryRenderer4::StrokedPolygons :
        sink.builder = sprites ? &ctx->owner->builders.sprites.strokedPolygons : &ctx->owner->builders.surface.strokedPolygons;
        sink.buffers = sprites ? &ctx->owner->buffers.sprites.strokedPolygons : &ctx->owner->buffers.surface.strokedPolygons;
        break;
    case GLBatchGeometryRenderer4::Points :
        // points are always sprites
        sink.builder = &ctx->owner->builders.sprites.points;
        sink.buffers = &ctx->owner->buffers.sprites.points;
        break;
    }
    
    primitive[idx] = TAK::Engine::Math::Point2<float>((float)p.x, (float)p.y, (float)p.z);
    exteriorVertex[idx] = isExteriorVertex;
    idx++;
    if (idx == ctx->impl.verticesPerEmitPrimitives.in) {
        std::size_t remaining = 0u;
        sink.builder->buffer.remaining(&remaining);
        if (!sink.builder->pb.vbo ||
            ctx->style.texid != sink.builder->pb.texid ||
            remaining < (ctx->impl.verticesPerEmitPrimitives.out*ctx->impl.vertexSize)) {

            // flush builder contents
            if (sink.builder->pb.vbo) {
                if (ctx->owner->callback->unmapBuffer(sink.builder->pb.vbo) != TE_Ok) {
                    error = true;
                    return;
                }

                sink.buffers->push_back(sink.builder->pb);
            }
            // reset
            sink.builder->pb = GLBatchGeometryRenderer4::PrimitiveBuffer();
            sink.builder->buffer.close();

            GLuint handle;
            void *data;
            if (ctx->owner->callback->mapBuffer(&handle, &data, ctx->bufsize) != TE_Ok) {
                error = true;
                return;
            }
            sink.builder->pb.vbo = handle;
            if (sink.builder->buffer.open((uint8_t*)data, ctx->bufsize) != TE_Ok) {
                error = true;
                return;
            }
            sink.builder->buffer.setSourceEndian2(TE_PlatformEndian);
            sink.builder->pb.mode = ctx->impl.mode;
            sink.builder->pb.texid = ctx->style.texid;
        }

        idx = 0u;
        if (ctx->impl.emitPrimitives(sink.builder->buffer, ctx->style, primitive, ctx->hitid, exteriorVertex) != TE_Ok) {
            error = true;
            return;
        }
        sink.builder->pb.count += (GLsizei)ctx->impl.verticesPerEmitPrimitives.out;
    }
}

namespace
{
    const atakmap::feature::Style& getDefaultLinestringStyle() NOTHROWS
    {
        static atakmap::feature::BasicStrokeStyle defls(0xFFFFFFFF, 1.0f);
        return defls;
    }
    const atakmap::feature::Style &getDefaultPolygonStyle() NOTHROWS
    {
        static atakmap::feature::BasicStrokeStyle defls(0xFFFFFFFF, 1.0f);
        return defls;
    }
    const atakmap::feature::Style &getDefaultPointStyle() NOTHROWS
    {
        static atakmap::feature::BasicPointStyle defls(0xFFFFFFFF, 32.0f);
        return defls;
    }

    TAKErr push(VertexWriterContext &ctx, const char *featureName, const atakmap::feature::Geometry &geom, const double extrude, const TAK::Engine::Feature::AltitudeMode altmode, const atakmap::feature::Style *style_, const bool processLabels) NOTHROWS
    {
        if (geom.getType() == atakmap::feature::Geometry::COLLECTION) {
            const auto &gc = static_cast<const atakmap::feature::GeometryCollection &>(geom);
            for (auto it = gc.contents().first; it != gc.contents().second; it++)
                push(ctx, featureName, **it, extrude, altmode, style_, false);
            return TE_Ok;
        }

        bool needsLabel = (geom.getType() == atakmap::feature::Geometry::POINT && processLabels);
        if (!style_) {
            switch (geom.getType()) {
            case atakmap::feature::Geometry::POINT :
                style_ = &getDefaultPointStyle();
                break;
            case atakmap::feature::Geometry::LINESTRING:
                style_ = &getDefaultLinestringStyle();
                break;
            case atakmap::feature::Geometry::POLYGON :
                style_ = &getDefaultPolygonStyle();
                break;
            default :
                return TE_IllegalState;
            }
        }
        const atakmap::feature::Style &style = *style_;
        switch (style.getClass()) {
        case atakmap::feature::TESC_BasicFillStyle :
        {
            if (ctx.style.composite.basicFill) {
                flushPrimitives(ctx, geom, extrude, altmode);
                ctx.style = FeatureStyle();
            }
            const auto &impl = static_cast<const atakmap::feature::BasicFillStyle &>(style);
            ctx.style.color.fill.r = (impl.getColor() >> 16u) & 0xFFu;
            ctx.style.color.fill.g = (impl.getColor() >> 8u) & 0xFFu;
            ctx.style.color.fill.b = impl.getColor() & 0xFFu;
            ctx.style.color.fill.a = (impl.getColor() >> 24u) & 0xFFu;
            ctx.style.composite.basicFill++;
            break;
        }
        case atakmap::feature::TESC_BasicPointStyle :
            break;
        case atakmap::feature::TESC_BasicStrokeStyle :
        {
            if (ctx.style.composite.basicStroke || ctx.style.composite.pattern) {
                flushPrimitives(ctx, geom, extrude, altmode);
                ctx.style = FeatureStyle();
            }
            const auto &impl = static_cast<const atakmap::feature::BasicStrokeStyle &>(style);
            ctx.style.color.stroke.r = (impl.getColor() >> 16u) & 0xFFu;
            ctx.style.color.stroke.g = (impl.getColor() >> 8u) & 0xFFu;
            ctx.style.color.stroke.b = impl.getColor() & 0xFFu;
            ctx.style.color.stroke.a = (impl.getColor() >> 24u) & 0xFFu;
            ctx.style.stroke.width = impl.getStrokeWidth();
            ctx.style.composite.basicStroke++;
            break;
        }
        case atakmap::feature::TESC_CompositeStyle :
        {
            const auto &impl = static_cast<const atakmap::feature::CompositeStyle &>(style);
            for (std::size_t i = 0u; i < impl.getStyleCount(); i++)
                push(ctx, featureName, geom, extrude, altmode, &impl.getStyle(i), false);
            break;
        }
        case atakmap::feature::TESC_IconPointStyle :
        {
            if (ctx.style.composite.icon) {
                flushPrimitives(ctx, geom, extrude, altmode);
                ctx.style = FeatureStyle();
            }
            const auto &impl = static_cast<const atakmap::feature::IconPointStyle &>(style);
            if (ctx.callback->getIcon(&ctx.style.texid,
                                      &ctx.style.texCoords.u0,
                                      &ctx.style.texCoords.v0,
                                      &ctx.style.texCoords.u1,
                                      &ctx.style.texCoords.v1,
                                      &ctx.style.icon.width,
                                      &ctx.style.icon.height, 
                                      &ctx.style.icon.rotation,
                                      &ctx.style.icon.isAbsoluteRotation,
                                      impl.getIconURI()) != TE_Ok) {

                break;
            }
            if (impl.getScaling()) {
                ctx.style.icon.width = (std::size_t)std::max(impl.getScaling() * (float)ctx.style.icon.width, 1.f);
                ctx.style.icon.height = (std::size_t)std::max(impl.getScaling() * (float)ctx.style.icon.height, 1.f);
            }
            ctx.style.color.fill.r = (impl.getColor() >> 16u) & 0xFFu;
            ctx.style.color.fill.g = (impl.getColor() >> 8u) & 0xFFu;
            ctx.style.color.fill.b = impl.getColor() & 0xFFu;
            ctx.style.color.fill.a = (impl.getColor() >> 24u) & 0xFFu;
            ctx.style.icon.rotation = impl.getRotation() * (float)(M_PI / 180.0f); 
            ctx.style.icon.isAbsoluteRotation = impl.isRotationAbsolute();
            ctx.style.composite.icon++;
            break;
        }
        case atakmap::feature::TESC_LabelPointStyle :
        {
            if (ctx.style.composite.label) {
                flushPrimitives(ctx, geom, extrude, altmode);
                ctx.style = FeatureStyle();
            }

            const auto &impl = static_cast<const atakmap::feature::LabelPointStyle &>(style);

            ctx.style.label.format = TextFormatParams(impl.getFontFace(), impl.getTextSize());
            ctx.style.label.format.bold = !!(impl.getStyle() & atakmap::feature::LabelPointStyle::BOLD);
            ctx.style.label.format.italic = !!(impl.getStyle() & atakmap::feature::LabelPointStyle::ITALIC);
            ctx.style.label.format.underline = !!(impl.getStyle() & atakmap::feature::LabelPointStyle::UNDERLINE);
            ctx.style.label.format.strikethrough = !!(impl.getStyle() & atakmap::feature::LabelPointStyle::STRIKETHROUGH);

            ctx.style.label.text = impl.getText();
            // XXX -
            const double drawTilt = 0.0;
            ctx.style.label.offset = TAK::Engine::Math::Point2<double>(
                impl.getOffsetX(),
                impl.getOffsetY() + ctx.style.icon.height * 3.0 / 4.0,
                (-0.00025 * cos(M_PI / 180.0 * drawTilt)));
            ctx.style.label.maxResolution = impl.getLabelMinRenderResolution();
            ctx.style.label.alignment.horizontal = TETA_Center;
            ctx.style.label.alignment.vertical = TEVA_Top;
            ctx.style.label.color.foreground = impl.getTextColor();
            ctx.style.label.color.background = !!(impl.getBackgroundColor() & 0xFF000000) ? impl.getBackgroundColor() : 0u;
            ctx.style.label.rotation.angle = impl.getRotation();
            ctx.style.label.rotation.absolute = impl.isRotationAbsolute();

            needsLabel = false;
            break;
        }
        case atakmap::feature::TESC_PatternStrokeStyle :
        {
            if (ctx.style.composite.basicStroke || ctx.style.composite.pattern) {
                flushPrimitives(ctx, geom, extrude, altmode);
                ctx.style = FeatureStyle();
            }
            const auto &impl = static_cast<const atakmap::feature::PatternStrokeStyle &>(style);
            ctx.style.color.stroke.r = (impl.getColor() >> 16u) & 0xFFu;
            ctx.style.color.stroke.g = (impl.getColor() >> 8u) & 0xFFu;
            ctx.style.color.stroke.b = impl.getColor() & 0xFFu;
            ctx.style.color.stroke.a = (impl.getColor() >> 24u) & 0xFFu;
            ctx.style.stroke.width = impl.getStrokeWidth();
            ctx.style.stroke.pattern = impl.getPattern();
            ctx.style.stroke.factor = (uint32_t)impl.getFactor();
            ctx.style.composite.pattern++;
            break;
        }
        default :
            break;
        }

        if(needsLabel) {
            ctx.style.label.text = featureName;
            // XXX -

            const double drawTilt = 0.0;
            ctx.style.label.offset = TAK::Engine::Math::Point2<double>(
                0.0,
                ctx.style.icon.height * 3.0 / 4.0,
                (-0.00025 * cos(M_PI / 180.0 * drawTilt)));

            ctx.style.composite.label++;
        }

        if (ctx.style.composite.basicFill ||
            ctx.style.composite.basicPoint ||
            ctx.style.composite.basicStroke ||
            ctx.style.composite.icon ||
            ctx.style.composite.label ||
            ctx.style.composite.pattern) {

            flushPrimitives(ctx, geom, extrude, altmode);
        }

        return TE_Ok;
    }

    TAKErr flushPrimitives(VertexWriterContext &ctx, const atakmap::feature::Geometry &geom, const double extrude, const TAK::Engine::Feature::AltitudeMode altmode) NOTHROWS
    {
        // XXX - 
        EmptyPointStream empty;
        LabelingPointStream labeler(empty, *ctx.callback);
        if (ctx.style.composite.label) {
            // set up label templates
            labeler.labels.primary.setTextFormat(&ctx.style.label.format);
            labeler.labels.primary.setText(ctx.style.label.text);
            labeler.labels.primary.setRotation((float)ctx.style.label.rotation.angle, ctx.style.label.rotation.absolute);
            labeler.labels.primary.setDesiredOffset(ctx.style.label.offset);
            labeler.labels.primary.setAlignment(ctx.style.label.alignment.horizontal);
            labeler.labels.primary.setVerticalAlignment(ctx.style.label.alignment.vertical);
            labeler.labels.primary.setColor(ctx.style.label.color.foreground);
            labeler.labels.primary.setBackColor(ctx.style.label.color.background);
            labeler.labels.primary.setFill(!!(ctx.style.label.color.background & 0xFF000000u));
            labeler.labels.primary.setHints(ctx.style.label.hints);
            labeler.labels.primary.setAltitudeMode(TAK::Engine::Feature::TEAM_Absolute);
            labeler.labels.primary.setMaxDrawResolution(50.0);
        }

        GLGeometryBatchBuilder::VertexWriter vw;
        vw.ctx = &ctx;
        if (ctx.style.texid) {
            if (geom.getType() == atakmap::feature::Geometry::POINT) {
                PointSpriteVertexWriter psvw;
                psvw.altmode = (altmode == TAK::Engine::Feature::TEAM_ClampToGround) ?
                    TAK::Engine::Feature::TEAM_Relative : altmode;
                ctx.impl = psvw;

                PointStream *ps = nullptr;
                PointPointStream point(static_cast<const atakmap::feature::Point &>(geom));
                RelativeAltPointStream agl(point, *ctx.callback);
                if (altmode == TAK::Engine::Feature::TEAM_Relative)
                    ps = &agl;
                else
                    ps = &point;
                // XXX - supporting legacy UX. elevation queries are roughly
                //       doubling vertex construction time. should utilize
                //       some caching mechanism (incalidate on elevation
                //       content changes and feature version changes)
                TerrainCollisionPointStream collide(*ps, *ctx.callback);
                ps = &collide;
                if (ctx.style.composite.label) {
                    labeler.reset(*ps);
                    ps = &labeler;
                }
                ps->start(vw);
                if (extrude) {
                    VertexWriterContext extrudeCtx = ctx;
                    AntiAliasVertexWriter aavw;
                    aavw.altmode = TAK::Engine::Feature::TEAM_Absolute;
                    extrudeCtx.impl = aavw;

                    extrudeCtx.style.stroke.width = 1.f;
                    // use icon color for stroke
                    extrudeCtx.style.color.stroke = extrudeCtx.style.color.fill;
                    extrudeCtx.style.color.fill.a = 0u;

                    vw.ctx = &extrudeCtx;
                    PointExtruder extruder(*ps, extrude, *extrudeCtx.callback);
                    extruder.start(vw);
                }
            }
        } else if (ctx.style.color.fill.a && ctx.style.color.stroke.a) {
            if (geom.getType() == atakmap::feature::Geometry::LINESTRING) {
                VertexWriterContext stroke = ctx;
                stroke.style.color.fill.a = 0u;
                flushPrimitives(stroke, geom, extrude, altmode);
                VertexWriterContext fill = ctx;
                fill.style.color.stroke.a = 0u;
                flushPrimitives(fill, geom, extrude, altmode);
            } else if (geom.getType() == atakmap::feature::Geometry::POLYGON) {
                // XXX - need to implement separate path for combined stroke+fill for surface polygons

                // sprite polygons should be decomposed into stroke and fill and may be rendered separately
                VertexWriterContext stroke = ctx;
                stroke.style.color.fill.a = 0u;
                flushPrimitives(stroke, geom, extrude, altmode);
                VertexWriterContext fill = ctx;
                fill.style.color.stroke.a = 0u;
                flushPrimitives(fill, geom, extrude, altmode);
            }
        } else if (ctx.style.color.fill.a) {
            PolygonVertexWriter pgvw;
            pgvw.altmode = altmode;
            ctx.impl = pgvw;
            if (geom.getType() == atakmap::feature::Geometry::LINESTRING && extrude && altmode != TAK::Engine::Feature::TEAM_ClampToGround) {
                PointStream *ps = nullptr;
                LineStringPointStream linestring(static_cast<const atakmap::feature::LineString &>(geom));
                RelativeAltPointStream agl(linestring, *ctx.callback);
                if (altmode == TAK::Engine::Feature::TEAM_Relative)
                    ps = &agl;
                else
                    ps = &linestring;
                // inject labels before quadstrip extrusion
                if (ctx.style.composite.label) {
                    labeler.reset(*ps);
                    ps = &labeler;
                }
                QuadStripPointStream qsps(*ps, extrude, *ctx.callback);
                ps = &qsps;
                ps->start(vw);
            } else if (geom.getType() == atakmap::feature::Geometry::POLYGON && extrude && altmode != TAK::Engine::Feature::TEAM_ClampToGround) {
                PointStream *ps = nullptr;
                // top/bottom faces
                {
                    //FillPolygonPointStream polygon(static_cast<const atakmap::feature::Polygon&>(geom));
                    FillPolygonPointStream polygon(static_cast<const atakmap::feature::Polygon&>(geom));
                    ps = &polygon;
                    RelativeAltPointStream agl(polygon, *ctx.callback);
                    RelativeBasePointStream base(polygon, *ctx.callback);
                    if (altmode != TAK::Engine::Feature::TEAM_Relative)
                        ps = &polygon;
                    else if (extrude < 0.0)
                        ps = &base;
                    else // extrude >= 0.0
                        ps = &agl;
                    // stream top face
                    ps->start(vw);

                    // XXX - stream bottom face
                    if (extrude > 0.0) {

                    }
                }

                // stream walls
                {
                    StrokePolygonPointStream polygon(static_cast<const atakmap::feature::Polygon&>(geom));
                    RelativeAltPointStream agl(polygon, *ctx.callback);
                    RelativeBasePointStream base(polygon, *ctx.callback);
                    if (altmode != TAK::Engine::Feature::TEAM_Relative)
                        ps = &polygon;
                    else if (extrude < 0.0)
                        ps = &base;
                    else // extrude >= 0.0
                        ps = &agl;

                    // inject labels before quadstrip extrusion
                    if (ctx.style.composite.label) {
                        labeler.reset(*ps);
                        ps = &labeler;
                    }

                    QuadStripPointStream qsps(*ps, extrude, *ctx.callback);
                    ps = &qsps;
                    ps->start(vw);
                }
            } else if (geom.getType() == atakmap::feature::Geometry::POLYGON) {
                // no extrude
                PointStream *ps = nullptr;
                FillPolygonPointStream polygon(static_cast<const atakmap::feature::Polygon &>(geom));
                RelativeAltPointStream agl(polygon, *ctx.callback);
                if (altmode == TAK::Engine::Feature::TEAM_Relative)
                    ps = &agl;
                else
                    ps = &polygon;
                // inject labels
                if (ctx.style.composite.label) {
                    labeler.reset(*ps);
                    ps = &labeler;
                }
                ps->start(vw);
            }
        } else if (ctx.style.color.stroke.a) {
            AntiAliasVertexWriter aavw;
            aavw.altmode = altmode;
            ctx.impl = aavw;
            if (geom.getType() == atakmap::feature::Geometry::LINESTRING) {
                PointStream *ps = nullptr;
                LineStringPointStream linestring(static_cast<const atakmap::feature::LineString &>(geom));
                RelativeAltPointStream agl(linestring, *ctx.callback);
                if (altmode == TAK::Engine::Feature::TEAM_Relative)
                    ps = &agl;
                else
                    ps = &linestring;
                // inject labels before extrusion
                if (ctx.style.composite.label) {
                    labeler.reset(*ps);
                    ps = &labeler;
                }
                SegmentExtruder extruder(*ps, extrude, *ctx.callback);
                if (extrude)
                    ps = &extruder;
                    
                ps->start(vw);
            } else if (geom.getType() == atakmap::feature::Geometry::POLYGON) {
                PointStream *ps = nullptr;
                StrokePolygonPointStream polygon(static_cast<const atakmap::feature::Polygon &>(geom));
                RelativeAltPointStream agl(polygon, *ctx.callback);
                RelativeBasePointStream base(polygon, *ctx.callback);
                if (altmode != TAK::Engine::Feature::TEAM_Relative)
                    ps = &polygon;
                else if (extrude < 0.0)
                    ps = &base;
                else // extrude >= 0.0
                    ps = &agl;
                // inject labels before extrusion
                if (ctx.style.composite.label) {
                    labeler.reset(*ps);
                    ps = &labeler;
                }
                SegmentExtruder extruder(*ps, extrude, *ctx.callback);
                if (extrude)
                    ps = &extruder;
                    
                ps->start(vw);
            }
        }
        return TE_Ok;
    }

    bool GLGeometryBatchBuilder_init() NOTHROWS
    {
        // layout
        layout.vertex.antiAliasedLines.position0 = GLVertexArray(3u, GL_FLOAT, false, LINE_VERTEX_SIZE, LINE_VERTEX_POSITION0_OFFSET);
        layout.vertex.antiAliasedLines.position1 = GLVertexArray(3u, GL_FLOAT, false, LINE_VERTEX_SIZE, LINE_VERTEX_POSITION1_OFFSET);
        layout.vertex.antiAliasedLines.color = GLVertexArray(4u, GL_UNSIGNED_BYTE, true, LINE_VERTEX_SIZE, LINE_VERTEX_COLOR_OFFSET);
        layout.vertex.antiAliasedLines.pattern = GLVertexArray(1u, GL_UNSIGNED_INT, false, LINE_VERTEX_SIZE, LINE_VERTEX_PATTERN_OFFSET);
        layout.vertex.antiAliasedLines.normal = GLVertexArray(1u, GL_UNSIGNED_BYTE, true, LINE_VERTEX_SIZE, LINE_VERTEX_NORMAL_OFFSET);
        layout.vertex.antiAliasedLines.halfStrokeWidth = GLVertexArray(1u, GL_UNSIGNED_BYTE, false, LINE_VERTEX_SIZE, LINE_VERTEX_HALF_STROKE_WIDTH_OFFSET);
        layout.vertex.antiAliasedLines.dir = GLVertexArray(1u, GL_UNSIGNED_BYTE, true, LINE_VERTEX_SIZE, LINE_VERTEX_DIR_OFFSET);
        layout.vertex.antiAliasedLines.factor = GLVertexArray(1u, GL_UNSIGNED_BYTE, false, LINE_VERTEX_SIZE, LINE_VERTEX_FACTOR_OFFSET);
        layout.vertex.antiAliasedLines.id = GLVertexArray(4u, GL_UNSIGNED_BYTE, GL_TRUE, LINE_VERTEX_SIZE, LINE_VERTEX_ID_OFFSET);

        layout.vertex.points.position = GLVertexArray(3u, GL_FLOAT, GL_FALSE, POINT_VERTEX_SIZE, POINT_VERTEX_POSITION_OFFSET);
        layout.vertex.points.rotation = GLVertexArray(2u, GL_SHORT, GL_TRUE, POINT_VERTEX_SIZE, POINT_VERTEX_ROTATION_OFFSET);
        layout.vertex.points.spriteBottomLeft = GLVertexArray(2u, GL_UNSIGNED_SHORT, GL_TRUE, POINT_VERTEX_SIZE, POINT_VERTEX_SPRITE_BOTTOM_LEFT_OFFSET);
        layout.vertex.points.spriteDimensions = GLVertexArray(2u, GL_UNSIGNED_SHORT, GL_TRUE, POINT_VERTEX_SIZE, POINT_VERTEX_SPRITE_DIMENSIONS_OFFSET);
        layout.vertex.points.pointSize = GLVertexArray(1u, GL_FLOAT, GL_FALSE, POINT_VERTEX_SIZE, POINT_VERTEX_POINT_SIZE_OFFSET);
        layout.vertex.points.color = GLVertexArray(4u, GL_UNSIGNED_BYTE, GL_TRUE, POINT_VERTEX_SIZE, POINT_VERTEX_COLOR_OFFSET);
        layout.vertex.points.id = GLVertexArray(4u, GL_UNSIGNED_BYTE, GL_TRUE, POINT_VERTEX_SIZE, POINT_VERTEX_ID_OFFSET);
        layout.vertex.points.absoluteRotationFlag = GLVertexArray(1u, GL_FLOAT, GL_FALSE, POINT_VERTEX_SIZE, POINT_VERTEX_ABSOLUTE_ROTATION_FLAG_OFFSET);

        layout.vertex.polygons.position = GLVertexArray(3u, GL_FLOAT, GL_FALSE, POLYGON_VERTEX_SIZE, POLYGON_VERTEX_POSITION_OFFSET);
        layout.vertex.polygons.outlineWidth = GLVertexArray(1u, GL_FLOAT, GL_FALSE, POLYGON_VERTEX_SIZE, POLYGON_VERTEX_OUTLINE_WIDTH_OFFSET);
        layout.vertex.polygons.exteriorVertex = GLVertexArray(1u, GL_FLOAT, GL_FALSE, POLYGON_VERTEX_SIZE, POLYGON_VERTEX_EXTERIOR_VERTEX_OFFSET);


        layout.vertex.polygons.color = GLVertexArray(4u, GL_UNSIGNED_BYTE, GL_TRUE, POLYGON_VERTEX_SIZE, POLYGON_VERTEX_COLOR_OFFSET);
        layout.vertex.polygons.id = GLVertexArray(4u, GL_UNSIGNED_BYTE, GL_TRUE, POLYGON_VERTEX_SIZE, POLYGON_VERTEX_ID_OFFSET);

        return true;
    }
}
