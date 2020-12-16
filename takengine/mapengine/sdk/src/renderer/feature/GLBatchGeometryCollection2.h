#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYCOLLECTION2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYCOLLECTION2_H_INCLUDED

#include "port/Collection.h"
#include "renderer/feature/GLBatchGeometry2.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchGeometryCollection2 : public GLBatchGeometry2
                {
                public :
                    GLBatchGeometryCollection2(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                protected :
                    GLBatchGeometryCollection2(TAK::Engine::Core::RenderContext &surface, const int zOrder, const int collectionEntityType) NOTHROWS;
                public :
                    ~GLBatchGeometryCollection2() NOTHROWS;
                public :
                    virtual Util::TAKErr init(const int64_t feature_id, const char *name_val) NOTHROWS;
                public :
                    virtual void draw(const atakmap::renderer::map::GLMapView *view);
                public :
                    virtual void release();
                public :
                    virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view);
                public :
                    virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch);
                public :
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS;
                    virtual Util::TAKErr setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS;
                public :
                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS;
                    virtual Util::TAKErr setGeometry(const atakmap::feature::Geometry &geom) NOTHROWS;
                protected :
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS;
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &blob) NOTHROWS;
                public :
                    Util::TAKErr getChildren(Port::Collection<GLBatchGeometry2 *>::IteratorPtr &result) NOTHROWS;
                    Util::TAKErr getChildren(Port::Collection<std::shared_ptr<GLBatchGeometry2>> &value) NOTHROWS;
                private :
                    std::set<GLBatchGeometry2 *> children;
                private :
                    std::set<std::shared_ptr<GLBatchGeometry2>> childrenPtrs;
                    std::shared_ptr<const atakmap::feature::Style> style;
                    int collectionEntityType;

                    Thread::Mutex mutex;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYCOLLECTION2_H_INCLUDED
