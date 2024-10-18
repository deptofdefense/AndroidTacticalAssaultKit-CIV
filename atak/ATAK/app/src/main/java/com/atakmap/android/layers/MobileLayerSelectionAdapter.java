
package com.atakmap.android.layers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.PorterDuff.Mode;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.maps.CardLayer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.android.layers.RangeSeekBar.OnRangeSeekBarChangeListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.service.OnlineImageryExtension;
import com.atakmap.map.layer.raster.tilematrix.TileClient;
import com.atakmap.map.layer.raster.tilematrix.TileClientFactory;

import java.io.File;
import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MobileLayerSelectionAdapter extends LayerSelectionAdapter
        implements AtakMapView.OnMapMovedListener, View.OnClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private boolean onlyViewport = true;

    public static final String TAG = "MobileLayerSelectionAdapter";

    private static final String PREF_SELECTED = TAG + ".selected";
    private static final String PREF_OFFLINE_ONLY = TAG + ".isOfflineOnly";

    // flag to show sources with slider and checkbox
    private boolean expandedLayout = false;

    private final LimitingThread calc;

    protected final CardLayer cardLayer;
    protected final AbstractDataStoreRasterLayer2 layer;

    // remember the location of the slider bar for each layer
    protected final Map<DatasetDescriptor, ResolutionLevelSpec> resolutionLevel = new HashMap<>();

    private final static Comparator<LayerSelection> comparator = new Comparator<LayerSelection>() {
        @Override
        public int compare(LayerSelection ls1, LayerSelection ls2) {
            return ls1.getName().compareToIgnoreCase(ls2.getName());
        }
    };

    private final static Comparator<LayerSelection> distanceComparator = new Comparator<LayerSelection>() {
        @Override
        public int compare(LayerSelection ls1, LayerSelection ls2) {
            double d1 = Double.MAX_VALUE;
            double d2 = Double.MAX_VALUE;
            if (ls1.getTag() != null) {
                d1 = ((MobileImagerySpec) ls1.getTag()).distance;
            }
            if (ls2.getTag() != null) {
                d2 = ((MobileImagerySpec) ls2.getTag()).distance;
            }
            return Double.compare(d1, d2);
        }
    };

    MobileLayerSelectionAdapter(CardLayer cardLayer,
            MobileImageryRasterLayer2 layer, FeatureDataStore outlinesDataStore,
            MapView mapView, Context context) {

        super(layer, outlinesDataStore, mapView, context);

        this.cardLayer = cardLayer;
        this.layer = layer;

        final boolean offlineOnly = _prefs.get(PREF_OFFLINE_ONLY, false);

        setOfflineOnly(offlineOnly);

        calc = new LimitingThread(
                "MobileLayerSelectionAdapter-AOI-Isect-thread",
                new Runnable() {
                    @Override
                    public void run() {
                        final boolean offline = isOfflineOnly();
                        if (offline) {
                            invalidate(true);
                            try {
                                // TODO: Artificial time but allows me not to
                                // have to reconstruct the logic in validateImpl
                                // the day before release.
                                Thread.sleep(500);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                });

        mapView.addOnMapMovedListener(this);

        String active = _prefs.get("lastViewedLayer.active", null);

        final String selected = _prefs.get(PREF_SELECTED, null);
        if (selected != null && active != null && active.equals("Mobile")) {
            if (offlineOnly) {
                mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        // probably not thread safe
                        Log.d(TAG, "offline layer selected: " + selected);
                        visible = true;
                        onlyViewport = false;
                        validateImpl(true);
                        visible = false;
                        onlyViewport = true;
                        setSelected(selected);
                    }
                });
            } else {
                postSelected(selected);
            }
        }

        _prefs.registerListener(this);
    }

    @Override
    public void dispose() {

        _mapView.removeOnMapMovedListener(this);
        _prefs.unregisterListener(this);
        _prefs.set(PREF_OFFLINE_ONLY, isOfflineOnly());
        calc.dispose();

        super.dispose();
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        calc.exec();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals(PREF_SELECTED)) {
            String selected = p.getString(PREF_SELECTED, null);
            String curr = this.curr != null ? this.curr.toString() : null;
            if (!FileSystemUtils.isEquals(selected, curr)) {
                cardLayer.show("Mobile");
                postSelected(selected);
            }
        }
    }

    synchronized void setOnlyViewportDisplay(final boolean ovp) {
        onlyViewport = ovp;
        invalidate(true);
    }

    /**
     * Brute force method to fall back to the original selections at code, otherwise
     * just use the list cause its accurate.
     */
    @Override
    public synchronized List<LayerSelection> getAllSelectionsAt(
            GeoPoint point) {

        if (onlyViewport) {
            validateNoSync();
            return new ArrayList<>(_selections);

        } else {
            return super.getAllSelectionsAt(point);
        }
    }

    @Override
    protected void validateImpl(boolean rebuild) {
        if (!visible)
            return;

        super.validateImpl(rebuild);

        DatasetQueryParameters params = new DatasetQueryParameters();

        final boolean offline = isOfflineOnly();

        final GeoPoint mapCenter = _mapView.getPoint().get();

        if (offline) {

            params.remoteLocalFlag = RasterDataStore.DatasetQueryParameters.RemoteLocalFlag.LOCAL;

            final GeoBounds bnds = _mapView.getBounds();

            if (!Double.isNaN(bnds.getNorth()) &&
                    !Double.isNaN(bnds.getWest()) &&
                    !Double.isNaN(bnds.getSouth()) &&
                    !Double.isNaN(bnds.getWest())) {
                final GeoPoint upperLeft = new GeoPoint(bnds.getNorth(),
                        bnds.getWest());
                final GeoPoint lowerRight = new GeoPoint(bnds.getSouth(),
                        bnds.getEast());

                if (onlyViewport) {
                    params.spatialFilter = new DatasetQueryParameters.RegionSpatialFilter(
                            upperLeft, lowerRight);
                }

            }

        }

        this.layer.filterQueryParams(params);

        if (rebuild || offline) {

            ArrayList<LayerSelection> f = new ArrayList<>();

            for (final LayerSelection sel : _selections) {
                RasterDataStore.DatasetDescriptorCursor result = null;
                try {
                    params.imageryTypes = Collections.singleton(sel.getName());

                    MobileImagerySpec spec = (MobileImagerySpec) sel.getTag();
                    if (spec == null) {
                        spec = new MobileImagerySpec();
                    } else {
                        // clear the offline size
                        spec.offlineSize = 0;
                    }

                    result = this.layer.getDataStore().queryDatasets(params);
                    DatasetDescriptor desc;
                    while (result.moveToNext()) {
                        // for offline, generate a new tag for each entry

                        desc = result.get();
                        if (desc.isRemote()) {
                            if (spec.desc == null)
                                spec.desc = desc;
                            final String cachePath = desc
                                    .getExtraData("offlineCache");
                            if (cachePath != null) {
                                File file = new File(
                                        FileSystemUtils
                                                .sanitizeWithSpacesAndSlashes(
                                                        cachePath));
                                if (IOProviderFactory.exists(file))
                                    spec.cache = file;
                            }
                        } else {
                            final File file = new File(
                                    FileSystemUtils
                                            .sanitizeWithSpacesAndSlashes(desc
                                                    .getUri()));
                            if (IOProviderFactory.exists(file))
                                spec.offlineSize += IOProviderFactory
                                        .length(file);

                            if (offline) {
                                spec.parent = sel;
                                Geometry g = desc.getCoverage(null);
                                LayerSelection childsel = new LayerSelection(
                                        file.getName(), g,
                                        desc.getMinResolution(null),
                                        desc.getMaxResolution(null));
                                spec.distance = computeDistance(mapCenter, g);
                                childsel.setTag(spec);
                                f.add(childsel);
                                spec = new MobileImagerySpec();
                            }
                        }
                        spec.count++;

                    }
                    if (spec.count > 0) {
                        spec.offlineOnly = (spec.desc == null);
                        // add only once and only if the spec count is greater than 0 and 
                        // online only  offline gets a single spec for each file found.
                        if (!offline)
                            sel.setTag(spec);
                    }
                } finally {
                    if (result != null)
                        result.close();
                }
            }
            if (offline) {
                _selections.clear();
                _selections.addAll(f);
                Collections.sort(_selections, distanceComparator);
            }
        }

    }

    private double computeDistance(GeoPoint p, Geometry g) {
        Envelope mbb = g.getEnvelope();
        GeoPoint cg = new GeoPoint((mbb.minY + mbb.maxY) / 2,
                (mbb.minX + mbb.maxX) / 2);
        return cg.distanceTo(p);
    }

    /**
     * get the number of tiles in the selected rectangle for all the levels in each selected layer
     */
    synchronized int getTileCount() {
        LayerSelection sel = getSelected();
        final ResolutionLevelSpec res = resolutionLevel.get(getDataset(sel));
        if (sel != null && (sel.getTag() instanceof MobileImagerySpec)
                && res != null)
            return getTileCount2((MobileImagerySpec) sel.getTag(),
                    res.getDownloadMinRes(), res.getDownloadMaxRes());
        return 0;
    }

    protected String getStringTileCount(int tiles) {
        if (tiles <= LayersManagerBroadcastReceiver.TILE_DOWNLOAD_LIMIT)
            return "≤ " + tiles + " tiles";
        else
            return "> " + LayersManagerBroadcastReceiver.TILE_DOWNLOAD_LIMIT
                    + " tiles";
    }

    protected int getTileCount2(MobileImagerySpec spec, double minRes,
            double maxRes) {
        ResolutionLevelSpec res = resolutionLevel.get(spec.desc);
        if (res != null && maxRes < res.defaultMinRes) {

            // Approximate tile count based on a fetch at a lower resolution
            double dmr = res.defaultMinRes / 8;
            int tileApprox = zoomRec.getTileNum2(spec.desc.getUri(), dmr, dmr);

            long tiles = 0;
            while (dmr >= maxRes) {
                long tilesPerLevel = (long) Math.pow((int) (dmr / maxRes), 2);
                tiles += tilesPerLevel * tileApprox;
                maxRes *= 2;
            }
            while (minRes >= maxRes) {
                tileApprox = (int) Math.sqrt(tileApprox);
                tiles += tileApprox;
                maxRes *= 2;
            }
            if (tiles > Integer.MAX_VALUE)
                tiles = Integer.MAX_VALUE;

            // Round to the nearest power of 10 to make it look more like
            // an approximation
            float pow10 = (int) Math.pow(10, Math.floor(Math.log10(tiles)));
            tiles = (int) (Math.round(tiles / pow10) * pow10);
            return (int) tiles;
        }
        return zoomRec.getTileNum2(spec.desc.getUri(), minRes, maxRes);
    }

    /**
     * TODO depending if the expanded flag is set to true or false, this returns
     * a basic view with just layer name and foreground or background for false or
     * an expanded view with view level slider and checkbox if true.
     */
    @Override
    protected View getViewImpl(final LayerSelection tsInfo, final int position,
            View row, ViewGroup parent) {

        final boolean offline = isOfflineOnly();

        final MobileImagerySpec spec;
        if (tsInfo.getTag() instanceof MobileImagerySpec)
            spec = (MobileImagerySpec) tsInfo.getTag();
        else
            spec = null;

        // resolve the parent tsInfo if it exists, otherwise use the existing
        // tsInfo.
        final LayerSelection ftsInfo;
        if (spec != null && spec.parent != null)
            ftsInfo = spec.parent;
        else
            ftsInfo = tsInfo;

        final LayerViewHolder h = LayerViewHolder.get(_context, row, parent);
        h.selection = ftsInfo;

        final String name = tsInfo.getName();

        if (name.length() > 26)
            h.title.setTextSize(12);
        else if (name.length() > 23)
            h.title.setTextSize(13);
        else
            h.title.setTextSize(18);

        h.title.setText(name);

        StringBuilder sb = new StringBuilder();
        if (spec != null) {
            if (spec.offlineSize > 0L)
                sb.append(MathUtils.GetLengthString(spec.offlineSize))
                        .append(" local ");
            if (!offline) {
                final long cacheSize = (spec.cache != null)
                        ? IOProviderFactory.length(spec.cache)
                        : 0L;
                if (cacheSize > 0L)
                    sb.append(MathUtils.GetLengthString(cacheSize))
                            .append(" cached ");
            }
        }
        h.desc.setText(sb);

        h.lockBtn.setVisibility(View.GONE);
        h.vizBtn.setVisibility(View.GONE);

        if (getDescriptorFile(tsInfo) != null && !isOfflineOnly())
            h.sendBtn.setVisibility(View.VISIBLE);
        else
            h.sendBtn.setVisibility(View.INVISIBLE);

        h.sendBtn.setOnClickListener(this);

        FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
        params.featureNames = Collections.singleton(name);
        params.visibleOnly = true;

        final boolean outlineVisible = (this.outlinesDatastore
                .queryFeaturesCount(params) > 0);

        // no outline color
        h.outlineBorder.getBackground().mutate().setColorFilter(0,
                Mode.MULTIPLY);

        h.outlineBtn.setOnCheckedChangeListener(null);
        h.outlineBtn.setChecked(outlineVisible);
        h.outlineBtn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean s) {
                FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
                params.featureNames = Collections.singleton(name);
                LayersMapComponent.setFeaturesVisible(outlinesDatastore,
                        params, s);
                invalidate(true);
            }
        });

        final boolean isSelection;
        if (offline) {
            final LayerSelection selected = getSelected();
            //Log.d(TAG, "selected object: " + selected);

            if (selected != null) {
                //Log.d(TAG, "selected: " + selected.getName());
                //Log.d(TAG, "checking - current entry: " + tsInfo.getName());
                isSelection = this.active
                        && (selected != null && selected.getName().equals(
                                tsInfo.getName()));
                h.background.setBackgroundColor(isSelection
                        ? selectedBackgroundColor
                        : normalBackgroundColor);
            } else {
                isSelection = false;
            }
        } else {
            // set up foreground/background
            final String selected = this.layer.getSelection();
            isSelection = this.active
                    && (selected != null && selected.equals(ftsInfo.getName()));
            h.background.setBackgroundColor(isSelection
                    ? selectedBackgroundColor
                    : normalBackgroundColor);
        }

        final boolean downloadable = LayersManagerBroadcastReceiver
                .isSelectionDownloadable(tsInfo);

        // if an area has been selected the expandedLayout will be true. If so,
        // show the sliders for the selected item
        if (isSelection && expandedLayout && downloadable) {
            h.outlineBorder.setVisibility(View.GONE);
            h.sendBtn.setVisibility(View.GONE);

            // set a click listener for the text view because the other elements in this view
            // prevent the
            // onItemClicked event of the listView from firing
            h.titleLayout.setOnClickListener(this);

            h.resLayout.setVisibility(View.VISIBLE);
            h.dlLayout.setVisibility(View.VISIBLE);

            // create RangeSeekBar as Integer range between 0 and the number of layers the source
            // has
            int numLevels = 0;
            if (downloadable && spec != null) {
                numLevels = Integer.parseInt(DatasetDescriptor.getExtraData(
                        spec.desc, "_levelCount", "0"));
            }

            if (numLevels < 1)
                numLevels = (int) Math.ceil(Math.log(ftsInfo.getMinRes()
                        / ftsInfo.getMaxRes())
                        / Math.log(2d));

            RangeSeekBar<Integer> seekBar = new RangeSeekBar<>(0,
                    numLevels,
                    _mapView.getContext());
            seekBar.setNotifyWhileDragging(true);
            seekBar.setOnRangeSeekBarChangeListener(
                    new OnRangeSeekBarChangeListener<Integer>() {
                        @Override
                        public void onRangeSeekBarValuesChanged(
                                RangeSeekBar<?> bar,
                                Integer minValue,
                                Integer maxValue) {

                            if (spec == null)
                                return;

                            ResolutionLevelSpec res = resolutionLevel
                                    .get(spec.desc);
                            if (res == null) {
                                res = new ResolutionLevelSpec(spec.desc);
                                resolutionLevel.put(spec.desc, res);
                            }

                            res.sliderMin = minValue;
                            res.sliderMax = maxValue;

                            // recalculate the resolution of the selected min and max levels
                            setCurrentRes(h.resTxt, res.getDownloadMinRes(),
                                    res.getDownloadMaxRes());

                            // update the number of tiles for the range the user selected
                            h.dlSize.setText(
                                    getStringTileCount(getTileCount2(spec,
                                            res.getDownloadMinRes(),
                                            res.getDownloadMaxRes())));
                        }
                    });

            // add RangeSeekBar to pre-defined layout
            h.rangeLayout.addView(seekBar);

            // start min at best fit for Rect
            // set the min and max level for each source
            int sliderMin;
            int sliderMax;
            double minRes;
            double maxRes;
            if (downloadable && spec != null) {
                ResolutionLevelSpec res = resolutionLevel.get(spec.desc);
                if (res == null) {
                    res = new ResolutionLevelSpec(spec.desc);
                    resolutionLevel.put(spec.desc, res);

                    TileClient client = null;
                    try {
                        client = TileClientFactory.create(spec.desc.getUri(),
                                null, null);
                        if (client != null) {
                            res.defaultMinRes = zoomRec
                                    .estimateMinDownloadResolution(client,
                                            res.maxAvailableRes);
                            res.sliderMin = com.atakmap.math.MathUtils.clamp(
                                    OSMUtils.mapnikTileLevel(res.defaultMinRes)
                                            - res.levelOffset,
                                    0,
                                    res.sliderMax);
                        }

                    } finally {
                        if (client != null)
                            client.dispose();
                    }

                    // XXX - there was some adjustment in here to set the
                    //       maximum level to 17 if the max level was greater
                    //       than 17 and the minimum level was more than 2
                    //       levels less -- the logic would be a little
                    //       complicated with the new resolution storage
                    //       mechanism and I'm not convinced it adds significant
                    //       value, so omitting for now

                    //int minLev = zoomRec.getMinLevel2(res.maxAvailableRes);
                    //if (minLev < (maxLev - 2) && maxLev > 17)
                    //    maxLev = 17;
                }

                sliderMin = res.sliderMin;
                sliderMax = res.sliderMax;

                minRes = res.getDownloadMinRes();
                maxRes = res.getDownloadMaxRes();
            } else {
                sliderMin = Integer.MIN_VALUE;
                sliderMax = Integer.MIN_VALUE;

                minRes = Double.NaN;
                maxRes = Double.NaN;
            }

            // set the seek bar knobs in the right spots
            seekBar.setSelectedMinValue(sliderMin);
            seekBar.setSelectedMaxValue(sliderMax);

            // update the resolution views to display the correct info for the current res levels
            if (downloadable && spec != null) {
                h.dlSize.setText(getStringTileCount(getTileCount2(spec, minRes,
                        maxRes)));
                setCurrentRes(h.resTxt, minRes, maxRes);
            } else {
                h.dlSize.setText("Local Only");
                h.resTxt.setText("");
            }
        } else {
            h.outlineBorder.setVisibility(View.VISIBLE);
            h.resLayout.setVisibility(View.GONE);
            h.dlLayout.setVisibility(View.GONE);
        }

        return h.root;
    }

    @Override
    public void onClick(View v) {
        Object tag = v.getTag();
        if (!(tag instanceof LayerViewHolder))
            return;

        LayerViewHolder h = (LayerViewHolder) tag;

        // Select row
        if (v == h.titleLayout) {
            setSelected(h.selection);
            setLocked(true);
            notifyDataSetChanged();
        }

        // Send layer
        else if (v == h.sendBtn) {
            showSendDialog(h.selection);
        }
    }

    private File getDescriptorFile(LayerSelection selection) {
        DatasetDescriptor desc = getDataset(selection);
        if (desc == null)
            return null;

        String uri = desc.getUri();
        if (FileSystemUtils.isEmpty(uri))
            return null;

        File xmlFile = new File(uri);
        if (!FileSystemUtils.isFile(xmlFile))
            return null;

        return xmlFile;
    }

    private void showSendDialog(LayerSelection selection) {
        File xmlFile = getDescriptorFile(selection);

        if (xmlFile == null)
            return;

        SendDialog.Builder b = new SendDialog.Builder(_mapView);
        b.addFile(xmlFile);
        b.show();
    }

    public void showDetailsDialog(final LayerSelection selection) {
        DatasetDescriptor desc = getDataset(selection);
        if (desc == null)
            return;

        // XXX - URL isn't stored anywhere in publicly available metadata...
        String url = null;
        try {
            File xmlFile = new File(desc.getUri());
            List<String> lines = FileSystemUtils.readLines(
                    xmlFile.getAbsolutePath());
            for (String line : lines) {
                int startIdx = line.indexOf("<url>");
                int endIdx = line.indexOf("</url>", startIdx + 1);
                if (startIdx > -1 && endIdx > -1)
                    url = line.substring(startIdx + 5, endIdx);
            }
        } catch (Exception ignored) {
        }

        View v = LayoutInflater.from(_context).inflate(
                R.layout.layer_details_dialog, _mapView, false);
        TextView name = v.findViewById(R.id.layer_name);
        TextView path = v.findViewById(R.id.layer_path);

        name.setText(desc.getName());
        path.setText(desc.getUri());

        View urlLayout = v.findViewById(R.id.layout_url);
        TextView urlTxt = v.findViewById(R.id.layer_url);
        if (url != null) {
            urlLayout.setVisibility(View.VISIBLE);
            urlTxt.setText(url);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.layer_details);
        b.setView(v);
        b.setPositiveButton(R.string.send,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showSendDialog(selection);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    /**
     * Determine the best units to display the resolution in and format the string so that it looks
     * nice.
     * 
     * @param resView - the text view to change
     * @param resMin - the minimum resolution chosen by the user
     * @param resMax - the max resolution chosen by the user
     */
    protected void setCurrentRes(TextView resView, double resMin,
            double resMax) {
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
    protected Comparator<LayerSelection> getSortComparator() {
        if (isOfflineOnly())
            return distanceComparator;
        else
            return comparator;
    }

    public synchronized void setExpandedLayout(boolean isExpanded) {
        expandedLayout = isExpanded;
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    /**
     * Given a layer, if it has an associated MobileImagerySpec and parent 
     * return the layer with restricted bounds and the parents name.
     */
    public static LayerSelection getParentLayer(final LayerSelection ls) {
        if (ls != null && ls.getTag() != null &&
                ls.getTag() instanceof MobileImagerySpec) {
            MobileImagerySpec mspec = (MobileImagerySpec) ls.getTag();
            if (mspec.parent != null) {
                return new LayerSelection(mspec.parent.getName(),
                        ls.getBounds(), ls.getMinRes(), ls.getMaxRes());
            }
        }
        return ls;
    }

    LayerSelection curr = null;

    @Override
    public LayerSelection getSelected() {
        return curr;
    }

    @Override
    public void setSelected(final String sstr) {
        for (LayerSelection selection : _selections) {
            //Log.d(TAG, selection.getName() + " searching for " + sstr);
            if (selection.getName().equalsIgnoreCase(sstr)) {
                this.curr = selection;
                LayerSelection ls = getParentLayer(curr);
                if (ls != null) {
                    super.setSelected(ls.getName());
                } else {
                    super.setSelected((String) null);
                }
                _prefs.set(PREF_SELECTED, curr.toString());
                return;
            }
        }
    }

    // Set selected layer name
    private void postSelected(final String selected) {
        // Delay a bit to give newly imported sources a chance to import
        _mapView.postDelayed(new Runnable() {
            @Override
            public void run() {
                // probably not thread safe
                Log.d(TAG, "online layer selected: " + selected);
                visible = true;
                validateImpl(true);
                setSelected(selected);
                visible = false;
            }
        }, 500);
    }

    @Override
    public Object getItem(int position) {
        return _selections.get(position);
    }

    /** @return a map of layers that the users selected */
    public synchronized Map<LayerSelection, Pair<Double, Double>> getLayersToDownload() {
        LayerSelection sel = this.getSelected();
        if (sel == null || !(sel.getTag() instanceof MobileImagerySpec))
            return Collections
                    .emptyMap();

        MobileImagerySpec spec = (MobileImagerySpec) sel.getTag();
        if (spec.offlineOnly)
            return Collections
                    .emptyMap();

        ResolutionLevelSpec res = resolutionLevel.get(getDataset(this
                .getSelected()));
        if (res == null)
            return Collections
                    .emptyMap();

        return Collections
                .singletonMap(
                        sel,
                        Pair.create(res.getDownloadMinRes(),
                                res.getDownloadMaxRes()));
    }

    /** reset the view as if it were just opened */
    public void reset() {
        resolutionLevel.clear();
        expandedLayout = false;
    }

    public boolean isOfflineOnly() {
        OnlineImageryExtension svc = this.layer
                .getExtension(OnlineImageryExtension.class);
        return (svc != null) && svc.isOfflineOnlyMode();
    }

    public void setOfflineOnly(boolean offline) {
        OnlineImageryExtension svc = this.layer
                .getExtension(OnlineImageryExtension.class);
        if (svc != null)
            svc.setOfflineOnlyMode(offline);
        invalidate(true);

        FeatureDataStore fds = getOutlinesDataStore();
        if (fds instanceof MobileOutlinesDataStore) {
            ((MobileOutlinesDataStore) fds).setUnion(!offline);
        }
    }

    public boolean supportsOfflineMode() {
        OnlineImageryExtension svc = this.layer
                .getExtension(OnlineImageryExtension.class);
        return (svc != null);
    }

    static DatasetDescriptor getDataset(LayerSelection sel) {
        if (sel == null)
            return null;
        if (!(sel.getTag() instanceof MobileImagerySpec))
            return null;
        return ((MobileImagerySpec) sel.getTag()).desc;
    }

    static class MobileImagerySpec {
        protected File cache;
        protected long offlineSize;
        public LayerSelection parent;
        private int count;
        private double distance;
        public boolean offlineOnly;
        public DatasetDescriptor desc;

        public MobileImagerySpec() {
            this.cache = null;
            this.parent = null;
            this.distance = Double.MAX_VALUE;
            this.offlineSize = 0L;
            this.count = 0;
            this.offlineOnly = true;
            this.desc = null;

        }
    }

    protected static class ResolutionLevelSpec {
        final DatasetDescriptor desc;
        final int levelOffset;
        final int numLevels;
        final double minAvailableRes;
        final double maxAvailableRes;

        double defaultMinRes;
        int sliderMax;
        int sliderMin;

        public ResolutionLevelSpec(DatasetDescriptor desc) {
            this.desc = desc;
            this.minAvailableRes = desc.getMinResolution(null);
            this.maxAvailableRes = desc.getMaxResolution(null);
            this.defaultMinRes = this.minAvailableRes;

            int l = Integer.parseInt(DatasetDescriptor.getExtraData(
                    this.desc, "_levelCount", "0"));
            if (l < 1)
                l = (int) Math.ceil(Math.log(this.minAvailableRes
                        / this.maxAvailableRes)
                        / Math.log(2d)) + 1;
            this.numLevels = l;

            this.levelOffset = OSMUtils.mapnikTileLevel(this.minAvailableRes);

            this.sliderMin = 0;
            this.sliderMax = this.numLevels - 1;
        }

        public double getDownloadMinRes() {
            if (this.sliderMin <= 0)
                return this.minAvailableRes;
            return Math.min(
                    OSMUtils.mapnikTileResolution(this.sliderMin
                            + this.levelOffset),
                    this.minAvailableRes);
        }

        public double getDownloadMaxRes() {
            if (this.sliderMax >= this.numLevels - 1)
                return this.maxAvailableRes;
            return Math.max(
                    OSMUtils.mapnikTileResolution(this.sliderMax
                            + this.levelOffset),
                    this.maxAvailableRes);
        }
    }
}
