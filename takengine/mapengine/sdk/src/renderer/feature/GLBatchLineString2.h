#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHLINESTRING2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHLINESTRING2_H_INCLUDED

#include "feature/LineString.h"
#include "port/Platform.h"
#include "renderer/feature/GLBatchGeometry2.h"
#include "renderer/map/GLMapView.h"
#include "util/Memory.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Feature
            {
                class ENGINE_API GLBatchLineString2 : public GLBatchGeometry2
                {

                private:
                    const char *TAG = "GLLineString";

                    static double cachedNominalUnitSize;
                    static double cachedNominalUnitSizeSrid;

                public:
                    Util::array_ptr<float> points;
                protected :
                    std::size_t pointsLength;
                public :
                    Util::array_ptr<float> vertices;
                protected :
                    std::size_t verticesLength;
                public :
                    Util::array_ptr<float> projectedVertices;
                public :
                    std::size_t projectedVerticesLength;
                    int projectedVerticesSrid;
                    int projectedVerticesSize;
                    double projectedNominalUnitSize;
                public :
                    std::size_t numPoints;

                    float strokeWidth;
                    float strokeColorR;
                    float strokeColorG;
                    float strokeColorB;
                    float strokeColorA;
                    int strokeColor;

                    atakmap::feature::Envelope mbb;
                protected:
                    double tessellationThreshold;
                public:
                    GLBatchLineString2(TAK::Engine::Core::RenderContext &surface);

                protected:
                    GLBatchLineString2(TAK::Engine::Core::RenderContext &surface, const int zOrder);

                public:
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS override;
                    virtual Util::TAKErr setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS override;
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
                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS;

                protected:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS override;

                public :
                    virtual Util::TAKErr setGeometry(const atakmap::feature::LineString &geom) NOTHROWS;
                protected :
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS override;

                public:
                    virtual Util::TAKErr projectVertices(const float **result, const atakmap::renderer::map::GLMapView *view, const int vertices_type) NOTHROWS;

                public:
                    virtual void draw(const atakmap::renderer::map::GLMapView *view) override;

                    /// <summary>
                    /// Draws the linestring using vertices in the projected map space.
                    /// </summary>
                    /// <param name="view"> </param>
                    virtual Util::TAKErr draw(const atakmap::renderer::map::GLMapView *view, const int vertices_type) NOTHROWS;

                protected:
                    virtual Util::TAKErr drawImpl(const atakmap::renderer::map::GLMapView *view, const float *v, const int size) NOTHROWS;

                public:
                    virtual void release() override;

                    virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view) override;

                    virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch) override;

                    virtual Util::TAKErr batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, const int vertices_type) NOTHROWS;

                    /// <summary>
                    /// Adds the linestring to the batch using the specified pre-projected
                    /// vertices.
                    /// </summary>
                    /// <param name="view"> </param>
                    /// <param name="batch"> </param>
                    /// <param name="vertices_type"> </param>
                    /// <param name="v"> </param>
                protected:
                    virtual Util::TAKErr batchImpl(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, const int vertices_type, const float *v) NOTHROWS;
                };

                typedef std::unique_ptr<GLBatchLineString2, void(*)(const GLBatchGeometry2 *)> GLBatchLineStringPtr;
                typedef std::unique_ptr<const GLBatchLineString2, void(*)(const GLBatchGeometry2 *)> GLBatchLineStringPtr_const;

            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHLINESTRING2_H_INCLUDED
