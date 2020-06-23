package transapps.mapi.events;

import transapps.mapi.MapView;



/*
 * The event generated when a map has finished scrolling to the coordinates (<code>x</code>,<code>y</code>).
 *
 * @author Theodore Hong
 */
public class ScrollEvent extends MapEvent {
    
    private static final  Pool<ScrollEvent> pool = new Pool<ScrollEvent>() {
        protected ScrollEvent create() {
            return new ScrollEvent();
        }
    };
    
    public static ScrollEvent obtain( ScrollEvent copy ) {
        return obtain(copy.getSource(), copy.x, copy.y,copy.type);
    }
    
    public static ScrollEvent obtain( MapView mapView, int x, int y, int type ) {
        ScrollEvent create = pool.obtain();
        create.setSource(mapView);
        create.x = x;
        create.y = y;
        create.type = type;
        return create;
    }
    
    public static final int TYPE_START = 0x001;
    public static final int TYPE_MOVE = 0x002;
    public static final int TYPE_END = 0x004;
    
    private int type;
    private int x;
    private int y;

    public ScrollEvent() {
        super(pool);
    }

    /*
     * Return the x-coordinate scrolled to.
     */
    public int getX() {
        return x;
    }

    /*
     * Return the y-coordinate scrolled to.
     */
    public int getY() {
        return y;
    }
    
    /**
     * @return start scroll, move, or end
     */
    public int getType() {
        return type;
    }
}
