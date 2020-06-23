package transapps.mapi.events;

import transapps.mapi.MapView;


/*
 * The event generated when a map has finished zooming to the level <code>zoomLevel</code>.
 *
 * @author Theodore Hong
 */
public class ZoomEvent extends MapEvent {
    
    private static final  Pool<ZoomEvent> pool = new Pool<ZoomEvent>() {
        protected ZoomEvent create() {
            return new ZoomEvent();
        }
    };

    public static ZoomEvent obtain( ZoomEvent copy ) {
        return obtain(copy.getSource(), copy.zoomLevel, copy.type);
    }
    
    public static ZoomEvent obtain( MapView mapView, int zoom, int type ) {
        ZoomEvent create = pool.obtain();
        create.setSource(mapView);
        create.zoomLevel = zoom;
        create.type = type;
        return create;
    }
    
    public static final int TYPE_START = 0x001;
    public static final int TYPE_MOVE = 0x002;
    public static final int TYPE_END = 0x004;
    
    private int type;
    private int zoomLevel;

    public ZoomEvent() {
        super( pool );
    }

    /*
     * Return the zoom level zoomed to.
     */
    public int getZoomLevel() {
        return zoomLevel;
    }
    
    /**
     * @return start scroll, move, or end
     */
    public int getType() {
        return type;
    }
}
