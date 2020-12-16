#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYCOLLECTION3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYCOLLECTION3_H_INCLUDED

#include "port/Collection.h"
#include "renderer/feature/GLBatchGeometry3.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchGeometryCollection3 : public GLBatchGeometry3
                {
                public :
                    GLBatchGeometryCollection3(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                protected :
                    GLBatchGeometryCollection3(TAK::Engine::Core::RenderContext &surface, const int zOrder, const int collectionEntityType) NOTHROWS;
                public :
                    ~GLBatchGeometryCollection3() NOTHROWS;
                public :
                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass) NOTHROWS override;
                public :
                    virtual void release() NOTHROWS override;
                public :
                    virtual Util::TAKErr batch(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS override;
                public :
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS override;
                    virtual Util::TAKErr setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS override;
                public :
                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS override;
                    virtual Util::TAKErr setGeometry(const atakmap::feature::Geometry &geom) NOTHROWS override;
                protected :
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS override;
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &blob) NOTHROWS override;
                    virtual Util::TAKErr setNameImpl(const char *name_val) NOTHROWS override;
                public :
                    Util::TAKErr getChildren(Port::Collection<GLBatchGeometry3 *>::IteratorPtr &result) NOTHROWS;
                    Util::TAKErr getChildren(Port::Collection<std::shared_ptr<GLBatchGeometry3>> &value) NOTHROWS;
                private :
                    std::set<GLBatchGeometry3 *> children;
                private :
                    std::set<std::shared_ptr<GLBatchGeometry3>> childrenPtrs;
                    std::shared_ptr<const atakmap::feature::Style> style;
                    int collectionEntityType;

                    Thread::Mutex mutex;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYCOLLECTION2_H_INCLUDED
