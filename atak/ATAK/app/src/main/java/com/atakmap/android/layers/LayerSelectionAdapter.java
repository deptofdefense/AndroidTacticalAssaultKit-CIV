
package com.atakmap.android.layers;

import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.math.MathUtils;
import gov.tak.api.util.Disposable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class LayerSelectionAdapter extends BaseAdapter implements
        RasterDataStore.OnDataStoreContentChangedListener,
        Disposable,
        FeatureDataStore.OnDataStoreContentChangedListener {

    /**************************************************************************/

    public interface OnItemSelectedListener {
        void onItemSelected(LayerSelectionAdapter adapter);
    }

    /**************************************************************************/

    private final static String TAG = "LayerSelectionAdapter";

    RasterLayer2 layer;
    private boolean invalid;
    private boolean rebuild;
    protected boolean active;
    protected boolean visible;

    protected final int selectedBackgroundColor = Color.argb(255, 0, 0, 102);
    protected final int normalBackgroundColor = Color.argb(0, 0, 0, 0);

    protected final MapView _mapView;
    final ArrayList<LayerSelection> _selections = new ArrayList<>();
    protected final Context _context;
    protected final AtakPreferences _prefs;
    LayersManagerBroadcastReceiver zoomRec;
    LayersManagerView view;
    final FeatureDataStore outlinesDatastore;

    private final Set<OnItemSelectedListener> listeners;

    public LayerSelectionAdapter(RasterLayer2 layer,
            FeatureDataStore outlinesDataStore,
            MapView mapView,
            Context context) {
        this.layer = layer;
        this.invalid = true;
        this.rebuild = true;

        _context = context;
        _mapView = mapView;
        _prefs = new AtakPreferences(_mapView);

        this.listeners = Collections
                .newSetFromMap(
                        new IdentityHashMap<OnItemSelectedListener, Boolean>());

        this.active = false;
        this.visible = false;

        this.outlinesDatastore = outlinesDataStore;

        if (this.layer instanceof AbstractDataStoreRasterLayer2)
            ((AbstractDataStoreRasterLayer2) this.layer).getDataStore()
                    .addOnDataStoreContentChangedListener(this);
        if (this.outlinesDatastore != null)
            this.outlinesDatastore.addOnDataStoreContentChangedListener(this);
    }

    final RasterLayer2 getLayer() {
        return this.layer;
    }

    final FeatureDataStore getOutlinesDataStore() {
        return this.outlinesDatastore;
    }

    public final synchronized void setVisible(boolean visible) {
        this.visible = visible;
        if (visible)
            this.invalidate(true);
    }

    public final synchronized void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            // XXX - rebuild???
            this.invalidate(true);
        }
    }

    public final synchronized boolean isActive() {
        return this.active;
    }

    @Override
    public void dispose() {
        if (this.outlinesDatastore != null)
            this.outlinesDatastore
                    .removeOnDataStoreContentChangedListener(this);

        if (this.layer instanceof AbstractDataStoreRasterLayer2)
            ((AbstractDataStoreRasterLayer2) this.layer).getDataStore()
                    .removeOnDataStoreContentChangedListener(this);

        _selections.clear();
        this.layer = null;
    }

    protected final void validateNoSync() {
        if (!isUIThread()) {
            Log.w(TAG, "Validation must be performed on UI thread.");
            return;
        }

        if (this.invalid) {
            this.validateImpl(this.rebuild);
            this.invalid = false;
            this.rebuild = false;
        }
    }

    protected void validateImpl(boolean rebuild) {
        Map<String, LayerSelection> last = new HashMap<>();
        if (!rebuild) {
            for (LayerSelection sel : _selections)
                last.put(sel.getName(), sel);
        }
        _selections.clear();

        if (layer != null) {

            Collection<String> sel = this.layer.getSelectionOptions();
            Geometry cov;
            double minGsd;
            double maxGsd;
            LayerSelection ls;
            for (String s : sel) {
                ls = last.get(s);
                if (ls == null) {
                    cov = this.layer.getGeometry(s);
                    minGsd = this.layer.getMinimumResolution(s);
                    maxGsd = this.layer.getMaximumResolution(s);

                    if (cov != null)
                        ls = new LayerSelection(s, cov, minGsd, maxGsd);
                }
                if (ls != null)
                    _selections.add(ls);
            }
        }
        this.sortNoSync();
    }

    protected final void invalidate(final boolean contentChanged) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (!LayerSelectionAdapter.this.invalid
                        || (contentChanged
                                && !LayerSelectionAdapter.this.rebuild)) {
                    LayerSelectionAdapter.this.invalid = true;
                    LayerSelectionAdapter.this.rebuild |= contentChanged;
                    LayerSelectionAdapter.this.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public final synchronized int getCount() {
        this.validateNoSync();
        return _selections.size();
    }

    public final synchronized LayerSelection findByName(String name) {
        if (name == null)
            return null;

        this.validateNoSync();
        for (LayerSelection ls : _selections) {
            if (ls.getName().equals(name)) {
                return ls;
            }
        }
        return null;
    }

    @Override
    public synchronized Object getItem(int position) {
        this.validateNoSync();
        return _selections.get(position);
    }

    @Override
    public final long getItemId(int position) {
        return position;
    }

    public final synchronized void addOnItemSelectedListener(
            OnItemSelectedListener l) {
        this.listeners.add(l);
    }

    public final synchronized void removeOnItemSelectedListener(
            OnItemSelectedListener l) {
        this.listeners.remove(l);
    }

    public final synchronized boolean isLocked() {
        if (this.layer != null) {
            return !this.layer.isAutoSelect();
        } else {
            return false;
        }
    }

    public final synchronized void setLocked(boolean isLocked) {
        if (!isLocked) {
            this.layer.setSelection(null);
        } else if (this.layer.isAutoSelect()) {
            final String autoSelect = this.layer.getSelection();
            if (autoSelect != null)
                this.layer.setSelection(autoSelect);
        }
        if (zoomRec != null) {
            // there is no guarantee that this will get called on the ui thread and updateAutoLayer
            // makes ui calls
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    zoomRec.updateAutoLayer();
                }
            });
        }
    }

    public final void setSelected(LayerSelection tsInfo) {
        if (tsInfo != null)
            setSelected(tsInfo.getName());
    }

    public final void setSelected(DatasetDescriptor tsInfo) {
        if (tsInfo != null)
            setSelected(tsInfo.getName());
    }

    public synchronized void setSelected(String selection) {
        if (!this.layer.getSelectionOptions().contains(selection))
            return;
        this.layer.setSelection(selection);
        this.invalid = true;
        this.active = true;
        notifyDataSetChanged();

        for (OnItemSelectedListener l : this.listeners)
            l.onItemSelected(this);
    }

    public synchronized LayerSelection getSelected() {
        final String selection = this.layer.getSelection();
        if (selection == null)
            return null;
        return this.findByName(selection);
    }

    public final synchronized int selectedIndex() {
        final String selection = this.layer.getSelection();
        if (selection == null)
            return -1;
        this.validateNoSync();
        int i = 0;
        for (LayerSelection layer : _selections) {
            if (layer.getName().equals(selection))
                return i;
            i++;
        }
        return -1;
    }

    public final synchronized void sort() {
        this.validateNoSync();
        this.sortNoSync();
        this.notifyDataSetChanged();
    }

    protected final void sortNoSync() {
        if (!isUIThread())
            Log.w(TAG, "Sort is being called off the UI thread");
        Collections.sort(_selections, this.getSortComparator());
    }

    protected abstract Comparator<LayerSelection> getSortComparator();

    public synchronized List<LayerSelection> getAllSelectionsAt(
            GeoPoint point) {
        ArrayList<LayerSelection> l = new ArrayList<>();
        validateNoSync();
        for (LayerSelection s : _selections) {
            if (s.getWest() <= point.getLongitude()
                    && s.getEast() >= point.getLongitude()
                    && s.getNorth() >= point.getLatitude()
                    && s.getSouth() <= point.getLatitude()) {
                l.add(s);
            }
        }
        Collections.sort(l, getSortComparator());
        return l;
    }

    @Override
    public final synchronized View getView(int position, View convertView,
            ViewGroup parent) {
        this.validateNoSync();

        // XXX - ugh
        position = MathUtils.clamp(position, 0, _selections.size() - 1);

        final LayerSelection selection = _selections.get(position);
        return this.getViewImpl(selection, position, convertView, parent);
    }

    /**
     * Returns the view for the specified {@link LayerSelection}. The
     * {@link #_selections} set can always be assumed to be in a valid state
     * when this method is invoked.
     * 
     * <P>This method is always invoked while locked on <code>this</code>.
     *  
     * @param sel           The {@link LayerSelection}
     * @param position      The position specified for
     *                      {@link #getView(int, View, ViewGroup)}
     * @param convertView   The position specified for
     *                      {@link #getView(int, View, ViewGroup)}
     * @param parent        The position specified for
     *                      {@link #getView(int, View, ViewGroup)}
     * @return              The {@link View} for the specified
     *                      {@link LayerSelection}
     */
    protected abstract View getViewImpl(LayerSelection sel, int position,
            View convertView, ViewGroup parent);

    final void setLayersMgrRec(LayersManagerBroadcastReceiver lmbr) {
        this.zoomRec = lmbr;
    }

    void setView(LayersManagerView view) {
        this.view = view;
    }

    /**************************************************************************/
    // RasterDataStore OnDataStoreContentChangedListener

    @Override
    public void onDataStoreContentChanged(RasterDataStore dataStore) {
        this.invalidate(true);
    }

    @Override
    public void onDataStoreContentChanged(FeatureDataStore dataStore) {
        this.invalidate(false);
    }

    /**************************************************************************/

    private static boolean isUIThread() {
        return (Looper.getMainLooper().getThread() == Thread.currentThread());
    }
}
