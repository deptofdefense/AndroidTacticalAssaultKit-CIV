
package com.atakmap.android.layers;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.gui.RangeEntryDialog;
import com.atakmap.android.layers.MobileLayerSelectionAdapter.MobileImagerySpec;
import com.atakmap.android.layers.wms.DownloadAndCacheService;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.missionpackage.MapItemSelectTool;
import com.atakmap.android.routes.Route;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.tilematrix.TileClient;
import com.atakmap.map.layer.raster.tilematrix.TileClientFactory;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class handles selecting an area to download and downloading the tiles
 */
public class OnlineLayersDownloadManager {

    public final static String TAG = "OnlineLayersDownloadManager";
    final static int MODE_RECTANGLE = 0;
    final static int MODE_FREE_FORM = 1;
    final static int MODE_MAP_SELECT = 2;

    private final static int MIN_TILES_PER_LAYER = 8;

    private final MapView map;
    private final Context context;
    private GeoPoint ulPoint = null, lrPoint = null;
    private Shape shape;
    private Geometry shapeGeom;
    private boolean selectingRegion = false;
    private double shapeDistance = 100;

    private final LayersManagerBroadcastReceiver callback;

    private final View progressViewsLayout;
    private final View mobileToolsLayout;
    private final TextView tileProg;
    private final TextView layerProg;
    private final TextView timeProg;
    private final TextView queueProg;
    private final View labelView;
    private final ProgressBar downloadPB;
    private int lastQueue = 0;

    OnlineLayersDownloadManager(MapView mv,
            LayersManagerBroadcastReceiver lmbr,
            View progressView, View mobileTools) {
        this.map = mv;
        this.context = mv.getContext();
        this.progressViewsLayout = progressView;
        this.mobileToolsLayout = mobileTools;
        callback = lmbr;
        tileProg = progressView.findViewById(R.id.tileProgressTV);
        layerProg = progressView.findViewById(R.id.layerProgressTV);
        timeProg = progressView.findViewById(R.id.timeTV);
        queueProg = progressView.findViewById(R.id.queueProgressTV);
        labelView = progressView.findViewById(R.id.label_layout);
        downloadPB = progressView
                .findViewById(R.id.downloadProgressBar);
    }

    public void toastStatus(String text, int length) {
        if (length != Toast.LENGTH_LONG && length != Toast.LENGTH_SHORT)
            length = Toast.LENGTH_SHORT;

        Toast.makeText(context, text, length).show();
    }

