#ifndef TAK_ENGINE_RENDERER_CORE_GLASYNCHRONOUSMAPRENDERHABLE3_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLASYNCHRONOUSMAPRENDERHABLE3_H_INCLUDED

#include <memory>
#include <atomic>

#include "core/GeoPoint2.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/core/controls/SurfaceRendererControl.h"
#include "thread/Monitor.h"
#include "thread/RWMutex.h"
#include "thread/Thread.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {

                class ENGINE_API GLAsynchronousMapRenderable3 : public virtual GLMapRenderable2
                {
                public:
                    class ENGINE_API QueryContext;
                protected:
                    class ENGINE_API WorkerThread;
                protected :
                    typedef std::unique_ptr<QueryContext, void(*)(const QueryContext *)> QueryContextPtr;
                protected:
                    GLAsynchronousMapRenderable3() NOTHROWS;
                protected :
                    virtual ~GLAsynchronousMapRenderable3() NOTHROWS;
                protected :
                    virtual Util::TAKErr createQueryContext(QueryContextPtr &value) NOTHROWS = 0;
                    virtual Util::TAKErr resetQueryContext(QueryContext &pendingData) NOTHROWS = 0;
                private:
                    /**
                     * Returns the render list for the current draw pump. Only ever invoked
                     * from GL thread in draw method while holding instance mutex.
                     * 
                     * @return  TE_Ok on success, TE_Done if no renderables at this
                     *          time; other codes on error
                     */                
                    virtual Util::TAKErr getRenderables(Port::Collection<GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS = 0;
                protected :
                    // Called by asynchronous processing thread - Produce the list
                    // of current renderables to be later obtained by getCurrentRenderables()
                    // on the rendering thread.
                    virtual Util::TAKErr updateRenderableLists(QueryContext &pendingData) NOTHROWS = 0;

                    // Called on rendering thread while holding mutex during release()
                    // to notify subclasses that the worker thread has been terminated
                    // and that any rendering items should be released at this time.
                    virtual Util::TAKErr releaseImpl() NOTHROWS;

                    virtual Util::TAKErr query(QueryContext &result, const GLGlobeBase::State &state)  NOTHROWS = 0;

                    virtual int getBackgroundThreadPriority() const NOTHROWS;

                    virtual Util::TAKErr getBackgroundThreadName(TAK::Engine::Port::String &value) NOTHROWS;

                    virtual bool shouldQuery() NOTHROWS;
                    virtual bool shouldCancel() NOTHROWS;
                    virtual void initImpl(const GLGlobeBase &view) NOTHROWS;
                    void invalidateNoSync() NOTHROWS;
                    void invalidate() NOTHROWS;

                public: // GLMapRenderable
                    virtual void draw(const GLGlobeBase &view, const int renderPass) NOTHROWS override;
                    virtual int getRenderPass() NOTHROWS override = 0;
                    virtual void release() NOTHROWS override;
                    virtual void start() NOTHROWS override = 0;
                    virtual void stop() NOTHROWS override = 0;
                protected :
                    GLGlobeBase::State prepared_state_;
                    GLGlobeBase::State target_state_;
                private :
                    TAK::Engine::Thread::ThreadPtr thread_;
                    std::unique_ptr<WorkerThread> background_worker_;
                protected :
                    Controls::SurfaceRendererControl *surface_ctrl_;
                    bool initialized_;
                    bool servicing_request_;
                    bool invalid_;
                    std::atomic<bool> cancelled_;

                    TAK::Engine::Thread::Monitor monitor_;
                    TAK::Engine::Thread::RWMutex renderables_mutex_;
                };

                class ENGINE_API GLAsynchronousMapRenderable3::QueryContext
                {
                protected:
                    virtual ~QueryContext() = 0;
                };

                class ENGINE_API GLAsynchronousMapRenderable3::WorkerThread
                {
                private:
                    GLAsynchronousMapRenderable3 &owner_;
                    QueryContext *pending_data_;

                public:
                    WorkerThread(GLAsynchronousMapRenderable3 &owner);
                private:
                    static void* asyncRun(void *self);
                    void asyncRunImpl();
                private:
                    friend class GLAsynchronousMapRenderable3;
                };

            }
        }
    }
}

#endif
