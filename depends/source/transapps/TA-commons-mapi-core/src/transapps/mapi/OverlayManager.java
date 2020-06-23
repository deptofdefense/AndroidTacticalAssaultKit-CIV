package transapps.mapi;

import transapps.mapi.overlay.Overlay;

import android.graphics.Canvas;
import android.graphics.Point;
import android.view.KeyEvent;
import android.view.MotionEvent;


/**
 * This is the overlay manager which is used for managing {@link Overlay} objects.
 *
 * @deprecated in NW SDK 1.1.6.3 use {@link MapOverlayManager} instead
 */
public interface OverlayManager {

    public Overlay get(final int pIndex);

    public int size();

    public boolean add(final Overlay pElement);
    
    public boolean remove(final Object pElement);
    
    public void add(final int pIndex, final Overlay pElement);

    public Overlay remove(final int pIndex);

    public Overlay set(final int pIndex, final Overlay pElement);

    /**
     * Gets the optional TilesOverlay class.
     *
     * @return the tilesOverlay
     */
    public Overlay getTilesOverlay();

    /**
     * Sets the optional TilesOverlay class. If set, this overlay will be drawn before all other
     * overlays and will not be included in the editable list of overlays and can't be cleared
     * except by a subsequent call to setTilesOverlay().
     *
     * @param tilesOverlay
     *            the tilesOverlay to set
     */
    public void setTilesOverlay(Overlay tilesOverlay);

    public void onDraw(final Canvas c, final MapView pMapView);

    public void onDetach(final MapView pMapView);

    public boolean onKeyDown(final int keyCode, final KeyEvent event, final MapView pMapView);

    public boolean onKeyUp(final int keyCode, final KeyEvent event, final MapView pMapView);

    public boolean onTouchEvent(final MotionEvent event, final MapView pMapView);

    public boolean onTrackballEvent(final MotionEvent event, final MapView pMapView);

    public boolean onSnapToItem(final int x, final int y, final Point snapPoint,
            final MapView pMapView);

    /** GestureDetector.OnDoubleTapListener **/

    public boolean onDoubleTap(final MotionEvent e, final MapView pMapView);

    public boolean onDoubleTapEvent(final MotionEvent e, final MapView pMapView);

    public boolean onSingleTapConfirmed(final MotionEvent e, final MapView pMapView);

    /** OnGestureListener **/

    public boolean onDown(final MotionEvent pEvent, final MapView pMapView);

    public boolean onFling(final MotionEvent pEvent1, final MotionEvent pEvent2,
            final float pVelocityX, final float pVelocityY, final MapView pMapView);

    public boolean onLongPress(final MotionEvent pEvent, final MapView pMapView);

    public boolean onScroll(final MotionEvent pEvent1, final MotionEvent pEvent2,
            final float pDistanceX, final float pDistanceY, final MapView pMapView);

    public void onShowPress(final MotionEvent pEvent, final MapView pMapView);

    public boolean onSingleTapUp(final MotionEvent pEvent, final MapView pMapView);
}
