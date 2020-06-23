
package com.atakmap.android.layers;

import android.content.Context;
import android.graphics.PorterDuff.Mode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.raster.nativeimagery.NativeImageryRasterLayer2;
import com.atakmap.spatial.SpatialCalculator;
import com.atakmap.util.Disposable;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import com.atakmap.coremap.log.Log;

class NativeLayerSelectionAdapter extends LayerSelectionAdapter
        implements AtakMapView.OnMapMovedListener {

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

    private AOIIsectComputer calc;

    public NativeLayerSelectionAdapter(NativeImageryRasterLayer2 layer,
            FeatureDataStore outlineDatastore,
            MapView mapView, Context context) {

        super(layer, outlineDatastore, mapView, context);

        mapView.addOnMapMovedListener(this);

        this.calc = new AOIIsectComputer();
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
            final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(_context);
        View view = inflater.inflate(R.layout.layers_manager_list_item, null);

        TextView title = view
                .findViewById(R.id.layers_manager_item_title);

        TextView desc = view
                .findViewById(R.id.layers_manager_item_desc);

        ImageView lockImage = view
                .findViewById(R.id.layers_manager_item_layer_lock);

        ImageView visibilityToggle = view
                .findViewById(R.id.layers_manager_item_toggle_image);

        CheckBox outlineCheckbox = view
                .findViewById(R.id.layers_manager_item_outline_checkbox);

        LinearLayout outlinesLayout = view
                .findViewById(R.id.layers_manager_item_outline_layout);

        String name = selection.getName();
        if (name.length() > 30)
            title.setTextSize(13);
        else if (name.length() > 23)
            title.setTextSize(15);

        title.setText(name);

        // show the min and max resolution
        setCurrentRes(desc,
                selection.getMinRes(),
                selection.getMaxRes());

        lockImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    if (position >= 0 && position < _selections.size()) {
                        if (isLocked()
                                && getSelected() == _selections.get(position)) {
                            setLocked(NativeLayerSelectionAdapter.this.layer
                                    .isAutoSelect());
                        } else {
                            setLocked(false);
                            setSelected(_selections.get(position));
                        }
                        notifyDataSetChanged();
                    }
                } catch (IndexOutOfBoundsException oobe) {
                    Log.d(TAG, "error setting lock", oobe);
                }

            }
        });

        visibilityToggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (position >= 0 && position < _selections.size()) {
                        LayerSelection s = _selections.get(position);
                        boolean val = NativeLayerSelectionAdapter.this.layer
                                .isVisible(s.getName());
                        NativeLayerSelectionAdapter.this.layer.setVisible(
                                s.getName(),
                                !val);

                        notifyDataSetChanged();
                    }
                } catch (IndexOutOfBoundsException oobe) {
                    Log.d(TAG, "error setting visibility", oobe);
                }
            }
        });

        boolean outlineVisible = false;
        int outlineColor = 0;
        final Set<Long> fids = new HashSet<>();

        FeatureCursor result = null;
        try {
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.featureNames = Collections.singleton(selection.getName());

            result = this.outlinesDatastore.queryFeatures(params);
            Feature f;
            while (result.moveToNext()) {
                f = result.get();
                fids.add(f.getId());
                outlineVisible |= this.outlinesDatastore.isFeatureVisible(f
                        .getId());

                if (outlineColor == 0
                        && f.getStyle() instanceof BasicStrokeStyle)
                    outlineColor = ((BasicStrokeStyle) f.getStyle()).getColor();
            }
        } finally {
            if (result != null)
                result.close();
        }

        outlinesLayout.getBackground().mutate()
                .setColorFilter(outlineColor, Mode.MULTIPLY);

        outlineCheckbox.setOnCheckedChangeListener(null);
        outlineCheckbox.setChecked(outlineVisible);
        outlineCheckbox
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        for (Long fid : fids)
                            NativeLayerSelectionAdapter.this.outlinesDatastore
                                    .setFeatureVisible(fid,
                                            isChecked);
                        refreshOutlinesButton();
                    }
                });

        final String selectName = this.layer.getSelection();
        final boolean isSelection = this.active
                && (selectName != null && selectName
                        .equals(selection.getName()));

        if (isSelection) {
            lockImage.setVisibility(View.VISIBLE);
            int icon;
            if (!this.layer.isAutoSelect())
                icon = R.drawable.ic_lock_lit;
            else
                icon = R.drawable.ic_lock_unlit;
            lockImage.setImageResource(icon);
            view.findViewById(R.id.layers_manager_item_background)
                    .setBackgroundColor(selectedBackgroundColor);
        } else {
            view.findViewById(R.id.layers_manager_item_background)
                    .setBackgroundColor(normalBackgroundColor);
        }

        final boolean isVisible = this.layer.isVisible(selection.getName());
        final int visibilityIcon = isVisible ? R.drawable.overlay_visible
                : R.drawable.overlay_not_visible;
        visibilityToggle.setImageResource(visibilityIcon);

        return view;
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
        ToggleButton outlinesButton = view.getOutlineToggleButton();
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
            final SpatialCalculator calc = new SpatialCalculator(true);
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
