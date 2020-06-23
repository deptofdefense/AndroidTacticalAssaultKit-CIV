
package com.atakmap.map.opengl;

/**
 * The base interface for renderable content in the map. The general pattern
 * followed in the map engine is that the renderable is a
 * <I>content observer</I> and not a <I>content owner</I>. This means that the
 * renderable will retain a reference to some subject and render the content for
 * that subject. The renderable will not assume ownership of the subject or any
 * handles to the subject's content that the subject may have. Owner activities
 * would include explicitly disposing of the subject or of resources previously
 * allocated by the subject.
 * 
 * <H2>Lifecycle</H2>
 * 
 * <P>The lifecycle of the renderer will consist of a single instantiation
 * followed by zero or more <code>draw/release</code> cycles. The
 * <code>draw/release</code> cycle will consist of one or more invocations of
 * {@link #draw(GLMapView)} followed by a single invocation of
 * {@link #release()}. There is no guarantee that {@link #release()} will be
 * invoked prior to finalization without a previous invocation of
 * {@link #draw(GLMapView)}.
 * 
 * <H3>Instantiation</H3>
 * 
 * <P>The renderable may be instantiated on any thread. Instantiation should be
 * a lightweight and high-performance operation. The renderable should assign
 * the reference to its subject but otherwise remain in an <I>uninitialized</I>
 * state.
 * 
 * <H3>Draw</H3>
 * 
 * <P>The renderable should perform initialization when its
 * {@link #draw(GLMapView)} method is invoked when it is in the
 * <I>uninitialized</I> state. In the event that initialization will not be
 * completed in frame-rate time (e.g. the time it would normally take to render
 * one frame), it should be offloaded to a background thread. The renderable
 * should generally perform callback registration on its subject during
 * initialization. The renderable will be considered in the <I>initializing</I>
 * state until initialization is complete and it moves into the
 * <I>initialized</I> state or the {@link #release()} method has been invoked.
 * The general pattern followed is that the renderable will return immediately
 * from its {@link #draw(GLMapView)} method while in the <I>initializing</I>
 * state.
 * 
 * <P>Once in the <I>initialized</I> state, the renderable should render the
 * appropriate content during each invocation of the {@link #draw(GLMapView)}
 * method. In the event that construction of the scene for the current
 * {@link GLMapView} state cannot be completed within frame-rate time, scene
 * construction should be offloaded to a background thread.
 * 
 * <H3>Release</H3>
 * 
 * <P>The renderable will release all resources allocated during initialization
 * when its {@link #release()} method is invoked. The renderable will be moved
 * into the <I>uninitialized</I> state prior to the method returning. Generally,
 * the renderable should unregister itself as a callback on its subject during
 * release. Note that the renderable only moves back to the <I>uninitialized</I>
 * state during release. This means that the renderable is eligible for
 * re-initialization via a subsequent invocation of {@link #draw(GLMapView)}.
 * 
 * @author Developer
 */
public interface GLMapRenderable {
    /**
     * Renders the content in the current EGL context for the given view.
     * Initialization of the renderer may occur following instantiation or a
     * prior call to {@link #release()}.
     *
     * <P>This method is <B>ALWAYS</B> invoked on the GL thread.
     * 
     * @param view The current view
     */
    public void draw(GLMapView view);

    /**
     * Releases any resources allocated by the renderable as a result of the
     * invocation of the {@link #draw(GLMapView)} method. This method will be
     * invoked when a renderable that was previously being rendered is no longer
     * eligible for rendering (e.g. a tile that is no longer in view on the
     * map). The renderable may be reinitialized and rendered via a subsequent
     * call to {@link #draw(GLMapView)} in the future.
     * 
     * <P>This method is <B>ALWAYS</B> invoked on the GL thread.
     */
    public void release();

} // GLMapRenderable
