#ifndef ATAKMAP_RENDERER_GLASYNCMAPRENDERHABLE_H_INCLUDED
#define ATAKMAP_RENDERER_GLASYNCMAPRENDERHABLE_H_INCLUDED

#include <list>
#include <sstream>

#include "core/GeoPoint.h"
#include "renderer/map/GLMapRenderable.h"
#include "renderer/map/GLMapView.h"
#include "thread/Mutex.h"
#include "thread/Cond.h"
#include "thread/Lock.h"
#include "thread/Thread.h"

namespace atakmap
{
    namespace port {
        template<class T>
        class Iterator;
    }

    namespace renderer
    {
        namespace map {

            class GLAsynchronousMapRenderable : public virtual GLMapRenderable
            {
            protected:
                struct ViewState;
                class QueryContext;
                class WorkerThread;

                friend class WorkerThread;

                ViewState *preparedState;
                ViewState *targetState;

                TAK::Engine::Thread::ThreadPtr thread;
                WorkerThread *backgroundWorker;

                bool initialized;
                bool servicingRequest;
                bool invalid;

                TAK::Engine::Thread::Mutex mutex;
                TAK::Engine::Thread::CondVar cv;


                GLAsynchronousMapRenderable();
                virtual ~GLAsynchronousMapRenderable();

                virtual void resetQueryContext(QueryContext *pendingData) = 0;
                virtual void releaseQueryContext(QueryContext *pendingData) = 0;
                virtual QueryContext *createQueryContext() = 0;

                // CHANGED FROM JAVA:
                // Retrieve currently valid set of renderable objects
                // Only ever called on the rendering thread with the
                // class mutex held.  Values herein need only be valid for the
                // duration of the the current mutex hold and rendering pump
                virtual atakmap::port::Iterator<GLMapRenderable *> *getRenderablesIterator() = 0;
                virtual void releaseRenderablesIterator(atakmap::port::Iterator<GLMapRenderable *> *iter) = 0;

                // Called by asynchronous processing thread - Produce the list
                // of current renderables to be later obtained by getCurrentRenderables()
                // on the rendering thread.
                virtual bool updateRenderableLists(QueryContext *pendingData) = 0;

                // Called on rendering thread while holding mutex during release()
                // to notify subclasses that the worker thread has been terminated
                // and that any rendering items should be released at this time.
                virtual void releaseImpl() {};

                virtual void query(const ViewState *state, QueryContext *result) = 0;

                virtual ViewState *newViewStateInstance();
                void releaseViewStateInstance(ViewState *vs);

                virtual int getBackgroundThreadPriority();

                virtual void setBackgroundThreadName(WorkerThread *worker);

                bool checkState();
                virtual void initImpl(const GLMapView *view);
                void invalidateNoSync();
                void invalidate();

            public:
                virtual void draw(const GLMapView *view);
                virtual void release();

            };

            struct GLAsynchronousMapRenderable::ViewState
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
                core::GeoPoint upperLeft;
                core::GeoPoint upperRight;
                core::GeoPoint lowerRight;
                core::GeoPoint lowerLeft;
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
            public:
                virtual ~ViewState();
            public:
                virtual void set(const GLMapView *view);
                virtual void copy(const ViewState *view);
            };

            class GLAsynchronousMapRenderable::QueryContext
            {
            public:
                virtual ~QueryContext();
            };

            class GLAsynchronousMapRenderable::WorkerThread
            {
            private:
                GLAsynchronousMapRenderable *owner_;
                QueryContext *pending_data_;

            public:
                WorkerThread(GLAsynchronousMapRenderable *owner);
            public :
                void setName(const char *name);
            private:
                static void* asyncRun(void *self);
                void asyncRunImpl();
            private :
                const char *name_;
            private :
                friend class GLAsynchronousMapRenderable;
            };

        }
    }
}

#endif
