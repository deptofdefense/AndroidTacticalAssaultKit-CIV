#ifndef ATAKMAP_RENDERER_FEATURE_GLBATCHGEOMETRY_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GLBATCHGEOMETRY_H_INCLUDED

#include "port/Platform.h"
#include "renderer/map/GLMapRenderable.h"
#include "renderer/map/GLMapBatchable.h"

namespace atakmap {
    namespace util {
        template<class T>
        class MemBufferT;
    }
}

namespace atakmap {
    
    namespace feature {
        class ENGINE_API Style;
    }
    
    namespace renderer {
        
        class ENGINE_API GLRenderContext;
        
        namespace feature {
            
            class ENGINE_API GLBatchGeometry : public atakmap::renderer::map::GLMapRenderable,
                                    public atakmap::renderer::map::GLMapBatchable
            {
            protected:
                atakmap::renderer::GLRenderContext *surface;

                std::string name;
                int zOrder = 0;
            public:
                int64_t featureId = 0;
                int lod = 0;

            protected:
                GLBatchGeometry(atakmap::renderer::GLRenderContext *surface, int zOrder);
                virtual ~GLBatchGeometry();

            public:
                virtual void init(int64_t featureId, const char *name);

                virtual void setStyle(atakmap::feature::Style *value) = 0;

                virtual void setGeometry(atakmap::util::MemBufferT<uint8_t> *blob, int type, int lod);

                virtual void draw(atakmap::renderer::map::GLMapView *view) = 0;

                virtual void start();
                virtual void stop();

                virtual void release() = 0;

                virtual bool isBatchable(atakmap::renderer::map::GLMapView *view) = 0;

                virtual void batch(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch) = 0;

            private:
                /*TODO--class RunnableAnonymousInnerClassHelper : public atakmap::renderer::GLRenderContext::GLRunnable
                {
                private:
                    initonly GLBatchGeometry ^outerInstance;

                    atakmap::cpp_cli::util::ByteBuffer ^blob;
                    int type = 0;

                public:
                    RunnableAnonymousInnerClassHelper(GLBatchGeometry ^outerInstance, atakmap::cpp_cli::util::ByteBuffer ^blob, int type);

                    virtual void glRun(System::Object ^opaque);
                };*/

            protected:
                virtual void setGeometryImpl(atakmap::util::MemBufferT<uint8_t> *blob, int type) = 0;
                
            private:
                static void setGeometryRunnable(void *opaque);
            };
        }
    }
}

#endif
