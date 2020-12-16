#ifndef TAK_ENGINE_RENDERER_GLASYNCHRONOUSMAPRENDERHABLE2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLASYNCHRONOUSMAPRENDERHABLE2_H_INCLUDED

#include <list>
#include <memory>
#include <sstream>

#include "core/GeoPoint.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "renderer/map/GLMapRenderable.h"
#include "renderer/map/GLMapView.h"
#include "thread/Mutex.h"
#include "thread/Cond.h"
#include "thread/Lock.h"
#include "thread/Thread.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {

                class GLAsynchronousMapRenderable2 : public virtual atakmap::renderer::map::GLMapRenderable
                {
                public:
                    struct ViewState;
                    class QueryContext;
                protected:
                    class WorkerThread;
                protected :
                    typedef std::unique_ptr<ViewState, void(*)(const ViewState *)> ViewStatePtr;
                    typedef std::unique_ptr<QueryContext, void(*)(const QueryContext *)> QueryContextPtr;
                protected:
                    GLAsynchronousMapRenderable2() NOTHROWS;
                protected :
                    virtual ~GLAsynchronousMapRenderable2() NOTHROWS;
                protected :
                    virtual Util::TAKErr createQueryContext(QueryContextPtr &value) NOTHROWS = 0;
                    virtual Util::TAKErr resetQueryContext(QueryContext &pendingData) NOTHROWS = 0;
                private:
                    // CHANGED FROM JAVA:
                    // Retrieve currently valid set of renderable objects
                    // Only ever called on the rendering thread with the
                    // class mutex held.  Values herein need only be valid for the
                    // duration of the the current mutex hold and rendering pump

                    /**
                     * Returns the render list for the current draw pump. Only ever invoked
                     * from GL thread in draw method while holding instance mutex.
                     * 
                     * @return  TE_Ok on success, TE_Done if no renderables at this
                     *          time; other codes on error
                     */                
                    virtual Util::TAKErr getRenderables(Port::Collection<atakmap::renderer::map::GLMapRenderable *>::IteratorPtr &iter) NOTHROWS = 0;
                protected :
                    // Called by asynchronous processing thread - Produce the list
                    // of current renderables to be later obtained by getCurrentRenderables()
                    // on the rendering thread.
                    virtual Util::TAKErr updateRenderableLists(QueryContext &pendingData) NOTHROWS = 0;

                    // Called on rendering thread while holding mutex during release()
                    // to notify subclasses that the worker thread has been terminated
                    // and that any rendering items should be released at this time.
                    virtual Util::TAKErr releaseImpl() NOTHROWS;

                    virtual Util::TAKErr query(QueryContext &result, const ViewState &state)  NOTHROWS = 0;

                    virtual Util::TAKErr newViewStateInstance(ViewStatePtr &value) NOTHROWS;

                    virtual int getBackgroundThreadPriority() const NOTHROWS;

                    virtual Util::TAKErr setBackgroundThreadName(WorkerThread &worker) NOTHROWS;

                    virtual bool shouldQuery() NOTHROWS;
                    virtual void initImpl(const atakmap::renderer::map::GLMapView *view) NOTHROWS;
                    void invalidateNoSync() NOTHROWS;
                    void invalidate() NOTHROWS;

                public: // GLMapRenderable
                    virtual void draw(const atakmap::renderer::map::GLMapView *view);
                    virtual void release();
                protected :
                    ViewStatePtr preparedState;
                    ViewStatePtr targetState;
                private :
                    TAK::Engine::Thread::ThreadPtr thread;
                    std::unique_ptr<WorkerThread> backgroundWorker;
                protected :
                    bool initialized;
                    bool servicingRequest;
                    bool invalid;

                    TAK::Engine::Thread::Mutex mutex;
                    TAK::Engine::Thread::CondVar cv;

                };

                struct GLAsynchronousMapRenderable2::ViewState
                {
                    double drawMapScale;
                    double drawMapResolution;
                    double drawLat;
                    double drawLng;
                    double drawRotation;
                    double animationFactor;
                    int drawVersion;
                    int drawSrid;
                    double westBound;
                    double southBound;
                    double northBound;
                    double eastBound;
                    atakmap::core::GeoPoint upperLeft;
                    atakmap::core::GeoPoint upperRight;
                    atakmap::core::GeoPoint lowerRight;
                    atakmap::core::GeoPoint lowerLeft;
                    double targetMapScale;
                    double targetLat;
                    double targetLng;
                    double targetRotation;
                    int _left;
                    int _right;
                    int _top;
                    int _bottom;
                    float focusx, focusy;
                    bool settled;
                public:
                    ViewState();
                    virtual ~ViewState();
                public:
                    virtual void set(const atakmap::renderer::map::GLMapView &view);
                    virtual void copy(const ViewState &view);
                };

                class GLAsynchronousMapRenderable2::QueryContext
                {
                protected:
                    virtual ~QueryContext() = 0;
                };

                class GLAsynchronousMapRenderable2::WorkerThread
                {
                private:
                    GLAsynchronousMapRenderable2 &owner_;
                    QueryContext *pending_data_;

                public:
                    WorkerThread(GLAsynchronousMapRenderable2 &owner);
                public:
                    void setName(const char *name);
                private:
                    static void* asyncRun(void *self);
                    void asyncRunImpl();
                private:
                    Port::String name_;

                    friend class GLAsynchronousMapRenderable2;
                };

            }
        }
    }
}

#endif
