#pragma once

#include "util/MemBuffer.h"

#include "renderer/GLRenderBatch.h"
#include "renderer/map/GLMapView.h"
#include "renderer/GLRenderContext.h"
#include "renderer/feature/GLBatchLineString.h"
//TODO--#include "renderer/GLBackground_CLI.h"

namespace atakmap {
    namespace feature {
        class Polygon;
    }

    namespace renderer {
        namespace feature {
            
            class GLBatchPolygon: public GLBatchLineString {
            protected:
//TODO--                static GLBackground ^bkgrnd = nullptr;

                float fillColorR;
                float fillColorG;
                float fillColorB;
                float fillColorA;
                int fillColor = 0;
                int polyRenderMode = 0;
                atakmap::util::MemBufferT<uint16_t> indices;
                
            public:
                GLBatchPolygon(atakmap::renderer::GLRenderContext *surface);

                virtual void setStyle(atakmap::feature::Style *value) override;
                
                virtual void setGeometry(atakmap::util::MemBufferT<uint8_t> *blob, int type, int lod) override;

            public:
                virtual void setGeometry(atakmap::feature::Polygon *geom);
            
            private:
            //TODO--    static void setGeometryImpl(System::Object ^opaque);
            
            protected:
                virtual void setGeometryImpl(atakmap::util::MemBufferT<uint8_t> *blob, int type) override;

            public:
                virtual void draw(const atakmap::renderer::map::GLMapView *view, int vertices) override;

                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view) override;

            private:
                void drawFillTriangulate(const atakmap::renderer::map::GLMapView *view, const float *v, int size);

                void drawFillConvex(const atakmap::renderer::map::GLMapView *view, const float *v, int size);

                void drawFillStencil(const atakmap::renderer::map::GLMapView *view, const float *v, int size);

            protected:
                virtual void batchImpl(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch,
                                       int vertices, const float *v, size_t count) override;
            };
        }
    }
}

