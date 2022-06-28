
package com.atakmap.android.maps;

import com.atakmap.android.track.crumb.Crumb;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.annotation.DeprecatedApi;

/**
 * @deprecated
 */
@Deprecated
@DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
public class PersistentCircleCrumbTrail extends MapItem {

    private static final String TAG = "PersistentCircleCrumbTrail";

    private PointMapItem theTarget;
    private int interval = 3; // seconds
    private int time = 1; // minutes

    public CircleCrumb first;
    public CircleCrumb last;
    protected int crumbCount = 0;

    private Thread trackThread;
    private boolean go = true;
    private double distThreshold = 0.00005; // DISTANCE THRESHOLD .00005 ~ 5m
    @SuppressWarnings("unused")
    private final MapView _viewForPosting;

    private CrumbItemChangedListener crumbItemChangedListener;

    private int defaultColor = 0xFF0000FF;

    private final ConcurrentLinkedQueue<OnCrumbTrailIntervalChangedListener> crumbTrailIntervalListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnCrumbTrailTimeChangedListener> crumbTrailTimeListeners = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<OnCrumbTrailUpdateListener> crumbTrailUpdateListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CrumbLogListener> crumbLogListeners = new ConcurrentLinkedQueue<>();

    public PersistentCircleCrumbTrail(final MapView mapView,
            final PointMapItem target) {
        super(MapGroup.createMapGroupSerialId(), UUID.randomUUID().toString());

        _viewForPosting = mapView;

        this.init();

        this.theTarget = target;
        if (this.theTarget != null)
            theTarget.getGroup().addOnItemListChangedListener(
                    this.crumbItemChangedListener);
    }

    private void init() {

        this.setMetaBoolean("addToObjList", false);

        this.crumbItemChangedListener = new CrumbItemChangedListener();

        distThreshold = 0.00005;

    }

    public PointMapItem getTarget() {
        return this.theTarget;
    }

    public void setTarget(PointMapItem target) {
        if (this.theTarget != null && this.theTarget.getGroup() != null)
            theTarget.getGroup().removeOnItemListChangedListener(
                    this.crumbItemChangedListener);
        this.theTarget = target;
        theTarget.getGroup().addOnItemListChangedListener(
                this.crumbItemChangedListener);
    }

    private class CrumbItemChangedListener implements
            MapGroup.OnItemListChangedListener {
        @Override
        public void onItemAdded(MapItem item, MapGroup group) {

        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            if (item == PersistentCircleCrumbTrail.this.theTarget) {
                PersistentCircleCrumbTrail.this.clearAllCrumbs();
            }
        }
    }

    public int assignCrumbColor(Map<String, Object> markerData) {
        return defaultColor;
    }

    public void start() {
        trackThread = new Thread(TAG) {
            @Override
            public void run() {

                while (go) {
                    synchronized (PersistentCircleCrumbTrail.this) {
                        // add another breadcrumb assuming it's far enough away
                        // from the last one, and set the color based on the CoT
                        // type
                        boolean doCrumb = true;

                        GeoPoint newP = theTarget.getPoint();
                        if (last != null) {
                            // Crumb oldCrumb = last;
                            GeoPoint oldP = last.getPoint();
                            double x = oldP.getLongitude()
                                    - newP.getLongitude();
                            double y = oldP.getLatitude() - newP.getLatitude();

                            if (Math.abs(x) < distThreshold
                                    && Math.abs(y) < distThreshold) {
                                doCrumb = false;
                            }

                            double d = Math.toDegrees(Math.atan2(y, x));
                            last.setDirection(d);
                        }

                        if (doCrumb) {

                            CircleCrumb crumb;

                            int color = getDefaultColor();

                            Map<String, Object> markerData = new HashMap<>();
                            if (theTarget instanceof Marker) {
                                Marker t = (Marker) theTarget;

                                if (t.getMetaString("sensorDbId", null) != null
                                        && t.getIcon() != null) {
                                    t.getMetaData(markerData);
                                    color = assignCrumbColor(markerData);
                                }
                            }

                            crumb = buildCircleCrumb(markerData, newP, color);
                            final CircleCrumb finalCrumb = crumb;

                            _viewForPosting.post(new Runnable() {
                                @Override
                                public void run() {
                                    addCrumb(finalCrumb);
                                    finalCrumb
                                            .setVisible(
                                                    PersistentCircleCrumbTrail.this
                                                            .getVisible());
                                }
                            });

                        }

                    }

                    try {
                        Thread.sleep(interval * 1000);
                    } catch (InterruptedException e) {
                        go = false;
                    }
                }

            }
        };
        trackThread.start();
    }

