
package com.atakmap.android.maps;

import android.content.SharedPreferences;
import android.graphics.Color;

import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.icons.Icon2525cIconAdapter;
import com.atakmap.android.track.crumb.Crumb;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.task.CreateTracksTask;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.annotation.DeprecatedApi;

/**
 * CrumbTrail manages crumbs for a map item using a dedicated trackThread
 * The intervalMillis is configurable and specifies how often to store crumbs in the CrumbDatabase
 * Crumbs are only stored if the map item has moved by distThreshold
 * Crumbs are sent to the UI no more than once every 2 seconds
 */
public class CrumbTrail extends MapItem implements FOVFilter.Filterable,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "CrumbTrail";

    private PointMapItem theTarget;

    /**
     * Last time a crumb was sent to UI
     */
    private long lastUICrumbTime = 0;

    /**
     * Minimum time between UI crumbs
     */
    private static final long minimumUICrumbTime = 2000;

    private int maxCrumbs = 200;
    private int transparencyRate = 1;
    private final boolean unlimited;

    public Crumb first;
    public Crumb last; //last crumb sent to UI
    private GeoPoint lastPoint; //last point logged in DB
    private int crumbCount = 0;
    private boolean tracking;

    public int size;
    public boolean drawLineToSurface = false;

    @SuppressWarnings("unused")
    private final SharedPreferences prefs;
    private final MapView _mapView;
    private final boolean circles;

    private CrumbItemChangedListener crumbItemChangedListener;

    private final ConcurrentLinkedQueue<OnCrumbTrailUpdateListener> crumbTrailUpdateListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CrumbLogListener> crumbLogListeners = new ConcurrentLinkedQueue<>();

    /**
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public CrumbTrail(final long serialId,
            final MetaDataHolder metadata,
            final MapView mapView,
            final SharedPreferences prefs,
            final String uid) {

        super(serialId, metadata, uid);
        circles = false;
        this.unlimited = false;
        _mapView = mapView;
        this.prefs = prefs;
        init();
    }

    public CrumbTrail(final MapView mapView,
            final PointMapItem target,
            final SharedPreferences prefs,
            final String uid) {
        this(mapView, target, prefs, uid, false, false);
    }

    /**
     * Creates a new crumb trail.
     * @param mapView the map view for the crumb trail
     * @param target the target to produce the crumb trail for
     * @param prefs the preferences to query
     * @param uid the uid for the trail
     * @param circles render the trail as circles instead of arrows
     * @param unlimited if the trail should ignore the system default max
     */
    public CrumbTrail(final MapView mapView,
            final PointMapItem target,
            final SharedPreferences prefs,
            final String uid, boolean circles, boolean unlimited) {

        super(MapItem.createSerialId(), uid);

        _mapView = mapView;
        this.prefs = prefs;
        this.circles = circles;
        this.unlimited = unlimited;

        init();

        this.theTarget = target;
        if (this.theTarget != null)
            theTarget.getGroup().addOnItemListChangedListener(
                    this.crumbItemChangedListener);
    }

    private void init() {
        this.setClickable(false);
        this.setMetaBoolean("addToObjList", false);
        this.setMovable(false);
        this.toggleMetaData("ignoreRender", true);

        this.crumbItemChangedListener = new CrumbItemChangedListener();

        prefs.registerOnSharedPreferenceChangeListener(this);
        this.size = (Integer
                .parseInt(prefs.getString("track_crumb_size", "10")));
        this.drawLineToSurface = prefs.getBoolean("track_line_to_surface",
                false);

        onSharedPreferenceChanged(prefs, "max_num_bread_tracks");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CrumbTrail))
            return super.equals(o);

        CrumbTrail rhs = (CrumbTrail) o;
        if (theTarget == null || rhs.theTarget == null)
            return false;

        return FileSystemUtils.isEquals(theTarget.getUID(),
                rhs.theTarget.getUID());
    }

    @Override
    public int hashCode() {
        if (theTarget == null)
            return super.hashCode();

        return theTarget.getUID().hashCode();
    }

    @Override
    public boolean accept(FOVFilter.MapState fov) {
        Crumb c = this.last;
        while (c != null) {
            if (fov.contains(c.getPoint(), true))
                return true;
            c = c.prev;
        }
        return false;
    }

    /**
     * Return the current point map item that this crumb trail represents.
     * @return the point map item
     */
    public PointMapItem getTarget() {
        return this.theTarget;
    }

    /**
     * Sets the point map item for an existing crumb trail
     * @param target the point map item that will be used for the next crumbs in the crumb trail.
     */
    public void setTarget(PointMapItem target) {
        if (this.theTarget != null && this.theTarget.getGroup() != null)
            theTarget.getGroup().removeOnItemListChangedListener(
                    this.crumbItemChangedListener);
        this.theTarget = target;
        theTarget.getGroup().addOnItemListChangedListener(
                this.crumbItemChangedListener);
    }

    /**
     * Sets the color for the crumb trail.    This applies to the entire trail.
     * @param color the color as a ARGB value.
     */
    public void setColor(int color) {
        Crumb c = first;
        while (c != null) {
            c.setColor(color);
            c = c.next;
        }
    }

    private class CrumbItemChangedListener implements
            MapGroup.OnItemListChangedListener {
        @Override
        public void onItemAdded(MapItem item, MapGroup group) {

        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            if (item == CrumbTrail.this.theTarget) {
                CrumbTrail.this.clearAllCrumbs();
            }

            if (item instanceof Crumb) {
                Crumb c = (Crumb) item;
                if (CrumbTrail.this.equals(c.getCrumbTrail())) {
                    --crumbCount;
                }
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;

        switch (key) {
            case "track_line_to_surface":
                this.drawLineToSurface = prefs.getBoolean(
                        "track_line_to_surface",
                        false);
                // Update all the crumbs in the trail so they're drawn correctly when the
                // track_line_to_surface setting is modified
                Crumb crumb = last;
                while (crumb != null) {
                    crumb.setDrawLineToSurface(drawLineToSurface);
                    crumb = crumb.prev;
                }
                break;
            case "max_num_bread_tracks":
                maxCrumbs = prefs.getInt("max_num_bread_tracks",
                        200);
                if (maxCrumbs < 1) {
                    maxCrumbs = 1;
                }

                transparencyRate = 255 / maxCrumbs;
                if (transparencyRate < 1) {
                    transparencyRate = 1;
                }
                break;
            case "track_crumb_size":
                Crumb c = last;
                size = Integer.parseInt(
                        prefs.getString("track_crumb_size",
                                "10"));
                while (c != null) {
                    c.setSize(size);
                    c = c.prev;
                }
                break;
        }
    }

    /**
     * add another breadcrumb assuming it's far enough away from the last one,
     * and set the color based on the CoT type
     */
    public synchronized void crumb(final CrumbDatabase cdb) {
        //Log.d(TAG, "Processing Crumb for : " + theTarget.getUID());

        GeoPoint newP = theTarget.getPoint();

        // defend against an invalid point
        if (!newP.isValid())
            return;

        if (lastPoint != null) {
            final double distance = GeoCalculations.slantDistanceTo(lastPoint,
                    newP);
            final double direction = GeoCalculations.bearingTo(lastPoint, newP);

            // Set direction of last crumb based on bearing
            if (last != null)
                last.setDirection(direction);

            // Check if we should add a new breadcrumb based on distance thresh
            double threshM;
            try {
                threshM = Double.parseDouble(prefs.getString(
                        "bread_dist_threshold", "2"));
            } catch (Exception e) {
                threshM = 2;
            }
            if (distance < threshM)
                return;
        }

        Crumb crumb;
        int color = Color.BLUE;

        if (theTarget.hasMetaValue("crumbColor")) {
            color = theTarget.getMetaInteger("crumbColor", Color.BLUE);
        } else if (theTarget instanceof Marker) {
            Marker t = (Marker) theTarget;
            if (t.getMetaString("team", null) != null
                    && t.getIcon() != null) {
                if (t.getUID().equals(
                        _mapView.getSelfMarker().getUID()))
                    color = Icon2525cIconAdapter.teamToColor(
                            prefs.getString("locationTeam", "Cyan"));
                else
                    color = t.getIcon().getColor(0);
            } else {
                color = Marker.getAffiliationColor(t);
            }
        }

        if (circles)
            crumb = new CircleCrumb(newP, color, 0);
        else
            crumb = new Crumb(newP, color, UUID.randomUUID()
                    .toString());

        crumb.setSize(size);
        crumb.setDrawLineToSurface(drawLineToSurface);
        // persisting the crumb information
        if (cdb != null)
            cdb.persist(theTarget, crumb.timestamp, prefs);
        lastPoint = newP;

        //see if crumb should be sent to UI

        // first determine if there has been an anomaly in the space time fabric, or
        // we finally received a GPS lock.

        if (crumb.timestamp < lastUICrumbTime) {
            Log.d(TAG,
                    "crumb timestamp is less than the previously recorded last timestamp, reset");
            lastUICrumbTime = crumb.timestamp;
        }

        if (crumb.timestamp >= (lastUICrumbTime + minimumUICrumbTime)) {

            //update crumb trail transparency
            Crumb c = last;
            while (c != null) {
                int col = c.getColor();
                color = ((Color.red(col) << 16)
                        | (Color.green(col) << 8) | Color
                                .blue(col));
                int alpha = Color.alpha(col);
                if (!unlimited) {
                    alpha -= transparencyRate;
                    if (alpha < 0) {
                        alpha = 0;
                    }
                }
                col = color | (alpha << 24);
                c.setColor(col);
                c = c.prev;
            }

            //now add new crumb
            crumb.setSize(size);
            addCrumb(crumb);
            crumb.setVisible(CrumbTrail.this.getVisible());

            lastUICrumbTime = crumb.timestamp;
            //Log.d(TAG, "Created UI Crumb at time: " + lastUICrumbTime);
        }
    }

    public void addOnCrumbTrailUpdateListener(OnCrumbTrailUpdateListener l) {
        crumbTrailUpdateListeners.add(l);
    }

    public void removeOnCrumbTrailUpdateListener(OnCrumbTrailUpdateListener l) {
        crumbTrailUpdateListeners.remove(l);
    }

    public void addCrumbLogListener(CrumbLogListener l) {
        crumbLogListeners.add(l);
    }

    public void removeCrumbLogListener(CrumbLogListener l) {
        crumbLogListeners.remove(l);
    }

    private void addCrumb(Crumb crumb) {
        synchronized (this) {

            if (crumb != null) {
                if (last != null) {
                    last.next = crumb;
                    crumb.prev = last;
                    last.setDirection(last.getPoint().bearingTo(
                            crumb.getPoint()));
                }
                last = crumb;

                if (first == null) {
                    first = crumb;
                }
                ++crumbCount;

                if (!unlimited) {
                    while (crumbCount > maxCrumbs) {
                        removeOldestCrumb();
                    }
                }

                if (this.getGroup() != null)
                    this.getGroup().addItem(crumb);
                if (theTarget != null
                        && theTarget.getZOrder() >= crumb.getZOrder())
                    crumb.setZOrder(theTarget.getZOrder() + 1);

                for (OnCrumbTrailUpdateListener l : crumbTrailUpdateListeners) {
                    l.onCrumbAdded(this, crumb);
                }
                for (CrumbLogListener l : crumbLogListeners) {
                    l.logCrumb(crumb);
                }
            }
        }
    }

    private void removeOldestCrumb() {
        synchronized (this) {
            Crumb c = first;
            if (c != null) {
                first = first.next;
                c.next = null;
                c.prev = null;

                if (this.getGroup() != null)
                    this.getGroup().removeItem(c);

                for (OnCrumbTrailUpdateListener l : crumbTrailUpdateListeners) {
                    l.onCrumbRemoved(this, c);
                }
                --crumbCount;
            }
        }

    }

    public void clearAllCrumbs() {
        while (first != null) {
            removeOldestCrumb();
        }
    }

    public interface OnCrumbTrailUpdateListener {
        void onCrumbAdded(CrumbTrail trail, Crumb crumb);

        void onCrumbRemoved(CrumbTrail trail, Crumb crumb);
    }

    public interface CrumbLogListener {
        void logCrumb(Crumb c);
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

    @Override
    public double getZOrder() {
        double minZ = Double.MAX_VALUE;
        Crumb c = this.last;
        while (c != null) {
            minZ = Math.min(c.getZOrder(), minZ);
            c = c.prev;
        }
        return minZ;
    }

    @Override
    protected void onVisibleChanged() {
        boolean visible = getVisible();
        Crumb c = last;
        while (c != null) {
            c.setVisible(visible);
            c = c.prev;
        }
        super.onVisibleChanged();
    }

    /**
     * Set the tracking state of this trail
     * Tracking controls whether BreadcrumbReceiver adds new crumbs
     * @param tracking True to enable tracking
     */
    public void setTracking(boolean tracking) {
        this.tracking = tracking;
    }

    /**
     * Whether this crumb trail should be tracked (new crumbs added)
     * @return True if tracking is enabled
     */
    public boolean getTracking() {
        return this.tracking;
    }

    /**
     * Persist this crumb trail to the track DB
     */
    public void persist() {
        PointMapItem target = getTarget();
        if (target == null) {
            Log.w(TAG, "Cannot persist trail with null target!",
                    new Throwable());
            return;
        }
        new CreateTracksTask(_mapView, target, false).execute();
    }

    /**
     * Convenience method for persisting a list of trails
     * @param trails List of crumb trails
     */
    public static void persistTrails(List<CrumbTrail> trails) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;
        List<PointMapItem> targets = new ArrayList<>();
        for (CrumbTrail trail : trails) {
            PointMapItem target = trail.getTarget();
            if (target != null)
                targets.add(target);
        }
        new CreateTracksTask(mv, targets, false).execute();
    }

    /*************************************************************************/
    public static boolean toggleCrumbTrail(CrumbTrail trail) {
        boolean vis = !trail.getVisible();
        trail.setVisible(vis);
        return vis;
    }
}
