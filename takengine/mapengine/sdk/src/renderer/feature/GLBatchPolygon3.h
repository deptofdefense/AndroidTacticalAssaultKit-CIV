#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOLYGON3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOLYGON3_H_INCLUDED

#include <cstdint>

#include "feature/Polygon.h"
#include "renderer/GLRenderBatch.h"
#include "renderer/map/GLMapView.h"
#include "renderer/feature/GLBatchLineString3.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer 
        {
            namespace Feature
            {
                class GLBatchPolygon3 :
                    public GLBatchLineString3
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
                    GLBatchPolygon3(TAK::Engine::Core::RenderContext &surface) NOTHROWS;

                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS override;
                    virtual Util::TAKErr setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS override;

                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS override;

                public :
                    virtual Util::TAKErr setGeometry(const atakmap::feature::Polygon &geom) NOTHROWS;
                protected:
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS override;
                protected:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS override;

                public:
                    virtual Util::TAKErr draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, const int vertices_type) NOTHROWS override;
                private:
                    Util::TAKErr drawFillTriangulate(const TAK::Engine::Renderer::Core::GLMapView2 &view, const float *v, const int size) NOTHROWS;

                    Util::TAKErr drawFillConvex(const TAK::Engine::Renderer::Core::GLMapView2 &view, const float *v, const int size) NOTHROWS;

                    Util::TAKErr drawFillStencil(const TAK::Engine::Renderer::Core::GLMapView2 &view, const float *v, const int size) NOTHROWS;

                protected:
                    virtual Util::TAKErr batchImpl(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, TAK::Engine::Renderer::GLRenderBatch2 &batch, const int vertices_type, const float *v) NOTHROWS override;
                };

                typedef std::unique_ptr<GLBatchPolygon3, void(*)(const GLBatchGeometry3 *)> GLBatchPolygon3Ptr;
                typedef std::unique_ptr<const GLBatchPolygon3, void(*)(const GLBatchGeometry3 *)> GLBatchPolygon3Ptr_const;
            }
        }
    }
}


#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHPOLYGON2_H_INCLUDED
