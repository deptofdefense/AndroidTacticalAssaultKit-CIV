package transapps.mapi.overlay;

import transapps.mapi.MapView;
import transapps.mapi.canvas.MapCanvasDrawParams;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;


/**
 * Base class representing an overlay which may be displayed on top of a {@link MapView}. To add an
 * overlay, subclass this class, create an instance, and add it to the list obtained from
 * getOverlays() of {@link MapView}.
 * 
 * This class implements a form of Gesture Handling similar to
 * {@link android.view.GestureDetector.SimpleOnGestureListener} and
 * {@link GestureDetector.OnGestureListener}. The difference is there is an additional argument for
 * the item.
 * 
 * @author Nicolas Gramlich and SRA
 * @since NW SDK 1.0.34
 */
public abstract class MapOverlay {

    // ===========================================================
    // Fields
    // ===========================================================

    /**
     * This stores the enabled state for the overlay
     */
    private boolean enabled = true;

    // ===========================================================
    // Constructors
    // ===========================================================


    // ===========================================================
    // Getter & Setter
    // ===========================================================

        /**
     * Sets whether the Overlay is marked to be enabled. This setting does nothing by default, but
     * should be checked before calling draw().
     * 
     * @param pEnabled
     *            The new enabled state of the overlay
     */
    public void setEnabled( final boolean pEnabled )
    {
        enabled = pEnabled;
    }

    /**
     * Specifies if the Overlay is marked to be enabled. This should be checked before calling
     * draw().
     *
     * @return true if the Overlay is marked enabled, false otherwise
     */
    public boolean isEnabled( )
    {
        return enabled;
    }

    // ===========================================================
    // Methods for SuperClass/Interfaces
    // ===========================================================

    /**
     * This is called when the layer needs to draw itself on the map. This method will be called
     * every frame and the layer needs to continue drawing an item for that item to continue to be
     * shown on the map (assuming that map location it is being drawn to is visible).
     * 
     * @param drawParams
     *            An object which contains a collection of other objects and useful values and
     *            methods which should be used for drawing the contents of the overlay
     */
    public abstract void draw( final MapCanvasDrawParams drawParams );

    // ===========================================================
    // Methods
    // ===========================================================

    /**
     * This method will be called when the overlay has been attached to the map view
     * 
     * @param mapView
     *            The map view the overlay is being attached to
     */
    public void onAttach( final MapView mapView )
    {
    }

    /**
     * This method will be called when the overlay has been detached from the map view.
     * 
     * @param mapView
     *            The map view the overlay is being detached from
     */
    public void onDetach( final MapView mapView )
    {
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param keyCode
     *            The code of the key
     * @param event
     *            The key event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onKeyDown( final int keyCode, final KeyEvent event, final MapView mapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param keyCode
     *            The code of the key
     * @param event
     *            The key event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onKeyUp( final int keyCode, final KeyEvent event, final MapView mapView )
    {
        return false;
    }

    /**
     * <b>You can prevent all(!) other Touch-related events from happening!</b><br />
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onTouchEvent( final MotionEvent event, final MapView mapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onTrackballEvent( final MotionEvent event, final MapView mapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onDoubleTap( final MotionEvent e, final MapView mapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onDown( final MotionEvent e, final MapView mapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param pEvent1
     *            The first motion/touch event that started the fling
     * @param pEvent2
     *            The motion/touch event that triggered the fling
     * @param pVelocityX
     *            The fling velocity in the x direction in pixels per second
     * @param pVelocityY
     *            The fling velocity in the y direction in pixels per second
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onFling(final MotionEvent pEvent1, final MotionEvent pEvent2,
                            final float pVelocityX, final float pVelocityY, final MapView pMapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onLongPress( final MotionEvent e, final MapView mapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param pEvent1
     *            The first motion/touch event that started the scroll
     * @param pEvent2
     *            The motion/touch event that triggered the scroll
     * @param pDistanceX
     *            The distance along the X axis that has been scrolled since the last call to
     *            onScroll
     * @param pDistanceY
     *            The distance along the Y axis that has been scrolled since the last call to
     *            onScroll
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onScroll(final MotionEvent pEvent1, final MotionEvent pEvent2,
                             final float pDistanceX, final float pDistanceY, final MapView pMapView )
    {
        return false;
    }

    /**
     * By default does nothing (<code>return false</code>). If you handled the Event, return
     * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
     * none of the following Overlays or the underlying {@link MapView} has the chance to handle
     * this event.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onSingleTapUp( final MotionEvent e, final MapView mapView )
    {
        return false;
    }

}
