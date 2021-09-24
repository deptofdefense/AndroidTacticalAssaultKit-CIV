
package com.atakmap.android.rubbersheet.maps;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.rubbersheet.data.AbstractSheetData;
import com.atakmap.android.rubbersheet.data.RubberSheetUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abstract class which rubber sheets and models extend off of
 */
public abstract class AbstractSheet extends DrawingRectangle {

    private static final String TAG = "AbstractSheet";

    protected final File _file;
    private int _alpha = 255;
    private final Set<OnAlphaChangedListener> _alphaListeners = new HashSet<>();

    private LoadState _loadState = LoadState.LOADING;
    private int _loadProgress = 0;
    private final Set<OnLoadListener> _loadListeners = new HashSet<>();

    protected AbstractSheet(AbstractSheetData data) {
        super(new DefaultMapGroup(data.label),
                GeoPointMetaData.wrap(data.points[0]),
                GeoPointMetaData.wrap(data.points[1]),
                GeoPointMetaData.wrap(data.points[2]),
                GeoPointMetaData.wrap(data.points[3]),
                data.getUID());
        _file = data.file;
        setAlpha(data.alpha);
        setStrokeColor(data.strokeColor);
        setStrokeWeight(data.strokeWeight);
        setMetaString("remarks", data.remarks);
        setMetaBoolean("nevercot", true);
        MapGroup mg = getChildMapGroup();
        mg.setMetaString("uid", getUID());
        setTitle(mg.getFriendlyName());
        setFilled(false);
        setVisible(data.visible);
    }

    /**
     * Get the file used by this rubber sheet
     * @return Base file
     */
    public File getFile() {
        return _file;
    }

    /**
     * Get the heading of the rectangle based on the bearing between the
     * back and forward association point
     * @return Heading in true degrees
     */
    public double getHeading() {
        GeoPoint start = getPoint(6);
        GeoPoint end = getPoint(4);
        if (start == null || end == null)
            return 0;
        return start.bearingTo(end);
    }

    /**
     * Set the heading of the sheet by modifying the rectangle back and
     * forward association point
     * @param heading Heading in true degrees
     */
    public void setHeading(double heading) {
        GeoPoint start = getPoint(6);
        GeoPoint end = getPoint(4);
        GeoPoint left = getPoint(5);
        GeoPoint right = getPoint(7);
        if (start == null || end == null || left == null || right == null)
            return;
        double curHeading = start.bearingTo(end);
        if (Double.compare(heading, curHeading) == 0)
            return;

        setPoints(getCenter(), getWidth(), getLength(), heading);
    }

    public void setPoints(GeoPointMetaData center, double width, double length,
            double heading) {
        GeoPoint[] corners = RubberSheetUtils.computeCorners(
                center.get(), length, width, heading);
        setPoints(GeoPointMetaData.wrap(corners[0]),
                GeoPointMetaData.wrap(corners[1]),
                GeoPointMetaData.wrap(corners[2]),
                GeoPointMetaData.wrap(corners[3]));
        setCenterPoint(center);
    }

    @Override
    public void setPoints(GeoPointMetaData p0, GeoPointMetaData p1,
            GeoPointMetaData p2, GeoPointMetaData p3) {
        // Expose protected method
        super.setPoints(p0, p1, p2, p3);
    }

    public GeoPoint getPoint(int index) {
        PointMapItem pmi = getPointAt(index);
        return pmi != null ? pmi.getPoint() : null;
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        PointMapItem center = getAnchorItem();
        if (center != null)
            center.setMetaBoolean("drag", editable);
    }

    public GeoPoint getCenterPoint() {
        return getCenter().get();
    }

    public Marker getCenterMarker() {
        PointMapItem pmi = getAnchorItem();
        return pmi instanceof Marker ? (Marker) pmi : null;
    }

    public boolean getLabelVisibility() {
        return hasMetaValue("labels_on");
    }

    public void setMenu(String menuPath) {
        setMetaString("menu", menuPath);
        MapItem center = getAnchorItem();
        if (center != null)
            center.setMetaString("menu", menuPath);
    }

    /* Load listeners */

    /**
     * Load this sheet's underlying data
     */
    public void load() {
        setLoadState(LoadState.LOADING);
        setLoadState(loadImpl());
    }

    /**
     * Underlying load implementation
     */
    protected abstract LoadState loadImpl();

    /**
     * Get the current load state
     * @return Load state
     */
    public LoadState getLoadState() {
        return _loadState;
    }

    public void setLoadState(LoadState ls) {
        _loadState = ls;

        Marker m = getCenterMarker();
        if (ls == LoadState.LOADING || ls == LoadState.FAILED)
            removeMetaData("editable");
        else
            setMetaBoolean("editable", true);
        if (m != null) {
            if (ls == LoadState.LOADING)
                m.setSummary("Loading...");
            else if (ls == LoadState.FAILED)
                m.setSummary("Failed to load");
            else
                m.setSummary(null);
        }

        for (OnLoadListener l : getLoadListeners())
            l.onLoadStateChanged(this, ls);
    }

    /**
     * Get the current progress of this item's loading state
     * @return Load progress
     */
    public int getLoadProgress() {
        return _loadProgress;
    }

    protected void setLoadProgress(int progress) {
        if (_loadState != LoadState.LOADING)
            return;

        _loadProgress = progress;
        Marker m = getCenterMarker();
        if (m != null)
            m.setSummary("Loading... " + progress + "%");

        for (OnLoadListener l : getLoadListeners())
            l.onLoadProgress(this, progress);
    }

    /**
     * Check if this sheet is finished loading
     * @return True if loaded
     */
    public boolean isLoaded() {
        return getLoadState() == LoadState.SUCCESS;
    }

    public synchronized void addLoadListener(OnLoadListener l) {
        _loadListeners.add(l);
    }

    public synchronized void removeLoadListener(OnLoadListener l) {
        _loadListeners.remove(l);
    }

    private synchronized List<OnLoadListener> getLoadListeners() {
        return new ArrayList<>(_loadListeners);
    }

    public interface OnLoadListener {
        void onLoadStateChanged(AbstractSheet sheet, LoadState ls);

        void onLoadProgress(AbstractSheet sheet, int progress);
    }

    /* Alpha transparency */

    @Override
    public void setFillColor(int color) {
        int alpha = Color.alpha(color);
        if (_alpha != alpha) {
            _alpha = alpha;
            // Ignore the AS warning - the listeners are null when this
            // method is called by the super constructor
            if (_alphaListeners != null) {
                for (OnAlphaChangedListener l : _alphaListeners)
                    l.onAlphaChanged(this, _alpha);
            }
        }
    }

    @Override
    public int getFillColor() {
        return Color.argb(_alpha, 255, 255, 255);
    }

    public void setAlpha(int alpha) {
        setFillColor(Color.argb(alpha, 255, 255, 255));
    }

    public int getAlpha() {
        return _alpha;
    }

    public void addOnAlphaChangedListener(OnAlphaChangedListener l) {
        _alphaListeners.add(l);
    }

    public void removeOnAlphaChangedListener(OnAlphaChangedListener l) {
        _alphaListeners.remove(l);
    }

    public interface OnAlphaChangedListener {
        void onAlphaChanged(AbstractSheet sheet, int alpha);
    }
}
