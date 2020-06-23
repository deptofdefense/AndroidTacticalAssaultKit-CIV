
package com.atakmap.android.maps.graphics;

import android.graphics.PointF;
import android.graphics.RectF;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;

import java.util.concurrent.ConcurrentLinkedQueue;

/** @deprecated use {@link GLMapItem2} */
public abstract class GLMapItem implements MapItem.OnClickableChangedListener,
        MapItem.OnVisibleChangedListener, MapItem.OnZOrderChangedListener,
        GLMapRenderable {

    protected final MapItem subject;
    private Object opaque;

    public GLMapItem(MapRenderer surface, MapItem subject) {
        if (subject == null)
            throw new NullPointerException("subject cannot be null");

        this.renderContext = surface;
        this.subject = subject;
        visible = subject.getVisible();
        zOrder = subject.getZOrder();
        this.bounds = new MutableGeoBounds(0, 0, 0, 0);
    }

    public MapItem getSubject() {
        return this.subject;
    }

    /**
     * Sets opaque data that can be used by the renderer. Only the object that has taken ownership
     * of rendering this <code>GLMapItem</code> should invoke this method.
     * <P>
     * This method serves as a convenience to allow the render to store application specific data
     * with an item.
     * 
     * @param opaque An object
     */
    public final void setOpaque(Object opaque) {
        this.opaque = opaque;
    }

    /**
     * Returns the data previously set via the call to {@link #setOpaque(Object)}. This method
     * should really only be invoked by the object that set the opaque data.
     * <P>
     * This method serves as a convenience to allow the render to obtain the application specific
     * data that has been previously associated with an item.
     * 
     * @return The previously set data.
     */
    public final Object getOpaque() {
        return this.opaque;
    }

    /**
     * Start observing the subject. This method should register callback listeners on any properties
     * of the subject that would affect its rendered representation.
     * <P>
     * Invoking this method should generally refresh the state of the item.
     * <P>
     * <B>This method may not be invoked on the GL context thread.</B>
     */
    public void startObserving() {
        this.visible = subject.getVisible();
        this.zOrder = subject.getZOrder();
        this.minMapGsd = subject.getMetaDouble("minMapGsd", Double.MAX_VALUE);

        this.subject.addOnClickableChangedListener(this);
        this.subject.addOnVisibleChangedListener(this);
        this.subject.addOnZOrderChangedListener(this);
    }

    /**
     * Stops observing the subject. This method should unwind any callback listeners registered via
     * {@link #startObserving()}.
     * <P>
     * <B>This method may not be invoked on the GL context thread.</B>
     */
    public void stopObserving() {
        this.subject.removeOnVisibleChangedListener(this);
        this.subject.removeOnClickableChangedListener(this);
        this.subject.removeOnZOrderChangedListener(this);
    }

    @Override
    public void onClickableChanged(MapItem item) {
        // nothing so far
    }

    @Override
    public void onVisibleChanged(MapItem item) {
        final boolean v = item.getVisible();
        post(new Runnable() {
            @Override
            public void run() {
                // this check has to happen inside the post because otherwise,
                // rapidly toggling visiblity false and then true again turns
                // into just a false; the change back to true gets skipped since
                // visible hasn't been changed to false yet when it happens
                if (v != visible) {
                    visible = v;
                    OnVisibleChanged();
                }
            }
        });
    }

    @Override
    public void onZOrderChanged(MapItem item) {
        final double z = item.getZOrder();
        post(new Runnable() {
            @Override
            public void run() {
                zOrder = z;
            }
        });
    }

    @Override
    public abstract void draw(GLMapView ortho);

    @Override
    public void release() {
    }

    public boolean visible;
    public double zOrder;
    public double minMapGsd;

    protected void post(Runnable r) {
        renderContext.queueEvent(r);
    }

    public void OnBoundsChanged() {
        for (OnBoundsChangedListener l : boundsListeners) {
            l.onBoundsChanged(this);
        }
    }

    protected interface OnBoundsChangedListener {
        void onBoundsChanged(GLMapItem item);
    }

    public void addBoundsListener(OnBoundsChangedListener l) {
        if (!boundsListeners.contains(l)) {
            boundsListeners.add(l);
        }
    }

    public void removeBoundsListener(OnBoundsChangedListener l) {
        boundsListeners.remove(l);
    }

    public interface OnVisibleChangedListener {
        void onVisibleChanged(GLMapItem item);
    }

    public void addVisibleListener(OnVisibleChangedListener l) {
        if (!visibleListeners.contains(l))
            visibleListeners.add(l);
    }

    public void removeVisibleListener(OnVisibleChangedListener l) {
        visibleListeners.remove(l);
    }

    protected void OnVisibleChanged() {
        for (OnVisibleChangedListener l : visibleListeners)
            l.onVisibleChanged(this);
    }

    /**
      * Retrieve the bounding RectF of the current state of the Map. This accounts for the
      * OrthoMapView's focus, so DropDowns will be accounted for.
      * 
      * NOTE- the RectF this returns is not a valid RectF since the origin coordinate
      * is in the lower left (ll is 0,0). Therefore the RectF.contains(PointF) method 
      * will not work to determine if a point falls inside the bounds.
      *
      * @return The bounding RectF
      */
    protected RectF getWidgetViewF() {
        return getDefaultWidgetViewF(this.renderContext);
    }

    protected static RectF getDefaultWidgetViewF(MapRenderer ctx) {
        // Could be in half or third display of dropdown, so use the offset;
        float right = ((GLMapView) ctx).focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        float top = ((GLMapView) ctx).focusy * 2;
        return new RectF(0f, top - MapView.getMapView().getActionBarHeight(),
                right, 0);
    }

    /**
     * Retrieve the bounding RectF of the current state of the Map. This accounts for the
     * OrthoMapView's focus, so DropDowns will be accounted for.
     * 
     * NOTE- the RectF this returns is not a valid RectF since the origin coordinate
     * is in the lower left (ll is 0,0). Therefore the RectF.contains(PointF) method 
     * will not work to determine if a point falls inside the bounds.
     *
     * @return The bounding RectF
     */
    protected RectF getWidgetViewWithoutActionbarF() {
        // Could be in half or third display of dropdown, so use the offset;
        float right = ((GLMapView) this.renderContext).focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        //float top = this.orthoView.focusy * 2;
        float top = ((GLMapView) this.renderContext).getTop();
        return new RectF(0f + 20,
                top - (MapView.getMapView().getActionBarHeight() + 20),
                right - 20, 0f + 20);
    }

    /**
     * Provides the top and the bottom most intersection points.
     */
    public static PointF[] _getIntersectionPoint(RectF r, PointF cF,
            PointF vF) {

        if (r.left < cF.x && cF.x < r.right && r.bottom < cF.y && cF.y < r.top
                &&
                r.left < vF.x && vF.x < r.right && r.bottom < vF.y
                && vF.y < r.top) {
            return new PointF[] {
                    cF, vF
            };
        }

        PointF[] ret = new PointF[2];
        Vector2D[] rets = new Vector2D[4];
        Vector2D c = new Vector2D(cF.x, cF.y);
        Vector2D v = new Vector2D(vF.x, vF.y);

        Vector2D topLeft = new Vector2D(r.left, r.top);
        Vector2D topRight = new Vector2D(r.right, r.top);
        Vector2D botRight = new Vector2D(r.right, r.bottom);
        Vector2D botLeft = new Vector2D(r.left, r.bottom);

        // Start at top line and go clockwise

        rets[0] = Vector2D
                .segmentToSegmentIntersection(topLeft, topRight, c, v);
        rets[1] = Vector2D.segmentToSegmentIntersection(topRight, botRight, c,
                v);
        rets[2] = Vector2D
                .segmentToSegmentIntersection(botRight, botLeft, c, v);
        rets[3] = Vector2D.segmentToSegmentIntersection(botLeft, topLeft, c, v);

        // Check the returned values - returns both the top and the bottom intersection points.
        for (int i = 0; i < 4; i++) {
            // Check to see if it intersected
            if (rets[i] != null) {
                if (i < 2) {
                    //Log.d("SHB", "interesection detected entry #" + i);
                    if (ret[0] == null)
                        ret[0] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                    else
                        ret[1] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                } else {
                    //Log.d("SHB", "interesection detected entry #" + i);
                    if (ret[1] == null)
                        ret[1] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                    else
                        ret[0] = new PointF((float) rets[i].x,
                                (float) rets[i].y);
                }
            }
        }

        return ret;
    }

    public final GeoBounds getBounds() {
        return this.bounds;
    }

    protected final MutableGeoBounds bounds;

    protected final MapRenderer renderContext;

    private final ConcurrentLinkedQueue<OnBoundsChangedListener> boundsListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnVisibleChangedListener> visibleListeners = new ConcurrentLinkedQueue<>();
}
