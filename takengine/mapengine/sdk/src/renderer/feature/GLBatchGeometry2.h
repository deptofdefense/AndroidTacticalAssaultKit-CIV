#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRY2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRY2_H_INCLUDED

#include <memory>

#include "core/RenderContext.h"
#include "feature/Feature2.h"
#include "feature/Style.h"
#include "port/Platform.h"
#include "renderer/GLRenderContext.h"
#include "renderer/map/GLMapBatchable.h"
#include "thread/Mutex.h"
#include "util/DataInput2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchGeometry2 : 
                    public atakmap::renderer::map::GLMapRenderable, 
                    public atakmap::renderer::map::GLMapBatchable
                {
                protected :
                    struct ENGINE_API Updater;
                    struct ENGINE_API GeometryUpdater;
                    struct ENGINE_API NameUpdater;
                public :
                    typedef std::unique_ptr<TAK::Engine::Util::MemoryInput2, void(*)(const TAK::Engine::Util::MemoryInput2 *)> BlobPtr;
                protected:
                    TAK::Engine::Core::RenderContext &surface;

                public:
                    Port::String name;
                    int zOrder;
                    int64_t featureId;
                    int lod;
                    int subid;
                    int64_t version;
                protected:
                    GLBatchGeometry2(TAK::Engine::Core::RenderContext &surface, const int zOrder) NOTHROWS;
                public: 
                    virtual ~GLBatchGeometry2() NOTHROWS;
                public:
                    virtual Util::TAKErr init(const int64_t feature_id, const char *name_val) NOTHROWS;

                    virtual Util::TAKErr setName(const char *name_val) NOTHROWS;

                    virtual Util::TAKErr setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS = 0;
                    virtual Util::TAKErr setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS = 0;

                    virtual Util::TAKErr setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS;

                    virtual void draw(const atakmap::renderer::map::GLMapView *view) override = 0;

                    virtual void start() override;
                    virtual void stop() override;

                    virtual void release() override = 0;

                    virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view) override = 0;

                    virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch) override = 0;

                public:
                    virtual Util::TAKErr setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS = 0;
                    virtual Util::TAKErr setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS = 0;
                    virtual Util::TAKErr setNameImpl(const char *name_val) NOTHROWS;
                public :
                    virtual Util::TAKErr setGeometry(const atakmap::feature::Geometry &geom) NOTHROWS;
                private:
                    static void setNameRunnable(void *opaque) NOTHROWS;
                    static void setGeometryBlobRunnable(void *opaque) NOTHROWS;
                    static void setGeometryGeomRunnable(void *opaque) NOTHROWS;
                protected :
                    std::set<Updater *> updateQueue;
                    //Thread::Mutex queueMutex;
                    std::shared_ptr<Thread::Mutex> sharedQueueMutex;
                };

                struct ENGINE_API GLBatchGeometry2::Updater
                {
                protected:
                    Updater(GLBatchGeometry2 &owner) NOTHROWS;
                public:
                    void cancel() NOTHROWS;
                public:
                    GLBatchGeometry2 &owner;
                    //Thread::Mutex mutex;
                    std::shared_ptr<Thread::Mutex> sharedMutex;
                    bool canceled;

                    friend class GLBatchGeometryCollection2;
                };

                struct ENGINE_API GLBatchGeometry2::GeometryUpdater : GLBatchGeometry2::Updater
                {
                public :
                    GeometryUpdater(GLBatchGeometry2 &owner, BlobPtr &&blob, const int type) NOTHROWS;
                    GeometryUpdater(GLBatchGeometry2 &owner, TAK::Engine::Feature::GeometryPtr_const &&geom) NOTHROWS;
                    BlobPtr blob;
                    TAK::Engine::Feature::GeometryPtr_const geom;
                    int type;
                };

                struct ENGINE_API GLBatchGeometry2::NameUpdater : GLBatchGeometry2::Updater
                {
                public:
                    NameUpdater(GLBatchGeometry2 &owner, const char *name) NOTHROWS;
                    Port::String name;
                };

                typedef std::unique_ptr<GLBatchGeometry2, void(*)(const GLBatchGeometry2 *)> GLBatchGeometryPtr;
                typedef std::unique_ptr<const GLBatchGeometry2, void(*)(const GLBatchGeometry2 *)> GLBatchGeometryPtr_const;
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRY2_H_INCLUDED
