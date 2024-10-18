
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

import com.atakmap.android.layers.MobileLayerSelectionAdapter.MobileImagerySpec;
import com.atakmap.android.layers.wms.DownloadAndCacheService;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.PointD;

/**
 * This class handles selecting an area to download and downloading the tiles
 */
public class OnlineLayersDownloadManager {

    public final static String TAG = "OnlineLayersDownloadManager";

    private final static int MIN_TILES_PER_LAYER = 8;

    protected final MapView map;
    protected final Context context;
    protected Shape shape;
    protected boolean selectingRegion = false;

    private final LayerDownloader downloader;
    private final LayersManagerBroadcastReceiver callback;

    private final View progressViewsLayout;
    private final View mobileToolsLayout;
    private final TextView tileProg;
    private final TextView layerProg;
    private final TextView timeProg;
    private final TextView queueProg;
    private final View labelView;
    protected final ProgressBar downloadPB;
    private int lastQueue = 0;

    OnlineLayersDownloadManager(MapView mv,
            LayersManagerBroadcastReceiver lmbr,
            View progressView, View mobileTools) {
        this.map = mv;
        this.context = mv.getContext();
        this.progressViewsLayout = progressView;
        this.mobileToolsLayout = mobileTools;
        this.downloader = new LayerDownloader(mv);
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
                    }
                    downloadPB.setProgress(currentProg);
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
    protected void resetProgressBar() {
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

    void promptSelectRegion() {
        if (isSelectingRegion())
            return;
        if (hasRegionShape())
            cancelRegionSelect();
        Intent i = new Intent(
                LayersManagerBroadcastReceiver.ACTION_TOOL_FINISHED);
        i.putExtra("toolId", RegionShapeTool.TOOL_ID);
        Bundle b = new Bundle();
        b.putParcelable("callback", i);
        ToolManagerBroadcastReceiver.getInstance().startTool(
                RegionShapeTool.TOOL_ID, b);
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
        downloader.setShape(shape);
        downloader.setExpandDistance(i.getDoubleExtra("expandDistance", 0));
        callback.receiveDownloadArea();
    }

    void startDownload(String title, String cacheUri,
            LayerSelection layerSelection,
            double minRes, double maxRes) {

        downloader.setTitle(title);
        downloader.setResolution(minRes, maxRes);
        downloader.setCacheURI(cacheUri);
        downloader.setSourceURI(
                ((MobileImagerySpec) layerSelection.getTag()).desc.getUri());
        if (downloader.startDownload()) {
            resetProgressBar();
            downloadPB.setMax(downloader.calculateTileCount());
        }
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
        downloader.reset();
        ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
        selectingRegion = false;
    }

    void stopDownload() {
        downloader.stopDownload();
    }

    public void setCallback(LayerDownloader.Callback cb) {
        downloader.setCallback(cb);
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
        downloader.setSourceURI(layerUri);
        downloader.setResolution(minRes, maxRes);
        return downloader.calculateTileCount();
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
        GeoPoint ul = downloader.getUpperLeft();
        GeoPoint lr = downloader.getLowerRight();
        if (ul == null || lr == null)
            return maxResolution;

        Projection proj = MobileImageryRasterLayer2.getProjection(tiles
                .getSRID());
        if (proj == null)
            return maxResolution;

        PointD ulProj = proj.forward(ul, null);
        PointD lrProj = proj.forward(lr, null);

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
