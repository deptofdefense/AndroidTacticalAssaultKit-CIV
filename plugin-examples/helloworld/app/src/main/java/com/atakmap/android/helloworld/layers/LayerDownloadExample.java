
package com.atakmap.android.helloworld.layers;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.layers.LayerDownloader;
import com.atakmap.android.layers.RasterUtils;
import com.atakmap.android.layers.RegionShapeTool;
import com.atakmap.android.layers.wms.DownloadJob;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.osm.OSMUtils;

import java.util.List;

/**
 * Demonstrates workflow for selecting and downloading map tiles
 *
 * 1) Start the region selection tool
 * 2) Once the user selects a region, the currently loaded layer will begin downloading
 * 3) The user is shown download progress in a dialog until completion
 */
public class LayerDownloadExample extends BroadcastReceiver
        implements LayerDownloader.Callback, DialogInterface.OnCancelListener {

    // Intent action fired when the region selection tool is finished
    private static final String TOOL_FINISH = "com.atakmap.android.helloworld.layers.LayerDownload_TOOL_FINISH";

    private final MapView _mapView;
    private final Context _context, _plugin;
    private final LayerDownloader _downloader;
    private final ProgressDialog _progDialog;

    public LayerDownloadExample(MapView mapView, Context plugin) {
        _mapView = mapView;
        _context = mapView.getContext();
        _plugin = plugin;

        // Create a new layer downloader
        // This will be used after the area is selected
        _downloader = new LayerDownloader(mapView);

        // Register intent callback for the selection tool
        AtakBroadcast.getInstance().registerReceiver(this,
                new DocumentedIntentFilter(TOOL_FINISH));

        // Download progress dialog
        _progDialog = new ProgressDialog(_context);
        _progDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        _progDialog.setCanceledOnTouchOutside(false);
        _progDialog.setOnCancelListener(this);
        _progDialog
                .setMessage(_plugin.getString(R.string.job_status_connecting));
        _progDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                _plugin.getString(R.string.cancel_btn),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        // Cancel button
                        _progDialog.cancel();
                    }
                });
    }

    public void dispose() {
        _progDialog.dismiss();
        AtakBroadcast.getInstance().unregisterReceiver(this);
    }

    /**
     * Start the example workflow
     */
    public void start() {

        // Callback intent fired when the tool finishes
        Bundle b = new Bundle();
        b.putParcelable("callback", new Intent(TOOL_FINISH));

        // Start the region select tool
        // The user will be prompted with 4 options for selecting a shape on
        // the map. The shape will be used for determining which tiles to
        // download from the online map layer.
        ToolManagerBroadcastReceiver.getInstance().startTool(
                RegionShapeTool.TOOL_ID, b);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        // Region selection tool is finished
        if (action.equals(TOOL_FINISH)) {

            // The UID of the shape the user selected/created
            String uid = intent.getStringExtra("uid");

            // Route expansion distance in meters
            // When selecting a route from the map, the user is prompted to
            // set how far outside the route to download map tiles.
            double distM = intent.getDoubleExtra("expandDistance", 0);

            // Lookup map item by UID
            MapItem item = _mapView.getMapItem(uid);

            // Ensure the item is a shape
            if (!(item instanceof Shape))
                return;

            // Enter download workflow
            downloadLayer((Shape) item, distM);
        }
    }

    /**
     * Start downloading layer imagery using a shape to determine the AOI
     * @param shape Download shape
     * @param distM Route expansion distance (if applicable)
     */
    private void downloadLayer(Shape shape, double distM) {
        // Layer title that will be appended to the tiles.sqlite file
        String title = "HelloWorld";

        // Get the current on-screen imagery within the shape bounds
        List<ImageDatasetDescriptor> datasets = RasterUtils
                .getCurrentImagery(_mapView, shape.getBounds(null));

        // Find the first available mobile imagery configuration
        ImageDatasetDescriptor mobac = null;
        for (ImageDatasetDescriptor d : datasets) {
            if (d.getProvider().equals("mobac")) {
                mobac = d;
                break;
            }
        }

        // Mobile imagery not loaded
        if (mobac == null) {
            Toast.makeText(_mapView.getContext(),
                    _plugin.getString(R.string.no_mobile_imagery_found),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Pull the imagery URI
        String sourceURI = mobac.getUri();

        // Get the min/max tile resolution
        double minAvailableRes = mobac.getMinResolution(null);
        double maxAvailableRes = mobac.getMaxResolution(null);

        // Get the total number of resolution levels
        int numLevels = Integer.parseInt(DatasetDescriptor.getExtraData(mobac,
                "_levelCount", "0"));

        // Get the resolution level offset
        int levelOffset = OSMUtils.mapnikTileLevel(minAvailableRes);

        // Get the current map resolution
        double mapRes = _mapView.getMapResolution();

        // Request one level higher and one level lower than the map resolution
        int mapLevel = OSMUtils.mapnikTileLevel(mapRes) - levelOffset;
        int maxLevel = Math.min(mapLevel + 1, numLevels - 1);
        int minLevel = Math.max(mapLevel - 1, 0);

        // Get the desired min and max resolution based on the levels
        double minRes = OSMUtils.mapnikTileResolution(minLevel);
        double maxRes = OSMUtils.mapnikTileResolution(maxLevel);

        // Setup the downloader
        _downloader.setTitle(title);
        _downloader.setSourceURI(sourceURI);
        _downloader.setResolution(minRes, maxRes);
        _downloader.setShape(shape);
        _downloader.setExpandDistance(distM);

        // Set the event callback receiver to this class, which implements
        // the LayerDownloader.Callback
        _downloader.setCallback(this);

        // Start the download service
        _downloader.startDownload();

        // Show progress dialog
        _progDialog.setTitle(_plugin.getString(R.string.map_layer_download));
        _progDialog.show();

        // Remove the temporary shape
        if (shape.hasMetaValue("layerDownload"))
            shape.removeFromGroup();
    }

    /**
     * Download progress update
     * @param status Download status and progress stats
     */
    @Override
    public void onDownloadStatus(LayerDownloader.DownloadStatus status) {
        // Show the current tile download progress
        _progDialog.setProgress(status.tilesDownloaded);
        _progDialog.setSecondaryProgress(status.levelTotalTiles);
        _progDialog.setMax(status.totalTiles);

        // Show status including the number of tiles downloaded, the number of
        // levels downloaded, and the estimated download time remaining
        _progDialog.setMessage(
                _plugin.getString(R.string.job_status_downloading) + "\n"
                        + _plugin.getString(R.string.download_tiles_progress,
                                status.tileStatus)
                        + ", "
                        + _plugin.getString(
                                R.string.download_layers_progress,
                                status.layerStatus)
                        + "\n"
                        + _plugin.getString(R.string.download_time_left,
                                MathUtils.GetTimeRemainingString(
                                        status.timeLeft)));
    }

    /**
     * Job status update
     * @param status Job status
     */
    @Override
    public void onJobStatus(LayerDownloader.JobStatus status) {
        int msg;
        switch (status.code) {
            case DownloadJob.CONNECTING:
                msg = R.string.job_status_connecting;
                break;
            case DownloadJob.DOWNLOADING:
                msg = R.string.job_status_downloading;
                break;
            case DownloadJob.COMPLETE:
                Toast.makeText(_context, _plugin.getString(
                        R.string.map_layer_download_complete),
                        Toast.LENGTH_LONG).show();
                _progDialog.dismiss();
                return;
            case DownloadJob.ERROR:
                Toast.makeText(_context, _plugin.getString(
                        R.string.map_layer_download_error),
                        Toast.LENGTH_LONG).show();
                _progDialog.dismiss();
                return;
            default:
            case DownloadJob.CANCELLED:
                return;
        }
        _progDialog.setMessage(_plugin.getString(msg));
    }

    /**
     * The max overall progress value has been changed
     * @param title Layer title
     * @param progress Progress
     */
    @Override
    public void onMaxProgressUpdate(String title, int progress) {
        _progDialog.setMax(progress);
    }

    /**
     * The max progress up to the current level has been changed
     * Usually this means the next level has begun downloading
     * @param title Layer title
     * @param progress Progress
     */
    @Override
    public void onLevelProgressUpdate(String title, int progress) {
        _progDialog.setSecondaryProgress(progress);
    }

    /**
     * Progress dialog has been canceled out
     * @param dialog Dialog
     */
    @Override
    public void onCancel(DialogInterface dialog) {
        // Stop the tile download
        _downloader.stopDownload();
    }
}
