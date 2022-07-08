
package com.atakmap.android.viewshed;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.util.Pair;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.graphics.GLMapGroup2;
import com.atakmap.android.maps.graphics.GLQuadtreeNode2;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.spatial.SpatialCalculator;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Driver;
import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContourLinesOverlay extends AbstractHierarchyListItem
        implements Layer, MapOverlay, MapView.OnMapMovedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "ContourLinesOverlay";
    static final String CONTOUR_PREFERENCE_LINE_COLOR_KEY = "contour_prefs_line_color";
    static final String CONTOUR_PREFERENCE_INTERVAL_KEY = "contour_prefs_interval";
    static final String CONTOUR_PREFERENCE_UNIT_KEY = "contour_prefs_unit";
    static final String CONTOUR_PREFERENCE_MAJOR_WIDTH_KEY = "contour_prefs_major_width";
    static final String CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY = "contour_prefs_contour_visible";
    static final String CONTOUR_PREFERENCE_LABEL_VISIBLE_KEY = "contour_prefs_label_visible";
    static final String CONTOUR_PREFERENCE_MAJOR_VISIBLE_KEY = "contour_prefs_major_visible";
    static final String CONTOUR_PREFERENCE_MINOR_VISIBLE_KEY = "contour_prefs_minor_visible";

    private boolean visible;
    private String workingPath;
    private PersistentDataSourceFeatureDataStore2 contourDataStore;
    private final String myDir;
    private final ArrayList<Polyline> polylines = new ArrayList<>();
    private GLOverlay _glOverlay;
    private final GLLayerSpi2 SPI2;
    private final DefaultMapGroup mapGroup;
    private final MapView mapView;
    private final SharedPreferences prefs;

    //flag to track if the current generation process should be cancelled and the current state
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    private final Set<OnLayerVisibleChangedListener> visibleChangedListeners;

    public ContourLinesOverlay(MapView mapView) {
        this.mapView = mapView;
        prefs = PreferenceManager.getDefaultSharedPreferences(
                mapView.getContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        mapView.addOnMapMovedListener(this);

        mapGroup = new DefaultMapGroup();
        mapGroup.setVisible(true);
        mapGroup.setFriendlyName("Contour Lines");
        SPI2 = new GLLayerSpi2() {
            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
                final MapRenderer mapRenderer = arg.first;
                final Layer layer = arg.second;
                if ((layer instanceof ContourLinesOverlay)) {
                    _glOverlay = new GLOverlay(mapRenderer,
                            (ContourLinesOverlay) layer);
                    return GLLayerFactory.adapt(_glOverlay);
                }
                return null;
            }
        };

        GLLayerFactory.register(SPI2);

        this.visibleChangedListeners = new HashSet<>();

        myDir = FileSystemUtils.getItem(FileSystemUtils.TMP_DIRECTORY)
                .getAbsolutePath() + "/ContourLines";

        File programDataContourLinesDirectory = new File(
                FileSystemUtils.getItem(FileSystemUtils.TMP_DIRECTORY),
                "ContourLinesWorkingDirectory");
        if (!IOProviderFactory.exists(programDataContourLinesDirectory))
            if (!IOProviderFactory.mkdirs(programDataContourLinesDirectory)) {
                Log.d(TAG, "could not make the contour lines directory: "
                        + programDataContourLinesDirectory);
            }

        try {
            workingPath = programDataContourLinesDirectory.getAbsolutePath()
                    + "/" + "WrkDir";
            if (!IOProviderFactory.exists(new File(workingPath)) &&
                    !IOProviderFactory.mkdirs(new File(workingPath))) {
                Log.w(TAG, "Failed to create contour lines data store");
            }
            contourDataStore = new PersistentDataSourceFeatureDataStore2(
                    new File(workingPath));
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            toast("error occurred processing the directory");
        }
    }

    public void getContourData() {
        isCancelled.set(false);
        if (!visible)
            return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                clearLines();
            }
        }).start();
        if (isCancelled.get()) {
            updateProgress(-1);
            return;
        }
        File dir = new File(FileSystemUtils
                .getItem(FileSystemUtils.EXPORT_DIRECTORY).getPath()
                + "/testContourDir/" + "Files");
        if (!IOProviderFactory.exists(dir) && !IOProviderFactory.mkdirs(dir)) {
            toast("failed to create the directory");
        }

        //clear out any files added to DS to reduce size of DS when re generating
        File[] files = IOProviderFactory.listFiles(dir);
        if (files != null) {
            for (File f : files) {
                try {
                    contourDataStore.remove(f);
                    if (!FileSystemUtils.deleteFile(f)) {
                        Log.d(TAG, "error deleting the file: " + f);
                    }
                } catch (Exception e) {
                    toast("failed to remove the directory");
                }
            }
        }
        if (isCancelled.get()) {
            updateProgress(-1);
            return;
        }
        updateProgress(10);
        GetNewData();
    }

    private GeoPoint ul, lr;

    private void GetNewData() {
        progress = 0.0;

        final GeoPointMetaData p = mapView.getPoint();

        if (p == null)
            return;

        final double centerNS = p.get().getLatitude();
        final double centerEW = p.get().getLongitude();
        final double padew = 0.15d;
        final double padns = 0.20d;

        ul = new GeoPoint(centerNS + padns, centerEW - padew);
        GeoPoint ur = new GeoPoint(centerNS + padns, centerEW + padew);
        lr = new GeoPoint(centerNS - padns, centerEW + padew);
        GeoPoint ll = new GeoPoint(centerNS - padns, centerEW - padew);

        final ElevationSource.Cursor cursor;

        // build out the params for the AOI
        ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
        params.spatialFilter = DatasetDescriptor.createSimpleCoverage(
                ul,
                ur,
                lr,
                ll);

        params.flags = ElevationData.MODEL_TERRAIN; // use only terrain model

        cursor = ElevationManager.queryElevationSources(params);
        if (cursor == null) {
            toast("No elevation data found for current map view.");
            updateProgress(-1);
            return;
        }
        final Map<String, List<DtedCell>> cells = new HashMap<>();
        while (cursor.moveToNext()) {
            if (cursor.getType().toLowerCase(LocaleUtil.getCurrent())
                    .contains("dted")) {

                final Envelope bounds = cursor.getBounds().getEnvelope();

                final DtedCell cell = new DtedCell();
                cell.path = cursor.getUri();
                cell.bounds = new GeoBounds(bounds.maxX, bounds.minX,
                        bounds.minY, bounds.maxX);
                try {
                    //DTED'#'
                    cell.dtedRank = Character.getNumericValue(cursor.getType()
                            .toCharArray()[cursor.getType().length() - 1]);
                    GeoPoint centerGp = cell.bounds.getCenter(null);
                    String cellKey = centerGp.toString();
                    if (!cells.containsKey(cellKey)) {
                        cells.put(cellKey, new ArrayList<DtedCell>());
                    }
                    cells.get(cellKey).add(cell);
                } catch (Exception ex) {
                    // ignored
                }
            }
        }

        //iterate and remove all cells that do not have the highest dted ranking and same areas for some reason
        //i have DTEd0,1,2,3 on the same region and i am getting 4 layers over the same area.
        //I thought the EleManager returns the best of each cell?
        final List<String> filtered = new ArrayList<>();
        for (Map.Entry<String, List<DtedCell>> entry : cells.entrySet()) {
            Collections.sort(entry.getValue());
            filtered.add(entry.getValue().get(0).path);
        }
        cells.clear();

        if (filtered.size() == 0) {
            toast("No elevation data found for current map view.");
            updateProgress(-1);
            return;
        }

        final double progressStep = (40.0 / filtered.size());

        new Thread(new Runnable() {
            @Override
            public void run() {

                //wait for all threads to finish up their work adding their files to the DS
                int threadPools = filtered.size();
                if (filtered.size() > 4) {
                    threadPools = (int) Math.round(filtered.size() / 2.0);
                }
                ExecutorService executor = Executors
                        .newFixedThreadPool(threadPools);
                for (int i = 0; i < filtered.size(); i++) {
                    Runnable worker = new ContourRunnable(filtered.get(i),
                            myDir, progressStep);
                    executor.execute(worker);
                }
                executor.shutdown();
                while (!executor.isTerminated()) {
                    if (isCancelled.get()) {
                        return;
                    }
                }
                BuildContours();
            }
        }).start();
    }

    private void WriteToDataStore(String filePath) {
        if (isCancelled.get()) {
            return;
        }
        File folder = new File(
                FileSystemUtils.getItem(FileSystemUtils.EXPORT_DIRECTORY)
                        .getPath() + "/testContourDir/" + "Files");
        if (!IOProviderFactory.exists(folder)
                && !IOProviderFactory.mkdirs(folder)) {
            toast("failed to create the folder");
        }
        File[] files = IOProviderFactory.listFiles(new File(filePath));

        if (files != null) {
            for (File f : files) {
                try {
                    if (FileSystemUtils.getExtension(f, false, false)
                            .equals("prj"))
                        continue;
                    FileSystemUtils.copyFile(f,
                            new File(folder.getAbsolutePath() + "/"
                                    + f.getName()));
                } catch (Exception e) {
                    toast("failed to copy file");
                }
            }
        }
        AddDataStoreFiles();
    }

    private void AddDataStoreFiles() {
        if (isCancelled.get()) {
            return;
        }
        File folder = new File(
                FileSystemUtils.getItem(FileSystemUtils.EXPORT_DIRECTORY)
                        .getPath() + "/testContourDir/" + "Files");
        if (!IOProviderFactory.exists(folder)
                && !IOProviderFactory.mkdirs(folder)) {
            //Failed
            toast("failed to create the folder");
        }

        final File[] files = IOProviderFactory.listFiles(folder);
        if (files != null) {
            for (File f : files) {
                try {
                    contourDataStore.add(f);
                } catch (Exception e) {
                    toast("failed to add the datasource");
                }
            }
        }
        contourDataStore.refresh();

    }

    private double progress;

    //queries the datastore for valid spatial referenced features
    //each feature is created as a polyline and required properties are attached.
    //the line's points are squashed using the spatial calc to remove uneeded same line points
    //lines are added to drawing group to be rendered using the core rendering method
    private void BuildContours() {

        FeatureCursor cursor = null;
        boolean showingMajor = false;
        boolean showingMinor = false;
        updateMajorCb(false);
        updateMinorCb(false);

        try {
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.spatialFilter = new FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter(
                    ul, lr);

            double totalCount = (50.0
                    / contourDataStore.queryFeaturesCount(params));
            progress = 50;
            cursor = contourDataStore.queryFeatures(params); // TEMP COMMENTED;
            double conversionFactor = ((prefs
                    .getString(CONTOUR_PREFERENCE_UNIT_KEY, "m")
                    .equals("m")) ? 1
                            : ConversionFactors.METERS_TO_FEET);
            while (cursor.moveToNext()) {
                if (isCancelled.get()) {
                    return;
                }
                progress += totalCount;
                mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isCancelled.get()) {
                            return;
                        }
                        updateProgress((int) progress);
                    }
                });
                Feature set = cursor.get();
                if (set.getAttributes() != null) {
                    if (set.getAttributes()
                            .containsAttribute("ELEVATION")) {
                        double ele = set.getAttributes()
                                .getDoubleAttribute("ELEVATION");
                        if (set.getGeometry() instanceof LineString) {
                            LineString lineString = (LineString) set
                                    .getGeometry();
                            Polyline polyLine = new Polyline(
                                    UUID.randomUUID().toString());
                            polyLine.setZOrder(-1);
                            polyLine.setMaxRenderResolution(300d);
                            List<GeoPointMetaData> pts = new ArrayList<>();

                            if (lineString.getNumPoints() > 1) {
                                if (isCancelled.get()) {
                                    return;
                                }
                                for (int i = 0; i < lineString
                                        .getNumPoints(); i++) {
                                    pts.add(new GeoPointMetaData(
                                            new GeoPoint(
                                                    lineString.getY(i),
                                                    lineString
                                                            .getX(i),
                                                    ele)));
                                }
                            }

                            SpatialCalculator calculator = new SpatialCalculator.Builder()
                                    .inMemory().build();

                            List<GeoPoint> simplified = (List<GeoPoint>) calculator
                                    .simplify(
                                            GeoPointMetaData.unwrap(
                                                    pts),
                                            0.0015,
                                            false);
                            pts.clear();
                            calculator.dispose();

                            if (simplified != null && simplified.size() > 2) {
                                List<GeoPointMetaData> reducedPoints = GeoPointMetaData
                                        .wrap(simplified);

                                polyLine.setPoints(
                                        reducedPoints.toArray(
                                                new GeoPointMetaData[0]));
                                reducedPoints.clear();

                            } else {
                                continue;
                            }
                            polyLine.setStrokeColor(prefs.getInt(
                                    CONTOUR_PREFERENCE_LINE_COLOR_KEY,
                                    Color.WHITE));
                            updatePolylineWidth();
                            polyLine.setClickable(false);
                            polyLine.setEditable(false);
                            polyLine.setMovable(false);
                            if (checkMajorLine(
                                    ele * conversionFactor)) {
                                polyLine.setVisible(prefs.getBoolean(
                                        CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY,
                                        true)
                                        && prefs.getBoolean(
                                                CONTOUR_PREFERENCE_MAJOR_VISIBLE_KEY,
                                                true));
                                if (!showingMajor) {
                                    showingMajor = true;
                                    updateMajorCb(true);
                                }
                            } else {
                                polyLine.setVisible(prefs.getBoolean(
                                        CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY,
                                        true)
                                        && prefs.getBoolean(
                                                CONTOUR_PREFERENCE_MINOR_VISIBLE_KEY,
                                                true));
                                if (!showingMinor) {
                                    showingMinor = true;
                                    updateMinorCb(true);
                                }
                            }
                            polyLine.addStyleBits(
                                    Polyline.BASIC_LINE_STYLE_SOLID);
                            polyLine.addStyleBits(Shape.STYLE_STROKE_MASK);
                            polyLine.addStyleBits(
                                    Polyline.STYLE_OUTLINE_STROKE_MASK);
                            polyLine.setMetaDouble("elevation",
                                    ele);
                            polyLine.setMetaDouble("convFact",
                                    conversionFactor);
                            polyLine.setLineLabel((int) Math.round(
                                    ele * conversionFactor)
                                    + " "
                                    + prefs.getString(
                                            CONTOUR_PREFERENCE_UNIT_KEY,
                                            "m"));
                            polyLine.setTitle(
                                    "Contour Line " + polyLine.getLineLabel());
                            polyLine.toggleMetaData("labels_on",
                                    prefs.getBoolean(
                                            CONTOUR_PREFERENCE_LABEL_VISIBLE_KEY,
                                            false));

                            if (isCancelled.get()) {
                                return;
                            }
                            getMapGroup().addItem(polyLine);
                            if (!getMapGroup().getVisible())
                                getMapGroup().setVisible(true);
                        }
                    }
                }

            }
        } catch (Exception e) {
            Log.e("ContourLines", e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    updateProgress(-1);

                    //tell to OM to refresh its data after we add all avaialble contour lines
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            HierarchyListReceiver.REFRESH_HIERARCHY));
                }
            });
            if (getMapGroup().getItems().size() <= 0 && !isCancelled.get()) {
                toast("No Contour Lines generated for current Interval and Map View.");
            }

            //hide/reshow the checkboxes for filtering our minor vs major lines
            //depending on the interval and if we actualy got ay minor or major we need to update it here regardless
            updateMajorCb(showingMajor);
            updateMinorCb(showingMinor);
        }
    }

    //sets the visibility of the polyline labels that are currently being rendered
    private synchronized void setLabelsVisible(boolean visible) {
        for (MapItem mapItem : getMapGroup().getItems()) {
            Polyline p = (Polyline) mapItem;
            p.toggleMetaData("labels_on", visible);
        }
    }

    private synchronized void setContourVisible(boolean major, boolean minor) {
        for (MapItem mapItem : getMapGroup().getItems()) {
            Polyline p = (Polyline) mapItem;
            if (checkMajorLine(p.getMetaDouble("elevation", -1)
                    * p.getMetaDouble("convFact", -1))) {
                if (major) {
                    p.setVisible(prefs.getBoolean(
                            CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY,
                            true)
                            && prefs.getBoolean(
                                    CONTOUR_PREFERENCE_MAJOR_VISIBLE_KEY,
                                    true));
                }
                //MAJOR
            } else {
                if (minor) {
                    p.setVisible(prefs.getBoolean(
                            CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY,
                            true)
                            && prefs.getBoolean(
                                    CONTOUR_PREFERENCE_MINOR_VISIBLE_KEY,
                                    true));
                }
                //MINOR
            }
        }
    }

    //updates the line color(stroke color) of all polylines being rendered currently
    private synchronized void setLineColor(int color) {
        for (MapItem mapItem : getMapGroup().getItems()) {
            Polyline p = (Polyline) mapItem;
            p.setStrokeColor(color);
        }
    }

    //updates the label type on all polylines
    private synchronized void updateLabelUnits() {
        for (MapItem mapItem : getMapGroup().getItems()) {
            Polyline p = (Polyline) mapItem;
            String newUnit = prefs.getString(CONTOUR_PREFERENCE_UNIT_KEY,
                    "m");
            if (p.getLineLabel().endsWith(newUnit)) {
                return;
            } else {
                double newElev = p.getMetaDouble("elevation", -1)
                        * ((newUnit.equals("m")) ? 1
                                : ConversionFactors.METERS_TO_FEET);
                p.setLineLabel((int) Math.round(newElev) + " " + newUnit);
                p.setTitle("Contour Line " + p.getLineLabel());
            }
        }

    }

    //updates the polyline stroke width property on all shapes
    private synchronized void updatePolylineWidth() {
        for (MapItem mapItem : getMapGroup().getItems()) {
            int newWidth = prefs.getInt(
                    ContourLinesOverlay.CONTOUR_PREFERENCE_MAJOR_WIDTH_KEY,
                    4);
            Polyline p = (Polyline) mapItem;
            boolean isMajorLine = checkMajorLine(
                    p.getMetaDouble("elevation", -1)
                            * p.getMetaDouble("convFact", -1));
            if (isMajorLine) {
                p.setStrokeWeight(newWidth);
            } else {
                double minorWidth = 1.0;
                p.setStrokeWeight(minorWidth);
            }
        }
    }

    @Override
    public void addOnLayerVisibleChangedListener(
            OnLayerVisibleChangedListener l) {
        synchronized (visibleChangedListeners) {
            this.visibleChangedListeners.add(l);
        }
    }

    public void removeOnLayerVisibleChangedListener(
            OnLayerVisibleChangedListener l) {
        synchronized (visibleChangedListeners) {
            this.visibleChangedListeners.remove(l);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
        }
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public String getName() {
        return "Contour Lines Overlay";
    }

    // Map Overlay

    @Override
    public String getIdentifier() {
        return this.getClass().getName();
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListItem.Sort preferredSort) {
        if ((capabilities
                & Actions.ACTION_VISIBILITY) != Actions.ACTION_VISIBILITY)
            return null;
        return this;
    }

    @Override
    public String getTitle() {
        return this.getName();
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        return null;
    }

    @Override
    public boolean isChildSupported() {
        return false;
    }

    @Override
    public String getIconUri() {
        return null;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(Visibility.class))
            return clazz.cast(this.visibility);
        else
            return null;
    }

    @Override
    public Object getUserObject() {
        return this;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public Sort refresh(Sort sort) {
        return sort;
    }

    private final Visibility visibility = new Visibility() {

        @Override
        public boolean setVisible(boolean visible) {
            ContourLinesOverlay.this.setVisible(visible);
            return true;
        }

        @Override
        public boolean isVisible() {
            return ContourLinesOverlay.this.isVisible();
        }

    };

    //messages the view to update the enabled state of the generate button
    private void genEnabled(boolean enabled) {
        Intent intent = new Intent();
        intent.setAction(ViewshedMapComponent.UPDATE_CONTOUR_GEN_ENABLED);
        intent.putExtra(ViewshedMapComponent.CONTOUR_GEN_ENABLED, enabled);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    //messages the view to update the int progress for the rogress bar
    private void updateProgress(int progress) {
        Intent intent = new Intent();
        intent.setAction(ViewshedMapComponent.UPDATE_CONTOUR_PROGRESS);
        intent.putExtra(ViewshedMapComponent.CONTOUR_PROGRESS, progress);
        AtakBroadcast.getInstance().sendBroadcast(intent);

    }

    private void updateMajorCb(boolean state) {
        Intent intent = new Intent();
        intent.setAction(ViewshedMapComponent.UPDATE_VIS_MAJOR_CHECKBOX);
        intent.putExtra("state", state);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    private void updateMinorCb(boolean state) {
        Intent intent = new Intent();
        intent.setAction(ViewshedMapComponent.UPDATE_VIS_MINOR_CHECKBOX);
        intent.putExtra("state", state);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    public void dispose() {
        FileSystemUtils.deleteDirectory(new File(myDir), false);
        FileSystemUtils.deleteDirectory(new File(workingPath), false);
        clearLines();
        if (SPI2 != null)
            GLLayerFactory.unregister(SPI2);
    }

    //remove all lines currently held from the drawing group which is being rendered
    private synchronized void clearLines() {
        if (getMapGroup() == null)
            return;
        for (MapItem mapItem : getMapGroup().getItems()) {
            Polyline p = (Polyline) mapItem;
            if (getMapGroup().containsItem(p)) {
                getMapGroup().removeItem(p);
            }
        }
    }

    //checks if the line elevation is a multiple of 100 == major line
    private boolean checkMajorLine(double elevation) {
        int elev = (int) Math.round(elevation);
        return (elev % 100) == 0;
    }

    //helper method to post a UI message
    protected void toast(final String txt) {
        mapView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mapView.getContext(),
                        txt, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setCancelled() {
        isCancelled.set(true);
        updateProgress(-1);
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        boolean zoom = checkZoom();
        visibility.setVisible(zoom);
        genEnabled(zoom); //controls the enabled property of the generate button on the view
    }

    public boolean checkZoom() {
        return ATAKUtilities.getMetersPerPixel() < 300d;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        switch (key) {
            case CONTOUR_PREFERENCE_INTERVAL_KEY:
                break;
            case CONTOUR_PREFERENCE_LINE_COLOR_KEY:
                setLineColor(sharedPreferences.getInt(
                        CONTOUR_PREFERENCE_LINE_COLOR_KEY, Color.WHITE));
                break;
            case CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY:
                setContourVisible(true, true);
                break;
            case CONTOUR_PREFERENCE_LABEL_VISIBLE_KEY:
                setLabelsVisible(sharedPreferences.getBoolean(
                        CONTOUR_PREFERENCE_LABEL_VISIBLE_KEY, false));
                break;
            case CONTOUR_PREFERENCE_MAJOR_VISIBLE_KEY:
                setContourVisible(true, false);
                break;
            case CONTOUR_PREFERENCE_MINOR_VISIBLE_KEY:
                setContourVisible(false, true);
                break;
            case CONTOUR_PREFERENCE_UNIT_KEY:
                updateLabelUnits();
                break;
            case CONTOUR_PREFERENCE_MAJOR_WIDTH_KEY:
                updatePolylineWidth();
                break;
        }
    }

    class ContourRunnable implements Runnable {

        private final String _dtedPath;
        private final String _myDirectory;
        private final double _progressStep;

        ContourRunnable(String dtedPath, String myDirectory,
                double progressStep) {
            _dtedPath = dtedPath;
            _myDirectory = myDirectory;
            _progressStep = progressStep;
        }

        @Override
        public void run() {
            double interval = prefs.getInt(CONTOUR_PREFERENCE_INTERVAL_KEY, 20);
            if (prefs.getString(CONTOUR_PREFERENCE_UNIT_KEY, "m")
                    .equalsIgnoreCase("ft"))
                interval = interval * ConversionFactors.FEET_TO_METERS;

            Dataset dataset = GdalLibrary.openDatasetFromFile(
                    new File(_dtedPath), gdalconst.GA_ReadOnly);
            if (dataset == null)
                return;

            //create a temp file to store of created shape file
            String tempFileName = UUID.randomUUID().toString();
            File myDir2 = new File(_myDirectory + "/" + tempFileName);
            if (!IOProviderFactory.exists(myDir2)
                    && !IOProviderFactory.mkdirs(myDir2)) {
                toast("failed to create the directory");
                return;
            }

            //get the Band of the dataset

            Band band = dataset.GetRasterBand(1);
            if (band == null)
                return;

            //specify our output type as ShapeFile
            Driver driver = ogr.GetDriverByName("ESRI Shapefile");
            if (driver == null)
                return;

            String path = myDir2.getAbsolutePath();
            if (!IOProviderFactory.isDefault()) {
                path = VSIFileFileSystemHandler.PREFIX + path;
            }
            //create datasource that will contain the shape features created
            DataSource dataSource = driver
                    .CreateDataSource(path);
            if (dataSource == null)
                return;
            String projectionString = dataset.GetProjection();
            if (projectionString == null)
                return;
            SpatialReference spatialReference = new SpatialReference(
                    projectionString);

            //create our layer using GDAL
            org.gdal.ogr.Layer contourLayer = dataSource.CreateLayer(
                    tempFileName,
                    spatialReference,
                    ogr.wkbLineString, null);

            if (contourLayer == null)
                return;

            Double[] noDataValue = new Double[1];
            band.GetNoDataValue(noDataValue);

            FieldDefn field = new FieldDefn("ELEVATION", ogrConstants.OFTReal);
            field.SetWidth(12);
            field.SetPrecision(3);

            contourLayer.CreateField(field, 0);

            FeatureDefn feature = contourLayer.GetLayerDefn();
            if (feature == null)
                return;

            try {
                if (isCancelled.get()) {
                    return;
                }

                //call to generate the contours based upon a interval count and starting elevation
                gdal.ContourGenerate(band, interval, 0, null,
                        ((noDataValue[0] == null) ? 0 : 1),
                        ((noDataValue[0] == null) ? 0 : noDataValue[0]),
                        contourLayer,
                        feature.GetFieldIndex("ID"),
                        feature.GetFieldIndex(field.GetName()));

                if (isCancelled.get()) {
                    return;
                }

                MapView.getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        updateProgress((int) (progress + _progressStep));
                    }
                });

                try {
                    dataSource.FlushCache();
                    if (contourLayer.GetFeatureCount(1) >= 0) {
                        WriteToDataStore(myDir2.getAbsolutePath());
                    }
                } catch (Exception e) {
                    Log.d(TAG, "error flushing datasource, removing", e);
                    try {
                        dataSource.delete();
                    } catch (Exception ignored) {
                        Log.e(TAG, "error deleting datasource");
                    }
                }
            } finally {
                try {
                    contourLayer.delete();
                    dataset.delete();
                    band.delete();
                    driver.delete();
                } catch (Exception ignored) {
                    // delete and clean up has failed, just continue
                }
            }
        }
    }

    public synchronized DefaultMapGroup getMapGroup() {
        return mapGroup;
    }

    //describes a traditional dted cell data including geobounds, cursor path and dted level
    //used to filter out same areas and use the highest dted level for that cell
    private static class DtedCell implements Comparable<DtedCell> {

        private GeoBounds bounds;
        private int dtedRank;
        private String path;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            DtedCell dtedCell = (DtedCell) o;
            return dtedRank == dtedCell.dtedRank &&
                    Objects.equals(bounds, dtedCell.bounds) &&
                    Objects.equals(path, dtedCell.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bounds, dtedRank, path);
        }

        @Override
        public int compareTo(@NonNull DtedCell another) {
            if (another == null)
                return 0;
            return another.dtedRank - this.dtedRank;
        }
    }

    public static class GLOverlay implements GLLayer3 {

        private final MapRenderer renderContext;
        private GLMapGroup2 _rootObserver;
        private GLQuadtreeNode2 renderer;
        private final ContourLinesOverlay _layer;

        public GLOverlay(MapRenderer rendererContext,
                ContourLinesOverlay layer) {
            renderContext = rendererContext;
            _layer = layer;
        }

        @Override
        public void release() {
            renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    if (_rootObserver != null) {
                        _rootObserver.stopObserving(getSubject().getMapGroup());
                        _rootObserver.dispose();
                        renderer.release();
                    }
                }
            });
        }

        @Override
        public void draw(GLMapView view, int renderPass) {
            if (!getSubject().isVisible())
                return;
            if (_rootObserver == null) {
                this.renderer = new GLQuadtreeNode2();

                // add the root group observer
                _rootObserver = new GLMapGroup2(this.renderContext,
                        this.renderer, getSubject().getMapGroup());
                _rootObserver.startObserving(getSubject().getMapGroup());
            }
            if (this.renderer != null)
                this.renderer.draw(view, renderPass);
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SPRITES
                    | GLMapView.RENDER_PASS_SURFACE;
        }

        @Override
        public void draw(GLMapView glMapView) {

        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public ContourLinesOverlay getSubject() {
            return _layer;
        }
    }
}
