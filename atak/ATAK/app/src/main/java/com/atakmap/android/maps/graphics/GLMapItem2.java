
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.opengl.GLMapRenderable2;

public interface GLMapItem2 extends GLMapRenderable2 {

    MapItem getSubject();

    /**
     * Sets opaque data that can be used by the renderer. Only the object that has taken ownership
     * of rendering this <code>GLMapItem</code> should invoke this method.
     * <P>
     * This method serves as a convenience to allow the render to store application specific data
     * with an item.
     * 
     * @param opaque An object
     */
    void setOpaque(Object opaque);

    /**
     * Returns the data previously set via the call to {@link #setOpaque(Object)}. This method
     * should really only be invoked by the object that set the opaque data.
     * <P>
     * This method serves as a convenience to allow the render to obtain the application specific
     * data that has been previously associated with an item.
     * 
     * @return The previously set data.
     */
    Object getOpaque();

    /**
     * Start observing the subject. This method should register callback listeners on any properties
     * of the subject that would affect its rendered representation.
     * <P>
     * Invoking this method should generally refresh the state of the item.
     * <P>
     * <B>This method may not be invoked on the GL context thread.</B>
     */
    void startObserving();

    /**
     * Stops observing the subject. This method should unwind any callback listeners registered via
     * {@link #startObserving()}.
     * <P>
     * <B>This method may not be invoked on the GL context thread.</B>
     */
    void stopObserving();

    boolean isVisible();

    double getZOrder();

    double getMinDrawResolution();

    interface OnBoundsChangedListener {
        void onBoundsChanged(GLMapItem2 item, GeoBounds bounds);
    }

    void addBoundsListener(OnBoundsChangedListener l);

    void removeBoundsListener(OnBoundsChangedListener l);

    interface OnVisibleChangedListener {
        void onVisibleChanged(GLMapItem2 item, boolean visible);
    }

    void addVisibleListener(OnVisibleChangedListener l);

    void removeVisibleListener(OnVisibleChangedListener l);

    void getBounds(MutableGeoBounds bnds);
}
