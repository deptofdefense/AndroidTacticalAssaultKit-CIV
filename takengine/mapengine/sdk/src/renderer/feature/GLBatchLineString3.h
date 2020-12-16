#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHLINESTRING3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHLINESTRING3_H_INCLUDED

#include "feature/LineString.h"
#include "feature/AltitudeMode.h"
#include "renderer/feature/GLBatchGeometry3.h"
#include "util/Memory.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Feature
            {
                class GLBatchLineString3 : public GLBatchGeometry3
                {
                public :
                    struct Stroke
                    {
                        Stroke() NOTHROWS;
                        Stroke(const float width, const unsigned int color) NOTHROWS;
                        Stroke(const float width, const unsigned int color, const GLsizei factor, const GLushort pattern) NOTHROWS;

                        float width;
                        struct {
                            float r;
                            float g;
                            float b;
                            float a;
                            unsigned int argb;
                        } color;
                        GLushort pattern;
                        GLsizei factor;
                    };
                private:
                    const char *TAG = "GLLineString";

                    static double cachedNominalUnitSize;
                    static double cachedNominalUnitSizeSrid;

                public:
                    Util::array_ptr<double> points;
                protected :
                    std::size_t pointsLength;
                public :
                    Util::array_ptr<float> vertices;
                protected :
                    std::size_t verticesLength;
                public :
                    Util::array_ptr<float> projectedVertices;
                    /** All projected vertices are relative to this position */
                    TAK::Engine::Math::Point2<double> projectedCentroid;
                public :
                    std::size_t projectedVerticesLength;
                    int projectedVerticesSrid;
                    int projectedVerticesSize;
                    double projectedNominalUnitSize;
                public :
                    std::size_t numPoints;

                    std::vector<Stroke> stroke;
                    int drawVersion;

                    atakmap::feature::Envelope mbb;
                    atakmap::feature::Envelope screen_mbb;

                protected:
                    double tessellationThreshold;
                public:
                    GLBatchLineString3(TAK::Engine::Core::RenderContext &surface);

                protected:
                    GLBatchLineString3(TAK::Engine::Core::RenderContext &surface, const int zOrder);

                public:
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS override;
                    virtual Util::TAKErr setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS override;
                public :
                    /**
                        * Sets the line tessellation threshold. When the
                        * geometry is set, any segment that exceeds the
                        * threshold will be subdivided such that no
                        * subdivision exceeds the threshold. A value of
                        * zero disables tessellation.
                        */
                    Util::TAKErr setTessellationThreshold(double threshold) NOTHROWS;
                    double getTessellationThreshold() NOTHROWS;
                protected:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS;

                public:
                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS override;

                protected:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS override;

                public :
                    virtual Util::TAKErr setGeometry(const atakmap::feature::LineString &geom) NOTHROWS;
                protected :
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS override;

                public:
                    virtual Util::TAKErr projectVertices(const float **result, const TAK::Engine::Renderer::Core::GLMapView2 &view, const int vertices_type) NOTHROWS;

                public:
                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass) NOTHROWS override;

                    /// <summary>
                    /// Draws the linestring using vertices in the projected map space.
                    /// </summary>
                    /// <param name="view"> </param>
                    virtual Util::TAKErr draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, const int vertices_type) NOTHROWS;

                protected:
                    virtual Util::TAKErr drawImpl(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, const float *v, const int size) NOTHROWS;

                public:
                    virtual void release() NOTHROWS override;

                    virtual Util::TAKErr batch(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS override;

                    virtual Util::TAKErr batch(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, TAK::Engine::Renderer::GLRenderBatch2 &batch, const int vertices_type) NOTHROWS;

                    /// <summary>
                    /// Adds the linestring to the batch using the specified pre-projected
                    /// vertices.
                    /// </summary>
                    /// <param name="view"> </param>
                    /// <param name="batch"> </param>
                    /// <param name="vertices_type"> </param>
                    /// <param name="v"> </param>
                protected:
                    virtual Util::TAKErr batchImpl(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, TAK::Engine::Renderer::GLRenderBatch2 &batch, const int vertices_type, const float *v) NOTHROWS;
                protected :
                    virtual Util::TAKErr pushStyle(const atakmap::feature::Style& style) NOTHROWS;
                };

                typedef std::unique_ptr<GLBatchLineString3, void(*)(const GLBatchGeometry3 *)> GLBatchLineString3Ptr;
                typedef std::unique_ptr<const GLBatchLineString3, void(*)(const GLBatchGeometry3 *)> GLBatchLineString3Ptr_const;

            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHLINESTRING2_H_INCLUDED