    /**
     * set ProgressBar progress, secondary progress and max all in one handy method.
     */
    public void setProgress(final int currentProg, final int secondaryProg,
            final int maxProg) {
        map.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (downloadPB.getMax() != maxProg
                            || downloadPB
                                    .getSecondaryProgress() != secondaryProg) {
                        downloadPB.setMax(maxProg);
                        downloadPB.setSecondaryProgress(secondaryProg);
                        downloadPB.setProgress(currentProg);
                    } else {
                        downloadPB.setProgress(currentProg);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "error occurred setting the progress bar", e);
                }
            }
        });
    }

    public void adjustProgressBarSecondary(final int offset) {
        map.post(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadPB.setSecondaryProgress(downloadPB
                            .getSecondaryProgress() + offset);
                    Log.d("MapMan",
                            "sec progress: offset"
                                    + downloadPB.getSecondaryProgress());
                    Log.d("MapMan",
                            "max progress: offset" + downloadPB.getMax());
                } catch (Exception e) {
                    Log.d(TAG,
                            "error occurred adjusting the download progress bar",
                            e);
                }
            }
        });
    }

    public void setProgressBarMax(final int max) {
        map.post(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadPB.setMax(max);
                    downloadPB.setSecondaryProgress(downloadPB
                            .getSecondaryProgress());
                    downloadPB.invalidate();
                    Log.d("MapMan",
                            "sec progress: offset"
                                    + downloadPB.getSecondaryProgress());
                    Log.d("MapMan",
                            "max progress: offset" + downloadPB.getMax());
                } catch (Exception e) {
                    Log.d(TAG,
                            "error occurred setting the download progress bar",
                            e);
                }
            }
        });
    }

    /** reset progressBar progress, secondary progress and max to 0 */
    private void resetProgressBar() {
        map.post(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadPB.setProgress(0);
                    downloadPB.setSecondaryProgress(0);
                    downloadPB.setMax(0);
                } catch (Exception e) {
                    Log.d(TAG,
                            "error occurred resetting the download progress bar",
                            e);
                }
            }
        });
    }

    public void toggleProgressBarVisibility(final boolean visible) {
        map.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (visible) {
                        mobileToolsLayout.setVisibility(View.GONE);
                        progressViewsLayout.setVisibility(View.VISIBLE);
                    } else {
                        progressViewsLayout.setVisibility(View.GONE);
                        mobileToolsLayout.setVisibility(View.VISIBLE);
                    }
                } catch (Exception ignore) {
                }
            }
        });
    }

    public void setLayerProgressText(final String progressText) {
        setProgressText(progressText, layerProg);
    }

    public void setTileProgressText(final String progressText) {
        setProgressText(progressText, tileProg);
    }

    public void setTimeProgressText(final long secondsLeft) {
        String formattedTime = "";
        if (secondsLeft <= 0) {
            setProgressText(formattedTime, timeProg);
            return;
        }

        if (secondsLeft < 60) {
            formattedTime = secondsLeft + "s";
        } else if (secondsLeft < 3600) {
            formattedTime = secondsLeft / 60 + "m " + secondsLeft % 60 + "s";
        } else if (secondsLeft < 86400) {
            formattedTime = secondsLeft / 3600 + "hr " + secondsLeft % 1200
                    / 60 + "m "
                    + secondsLeft % 60 + "s";
        } else {
            formattedTime = "> 1day";
        }

        setProgressText(formattedTime, timeProg);
    }

    public void setLabelVisibility(final boolean visible, final int queue) {
        if (lastQueue == queue)
            return;

        lastQueue = queue;

        map.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (visible) {
                        labelView.setVisibility(View.VISIBLE);
                        queueProg.setText("(" + queue + " tilesets)");
                    } else
                        labelView.setVisibility(View.GONE);
                } catch (Exception ignore) {
                }
            }
        });
    }

    private void setProgressText(final String progressText,
            final TextView view) {
        map.post(new Runnable() {
            @Override
            public void run() {
                try {
                    view.setText(progressText);
                } catch (Exception ignore) {
                }
            }
        });
    }

    void selectRegion(int mode) {
        if (isSelectingRegion())
            return;
        if (hasRegionShape())
            cancelRegionSelect();
        String toolId;
        Bundle b = new Bundle();
        if (mode == MODE_RECTANGLE) {
            toolId = RegionShapeTool.TOOL_ID;
            b.putBoolean("freeform", false);
        } else if (mode == MODE_FREE_FORM) {
            toolId = RegionShapeTool.TOOL_ID;
            b.putBoolean("freeform", true);
        } else if (mode == MODE_MAP_SELECT) {
            toolId = MapItemSelectTool.TOOL_NAME;
            b.putString("prompt", context.getString(
                    R.string.region_shape_select_prompt));
            b.putBoolean("multiSelect", false);
            b.putStringArray("allowTypes", new String[] {
                    "u-d-f", "u-d-r", "b-m-r", "u-d-c-c", "u-r-b-c-c",
                    "u-d-feature"
            });
        } else
            return;
        Intent i = new Intent(
                LayersManagerBroadcastReceiver.ACTION_TOOL_FINISHED);
        i.putExtra("toolId", toolId);
        b.putParcelable("callback", i);
        ToolManagerBroadcastReceiver.getInstance().startTool(toolId, b);
        selectingRegion = true;
    }

    void finishRegionSelect(Intent i) {
        if (!selectingRegion)
            return;
        selectingRegion = false;
        String uid = i.getStringExtra("uid");
        if (FileSystemUtils.isEmpty(uid))
            uid = i.getStringExtra("itemUID");
        MapItem mi = map.getRootGroup().deepFindUID(uid);
        if (!(mi instanceof Shape)) {
            callback.onKey(null, KeyEvent.KEYCODE_BACK, null);
            return;
        }
        shape = (Shape) mi;
        GeoBounds bounds = GeoBounds.createFromPoints(
                shape.getPoints(),
                map.isContinuousScrollEnabled());
        if (bounds.crossesIDL()) {
            this.ulPoint = new GeoPoint(bounds.getNorth(), bounds.getEast());
            this.lrPoint = new GeoPoint(bounds.getSouth(),
                    bounds.getWest() + 360);

        } else {
            this.ulPoint = new GeoPoint(bounds.getNorth(), bounds.getWest());
            this.lrPoint = new GeoPoint(bounds.getSouth(), bounds.getEast());
        }
        callback.receiveDownloadArea();

        // Prompt for meters from route to extend the tile download
        if (shape instanceof Route) {
            RangeEntryDialog d = new RangeEntryDialog(map);
            d.show(R.string.route_download_range, shapeDistance, Span.METER,
                    new RangeEntryDialog.Callback() {
                        @Override
                        public void onSetValue(double valueM, Span unit) {
                            shapeDistance = valueM;
                        }
                    });
        }
    }

    private GeoPoint[] getPoints() {
        if (this.shape == null)
            return new GeoPoint[0];

        GeoPoint[] points = shape.getPoints();
        if (this.shape instanceof Rectangle)
            points = Arrays.copyOf(points, 4);

        List<GeoPoint> pointList = new ArrayList<>(points.length);
        if (map.isContinuousScrollEnabled()
                && GeoCalculations.crossesIDL(points, 0, points.length)) {
            for (GeoPoint p : points) {
                if (p.getLongitude() < 0)
                    pointList.add(new GeoPoint(p.getLatitude(),
                            p.getLongitude() + 360));
                else
                    pointList.add(p);
            }
        } else
            pointList.addAll(Arrays.asList(points));

        if (pointList.isEmpty())
            return new GeoPoint[0];

        boolean closed = this.shape instanceof DrawingRectangle
                || MathUtils.hasBits(shape.getStyle(),
                        Polyline.STYLE_CLOSED_MASK)
                || points[0].equals(points[points.length - 1]);

        // Extrude route by a certain meter distance
        if (!closed && this.shapeDistance > 0) {
            double d = this.shapeDistance;
            double lastDist = 0;
            double lb = 0;
            List<GeoPoint> leftLine = new ArrayList<>();
            List<GeoPoint> rightLine = new ArrayList<>();
            int size = pointList.size();
            for (int i = 0; i < size; i++) {
                GeoPoint c = pointList.get(i);
                GeoPoint n = pointList.get(i == size - 1 ? i - 1 : i + 1);
                double[] ra = DistanceCalculations.computeDirection(c, n);
                lastDist += ra[0];
                if (i > 1 && i <= size - 2 && lastDist < 1)
                    continue;
                lastDist = 0;
                double b = ra[1];
                if (i > 0 && i < size - 1) {
                    if (Math.abs(lb - b) > 180)
                        b = ((Math.min(b, lb) + 360) + Math.max(b, lb)) / 2;
                    else
                        b = (b + lb) / 2;
                } else if (i == size - 1)
                    b += 180;
                lb = ra[1];
                GeoPoint left = GeoCalculations.pointAtDistance(c, b - 90, d);
                GeoPoint right = GeoCalculations.pointAtDistance(c, b + 90, d);
                if (i == 0 || i == size - 1) {
                    b += i == 0 ? 180 : 0;
                    left = GeoCalculations.pointAtDistance(left, b, d);
                    right = GeoCalculations.pointAtDistance(right, b, d);
                }
                leftLine.add(left);
                rightLine.add(right);
            }
            pointList.clear();
            pointList.addAll(leftLine);
            Collections.reverse(rightLine);
            pointList.addAll(rightLine);
            closed = true;
        }

        GeoPoint start = pointList.get(0);
        GeoPoint end = pointList.get(pointList.size() - 1);
        if (closed && !start.equals(end))
            pointList.add(start);

        return pointList.toArray(new GeoPoint[0]);
    }

    void startDownload(String title, String cacheUri,
            LayerSelection layerSelection,
            double minRes, double maxRes) {
        if (shape == null)
            return;

        Intent i = new Intent();
        i.setClass(context, DownloadAndCacheService.class);
        i.putExtra(DownloadAndCacheService.QUEUE_DOWNLOAD, "");
        i.putExtra(DownloadAndCacheService.TITLE, title);
        if (cacheUri != null)
            i.putExtra(DownloadAndCacheService.CACHE_URI, cacheUri);
        i.putExtra(DownloadAndCacheService.SOURCE_URI,
                ((MobileImagerySpec) layerSelection.getTag()).desc.getUri());
        i.putExtra(DownloadAndCacheService.UPPERLEFT, ulPoint);
        i.putExtra(DownloadAndCacheService.LOWERRIGHT, lrPoint);
        i.putExtra(DownloadAndCacheService.GEOMETRY, getPoints());
        i.putExtra(DownloadAndCacheService.MIN_RESOLUTION, minRes);
        i.putExtra(DownloadAndCacheService.MAX_RESOLUTION, maxRes);
        int numTiles = getTilesOverArea2(((MobileImagerySpec) layerSelection
                .getTag()).desc.getUri(), minRes, maxRes);

        resetProgressBar();
        downloadPB.setMax(numTiles);

        context.startService(i);
    }

    public boolean isDownloading() {
        ActivityManager manager = (ActivityManager) context
                .getSystemService(
                        Context.ACTIVITY_SERVICE);

        if (manager.getRunningServices(Integer.MAX_VALUE) != null) {
            for (RunningServiceInfo service : manager
                    .getRunningServices(Integer.MAX_VALUE)) {
                if (DownloadAndCacheService.class.getName().equals(
                        service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSelectingRegion() {
        return selectingRegion;
    }

    public boolean hasRegionShape() {
        return shape != null;
    }

    /**
     * cancel rectangle select and/or download if applicable
     */
    public void cancelRegionSelect() {
        if (shape != null && shape.hasMetaValue("layerDownload"))
            shape.removeFromGroup();
        shape = null;
        shapeGeom = null;
        ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
        selectingRegion = false;
    }

    void stopDownload() {
        Intent stopIntent = new Intent(context,
                DownloadAndCacheService.class);
        stopIntent.putExtra(DownloadAndCacheService.CANCEL_DOWNLOAD, "");
        context.startService(stopIntent);
    }

    /**
     * Get the number of tiles it takes to cover an area (the rect that the user selected) for a
     * range of zoom levels
     * 
     * @param minRes - the lowest (most coarse) level
     * @param maxRes - the highest (most fine) level
     * @return - the total number of tiles
     */
    int getTilesOverArea2(String layerUri, double minRes, double maxRes) {
        // validate levels and rect bounds
        if (shape == null)
            return 0;
        if (minRes < maxRes)
            return 0;

        CacheRequest request = new CacheRequest();
        request.maxResolution = maxRes;
        request.minResolution = minRes;
        if (this.shapeGeom == null) {
            LineString ls = new LineString(2);
            GeoPoint[] geometry = getPoints();
            if (FileSystemUtils.isEmpty(geometry))
                return 0;
            for (GeoPoint gp : geometry)
                ls.addPoint(gp.getLongitude(), gp.getLatitude());
            this.shapeGeom = new Polygon(ls);
        }
        request.region = this.shapeGeom;
        request.countOnly = true;

        int numTiles = 0;
        TileClient client = null;
        try {
            client = TileClientFactory.create(layerUri, null, null);
            if (client != null)
                numTiles = client.estimateTileCount(request);
        } finally {
            if (client != null)
                client.dispose();
        }

        // if all of the layers were too zoomed out, then it can be covered in one tile
        if (numTiles == 0)
            return 1;
        return numTiles;
    }

    double estimateMinimumResolution(TileMatrix tiles, double maxResolution) {
        // XXX - NPE with stack -- this whole class should be rewritten with
        //       a very clear set of states
        /*
        at com.atakmap.android.layers.OnlineLayersDownloadManager.getMinScale(SourceFile:423)
        at com.atakmap.android.layers.LayersManagerBroadcastReceiver.getMinLevel(SourceFile:1306)
        at com.atakmap.android.layers.MobileLayerSelectionAdapter.getViewImpl(SourceFile:312)
        at com.atakmap.android.layers.LayerSelectionAdapter.getView(SourceFile:454)
        at android.widget.AbsListView.obtainView(AbsListView.java:2823
         */
        // validate levels and rect bounds
        if (ulPoint == null || lrPoint == null)
            return maxResolution;

        Projection proj = MobileImageryRasterLayer2.getProjection(tiles
                .getSRID());
        if (proj == null)
            return maxResolution;

        PointD ulProj = proj.forward(ulPoint, null);
        PointD lrProj = proj.forward(lrPoint, null);

        TileMatrix.ZoomLevel[] zoomLevels = tiles.getZoomLevel();
        for (int i = 0; i < zoomLevels.length; i++) {
            Point minTile = TileMatrix.Util.getTileIndex(tiles, i, ulProj.x,
                    ulProj.y);
            Point maxTile = TileMatrix.Util.getTileIndex(tiles, i, lrProj.x,
                    lrProj.y);

            int total = ((maxTile.y - minTile.y) + 1)
                    * ((maxTile.x - minTile.x) + 1);
            if (total >= MIN_TILES_PER_LAYER)
                return zoomLevels[i].resolution;
        }

        return maxResolution;
    }

    void freezeRegionShape() {
        if (this.shape != null)
            this.shape.setEditable(false);
    }
}
