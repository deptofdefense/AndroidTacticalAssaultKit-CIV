
package com.atakmap.android.maps.graphics;

import android.opengl.GLES30;

import com.atakmap.android.importexport.handlers.ParentMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.map.layer.control.LollipopControl;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.RenderContext;
import com.atakmap.map.opengl.GLAsynchronousMapRenderable2;
import com.atakmap.map.opengl.GLMapBatchable;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.LayerHitTestControl;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.util.Collections2;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class GLQuadtreeNode2 extends
        GLAsynchronousMapRenderable2<Collection<GLMapItem2>>
        implements LollipopControl, ClampToGroundControl,
        LayerHitTestControl {

    /*
     * THREAD SAFETY Much of GLAsynchronousMapRenderable is synchronized on
     * 'this'. We need additional synchronization to protect the quadtree data
     * structure so all operations modify the quadtree should be synchronized on
     * the root of the quadtree (i.e. 'this.root').
     *
     * !!! WHEN BOTH LOCKS ARE REQUIRED, ALWAYS SYNCHRONIZE ON 'this.root'
     *     BEFORE SYNCHRONIZING ON 'this' !!!
     */

    /**************************************************************************/

    private final static Comparator<GLMapItem2> ITEM_COMPARATOR = new Comparator<GLMapItem2>() {
        @Override
        public int compare(GLMapItem2 o1, GLMapItem2 o2) {
            return MapItem.ZORDER_RENDER_COMPARATOR.compare(o1.getSubject(),
                    o2.getSubject());
        }
    };

    public static final String TAG = "GLQuadtreeNode";

    /******************************************************************/

    private final QuadTreeNode root;
    private final MutableGeoBounds queryBounds;
    private GLRenderBatch2 batch;
    private GLRenderBatch batchLegacy;
    private final List<GLMapRenderable2> renderables;
    private final List<GLMapRenderable2> surfaceRenderables;
    private final List<GLMapRenderable2> spriteRenderables;
    private final Renderable renderable;
    private final Collection<GLMapRenderable2> renderList;
    /** only to be accessed on query thread */
    private final Collection<GLMapItem2> lastFetch;
    private RenderContext context;

    private boolean lollipopsVisible = true;
    private boolean clampToGroundAtNadir = false;

    /**
     * Provides better information as to the nature of of the item that triggered the warning.
     */
    static private String debugItem(MapItem mi) {
        if (mi == null)
            return "[error item is null]";

        String s = mi.getMetaString("title",
                mi.getMetaString("callsign", mi.getUID()));
        s += ", type=" + mi.getType();
        s += ", class=" + mi.getClass().getName();
        final MapGroup mg = mi.getGroup();
        if (mg != null)
            s = s + " in group: " + mg.getFriendlyName();
        else
            s = s + " (no Map Group)";
        return s;
    }

    public GLQuadtreeNode2() {
        this.root = new QuadTreeNode();
        this.queryBounds = new MutableGeoBounds(0, 0, 0, 0);

        this.renderables = new ArrayList<>();
        this.surfaceRenderables = new ArrayList<>();
        this.spriteRenderables = new ArrayList<>();
        this.renderable = new Renderable();
        this.renderList = Collections
                .singleton(this.renderable);
        this.lastFetch = Collections2.newIdentityHashSet();
    }

    /**
     * Insert a GLMapItem at the current QuadNode root.   Double insertions are disallowed.
     */
    public void insertItem(final GLMapItem2 item) {
        synchronized (this.root) {
            if (item.getOpaque() instanceof QuadTreeNode) {
                Log.w(TAG,
                        "trying to insert a GLMapItem that is already inserted: "
                                + debugItem(item.getSubject()));
                if (com.atakmap.app.BuildConfig.DEBUG)
                    Log.e(TAG, "backtrace", new Exception());
            } else {
                this.root.add(item);
            }
        }
    }

    /**
     * Remove a GLMapItem at the current QuadNode root.  A warning is printed if a
     * removal is called on an item not in the tree.
     */
    public void removeItem(final GLMapItem2 item) {
        synchronized (this.root) {
            Object opaque = item.getOpaque();
            if (!(opaque instanceof QuadTreeNode)) {
                Log.w(TAG, "trying to remove a GLMapItem that is not present: "
                        + debugItem(item.getSubject()));
                if (com.atakmap.app.BuildConfig.DEBUG)
                    Log.e(TAG, "backtrace", new Exception());
            } else {
                ((QuadTreeNode) opaque).removeImpl(item, true);
            }
        }
    }

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES | GLMapView.RENDER_PASS_SURFACE;
    }

    @Override
    protected void initImpl(GLMapView view) {
        super.initImpl(view);

        this.batch = new GLRenderBatch2();
        this.batchLegacy = new GLRenderBatch(this.batch);
        this.context = view.getRenderContext();
    }

    @Override
    public void release() {
        super.release();

        this.batch = null;
    }

    @Override
    protected String getBackgroundThreadName() {
        return "GLQuadtreeNode-" + Integer.toString(this.root.hashCode(), 16);
    }

    @Override
    protected Collection<GLMapRenderable2> getRenderList() {
        return this.renderList;
    }

    @Override
    protected void resetPendingData(Collection<GLMapItem2> pendingData) {
        pendingData.clear();
    }

    @Override
    protected void releasePendingData(Collection<GLMapItem2> pendingData) {
        pendingData.clear();
    }

    @Override
    protected Collection<GLMapItem2> createPendingData() {
        // this ensures pending data, and resulting render list, is always in
        // correct render order
        //return new TreeSet<GLMapItem2>(ITEM_COMPARATOR);
        return new ArrayList<>();
    }

    @Override
    protected boolean updateRenderList(ViewState state,
            Collection<GLMapItem2> pendingData) {
        if (this.invalid)
            return false;

        this.renderables.clear();
        this.surfaceRenderables.clear();
        this.spriteRenderables.clear();

        GLBatchRenderer[] spriteBatch = new GLBatchRenderer[1];
        GLBatchRenderer[] surfaceBatch = new GLBatchRenderer[1];
        for (GLMapItem2 item : pendingData) {
            if (MathUtils.hasBits(item.getRenderPass(),
                    GLMapView.RENDER_PASS_SPRITES))
                process(item, spriteRenderables, spriteBatch);
            if (MathUtils.hasBits(item.getRenderPass(),
                    GLMapView.RENDER_PASS_SURFACE))
                process(item, surfaceRenderables, surfaceBatch);
        }
        this.renderables.addAll(pendingData);

        if (spriteBatch[0] != null && spriteBatch[0].batchables.size() > 1)
            this.spriteRenderables.add(spriteBatch[0]);
        else if (spriteBatch[0] != null)
            this.spriteRenderables.add(spriteBatch[0].batchables.getFirst());
        if (surfaceBatch[0] != null && surfaceBatch[0].batchables.size() > 1)
            this.surfaceRenderables.add(surfaceBatch[0]);
        else if (surfaceBatch[0] != null)
            this.surfaceRenderables.add(surfaceBatch[0].batchables.getFirst());
        pendingData.clear();

        return true;
    }

    private void process(GLMapItem2 item,
            Collection<GLMapRenderable2> renderables,
            GLBatchRenderer[] batchRenderer) {

        if (item instanceof LollipopControl)
            ((LollipopControl) item).setLollipopsVisible(lollipopsVisible);
        if (item instanceof ClampToGroundControl)
            ((ClampToGroundControl) item)
                    .setClampToGroundAtNadir(clampToGroundAtNadir);

        // XXX - need to be conscious of render pass given 2D batch
        if (item instanceof GLMapBatchable2) {
            // push the sprite into the current batch renderer
            if (batchRenderer[0] == null)
                batchRenderer[0] = new GLBatchRenderer();
            batchRenderer[0].addSprite(item);
        } else if (item instanceof GLMapBatchable) {
            // push the sprite into the current batch renderer
            if (batchRenderer[0] == null)
                batchRenderer[0] = new GLBatchRenderer();
            batchRenderer[0].addSprite(item);
        } else {
            // if we've been batching sprites, push the batch renderer onto
            // the renderable list
            if (batchRenderer[0] != null) {
                // make sure we have more than one sprite, if not, push the
                // sprite onto the list by itself to avoid the batch render
                // overhead
                if (batchRenderer[0].batchables.size() > 1)
                    renderables.add(batchRenderer[0]);
                else
                    renderables
                            .add(batchRenderer[0].batchables.getFirst());
                batchRenderer[0] = null;
            }
            renderables.add(item);
        }
    }

    @Override
    protected void query(
            GLAsynchronousMapRenderable2.ViewState state,
            Collection<GLMapItem2> result) {

        if (state.crossesIDL) {
            Set<GLMapItem2> resultSet = new HashSet<>();
            // west of IDL
            this.queryBounds.set(state.northBound, state.westBound,
                    state.southBound, 180d);
            this.queryImpl(state.scene, this.queryBounds,
                    state.drawMapResolution,
                    resultSet);
            // east of IDL
            this.queryBounds.set(state.northBound, -180d, state.southBound,
                    state.eastBound);
            this.queryImpl(state.scene, this.queryBounds,
                    state.drawMapResolution,
                    resultSet);
            result.addAll(resultSet);
        } else {
            this.queryBounds.set(state.northBound,
                    state.westBound,
                    state.southBound,
                    state.eastBound);
            this.queryImpl(state.scene, this.queryBounds,
                    state.drawMapResolution, result);
        }

        // build out the list to be released
        final Collection<GLMapItem2> toRelease = Collections2
                .newIdentityHashSet(lastFetch);
        toRelease.removeAll(result);
        lastFetch.clear();
        lastFetch.addAll(result);

        if (this.context != null && !toRelease.isEmpty())
            this.context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    for (GLMapItem2 item : toRelease)
                        item.release();
                }
            });

        try {
            Collections.sort((ArrayList<GLMapItem2>) result,
                    (state.drawTilt > 0d) ? new DepthComparator(state)
                            : ITEM_COMPARATOR);
        } catch (Exception e) {
            // if a sort occurs during insertion there is a chance that this will throw an
            // exeption.   Catch the error instead of paying the cost to synchroniz and
            // continue on.
            Log.w(TAG,
                    "WARNING: sort failed possibly due to insertion during sort");

        }
    }

    private void queryImpl(
            MapSceneModel scene,
            GeoBounds bnds,
            double resolution,
            Collection<GLMapItem2> result) {

        if (this.checkQueryThreadAbort())
            return;

        synchronized (this.root) {
            this.root.collectDrawables(scene, bnds, resolution,
                    result);
        }
    }

    boolean accessCheckQueryThreadAbort() {
        return this.checkQueryThreadAbort();
    }

    @Override
    public boolean getLollipopsVisible() {
        return lollipopsVisible;
    }

    @Override
    public void setLollipopsVisible(boolean v) {
        lollipopsVisible = v;
        invalidateNoSync();
    }

    @Override
    public void setClampToGroundAtNadir(boolean v) {
        clampToGroundAtNadir = v;
        invalidateNoSync();
    }

    @Override
    public boolean getClampToGroundAtNadir() {
        return lollipopsVisible;
    }

    @Override
    public synchronized Collection<?> getHitTestList() {
        List<GLMapRenderable2> items = new ArrayList<>(this.renderables);
        Collections.reverse(items); // Reverse so items drawn last are hit-tested first
        return items;
    }

    /**************************************************************************/

    private static double getOrIfNan(double v, double ifnan) {
        return Double.isNaN(v) ? ifnan : v;
    }

    /**************************************************************************/

    final class QuadTreeNode implements GLMapItem2.OnBoundsChangedListener {
        static final int MAX_DEPTH = 18;
        static final int CELL_LIMIT = 100;

        final QuadTreeNode parent;
        final GeoBounds bounds;
        final QuadTreeNode[] children;
        final Set<GLMapItem2> drawables;
        final int depth;

        final MutableGeoBounds scratchQuarterBounds;

        public QuadTreeNode() {
            this(null, 90, -180, -90, 180);
        }

        private QuadTreeNode(QuadTreeNode parent, double ulLat, double ulLng,
                double lrLat,
                double lrLng) {
            this.parent = parent;
            this.bounds = new GeoBounds(ulLat, ulLng, lrLat, lrLng);
            this.children = new QuadTreeNode[4];
            this.drawables = new HashSet<>();

            this.scratchQuarterBounds = new MutableGeoBounds(0, 0, 0, 0);

            this.depth = (this.parent != null) ? this.parent.depth + 1 : 0;
        }

        final void collectDrawables(
                MapSceneModel scene,
                GeoBounds queryBounds,
                double mapResolution,
                Collection<GLMapItem2> retval) {

            MutableGeoBounds bnds = new MutableGeoBounds(0d, 0d, 0d, 0d);
            for (GLMapItem2 item : this.drawables) {
                if (accessCheckQueryThreadAbort())
                    break;
                if (mapResolution > item.getMinDrawResolution())
                    continue;
                bnds.setMinAltitude(Double.NaN);
                bnds.setMaxAltitude(Double.NaN);
                item.getBounds(bnds);
                boolean intersects;
                do {
                    intersects = false;
                    final int renderPass = item.getRenderPass();
                    if (MathUtils.hasBits(renderPass,
                            GLMapView.RENDER_PASS_SURFACE))
                        intersects |= bnds.intersects(queryBounds);
                    if (MathUtils.hasBits(renderPass,
                            GLMapView.RENDER_PASS_SPRITES)) {
                        // XXX - crude heuristic to filter out items beyond the
                        //       far plane. An assumption is being made that
                        //       sprite renderables have relatively small
                        //       coverages
                        GeoPoint center = bnds.getCenter(null);
                        final double radius = GeoCalculations.distanceTo(
                                new GeoPoint(bnds.getNorth(), bnds.getWest()),
                                center);
                        final double slant = GeoCalculations
                                .slantDistanceTo(
                                        scene.mapProjection.inverse(
                                                scene.camera.location, null),
                                        center);
                        if (slant > (radius + scene.camera.farMeters))
                            break;

                        // ensure there are testable vertical bounds
                        {
                            final double ominAlt = bnds.getMinAltitude();
                            final double omaxAlt = bnds.getMaxAltitude();
                            if (Double.isNaN(ominAlt)) {
                                bnds.setMinAltitude(
                                        getOrIfNan(ominAlt - 20d, -900));
                            }
                            if (Double.isNaN(omaxAlt)) {
                                bnds.setMaxAltitude(
                                        getOrIfNan(ominAlt + 20d, 19000));
                            }
                        }

                        intersects |= MapSceneModel.intersects(scene,
                                bnds.getWest(), bnds.getSouth(),
                                bnds.getMinAltitude(),
                                bnds.getEast(), bnds.getNorth(),
                                bnds.getMaxAltitude());
                    }
                } while (false);
                if (intersects)
                    retval.add(item);
            }
            if (accessCheckQueryThreadAbort())
                return;

            for (QuadTreeNode child : this.children) {
                if (accessCheckQueryThreadAbort())
                    break;
                if (child == null)
                    continue;

                boolean intersects = child.bounds.intersects(queryBounds);
                // if no surface intersection, check for frustum intersection
                if (!intersects) {
                    intersects |= MapSceneModel.intersects(scene,
                            bnds.getWest(), bnds.getSouth(),
                            bnds.getMinAltitude(),
                            bnds.getEast(), bnds.getNorth(),
                            bnds.getMaxAltitude());
                }
                if (!intersects)
                    continue;
                if (queryBounds.contains(child.bounds))
                    child.collectDrawables(mapResolution, retval);
                else
                    child.collectDrawables(scene, queryBounds,
                            mapResolution, retval);
            }
        }

        protected final void collectDrawables(double mapResolution,
                Collection<GLMapItem2> retval) {

            if (accessCheckQueryThreadAbort())
                return;
            for (GLMapItem2 item : this.drawables) {
                if (accessCheckQueryThreadAbort())
                    break;

                if (mapResolution <= item.getMinDrawResolution())
                    retval.add(item);
            }
            if (accessCheckQueryThreadAbort())
                return;

            for (QuadTreeNode child : this.children) {
                if (accessCheckQueryThreadAbort())
                    break;
                if (child != null)
                    child.collectDrawables(mapResolution, retval);
            }
        }

        public final void add(GLMapItem2 item) {
            if (this != GLQuadtreeNode2.this.root)
                throw new IllegalStateException(
                        "GLQuadtreeNode2.add may only be invoked on root node!");

            this.addImpl(item);
            GLQuadtreeNode2.this.invalidate();
        }

        private void addImpl(GLMapItem2 item) {
            if (this.depth < MAX_DEPTH) {
                MutableGeoBounds bnds = new MutableGeoBounds(0d, 0d, 0d, 0d);
                item.getBounds(bnds);
                for (int i = 0; i < this.children.length; i++) {
                    if (this.children[i] == null) {
                        final double halfLat = (this.bounds.getNorth()
                                - this.bounds
                                        .getSouth())
                                / 2.0d;
                        final double halfLng = (this.bounds.getEast()
                                - this.bounds
                                        .getWest())
                                / 2.0d;

                        final double qLat = this.bounds.getNorth()
                                - ((halfLat) * (i / 2f));
                        final double qLng = this.bounds.getWest()
                                + ((halfLng) * (i % 2));

                        this.scratchQuarterBounds.set(qLat,
                                qLng,
                                qLat - halfLat,
                                qLng + halfLng);

                        if (this.scratchQuarterBounds
                                .contains(bnds)) {
                            this.children[i] = new QuadTreeNode(this, qLat,
                                    qLng, qLat - halfLat,
                                    qLng + halfLng);
                            this.children[i].addImpl(item);
                            return;
                        }
                    } else if (this.children[i].bounds.contains(bnds)) {
                        this.children[i].addImpl(item);
                        return;
                    }
                }
            }

            // add the item to this node
            {
                this.drawables.add(item);
                item.setOpaque(this);
                item.addBoundsListener(this);
            }
        }

        public final void remove(GLMapItem2 item) {
            if (this != GLQuadtreeNode2.this.root)
                throw new IllegalStateException(
                        "GLQuadtreeNode2.remove may only be invoked on root node!");
            removeRecursive(item, true);
        }

        private void removeRecursive(GLMapItem2 item, boolean notifyInvalid) {
            if (this.depth < MAX_DEPTH) {
                MutableGeoBounds bnds = new MutableGeoBounds(0d, 0d, 0d, 0d);
                item.getBounds(bnds);
                for (QuadTreeNode child : this.children) {
                    if (child != null
                            && child.bounds.contains(bnds)) {
                        child.removeRecursive(item, notifyInvalid);
                        return;
                    }
                }
            }

            removeImpl(item, notifyInvalid);
        }

        // remove the item
        private void removeImpl(GLMapItem2 item, boolean notifyInvalid) {
            item.removeBoundsListener(this);
            item.setOpaque(null);
            this.drawables.remove(item);

            if (notifyInvalid)
                GLQuadtreeNode2.this.invalidate();
            prune(this);
        }

        private void prune(final QuadTreeNode qtn) {
            boolean empty = true;
            if (qtn.children != null) {
                for (int i = 0; i < qtn.children.length; ++i) {
                    if (qtn.children[i] != null) {
                        empty = false;
                        break;
                    }
                }
            }

            if (empty) {
                if (qtn.drawables != null && !qtn.drawables.isEmpty()) {
                    empty = false;
                }
            }

            if (empty) {
                if (qtn.parent != null && qtn.parent.children != null) {
                    for (int i = 0; i < qtn.parent.children.length; ++i) {
                        if (qtn.parent.children[i] == qtn) {
                            qtn.parent.children[i] = null;
                            prune(qtn.parent);
                        }
                    }
                }
            }
        }

        public void clear() {
            for (int i = 0; i < this.children.length; i++) {
                if (this.children[i] == null)
                    continue;
                this.children[i].clear();
                this.children[i] = null;
            }

            for (GLMapItem2 item : this.drawables) {
                item.removeBoundsListener(this);
                item.setOpaque(null);
            }
            this.drawables.clear();
        }

        @Override
        public void onBoundsChanged(GLMapItem2 item, GeoBounds bnds) {
            synchronized (GLQuadtreeNode2.this.root) {
                // remove without invalidation
                this.removeImpl(item, false);
                // add without invalidation
                GLQuadtreeNode2.this.root.addImpl(item);
                // mark layer invalid
                GLQuadtreeNode2.this.invalidate();
            }
        }
    }

    final static class DepthState {
        int[] func = new int[1];
        boolean[] mask = new boolean[1];
        boolean enabled;

        DepthState() {
            enabled = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST);
            GLES30.glGetBooleanv(GLES30.GL_DEPTH_WRITEMASK, mask, 0);
            GLES30.glGetIntegerv(GLES30.GL_DEPTH_FUNC, func, 0);
        }

        void set() {
            if (this.enabled)
                GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            else
                GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glDepthMask(this.mask[0]);
            GLES30.glDepthFunc(this.func[0]);
        }
    }

    final static class SurfacePassBookends {
        static boolean fillDepthBuffer = false;

        public final GLMapRenderable2 entry = new GLMapRenderable2() {
            @Override
            public void draw(GLMapView view, int renderPass) {
                if (!MathUtils.hasBits(renderPass,
                        GLMapView.RENDER_PASS_SURFACE))
                    return;
                if (view.drawSrid != 4978)
                    return;

                // record initial depth state
                state = new DepthState();

                // compute earth center
                view.scratch.pointD.x = 0d;
                view.scratch.pointD.y = 0d;
                view.scratch.pointD.z = 0d;
                view.scene.forward.transform(view.scratch.pointD,
                        view.scratch.pointD);

                // turn on depth
                GLES20FixedPipeline.glDepthMask(true);
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_DEPTH_TEST);
                GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_LEQUAL);

                if (!fillDepthBuffer)
                    return;

                // fill plane at earth center
                FloatBuffer fb = null;
                try {
                    fb = Unsafe.allocateDirect(4 * 3 * 4)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    int idx = 0;
                    fb.put(idx++, view._bottom); // lower-left
                    fb.put(idx++, view._left);
                    fb.put(idx++, (float) view.scratch.pointD.z);
                    fb.put(idx++, view._top); // upper-left
                    fb.put(idx++, view._left);
                    fb.put(idx++, (float) view.scratch.pointD.z);
                    fb.put(idx++, view._bottom); // lower-right
                    fb.put(idx++, view._right);
                    fb.put(idx++, (float) view.scratch.pointD.z);
                    fb.put(idx++, view._top); // upper-right
                    fb.put(idx++, view._right);
                    fb.put(idx++, (float) view.scratch.pointD.z);

                    // transparent
                    GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 1f);

                    GLES20FixedPipeline.glEnableClientState(
                            GLES20FixedPipeline.GL_VERTEX_ARRAY);
                    GLES20FixedPipeline.glVertexPointer(3,
                            GLES20FixedPipeline.GL_FLOAT, 0, fb);
                    GLES20FixedPipeline.glDrawArrays(
                            GLES20FixedPipeline.GL_TRIANGLE_STRIP, 0, 4);
                    GLES20FixedPipeline.glDisableClientState(
                            GLES20FixedPipeline.GL_VERTEX_ARRAY);
                } finally {
                    if (fb != null)
                        Unsafe.free(fb);
                }
            }

            @Override
            public void release() {
            }

            @Override
            public int getRenderPass() {
                return GLMapView.RENDER_PASS_SURFACE;
            }
        };

        public final GLMapRenderable2 exit = new GLMapRenderable2() {
            @Override
            public void draw(GLMapView view, int renderPass) {
                if (state != null)
                    state.set();
            }

            @Override
            public void release() {
            }

            @Override
            public int getRenderPass() {
                return GLMapView.RENDER_PASS_SURFACE;
            }
        };

        private DepthState state = null;
    }

    final static class SurfacePassEntry implements GLMapRenderable2 {
        @Override
        public void draw(GLMapView view, int renderPass) {

        }

        @Override
        public void release() {
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SURFACE;
        }
    }

    final static class SurfacePassExit implements GLMapRenderable2 {

        @Override
        public void draw(GLMapView view, int renderPass) {
            // restore depth settings
        }

        @Override
        public void release() {
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SURFACE;
        }
    }

    private class GLBatchRenderer
            implements GLMapRenderable2, LayerHitTestControl {
        final LinkedList<GLMapItem2> batchables;

        public GLBatchRenderer() {
            this.batchables = new LinkedList<>();
        }

        void addSprite(GLMapItem2 item) {
            this.batchables.add(item);
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SURFACE
                    | GLMapView.RENDER_PASS_SPRITES;
        }

        @Override
        public void draw(GLMapView view, int renderPass) {
            final boolean isLegacyRenderPump = (view.currentPass.drawTilt == 0d)
                    && (renderPass == (GLMapView.RENDER_PASS_SURFACE
                            | GLMapView.RENDER_PASS_SPRITES));

            boolean inBatch = false;

            int[] i = new int[1];
            GLES20FixedPipeline.glGetIntegerv(
                    GLES20FixedPipeline.GL_ACTIVE_TEXTURE, i, 0);
            final int originalTextureUnit = i[0];

            GLQuadtreeNode2.this.batch.begin(GLRenderBatch2.HINT_TWO_DIMENSION);

            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION,
                    view.scratch.matrixF, 0);
            GLQuadtreeNode2.this.batch.setMatrix(
                    GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);

            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW,
                    view.scratch.matrixF, 0);
            GLQuadtreeNode2.this.batch.setMatrix(
                    GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);

            for (GLMapItem2 item : this.batchables) {
                if (item instanceof GLMapBatchable2) {
                    if (!inBatch) {
                        GLQuadtreeNode2.this.batch.begin();
                        inBatch = true;
                    }
                    ((GLMapBatchable2) item).batch(view,
                            GLQuadtreeNode2.this.batch, renderPass);
                } else if (item instanceof GLMapBatchable) {
                    final GLMapBatchable batchable = (GLMapBatchable) item;

                    // XXX - until we have a 3D batch renderer, we will only support
                    //       batching if we're doing a legacy type pump (both
                    //       surface and sprites with no tilt)
                    if (isLegacyRenderPump && batchable.isBatchable(view)) {
                        if (!inBatch) {
                            GLQuadtreeNode2.this.batch.begin();
                            inBatch = true;
                        }
                        batchable.batch(view, GLQuadtreeNode2.this.batchLegacy);
                    } else {
                        if (inBatch) {
                            GLQuadtreeNode2.this.batch.end();
                            inBatch = false;
                        }
                        item.draw(view, renderPass);
                    }
                } else {
                    throw new IllegalStateException();
                }
            }
            if (inBatch)
                GLQuadtreeNode2.this.batch.end();

            GLES20FixedPipeline.glActiveTexture(originalTextureUnit);
        }

        @Override
        public void release() {
            for (GLMapItem2 item : this.batchables)
                item.release();
            this.batchables.clear();
        }

        @Override
        public Collection<?> getHitTestList() {
            return new ArrayList<>(batchables);
        }
    }

    private static class DepthComparator implements Comparator<GLMapItem2> {

        GeoPoint camera;
        MutableGeoBounds scratchBounds;
        final double metersDegLat;
        final double metersDegLng;

        DepthComparator(ViewState state) {
            // ortho camera is "in scene" at higher zoom levels, so we need to
            // push it back for purposes of computing range for depth sort. The
            // distance we move the camera back isn't strictly important as we
            // are only looking to establish relative back to front
            if (!state.scene.camera.perspective) {
                final double dx = state.scene.camera.location.x
                        - state.scene.camera.target.x;
                final double dy = state.scene.camera.location.y
                        - state.scene.camera.target.y;
                final double dz = state.scene.camera.location.z
                        - state.scene.camera.target.z;
                final double range = Math.max(MathUtils.distance(
                        dx * state.scene.displayModel.projectionXToNominalMeters,
                        dy * state.scene.displayModel.projectionYToNominalMeters,
                        dz * state.scene.displayModel.projectionZToNominalMeters,
                        0d, 0d, 0d), 4000);

                camera = state.scene.mapProjection
                        .inverse(
                                new PointD(
                                        state.scene.camera.target.x + (dx
                                                * range)
                                                / state.scene.displayModel.projectionXToNominalMeters,
                                        state.scene.camera.target.y + (dy
                                                * range)
                                                / state.scene.displayModel.projectionYToNominalMeters,
                                        state.scene.camera.target.z + (dz
                                                * range)
                                                / state.scene.displayModel.projectionZToNominalMeters),
                                null);
            } else {
                camera = state.scene.mapProjection
                        .inverse(state.scene.camera.location, null);
            }

            scratchBounds = new MutableGeoBounds(GeoPoint.ZERO_POINT,
                    GeoPoint.ZERO_POINT);

            final double rlat = Math.toRadians(camera.getLatitude());
            metersDegLat = 111132.92 - 559.82 * Math.cos(2 * rlat)
                    + 1.175 * Math.cos(4 * rlat);
            metersDegLng = 111412.84 * Math.cos(rlat)
                    - 93.5 * Math.cos(3 * rlat);
        }

        @Override
        public int compare(GLMapItem2 a, GLMapItem2 b) {
            // check if the same object
            MapItem aSub = a.getSubject(), bSub = b.getSubject();
            if (aSub.getSerialId() == bSub.getSerialId())
                return 0;

            // Both items are part of the same shape - sort by Z always
            MapGroup aGroup = aSub.getGroup(), bGroup = bSub.getGroup();
            if (aGroup != null && bGroup != null) {
                MapGroup aChildGroup = aSub instanceof ParentMapItem
                        ? ((ParentMapItem) aSub).getChildMapGroup()
                        : null;
                MapGroup bChildGroup = bSub instanceof ParentMapItem
                        ? ((ParentMapItem) bSub).getChildMapGroup()
                        : null;
                if (aGroup == bChildGroup || bGroup == aChildGroup
                        || aGroup == bGroup && aGroup.hasMetaValue("shapeUID"))
                    return ITEM_COMPARATOR.compare(a, b);
            }

            // check render pass. surface only items will be sorted at the head
            // of the draw list and will undergo Z comparison; only sprites are
            // depth sorted
            final boolean asurface = (a
                    .getRenderPass() == GLMapView.RENDER_PASS_SURFACE);
            final boolean bsurface = (b
                    .getRenderPass() == GLMapView.RENDER_PASS_SURFACE);
            if (asurface && bsurface)
                return ITEM_COMPARATOR.compare(a, b);
            else if (asurface)
                return -1;
            else if (bsurface)
                return 1;

            a.getBounds(scratchBounds);
            final double adlat = ((scratchBounds.getNorth()
                    + scratchBounds.getSouth()) / 2d);
            final double adlng = ((scratchBounds.getEast()
                    + scratchBounds.getWest()) / 2d);

            b.getBounds(scratchBounds);
            final double bdlat = ((scratchBounds.getNorth()
                    + scratchBounds.getSouth()) / 2d);
            final double bdlng = ((scratchBounds.getEast()
                    + scratchBounds.getWest()) / 2d);

            // approximate meters per degree given center

            // compute distance-squared for comparison
            final double aDistSq = GeoCalculations.slantDistanceTo(camera,
                    new GeoPoint(adlat, adlng));
            final double bDistSq = GeoCalculations.slantDistanceTo(camera,
                    new GeoPoint(bdlat, bdlng));

            if (aDistSq < bDistSq)
                return 1;
            else if (aDistSq > bDistSq)
                return -1;
            else
                return ITEM_COMPARATOR.compare(a, b);
        }
    }

    private class Renderable implements GLMapRenderable2 {

        @Override
        public void draw(GLMapView view, int renderPass) {
            if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
                for (GLMapRenderable2 r : surfaceRenderables)
                    r.draw(view, renderPass);
            if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
                for (GLMapRenderable2 r : spriteRenderables)
                    r.draw(view, renderPass);
        }

        @Override
        public void release() {
            for (GLMapRenderable2 r : surfaceRenderables)
                r.release();
            for (GLMapRenderable2 r : spriteRenderables)
                r.release();
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SURFACE
                    | GLMapView.RENDER_PASS_SPRITES;
        }
    }
}