    protected CircleCrumb buildCircleCrumb(Map<String, Object> markerData,
            GeoPoint p, int color) {
        return new CircleCrumb(p, color);
    }

    public void stop() {
        go = false;
        trackThread.interrupt();
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        if (this.interval != interval) {
            this.interval = interval;

            for (OnCrumbTrailIntervalChangedListener l : crumbTrailIntervalListeners) {
                l.onCrumbTrailIntervalChanged(this);
            }
        }

    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        if (this.time != time) {
            this.time = time;

            for (OnCrumbTrailTimeChangedListener l : crumbTrailTimeListeners) {
                l.onCrumbTrailTimeChanged(this);
            }
        }

    }

    /**
     * @return the defaultColor
     */
    public int getDefaultColor() {
        return defaultColor;
    }

    /**
     * @param defaultColor
     *            the defaultColor to set
     */
    public void setDefaultColor(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    protected void addCrumb(CircleCrumb crumb) {
        synchronized (this) {

            if (crumb != null) {
                if (last != null) {
                    last.next = crumb;
                    crumb.prev = last;
                }
                last = crumb;

                if (first == null) {
                    first = crumb;
                }
                ++crumbCount;

                if (this.getGroup() != null)
                    this.getGroup().addItem(crumb);

                for (OnCrumbTrailUpdateListener l : crumbTrailUpdateListeners) {
                    l.onCrumbAdded(crumb);
                }
                for (CrumbLogListener l : crumbLogListeners) {
                    l.logCrumb(crumb);
                }
            }
        }

    }

    protected void removeOldestCrumb() {
        synchronized (this) {
            CircleCrumb c = first;

            if (c != null) {
                first = (CircleCrumb) first.next;
                c.next = null;
                c.prev = null;

                if (this.getGroup() != null)
                    this.getGroup().removeItem(c);

                for (OnCrumbTrailUpdateListener l : crumbTrailUpdateListeners) {
                    l.onCrumbRemoved(c);
                }
                c = null;
                --crumbCount;
            }
        }

    }

    public void clearAllCrumbs() {
        while (first != null) {
            removeOldestCrumb();
        }
    }

    public interface OnCrumbTrailIntervalChangedListener {
        void onCrumbTrailIntervalChanged(PersistentCircleCrumbTrail trail);
    }

    public interface OnCrumbTrailTimeChangedListener {
        void onCrumbTrailTimeChanged(PersistentCircleCrumbTrail trail);
    }

    public interface OnCrumbTrailUpdateListener {
        void onCrumbAdded(CircleCrumb crumb);

        void onCrumbRemoved(CircleCrumb crumb);
    }

    public interface CrumbLogListener {
        void logCrumb(CircleCrumb c);
    }

    @Override
    public void onAdded(MapGroup newParent) {
        super.onAdded(newParent);

        Crumb c = this.last;
        while (c != null) {
            newParent.addItem(c);
            c = c.prev;
        }
    }

    @Override
    public void onRemoved(MapGroup oldParent) {
        super.onRemoved(oldParent);

        Crumb c = this.last;
        while (c != null) {
            oldParent.removeItem(c);
            c = c.prev;
        }
    }

    public boolean toggleCrumbTrail() {
        synchronized (this) {
            boolean vis = !getVisible();
            setVisible(vis);

            Crumb c = last;
            while (c != null) {
                c.setVisible(vis);
                c = c.prev;
            }
            return vis;
        }
    }

}
