
package com.atakmap.android.layers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff.Mode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetDescriptorCursor;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;
import com.atakmap.map.layer.raster.nativeimagery.NativeImageryRasterLayer2;
import com.atakmap.spatial.SpatialCalculator;
import gov.tak.api.util.Disposable;

import java.io.File;
import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import com.atakmap.coremap.log.Log;

class NativeLayerSelectionAdapter extends LayerSelectionAdapter
        implements AtakMapView.OnMapMovedListener, View.OnClickListener {

    public static final String TAG = "NativeLayerSelectionAdapter";

    private final static Comparator<LayerSelection> comparator = new Comparator<LayerSelection>() {

        @Override
        public int compare(LayerSelection layer1, LayerSelection layer2) {
            if (layer1.getMaxRes() > layer2.getMaxRes())
                return -1;
            else if (layer1.getMaxRes() < layer2.getMaxRes())
                return 1;
            else
                return layer1.getName().compareTo(layer2.getName());
        }
    };

    protected final NativeImageryRasterLayer2 layer;
    private final LocalRasterDataStore dataStore;
    private final LayoutInflater inflater;
    private AOIIsectComputer calc;
    private String expanded;

    public NativeLayerSelectionAdapter(NativeImageryRasterLayer2 layer,
            FeatureDataStore outlineDatastore,
            MapView mapView, Context context) {

        super(layer, outlineDatastore, mapView, context);

        mapView.addOnMapMovedListener(this);

        this.layer = layer;
        this.dataStore = LayersMapComponent.getLayersDatabase();
        this.calc = new AOIIsectComputer();
        this.inflater = LayoutInflater.from(_context);
    }

    @Override
    protected final Comparator<LayerSelection> getSortComparator() {
        return comparator;
    }

    @Override
    public void dispose() {
        _mapView.removeOnMapMovedListener(this);
        if (this.calc != null) {
            this.calc.dispose();
            this.calc = null;
        }
        super.dispose();
    }

    @Override
    protected void validateImpl(boolean rebuild) {
        if (!visible)
            return;

        if (rebuild)
            super.validateImpl(true);

        // NOTE: Original implementation did synchronous selection of "in view"
        //       datasets via SpatialCalculator intersection. Performance was
        //       not sufficient for real-time processing of onMapMoved callback
        if (this.calc != null)
            this.calc.query();
    }

    @Override
    protected View getViewImpl(final LayerSelection selection,
            final int position, View row, ViewGroup parent) {

        LayerViewHolder h = LayerViewHolder.get(_context, row, parent);
        h.selection = selection;

        String name = selection.getName();
        if (name.length() > 30)
            h.title.setTextSize(13);
        else if (name.length() > 23)
            h.title.setTextSize(15);

        h.title.setText(name);

        // show the min and max resolution
        setCurrentRes(h.desc, selection.getMinRes(), selection.getMaxRes());

        h.expandBtn.setVisibility(View.VISIBLE);
        if (FileSystemUtils.isEquals(expanded, selection.getName())) {
            h.expandBtn.setImageResource(R.drawable.arrow_down);
            List<DatasetDescriptor> datasets = queryDatasets(selection);
            int childCount = h.filesLayout.getChildCount();
            int numSets = datasets.size();
            for (int i = 0; i < numSets; i++) {
                View v1 = i < childCount ? h.filesLayout.getChildAt(i) : null;
                View v2 = getFileView(selection, datasets.get(i), v1,
                        h.filesLayout);
                if (v1 != v2) {
                    if (v1 != null)
                        h.filesLayout.removeView(v1);
                    h.filesLayout.addView(v2, i);
                }
            }
            for (int i = numSets; i < childCount; i++)
                h.filesLayout.removeViewAt(numSets);
            h.filesLayout.setVisibility(View.VISIBLE);
        } else {
            h.expandBtn.setImageResource(R.drawable.arrow_right);
            h.filesLayout.setVisibility(View.GONE);
        }
        h.expandBtn.setOnClickListener(this);

        boolean outlineVisible = queryOutlinesVisible(selection);
        int outlineColor = queryOutlineColor(selection);

        h.outlineBorder.getBackground().mutate()
                .setColorFilter(outlineColor, Mode.MULTIPLY);

        h.outlineBtn.setChecked(outlineVisible);
        h.outlineBtn.setOnClickListener(this);

        final String selectName = this.layer.getSelection();
        final boolean isSelection = this.active
                && (selectName != null && selectName
                        .equals(selection.getName()));

        if (isSelection)
            h.background.setBackgroundColor(selectedBackgroundColor);
        else
            h.background.setBackgroundColor(normalBackgroundColor);

        int icon;
        if (isSelection && isLocked())
            icon = R.drawable.ic_lock_lit;
        else
            icon = R.drawable.ic_lock_unlit;
        h.lockBtn.setImageResource(icon);

        h.lockBtn.setOnClickListener(this);

        h.vizIcon.setVisible(layer.isVisible(selection.getName()));
        h.vizBtn.setOnClickListener(this);

        return h.root;
    }

    private View getFileView(LayerSelection selection, DatasetDescriptor desc,
            View row, ViewGroup parent) {

        FileViewHolder h = row != null ? (FileViewHolder) row.getTag() : null;
        if (h == null) {
            h = new FileViewHolder();
            h.root = row = inflater.inflate(
                    R.layout.layers_manager_list_item_file, parent, false);
            h.name = row.findViewById(R.id.file_name);
            h.size = row.findViewById(R.id.file_size);
            h.delete = row.findViewById(R.id.file_delete);
            h.delete.setTag(h);
            row.setTag(h);
        }

        File file = new File(desc.getUri());

        h.desc = desc;
        h.selection = selection;
        h.name.setText(desc.getName());
        if (file.exists())
            h.size.setText(MathUtils.GetLengthString(file.length()));
        else
            h.size.setText("");

        h.root.setOnClickListener(this);
        h.delete.setOnClickListener(this);

        return row;
    }

    private static class FileViewHolder {
        View root;
        TextView name, size;
        ImageButton delete;
        LayerSelection selection;
        DatasetDescriptor desc;
    }

    @Override
    public void onClick(View v) {
        Object tag = v.getTag();

        if (tag instanceof LayerViewHolder) {

            LayerViewHolder h = (LayerViewHolder) tag;

            // Toggle selection lock
            if (v == h.lockBtn) {
                if (isLocked() && getSelected() == h.selection) {
                    setLocked(layer.isAutoSelect());
                } else {
                    setLocked(false);
                    setSelected(h.selection);
                }
                notifyDataSetChanged();
            }

            // Toggle visibility
            else if (v == h.vizBtn) {
                String name = h.selection.getName();
                layer.setVisible(name, !layer.isVisible(name));
                notifyDataSetChanged();
            }

            // Toggle outlines
            else if (v == h.outlineBtn) {
                try {
                    FeatureQueryParameters params = new FeatureQueryParameters();
                    params.featureNames = Collections.singleton(
                            h.selection.getName());
                    outlinesDatastore.setFeaturesVisible(params,
                            h.outlineBtn.isChecked());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to set features visible outlines: "
                            + h.selection.getName(), e);
                }
                refreshOutlinesButton();
            }

            // Expand files
            else if (v == h.expandBtn) {
                String name = h.selection.getName();
                if (!FileSystemUtils.isEquals(expanded, name))
                    expanded = name;
                else
                    expanded = null;
                notifyDataSetChanged();
            }
        }

        else if (tag instanceof FileViewHolder) {

            FileViewHolder h = (FileViewHolder) tag;

            // Pan to area
            if (v == h.root) {
                setSelected(h.selection);
                Envelope e = h.desc.getMinimumBoundingBox();
                ATAKUtilities.scaleToFit(_mapView,
                        new GeoBounds(e.minY, e.minX, e.maxY, e.maxX),
                        _mapView.getWidth(), _mapView.getHeight());
            }

            // Delete file
            else if (v == h.delete) {
                final DatasetDescriptor desc = h.desc;
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.are_you_sure);
                b.setMessage(_context.getString(R.string.are_you_sure_delete2,
                        desc.getName()));
                b.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dataStore.remove(desc);
                                FileSystemUtils.delete(new File(desc.getUri()));
                                _mapView.updateView(_mapView.getLatitude(),
                                        _mapView.getLongitude(),
                                        _mapView.getMapScale(),
                                        _mapView.getMapRotation(),
                                        _mapView.getMapTilt(), false);
                                notifyDataSetChanged();
                            }
                        });
                b.setNegativeButton(R.string.no, null);
                b.show();
            }
        }
    }

    private boolean queryOutlinesVisible(LayerSelection selection) {
        FeatureCursor c = null;
        try {
            c = queryOutlines(selection, true);
            return c.moveToNext();
        } catch (Exception e) {
            Log.e(TAG, "Failed to query visibility selection: "
                    + selection.getName(), e);
        } finally {
            if (c != null)
                c.close();
        }
        return false;
    }

    private int queryOutlineColor(LayerSelection selection) {
        FeatureCursor c = null;
        try {
            c = queryOutlines(selection, false);
            if (c.moveToNext()) {
                Feature f = c.get();
                if (f.getStyle() instanceof BasicStrokeStyle)
                    return ((BasicStrokeStyle) f.getStyle()).getColor();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query color for selection: "
                    + selection.getName(), e);
        } finally {
            if (c != null)
                c.close();
        }
        return 0;
    }

    private FeatureCursor queryOutlines(LayerSelection selection,
            boolean visibleOnly) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureNames = Collections.singleton(selection.getName());
        params.visibleOnly = visibleOnly;
        params.limit = 1;
        return outlinesDatastore.queryFeatures(params);
    }

    private List<DatasetDescriptor> queryDatasets(LayerSelection selection) {
        List<DatasetDescriptor> ret = new ArrayList<>();
        DatasetQueryParameters params = new DatasetQueryParameters();
        layer.filterQueryParams(params);
        params.imageryTypes = Collections.singleton(selection.getName());
        DatasetDescriptorCursor c = null;
        try {
            c = dataStore.queryDatasets(params);
            while (c.moveToNext())
                ret.add(c.get());
        } catch (Exception e) {
            Log.e(TAG, "Failed to query color for selection: "
                    + selection.getName(), e);
        } finally {
            if (c != null)
                c.close();
        }
        return ret;
    }

    /**
     * Determine the best units to display the resolution in and format the string so that it looks
     * nice.
     * 
     * @param resView - the text view to change
     * @param resMin - the minimum resolution chosen by the user
     * @param resMax - the max resolution chosen by the user
     */
    private void setCurrentRes(TextView resView, double resMin, double resMax) {
        String postfix1 = "m";
        String postfix2 = "m";
        String resString1 = String.valueOf(Math.round(resMin));
        String resString2 = String.valueOf(Math.round(resMax));

        // determine if the best way to display the value is in m/px or km/px
        if (resMin > 1000) {
            resMin = resMin / 1000d;
            resString1 = String.valueOf(Math.round(resMin));
            postfix1 = "km";
        } else if (resMin < 1) {
            DecimalFormat df = LocaleUtil.getDecimalFormat("#.00");
            resMin = resMin * 100d;
            resMin = (double) Math.round(resMin) / 100d;
            resString1 = df.format(resMin);
        }

        if (Double.compare(resMin, resMax) == 0) {
            resView.setText(resString1 + postfix1);
            return;
        }

        if (resMax > 1000) {
            resMax = resMax / 1000d;
            resString2 = String.valueOf(Math.round(resMax));
            postfix2 = "km";
        } else if (resMax < 1) {
            DecimalFormat df = LocaleUtil.getDecimalFormat("#.00");
            resMax = resMax * 100d;
            resMax = (double) Math.round(resMax) / 100d;
            resString2 = df.format(resMax);
        }

        resView.setText(resString1 + postfix1 + " - " + resString2 + postfix2);
    }

    @Override
    void setView(LayersManagerView v) {
        super.setView(v);
        refreshOutlinesButton();
    }

    private void refreshOutlinesButton() {
        if (this.view == null)
            return;
        boolean showOutlines = getOutlinesDataStore() != null;
        CompoundButton outlinesButton = view.getOutlineToggleButton();
        if (showOutlines && outlinesButton != null) {
            final boolean outlinesVisible = getOutlinesDataStore()
                    .queryFeaturesCount(getAOIParams()) > 0;
            view.setOnOutlineToggleListener(null);
            view.setOutlineToggleState(outlinesVisible);
            view.setOnOutlineToggleListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton cb, boolean check) {
                    LayersMapComponent.setFeatureSetsVisible(
                            getOutlinesDataStore(), null, check);
                }
            });
            view.getOutlineToggleButton().setVisibility(View.VISIBLE);
        } else if (outlinesButton != null) {
            view.getOutlineToggleButton().setVisibility(View.GONE);
        }
    }

    private FeatureQueryParameters getAOIParams() {
        GeoBounds aoi = _mapView.getBounds();
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.visibleOnly = true;
        params.spatialFilter = new RegionSpatialFilter(
                new GeoPoint(aoi.getNorth(), aoi.getWest()),
                new GeoPoint(aoi.getSouth(), aoi.getEast()));
        params.limit = 1;
        return params;
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        if (this.calc != null)
            this.calc.query();
    }

    /** background processes which RasterLayer "selections" are "in view" */
    private class AOIIsectComputer implements Runnable, Disposable {

        boolean disposed;
        int state;
        final Thread thread;

        public AOIIsectComputer() {
            this.disposed = false;
            this.state = 0;

            this.thread = new Thread(this);
            this.thread.setPriority(Thread.NORM_PRIORITY);
            this.thread.setName("NativeLayerSelectionAdapter-AOI-Isect-thread");
            this.thread.start();
        }

        public synchronized void query() {
            this.state++;
            this.notify();
        }

        @Override
        public void dispose() {
            synchronized (this) {
                this.disposed = true;
                this.notify();
            }

            try {
                this.thread.join();
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public void run() {
            final SpatialCalculator calc = new SpatialCalculator.Builder()
                    .inMemory().build();
            try {
                int compute = 0;
                while (true) {
                    final LinkedList<LayerSelection> computedSelections = new LinkedList<>();
                    synchronized (this) {
                        if (this.disposed)
                            break;
                        if (compute == this.state) {
                            try {
                                this.wait();
                            } catch (InterruptedException ignored) {
                            }
                            continue;
                        }
                        compute = this.state;
                    }

                    Collection<String> sel = new HashSet<>(
                            NativeLayerSelectionAdapter.this.layer
                                    .getSelectionOptions());

                    // compute which selections intersect the current view
                    calc.beginBatch();
                    try {
                        GeoBounds bnds = _mapView.getBounds();
                        // don't run the query if the map is not visible
                        if (Double.isNaN(bnds.getNorth()) ||
                                Double.isNaN(bnds.getWest()) ||
                                Double.isNaN(bnds.getSouth()) ||
                                Double.isNaN(bnds.getWest())) {

                            return;
                        }

                        final long aoiHandle = calc.createPolygon(
                                new GeoPoint(bnds.getNorth(), bnds.getWest()),
                                new GeoPoint(bnds.getNorth(), bnds.getEast()),
                                new GeoPoint(bnds.getSouth(), bnds.getEast()),
                                new GeoPoint(bnds.getSouth(), bnds.getWest()));

                        Geometry cov;
                        double minGsd;
                        double maxGsd;
                        long covHandle;
                        for (String s : sel) {
                            cov = layer.getGeometry(s);
                            minGsd = layer.getMinimumResolution(s);
                            maxGsd = layer.getMaximumResolution(s);

                            if (cov != null) {
                                covHandle = calc.createGeometry(cov);
                                if (calc.intersects(aoiHandle, covHandle))
                                    computedSelections.add(new LayerSelection(
                                            s, cov, minGsd,
                                            maxGsd));
                            }
                        }
                    } finally {
                        calc.endBatch(false);
                    }

                    // update the adapter and notify dataset changed
                    boolean contentChanged;
                    synchronized (NativeLayerSelectionAdapter.this) {
                        contentChanged = (_selections
                                .size() != computedSelections
                                        .size());
                        if (!contentChanged) {
                            Set<String> current = new HashSet<>();
                            for (LayerSelection ls : _selections)
                                current.add(ls.getName());
                            Set<String> computed = new HashSet<>();
                            for (LayerSelection ls : computedSelections)
                                computed.add(ls.getName());

                            Set<String> test = new HashSet<>();

                            test.clear();
                            test.addAll(current);
                            test.removeAll(computed);
                            contentChanged |= !test.isEmpty();

                            test.clear();
                            test.addAll(computed);
                            test.removeAll(current);
                            contentChanged |= !test.isEmpty();
                        }

                        if (contentChanged) {
                            _mapView.post(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (NativeLayerSelectionAdapter.this) {
                                        NativeLayerSelectionAdapter.this._selections
                                                .clear();
                                        NativeLayerSelectionAdapter.this._selections
                                                .addAll(computedSelections);
                                        NativeLayerSelectionAdapter.this
                                                .sortNoSync();
                                    }

                                    NativeLayerSelectionAdapter.this
                                            .notifyDataSetChanged();
                                    refreshOutlinesButton();
                                }
                            });
                        }
                    }
                }
            } finally {
                calc.dispose();
            }
        }
    }
}
