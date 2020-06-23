package transapps.mapi;

import transapps.mapi.overlay.MapOverlay;
import android.view.KeyEvent;
import android.view.MotionEvent;


/**
 * This is the overlay manager which is used for managing {@link MapOverlay} objects.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface MapOverlayManager
{
    /**
     * This will get the overlay at the given index
     * 
     * @param index
     *            The index of the overlay to get
     * @return The overlay at the given index
     * @throws IndexOutOfBoundsException
     *             - if the index is out of range (index < 0 || index >= size())
     */
    public MapOverlay get( final int index );

    /**
     * This returns the number of overlays being managed
     * 
     * @return The number of overlays being managed by the manager
     */
    public int size();

    /**
     * This will add the overlay to the manager
     * 
     * @param overlay
     *            The overlay to add to the manager
     * @return true if overlays being managed by the manager changed as a result of the call
     */
    public boolean add( final MapOverlay overlay );
    
    /**
     * This will remove the overlay from the manager
     * 
     * @param overlay
     *            THe overlay to remove from the manager
     * @return true if the overlay was removed
     */
    public boolean remove( final MapOverlay overlay );
    
    /**
     * This will add the overlay to the manager at the given index. If the overlay is already being
     * managed it will not move to the given position.
     * 
     * @param index
     *            The index at which the overlay should be inserted at
     * @param overlay
     *            The overlay to be added to the manager
     * @throws IndexOutOfBoundsException
     *             - if the index is out of range (index < 0 || index > size())
     */
    public void add( final int index, final MapOverlay overlay );

    /**
     * This will remove the overlay at the given index
     * 
     * @param index
     *            The index of the overlay to remove
     * @return The overlay that was removed from the manager
     * @throws IndexOutOfBoundsException
     *             - if the index is out of range (index < 0 || index >= size())
     */
    public MapOverlay remove( final int index );

    /**
     * This will pass the key down event to overlays which are being managed until it has been
     * handled or all the overlays have been provided the event to handle.
     * 
     * @param keyCode
     *            The code of the key
     * @param event
     *            The key event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onKeyDown( final int keyCode, final KeyEvent event, final MapView pMapView );

    /**
     * This will pass the key up event to overlays which are being managed until it has been handled
     * or all the overlays have been provided the event to handle.
     * 
     * @param keyCode
     *            The code of the key
     * @param event
     *            The key event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onKeyUp( final int keyCode, final KeyEvent event, final MapView pMapView );

    /**
     * This will pass the touch event to overlays which are being managed until it has been handled
     * or all the overlays have been provided the event to handle.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onTouchEvent(final MotionEvent event, final MapView pMapView);

    /**
     * This will pass the trackball event to overlays which are being managed until it has been
     * handled or all the overlays have been provided the event to handle.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onTrackballEvent(final MotionEvent event, final MapView pMapView);

    /**
     * This will pass the double tap event to overlays which are being managed until it has been
     * handled or all the overlays have been provided the event to handle.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onDoubleTap(final MotionEvent e, final MapView pMapView);

    /**
     * This will pass the on down event to overlays which are being managed until it has been
     * handled or all the overlays have been provided the event to handle.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onDown(final MotionEvent pEvent, final MapView pMapView);

    /**
     * This will pass the fling event to overlays which are being managed until it has been handled
     * or all the overlays have been provided the event to handle.
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
            final float pVelocityX, final float pVelocityY, final MapView pMapView);

    /**
     * This will pass the long press event to overlays which are being managed until it has been
     * handled or all the overlays have been provided the event to handle.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onLongPress(final MotionEvent pEvent, final MapView pMapView);

    /**
     * This will pass the on scroll event to overlays which are being managed until it has been
     * handled or all the overlays have been provided the event to handle.
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
            final float pDistanceX, final float pDistanceY, final MapView pMapView);

    /**
     * This will pass the on single tap up event to overlays which are being managed until it has
     * been handled or all the overlays have been provided the event to handle.
     * 
     * @param event
     *            The motion/touch event
     * @param mapView
     *            The map view the event took place on
     * @return true if the event was handled
     */
    public boolean onSingleTapUp(final MotionEvent pEvent, final MapView pMapView);
}
