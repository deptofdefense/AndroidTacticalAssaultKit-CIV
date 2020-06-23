#ifndef ATAKMAP_RENDERER_GLMAPRENDERABLE_H_INCLUDED
#define ATAKMAP_RENDERER_GLMAPRENDERABLE_H_INCLUDED

#include <memory>

namespace atakmap
{
    namespace renderer
    {
        namespace map {
            class GLMapView;

            /**
             * Renderable content for the map.
             *
             * The renderable has 4 defined states:
             * <UL>
             *  <LI>uninitialized - this is the state that the renderable is
             *                      in immediately following construction,
             *                      prior to 'draw' being invoked, or following
             *                      the invocation of 'release', prior to a
             *                      subsequent invocation of 'draw'. The
             *                      renderable should not be holding any GL
             *                      resources in this state, nor any other
             *                      memory allocated as a result of 'draw'.
             *                      This state may be entered or exited only on
             *                      the GL thread.
             *  <LI>initialized -   this is the state that the renderable is
             *                      in following an invocation of 'draw'. The
             *                      renderable may be holding GL resources and
             *                      other memory and data structures necessary
             *                      for rendering. This state may only be
             *                      entered or exited on the GL thread.
             *  <LI>stopped -       The renderable may not communicate with its
             *                      subject object while in the 'stopped' state
             *                      but may continue to render itself with any
             *                      render specific resources allocated during
             *                      initialization. This state is always
             *                      entered following an invocation of 'stop()'
             *                      or 'release()'. Communication is defined as
             *                      invoking functions on the subject and
             *                      receiving or processing callback
             *                      notifications. This includes the processing
             *                      of callback notifications that have been
             *                      previously queued, but unexecuted, on the
             *                      GL thread.
             *  <LI>started -       The renderable is allowed communicate with
             *                      its subject object while in the 'started'
             *                      state. This state is always entered
             *                      following an invocation of 'start()' or
             *                      'draw(GLMapView)'. Communication is defined
             *                      as invoking functions on the subject and
             *                      receiving or processing callback
             *                      notifications. This includes the processing
             *                      of callback notifications that have been
             *                      previously queued, but unexecuted, on the
             *                      GL thread.
             */
            class GLMapRenderable {
            public:
                virtual ~GLMapRenderable() {};

                /**
                 * Draws the content of the renderable. If the renderable is
                 * currently in the 'released' state, initialization should
                 * occur; 'start' is implicitly invoked.
                 *
                 * <P>This function is always invoked on the GL thread.
                 */
                virtual void draw(const GLMapView *view) = 0;

                /**
                 * This function is invoked when the renderable is removed from
                 * the map; 'stop' is implicitly invoked. Any resources
                 * allocated as a result of previous invocations of 'draw' must
                 * be freed before this function returns. The 'draw' function
                 * may be subsequently invoked, in which case the renderable is
                 * reinitialized.
                 *
                 * <P>This object should be in state where it is eligible for
                 * immediate deletion when this function returns.
                 *
                 * <P>This function is ALWAYS invoked on the GL thread.
                 */
                virtual void release() = 0;

                /**
                 * This function is invoked to signal the renderable that it
                 * will be added to the map in the near future. For those
                 * renderables that observe mutable objects, an initial
                 * snapshot of the state should be obtained and any callback
                 * listeners registered.
                 * 
                 * <P>This function may be invoked from any thread.
                 *
                 * <P>This function is implicitly invoked during initialization
                 * in 'draw(GLMapView)'.
                 */
                virtual void start() = 0;

                /**
                * This function is invoked to signal the renderable that it
                * will be removed from the map in the near future. For those
                * renderables that observe mutable objects, all communication
                * must cease immediately and any queued updates to the
                * renderer should be canceled.
                *
                * <P>This function may be invoked from any thread.
                *
                * <P>This function is implicitly invoked during teardown in
                * 'release()'.
                */
                virtual void stop() = 0;
            };

            typedef std::unique_ptr<GLMapRenderable, void(*)(const GLMapRenderable *)> GLMapRenderablePtr;
        }
    }
}

#endif
