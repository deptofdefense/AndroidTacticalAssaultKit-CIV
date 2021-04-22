
package com.atakmap.map.opengl;

import java.util.ArrayList;
import java.util.Collection;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.Globe;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;

/**
 * @author Developer
 * @param <Pending> The type for the pending data; should be mutable..
 */
public abstract class GLAsynchronousMapRenderable<Pending>
        implements GLMapRenderable,
                   GLResolvableMapRenderable {

    /**
     * The current state. This is the state that the renderable and release lists are currently
     * valid for.
     */
    protected ViewState preparedState;

    /**
     * The target state. The next invocation of {@link #query(ViewState, Object)} will gather the
     * data necessary to make the renderable and release lists current with this state.
     */
    protected ViewState targetState;

    /** The background worker thread */
    protected WorkerThread backgroundWorker;

    /**
     * A flag indicating whether or not the renderable is considered initialized. The renderable
     * enters the uninitialized state on construction and following a call to {@link #release()}.
     * Initialization will occur in the first invocation of {@link #draw(GLMapView)} following
     * entering the unitialized state, and will be considered initialized until subsequently
     * entering the uninitialized state.
     */
    protected boolean initialized;

    /**
     * A flag indicating whether or not the worker thread is currently servicing a request. When
     * <code>true</code>, {@link #checkState()} will not be invoked during invocations of
     * {@link #draw(GLMapView)}. This member is set to <code>true</code> immediately before
     * {@link #query(ViewState, Object)} is invoked and remains <code>true</code> until the next
     * subsequent invocation of {@link #updateRenderableReleaseLists(Object)}. The value of the
     * member is guaranteed to be valid while synchronized on <code>this</code>.
     */
    protected boolean servicingRequest;

    /**
     * A flag indicating that the underlying data has been externally invalidated. When true, a
     * query/update will occur even if the target and prepared states match. This field is always
     * set to <code>false</code> immediately before the query occurs. The value of this member is
     * only guaranteed to be valid while synchronized on <code>this</code>.
     * 
     * @see {@link #invalidate()}
     * @see {@link #invalidateNoSync()}
     */
    protected boolean invalid;
    
    private boolean suspended;
    
    protected long queryStateCaptureDelay;

    private Thread backgroundWorkerThread;

    private RenderContext renderCtx;
    private SurfaceRendererControl surfaceControl;

    /**
     * Instantiates a new <code>GLAsynchronousMapRenderable</code>
     */
    protected GLAsynchronousMapRenderable() {
        this.preparedState = null;
        this.targetState = null;
        this.backgroundWorker = null;
        this.backgroundWorkerThread = null;
        this.initialized = false;
        this.servicingRequest = false;
        this.invalid = false;
        this.suspended = false;
        this.queryStateCaptureDelay = 0L;

        this.renderCtx = null;
        this.surfaceControl = null;
    }

    /**
     * Returns the {@link java.util.Collection} containing the renderables
     * associated with the current {@link #preparedState}.
     * <P>
     * This method should only ever be invoked in {@link #draw(GLMapView)} while
     * holding the lock on <code>this</code>. References to the
     * {@link java.util.Collection} or any of its elements should not be held
     * outside of that method.
     * 
     * @return
     */
    protected abstract Collection<? extends GLMapRenderable> getRenderList();

    /**
     * Reset the pending data object. This method is invoked prior to
     * {@link #query(ViewState, Object)}.
     * <P>
     * This method will be invoked on the worker thread while synchronized on <code>this</code>.
     * 
     * @param pendingData The object that will hold the pending data
     */
    protected abstract void resetPendingData(Pending pendingData);

    /**
     * Release the pending data object. This method is invoked prior to worker thread exiting.
     * <P>
     * This method will be invoked on the worker thread in an unsynchronized manner.
     * 
     * @param pendingData The object holding pending data
     */
    protected abstract void releasePendingData(Pending pendingData);

    /**
     * Creates the pending data object. This object will store the result of the asynchronous query
     * such that the renderable and release lists can subsequently be constructed via the invocation
     * of {@link #updateRenderableReleaseLists(Object)}.
     * <P>
     * This method will be invoked on the worker thread in an unsynchronized manner.
     * 
     * @return The object to hold the query result
     */
    protected abstract Pending createPendingData();

    /**
     * Constructs the renderable and release lists based on the supplied query result.
     * <P>
     * This method will be invoked on the worker thread in an unsynchronized manner. It is
     * <B>STRONGLY</B> that this method not be synchronized in a manner that it may block any of the
     * other methods that are synchronized on <code>this</code> as this may serious degrade
     * rendering performance.
     * 
     * @param pendingData The result of the query
     * @return <code>true</code> if the renderable and release lists were brought into sync with the
     *         current {@link #targetState}, <code>false</code> otherwise. If this method returns
     *         <code>true</code>, {@link #preparedState} will take on the state of
     *         {@link #targetState} and the worker will remain idle until {@link #checkState()}
     *         returns <code>false</code>.
     */
    protected abstract boolean updateRenderableReleaseLists(Pending pendingData);

    /**
     * Performs a query based on the specified target state. The results of the
     * query will be used to create valid render and release lists via
     * {@link #updateRenderableReleaseLists(Object)}.
     * 
     * <P>This method will only be invoked on the worker thread and its
     * invocation will not be externally synchronized. Synchronization of the
     * query itself on <code>this</code> is strongly discouraged as doing so
     * will effectively block {@link #draw(GLMapView)}.
     * 
     * @param state     The state to query against
     * @param result    Stores the result of the query. The pending data will
     *                  subsquently be passed to
     *            {@link #updateRenderableReleaseLists(Object)}.
     */
    protected abstract void query(ViewState state, Pending result);

    /**
     * Creates a new <code>ViewState</code> instance. This method will be invoked to create the
     * values for {@link #targetState}, {@link #preparedState} as well as a temporary
     * <code>ViewState</code> that holds a copy of {@link #targetState} for the worker thread.
     * <P>
     * This method may be invoked off of the GL context thread.
     * 
     * @return A new <code>ViewState</code> instance.
     */
    protected ViewState newViewStateInstance() {
        return new ViewState();
    }

    /**
     * Returns the priority for the worker thread. Defaults to
     * {@link java.lang.Thread#NORM_PRIORITY}.
     * 
     * @return The priority for the worker thread.
     */
    protected int getBackgroundThreadPriority() {
        return Thread.NORM_PRIORITY;
    }

    /**
     * Returns the name for the worker thread.
     * 
     * @return The name for the worker thread.
     */
    protected String getBackgroundThreadName() {
        return "GLAsyncMapRenderableThread-" + Integer.toString(this.hashCode());
    }

    private boolean isDirty() {
        return !this.suspended && this.checkState();
    }
    
    /**
     * Returns a flag indicating whether or not the renderable and release lists should be
     * reconstructed based on the current {@link #preparedState} and {@link #targetState}. The
     * default implementation compares the <code>drawVersion</code> of {@link #targetState} and
     * {@link #preparedState} and <code>OR</code>s the result with {@link #invalid}.
     * 
     * @return <code>true</code> if the renderable and release lists should be reconstructed,
     *         <code>false</code> otherwise.
     */
    protected boolean checkState() {
        return this.invalid ||
                (this.preparedState.drawVersion != this.targetState.drawVersion);
    }

    /**
     * Performs initialization of the renderable. Subclasses are encouraged to override this method
     * to perform any
     * 
     * @param view
     */
    protected void initImpl(GLMapView view) {
    }

    /**
     * Sets the {@link #invalid} field to <code>true</code>. This method should only be invoked
     * while synchronized on <code>this</code>.
     * 
     * @see {@link #invalidate()}
     */
    protected void invalidateNoSync() {
        this.invalid = true;
        final SurfaceRendererControl ctrl = this.surfaceControl;
        final ViewState p = this.preparedState;
        if (ctrl != null)
            ctrl.markDirty();
        final RenderContext ctx = this.renderCtx;
        if(ctx != null)
            ctx.requestRefresh();
    }

    protected void backgroundThreadEntry() {}
    
    protected void backgroundThreadExit() {}

    /**
     * Sets the {@link #invalid} field to <code>true</code>. This method may be
     * invoked from any thread.
     * 
     * <P><B>Developer Note:</B> For content with high frequency or high volume
     * changes, the synchronization on this method may result in noticeable and
     * possibly significant performance degradation. In such a case, the
     * unsynchronized version, {@link #invalidateNoSync()} may be preferred and
     * will likely achieve the same effect, given then frequency of
     * invalidation, albeit in a non-thread-safe manner.
     * 
     * @see {@link #invalidateNoSync()}
     */
    protected final synchronized void invalidate() {
        this.invalidateNoSync();
    }

    /**
     * Checks if the current query should be aborted. This method may only be
     * called from the query thread, and, generally, should not be invoked
     * outside of {@link #query(ViewState, Object)}.
     * 
     * @return <code>true</code> if the query should be aborted,
     *         <code>false</code> otherwise.
     */
    protected final boolean checkQueryThreadAbort() {
        return (this.backgroundWorkerThread != Thread.currentThread());
    }
    
    /**************************************************************************/
    // GL Map Renderable

    @Override
    public synchronized void draw(GLMapView view) {
        if (!this.initialized) {
            this.preparedState = this.newViewStateInstance();
            this.targetState = this.newViewStateInstance();

            this.backgroundWorker = new WorkerThread();
            this.backgroundWorkerThread = new Thread(this.backgroundWorker, this.getBackgroundThreadName());
            this.backgroundWorkerThread.setPriority(this.getBackgroundThreadPriority());
            this.backgroundWorkerThread.start();

            this.initImpl(view);
            this.renderCtx = view.getRenderContext();
            this.surfaceControl = view.getControl(SurfaceRendererControl.class);

            this.initialized = true;
        }

        // if the target state has not already been computed for the pump and
        // it is a sprite pass if there is any sprite content or there is a pass
        // match and there is not any sprite content, update the target state
        if (this.invalid || (this.targetState.drawVersion != view.currentScene.drawVersion))
            this.targetState.set(view);
        if (!this.servicingRequest && this.isDirty())
            this.notify();

        final Collection<? extends GLMapRenderable> renderList = this.getRenderList();
        for (GLMapRenderable r : renderList)
            r.draw(view);
    }

    /**
     * Special care should be taken if overriding NOT to synchronize a call to
     * <code>super.release()</code> on this.
     */
    @Override
    public void release() {
        final Thread joinThread = this.backgroundWorkerThread;
        synchronized(this) {
            this.backgroundWorkerThread = null;
            this.backgroundWorker = null;
            this.notify();
        }
        if(joinThread != null) {
            try {
                joinThread.join();
            } catch(InterruptedException ignored) {}
        }
        synchronized(this) {
            this.releaseImpl();

            this.preparedState = null;
            this.targetState = null;

            this.renderCtx = null;
            this.surfaceControl = null;

            this.initialized = false;
        }
    }
    
    protected void releaseImpl() {
        for (GLMapRenderable r : this.getRenderList())
            r.release();
    }

    /**************************************************************************/
    // GLResolvableMapRenderable
    
    protected final boolean isSuspendedNoSync() {
        return this.suspended;
    }

    @Override
    public synchronized State getState() {
        if(this.suspended)
            return State.SUSPENDED;
        if(this.servicingRequest)
           return State.RESOLVING;
        else if(this.checkState())
            return State.UNRESOLVED;

        // the scene is up to date, check the renderables for resolution state
        Collection<? extends GLMapRenderable> renderList = this.getRenderList();
        for(GLMapRenderable r : renderList) {
            if(r instanceof GLResolvableMapRenderable) {
                switch(((GLResolvableMapRenderable)r).getState()) {
                    case UNRESOLVED :
                    case RESOLVING :
                        // the renderable has not yet arrived at a terminal
                        // state
                        return State.RESOLVING;
                    default :
                        break;
                }
            }
        }

        // the scene is up to date and all renderables are either not marked as
        // resolvable or they are in a terminal state (RESOLVED or UNRESOLVABLE)
        return State.RESOLVED;
    }

    @Override
    public synchronized void suspend() {
        if(!this.suspended) {
            // suspend renderables
            Collection<? extends GLMapRenderable> renderList = this.getRenderList();
            for(GLMapRenderable r : renderList) {
                if(r instanceof GLResolvableMapRenderable)
                    ((GLResolvableMapRenderable)r).suspend();
            }

            // mark as suspended
            this.suspended = true;
        }
    }

    @Override
    public synchronized void resume() {
        // if we are in a suspended state, resume and notify the background
        // worker
        if(this.suspended) {
            // clear suspended state
            this.suspended = false;
            
            // resume renderables
            Collection<? extends GLMapRenderable> renderList = this.getRenderList();
            for(GLMapRenderable r : renderList) {
                if(r instanceof GLResolvableMapRenderable)
                    ((GLResolvableMapRenderable) r).resume();
            }
            
            this.notify();
        }
    }

    /**************************************************************************/

    /**
     * A reflection of the map state. Subclasses may extend this class to pass
     * around implementation-specific information.
     * 
     * @author Developer
     * 
     * @see GLAsynchronousMapRenderable#newViewStateInstance()
     */
    protected static class ViewState {
        public double drawMapScale;
        public double drawMapResolution;
        public double drawLat;
        public double drawLng;
        public double drawRotation;
        public double animationFactor;
        public int drawVersion;
        public int drawSrid;
        public double westBound;
        public double southBound;
        public double northBound;
        public double eastBound;
        public GeoPoint upperLeft;
        public GeoPoint upperRight;
        public GeoPoint lowerRight;
        public GeoPoint lowerLeft;
        public double targetMapScale;
        public double targetLat;
        public double targetLng;
        public double targetRotation;
        public int _left;
        public int _right;
        public int _top;
        public int _bottom;
        public float focusx, focusy;
        public boolean settled;
        public boolean crossesIDL;
        public boolean continuousScrollEnabled;
        public ArrayList<Envelope> surfaceRegions;

        public ViewState() {
            this.drawMapScale = Double.NaN;
            this.drawMapResolution = Double.NaN;
            this.drawLat = Double.NaN;
            this.drawLng = Double.NaN;
            this.drawRotation = Double.NaN;
            this.animationFactor = Double.NaN;
            this.drawVersion = -1;
            this.drawSrid = -1;
            this.westBound = Double.NaN;
            this.southBound = Double.NaN;
            this.northBound = Double.NaN;
            this.eastBound = Double.NaN;
            this.upperLeft = GeoPoint.createMutable().set(Double.NaN, Double.NaN);
            this.upperRight = GeoPoint.createMutable().set(Double.NaN, Double.NaN);
            this.lowerRight = GeoPoint.createMutable().set(Double.NaN, Double.NaN);
            this.lowerLeft = GeoPoint.createMutable().set(Double.NaN, Double.NaN);
            this._left = -1;
            this._right = -1;
            this._top = -1;
            this._bottom = -1;
            this.focusx = Float.NaN;
            this.focusy = Float.NaN;
            this.settled = false;
            this.crossesIDL = false;
            this.continuousScrollEnabled = false;
            this.surfaceRegions = new ArrayList<>();
        }

        /**
         * Copies the state of the specified {@link GLMapView}. This method is
         * invoked to set up the
         * {@link GLAsynchronousMapRenderable#targetState targetState} in
         * {@link GLAsynchronousMapRenderable#draw(GLMapView)}.
         * 
         * <P>Subclasses should capture any implementation specific information
         * from the {@link GLAsynchronousMapRenderable} that needs to get passed
         * to the
         * {@link GLAsynchronousMapRenderable#query(ViewState, Object) query}
         * method during the invocation of this method.
         * 
         * <P>This method is always invoked on the GL context thread while
         * externally locked on <code>GLAsynchronousMapRenderable.this</code>.
         * 
         * @param view  The view
         */
        public void set(GLMapView view) {
            this.drawMapScale = Globe.getMapScale(view.currentScene.scene.dpi, view.currentScene.drawMapResolution);
            this.drawMapResolution = view.currentScene.drawMapResolution;
            this.drawLat = view.currentScene.drawLat;
            this.drawLng = view.currentScene.drawLng;
            this.drawRotation = view.currentScene.drawRotation;
            this.drawVersion = view.currentScene.drawVersion;
            this.drawSrid = view.currentScene.drawSrid;
            this.westBound = view.currentScene.westBound;
            this.southBound = view.currentScene.southBound;
            this.northBound = view.currentScene.northBound;
            this.eastBound = view.currentScene.eastBound;
            this.upperLeft.set(view.currentScene.upperLeft.getLatitude(), view.currentScene.upperLeft.getLongitude());
            this.upperRight.set(view.currentScene.upperRight.getLatitude(), view.currentScene.upperRight.getLongitude());
            this.lowerRight.set(view.currentScene.lowerRight.getLatitude(), view.currentScene.lowerRight.getLongitude());
            this.lowerLeft.set(view.currentScene.lowerLeft.getLatitude(), view.currentScene.lowerLeft.getLongitude());
            this._left = view.currentScene.left;
            this._right = view.currentScene.right;
            this._top = view.currentScene.top;
            this._bottom = view.currentScene.bottom;
            this.focusx = view.currentScene.focusx;
            this.focusy = view.currentScene.focusy;
            this.crossesIDL = view.currentScene.crossesIDL;

            this.settled = view.settled;
            this.animationFactor = view.animationFactor;
            this.continuousScrollEnabled = view.continuousScrollEnabled;
            this.surfaceRegions.clear();
            SurfaceRendererControl ctrl = view.getControl(SurfaceRendererControl.class);
            if (ctrl != null)
                this.surfaceRegions.addAll(ctrl.getSurfaceBounds());
        }

        /**
         * Copies the values for all fields of the specified state into this
         * state.
         * 
         * @param view
         */
        public void copy(ViewState view) {
            this.drawMapScale = view.drawMapScale;
            this.drawMapResolution = view.drawMapResolution;
            this.drawLat = view.drawLat;
            this.drawLng = view.drawLng;
            this.drawRotation = view.drawRotation;
            this.animationFactor = view.animationFactor;
            this.drawVersion = view.drawVersion;
            this.drawSrid = view.drawSrid;
            this.westBound = view.westBound;
            this.southBound = view.southBound;
            this.northBound = view.northBound;
            this.eastBound = view.eastBound;
            this.upperLeft.set(view.upperLeft.getLatitude(), view.upperLeft.getLongitude());
            this.upperRight.set(view.upperRight.getLatitude(), view.upperRight.getLongitude());
            this.lowerRight.set(view.lowerRight.getLatitude(), view.lowerRight.getLongitude());
            this.lowerLeft.set(view.lowerLeft.getLatitude(), view.lowerLeft.getLongitude());
            this._left = view._left;
            this._right = view._right;
            this._top = view._top;
            this._bottom = view._bottom;
            this.focusx = view.focusx;
            this.focusy = view.focusy;
            this.settled = view.settled;
            this.crossesIDL = view.crossesIDL;
            this.continuousScrollEnabled = view.continuousScrollEnabled;
            this.surfaceRegions.clear();
            this.surfaceRegions.addAll(view.surfaceRegions);
        }
    }

    protected class WorkerThread implements Runnable {
        @Override
        public void run() {
            GLAsynchronousMapRenderable.this.backgroundThreadEntry();

            Pending pendingData = null;
            try {
                pendingData = GLAsynchronousMapRenderable.this.createPendingData();
                ViewState queryState = GLAsynchronousMapRenderable.this.newViewStateInstance();
                long reentrySleep = 0L;
                
                while (true) {
                    if(reentrySleep > 0L) {
                        try {
                            Thread.sleep(reentrySleep);
                        } catch(InterruptedException ignored) {}
                    }

                    synchronized (GLAsynchronousMapRenderable.this) {
                        if(GLAsynchronousMapRenderable.this.checkQueryThreadAbort())
                            break;

                        // update release/renderable collections
                        if (GLAsynchronousMapRenderable.this.servicingRequest
                                && GLAsynchronousMapRenderable.this
                                        .updateRenderableReleaseLists(pendingData)) {
                            GLAsynchronousMapRenderable.this.preparedState.copy(queryState);
                            if(GLAsynchronousMapRenderable.this.renderCtx != null)
                                GLAsynchronousMapRenderable.this.renderCtx.requestRefresh();
                            if(GLAsynchronousMapRenderable.this.surfaceControl != null) {
                                GLAsynchronousMapRenderable.this.surfaceControl.markDirty();
                            }
                        }
                        GLAsynchronousMapRenderable.this.resetPendingData(pendingData);
                        GLAsynchronousMapRenderable.this.servicingRequest = false;

                        // check the state and wait if appropriate
                        if (!GLAsynchronousMapRenderable.this.isDirty()) {
                            try {
                                GLAsynchronousMapRenderable.this.wait();
                            } catch (InterruptedException ignored) {}
                            
                            // if waking up to service a query, sleep for the
                            // specified delay
                            if(!GLAsynchronousMapRenderable.this.checkQueryThreadAbort())
                                reentrySleep = GLAsynchronousMapRenderable.this.queryStateCaptureDelay;
                            continue;
                        } else {
                            reentrySleep = 0;
                        }

                        // copy the target state to query outside of the
                        // synchronized block
                        queryState.copy(GLAsynchronousMapRenderable.this.targetState);
                        GLAsynchronousMapRenderable.this.invalid = false;
                        GLAsynchronousMapRenderable.this.servicingRequest = true;
                    }

                    GLAsynchronousMapRenderable.this.query(queryState, pendingData);
                }
            } finally {
                if (pendingData != null)
                    GLAsynchronousMapRenderable.this.releasePendingData(pendingData);
                GLAsynchronousMapRenderable.this.servicingRequest = false;
                
                GLAsynchronousMapRenderable.this.backgroundThreadExit();
            }
        }
    }
}
