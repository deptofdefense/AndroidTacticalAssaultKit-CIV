#ifndef ATAKMAP_RENDERER_FEATURE_GL_BATCH_LINE_STRING_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GL_BATCH_LINE_STRING_H_INCLUDED


#include "renderer/feature/GLBatchGeometry.h"
#include "util/MemBuffer.h"

namespace atakmap {

    namespace feature {
        class LineString;
    }

    namespace renderer {
        namespace feature {
            
            class GLBatchGeometryRenderer;
            
            class GLBatchLineString : public GLBatchGeometry {
            private:
                //literal System::String *TAG = "GLLineString";

                static double cachedNominalUnitSize;
                static int cachedNominalUnitSizeSrid;

            protected:
                friend GLBatchGeometryRenderer;
                atakmap::util::MemBufferT<float> points;
                atakmap::util::MemBufferT<float> vertices;
                atakmap::util::MemBufferT<float> projectedVertices;
                int64_t projectedVerticesPtr = 0;
                int projectedVerticesSrid = 0;
                int projectedVerticesSize = 0;
                double projectedNominalUnitSize = 0;

                int numPoints = 0;

                int targetNumPoints = 0;

                float strokeWidth = 0;
                float strokeColorR = 0;
                float strokeColorG = 0;
                float strokeColorB = 0;
                float strokeColorA = 0;
                int strokeColor = 0;

            public:
                GLBatchLineString(atakmap::renderer::GLRenderContext *surface);
                
                inline int getNumPoints() const { return this->numPoints; }
                inline int getStrokeColor() const  { return this->strokeColor; }
                inline int getStrokeWidth() const { return this->strokeWidth; }

            protected:
                GLBatchLineString(atakmap::renderer::GLRenderContext *surface, int zOrder);

            public:
                virtual void setStyle(atakmap::feature::Style *style) override;
                
            protected:
                virtual void setGeometryImpl(atakmap::util::MemBufferT<uint8_t> *blob, int type, int lod);

            public:
                virtual void setGeometry(atakmap::util::MemBufferT<uint8_t> *blob, int type, int lod) override;

            protected:
                virtual void setGeometryImpl(atakmap::util::MemBufferT<uint8_t> *blob, int type) override;

            public :
                virtual void setGeometry(atakmap::feature::LineString *geom);

            protected:
                //TODO--static void setGeometryImpl(System::Object *opaque);

            //internal:
                virtual std::pair<float *, size_t> projectVertices(const atakmap::renderer::map::GLMapView *view, int vertices);

            public:
                virtual void draw(const atakmap::renderer::map::GLMapView *view) override;

                /// <summary>
                /// Draws the linestring using vertices in the projected map space.
                /// </summary>
                /// <param name="view"> </param>
                virtual void draw(const atakmap::renderer::map::GLMapView *view, int vertices);

            protected:
                virtual void drawImpl(const atakmap::renderer::map::GLMapView *view, const float *v, int size);

            public:
                virtual void release() override;

                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view) override;

                virtual void batch(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch) override;

                void batch(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch, int vertices);

                /// <summary>
                /// Adds the linestring to the batch using the specified pre-projected
                /// vertices.
                /// </summary>
                /// <param name="view"> </param>
                /// <param name="batch"> </param>
                /// <param name="vertices"> </param>
                /// <param name="v"> </param>
            protected:
                virtual void batchImpl(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch, int vertices, const float *v, size_t count);
            };
        }
    }
}

#endif
