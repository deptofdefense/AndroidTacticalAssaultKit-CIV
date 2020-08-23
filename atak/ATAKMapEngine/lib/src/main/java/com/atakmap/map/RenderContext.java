package com.atakmap.map;

public interface RenderContext {
    /**
     * Returns <code>true</code> if the current thread is the thread that the
     * context is attached to, <code>false</code> otherwise.
     *
     * @return  <code>true</code> if the current thread is the render thread,
     *          <code>false</code> otherwise.
     */
    public boolean isRenderThread();
    /**
     * Queues an event to be executed on the render thread during the next
     * pump. Enqueuing an event will automatically trigger a refresh.
     *
     * <P>If this context is a <code>child</code> context, the runnable is
     * queued on the <code>main</code> context's event queue.
     *
     * @param r The event runnable
     */
    public void queueEvent(Runnable r);
    /**
     * Requests that the renderer refresh. This call is a no-op if continuous
     * rendering is enabled.
     *
     * <P>If this context is a <code>child</code> context, requests a refresh
     * on the <code>main</code> context from which it derives.
     */
    public void requestRefresh();

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
    public void setFrameRate(float rate);

    /**
     * Returns the current target frame rate.
     *
     * <P>If this context is a <code>child</code> context, returns the frame
     * rate setting of the <code>main</code> context from which it derives.
     *
     * @return  The current target frame rate.
     */
    public float getFrameRate();

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
    public void setContinuousRenderEnabled(boolean enabled);

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
    public boolean isContinuousRenderEnabled();


    /**
     * Returns a flag indicating whether or not this context may create a child context.
     * @return
     */
    public boolean supportsChildContext();

    /**
     * Creates a new <code>RenderContext</code> that is a child of this <code>RenderContext</code>. Graphics resources may be shared between a child context and its parent, but only in a thread-safe manner.
     *
     * <P>When the child context is no longer needed, {@link #destroyChildContext(RenderContext)} should be invoked.
     *
     * @return  The child context or <code>null</code> if this <code>RenderContext</code> does not support child contexts.
     */
    public RenderContext createChildContext();

    /**
     * Destroys the specified child context created by this <code>RenderContext</code>.
     *
     * @param child A child context created by this <code>RenderContext</code>
     */
    public void destroyChildContext(RenderContext child);

    /**
     * Returns <code>true</code> if the context is currently attached to a thread, <code>false</code> otherwise.
     *
     * <P>Note: This method will always return <code>true</code> for a <I>main</I> context.
     * @return
     */
    public boolean isAttached();

    /**
     * Attaches the context to the current thread. This method may detach the context from another thread if it is currently attached.
     *
     * <P>Note: A <I>main</I> context may not be attached to any thread other than the main rendering thread.
     *
     * @return  <code>true</code> if the context is attached to the current thread when the method returns, <code>false</code> otherwise.
     */
    public boolean attach();

    /**
     * Detaches the context from its currently attached thread.
     *
     * <P>Note: A <I>main</I> context may not be detached.
     *
     * @return
     */
    public boolean detach();

    /**
     * Returns <code>true</code> if the context is a <I>main</I> context. A <I>main</I> context is always attached to a thread, has a render surface and may not be detached.
     * @return
     */
    public boolean isMainContext();

    /**
     * Returns the render surface associated with the context.
     *
     * <P>If this context is a <code>child</code> context, returns the
     * surface associated with the <code>main</code> context from which it
     * derives.
     * @return
     */
    public RenderSurface getRenderSurface();
}
