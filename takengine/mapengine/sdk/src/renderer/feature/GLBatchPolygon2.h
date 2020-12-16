#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOLYGON2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOLYGON2_H_INCLUDED

#include <cstdint>

#include "feature/Polygon.h"
#include "renderer/GLRenderBatch.h"
#include "renderer/map/GLMapView.h"
#include "renderer/GLRenderContext.h"
#include "renderer/feature/GLBatchLineString2.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer 
        {
            namespace Feature
            {
                class ENGINE_API GLBatchPolygon2 :
                    public GLBatchLineString2
                {
                public:
                    float fillColorR = 0;
                    float fillColorG = 0;
                    float fillColorB = 0;
                    float fillColorA = 0;
                    int fillColor = 0;
                    int polyRenderMode = 0;
                    Util::array_ptr<uint16_t> indices;
                    std::size_t numIndices;
                    std::size_t indicesLength;
                public:
                    GLBatchPolygon2(TAK::Engine::Core::RenderContext &surface) NOTHROWS;

                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS override;
                    virtual Util::TAKErr setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS override;

                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS override;

                public :
                    virtual Util::TAKErr setGeometry(const atakmap::feature::Polygon &geom) NOTHROWS;
                protected:
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS override;
                protected:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS override;

                public:
                    virtual Util::TAKErr draw(const atakmap::renderer::map::GLMapView *view, const int vertices_type) NOTHROWS override;

                    virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view) override;

                private:
                    Util::TAKErr drawFillTriangulate(const atakmap::renderer::map::GLMapView *view, const float *v, const int size) NOTHROWS;

                    Util::TAKErr drawFillConvex(const atakmap::renderer::map::GLMapView *view, const float *v, const int size) NOTHROWS;

                    Util::TAKErr drawFillStencil(const atakmap::renderer::map::GLMapView *view, const float *v, const int size) NOTHROWS;

                protected:
                    virtual Util::TAKErr batchImpl(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, const int vertices_type, const float *v) NOTHROWS override;
                };

                typedef std::unique_ptr<GLBatchPolygon2, void(*)(const GLBatchGeometry2 *)> GLBatchPolygonPtr;
                typedef std::unique_ptr<const GLBatchPolygon2, void(*)(const GLBatchGeometry2 *)> GLBatchPolygonPtr_const;
            }
        }
    }
}


#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOLYGON2_H_INCLUDED
