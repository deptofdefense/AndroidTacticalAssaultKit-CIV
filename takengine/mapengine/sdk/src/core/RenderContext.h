#ifndef TAK_ENGINE_CORE_RENDERCONTEXT_H_INCLUDED
#define TAK_ENGINE_CORE_RENDERCONTEXT_H_INCLUDED

#include "core/RenderSurface.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API RenderContext
            {
            public :
                virtual ~RenderContext() NOTHROWS = 0;
            public :
                /**
                 * Returns <code>true</code> if the current thread is the thread that the
                 * context is attached to, <code>false</code> otherwise.
                 *
                 * @return  <code>true</code> if the current thread is the render thread,
                 *          <code>false</code> otherwise.
                 */
                virtual bool isRenderThread() const NOTHROWS = 0;
                /**
                 * Queues an event to be executed on the render thread during the next
                 * pump. Enqueuing an event will automatically trigger a refresh.
                 *
                 * <P>If this context is a <code>child</code> context, the runnable is
                 * queued on the <code>main</code> context's event queue.
                 *
                 * @param r The event runnable
                 */
                virtual Util::TAKErr queueEvent(void(*runnable)(void *) NOTHROWS, std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS = 0;
                /**
                 * Requests that the renderer refresh. This call is a no-op if continuous
                 * rendering is enabled.
                 *
                 * <P>If this context is a <code>child</code> context, requests a refresh
                 * on the <code>main</code> context from which it derives.
                 */
                virtual void requestRefresh() NOTHROWS = 0;
                /**
                 * Set the target frame rate. If <code>0f</code> is specified, the frame
                 * rate will not be constrained.
                 *
                 * <P>Note that the maximum possible frame rate may be constrained by the
                 * device hardware.
                 *
                 * <P>If this context is a <code>child</code> context, sets the frame rate
                 * on the <code>main</code> context from which it derives.
                 *
                 * @param rate  The target frame rate, in frames-per-second. If
                 *              <code>0f</code>, the renderer will not constrain the frame
                 *              rate
                 */
                virtual Util::TAKErr setFrameRate(const float rate) NOTHROWS = 0;
                /**
                 * Returns the current target frame rate.
                 *
                 * <P>If this context is a <code>child</code> context, returns the frame
                 * rate setting of the <code>main</code> context from which it derives.
                 *
                 * @return  The current target frame rate.
                 */
                virtual float getFrameRate() const NOTHROWS = 0;
                /**
                 * Sets whether ot not continuous rendering is enabled. When enabled, the
                 * renderer will continously render frames, at the configured frame rate.
                 * When disabled, the renderer will only render frames on request.
                 *
                 * <P>If this context is a <code>child</code> context, set the
                 * continuous render state of the <code>main</code> context from
                 * which it derives.
                 *
                 * @param enabled   <code>true</code> to enable continuous rendering,
                 *                  <code>false</code> to disable
                 */
                virtual void setContinuousRenderEnabled(const bool enabled) NOTHROWS = 0;
                /**
                 * Returns <code>true</code> if continuous rendering is enabled,
                 * <code>false</code> if disabled.
                 *
                 * <P>If this context is a <code>child</code> context, returns the
                 * continuous render state of the <code>main</code> context from
                 * which it derives.
                 *
                 * @return  <code>true</code> if continuous rendering is enabled,
                 *          <code>false</code> if disabled.
                 */
                virtual bool isContinuousRenderEnabled() NOTHROWS = 0;
                /**
                 * Returns a flag indicating whether or not this context may create a child context.
                 * @return
                 */
                virtual bool supportsChildContext() const NOTHROWS = 0;
                /**
                 * Creates a new <code>RenderContext</code> that is a child of this
                 * <code>RenderContext</code>. Graphics resources may be shared
                 * between a child context and its parent, but only in a
                 * thread-safe manner.
                 *
                 * <P>When the child context is no longer needed,
                 * {@link #destroyChildContext(RenderContext)} should be invoked.
                 *
                 * @return  The child context or <code>null</code> if this <code>RenderContext</code> does not support child contexts.
                 */
                virtual Util::TAKErr createChildContext(std::unique_ptr<RenderContext, void(*)(const RenderContext *)> &value) NOTHROWS = 0;
                /**
                 * Returns <code>true</code> if the context is currently attached to a thread, <code>false</code> otherwise.
                 *
                 * <P>Note: This method will always return <code>true</code> for a <I>main</I> context.
                 * @return
                 */
                virtual bool isAttached() const NOTHROWS = 0;
                /**
                 * Attaches the context to the current thread. This method may
                 * detach the context from another thread if it is currently
                 * attached.
                 *
                 * <P>Note: A <I>main</I> context may not be attached to any thread
                 * other than the main rendering thread.
                 *
                 * @return  <code>true</code> if the context is attached to the
                 *          current thread when the method returns,
                 *          <code>false</code> otherwise.
                 */
                virtual bool attach() NOTHROWS = 0;
                /**
                 * Detaches the context from its currently attached thread.
                 *
                 * <P>Note: A <I>main</I> context may not be detached.
                 *
                 * @return
                 */
                virtual bool detach() NOTHROWS = 0;
                /**
                 * Returns <code>true</code> if the context is a <I>main</I>
                 * context. A <I>main</I> context is always attached to a thread,
                 * has a render surface and may not be detached.
                 *
                 * @return
                 */
                virtual bool isMainContext() const NOTHROWS = 0;
                /**
                 * Returns the render surface associated with the context.
                 *
                 * <P>If this context is a <code>child</code> context, returns the
                 * surface associated with the <code>main</code> context from which it
                 * derives.
                 * @return
                 */
                virtual RenderSurface *getRenderSurface() const NOTHROWS = 0;
            };

            typedef std::unique_ptr<RenderContext, void(*)(const RenderContext *)> RenderContextPtr;
        }
    }
}

#endif
