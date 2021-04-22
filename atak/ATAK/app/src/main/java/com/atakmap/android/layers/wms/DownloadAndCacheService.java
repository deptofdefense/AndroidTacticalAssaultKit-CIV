
package com.atakmap.android.layers.wms;

import android.app.IntentService;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.ScanLayersService;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.contentservices.CacheRequestListener;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.osm.OSMDroidTileContainer;
import com.atakmap.map.layer.raster.tilematrix.TileClient;
import com.atakmap.map.layer.raster.tilematrix.TileClientFactory;
import com.atakmap.map.layer.raster.tilematrix.TileClientSpi;
import com.atakmap.map.layer.raster.tilematrix.TileContainerFactory;
import com.atakmap.map.layer.raster.tilematrix.TileContainerSpi;

import java.io.File;
import java.util.Collection;

public class DownloadAndCacheService extends IntentService {

    // legacy method used if 1
    private final static int NUM_DOWNLOAD_THREADS = 4;

    public final static int MIN_TILES_PER_LAYER = 8;

    // Defines a custom Intent action
    public static final String BROADCAST_ACTION = "com.atakmap.android.layers.wms.BROADCAST";
    /**
     * Extended data key for canceling a download
     */
    public static final String QUEUE_DOWNLOAD = "com.atakmap.android.layers.wms.QUEUE_DOWNLOAD";
    /**
     * Extended data key for canceling a download
     */
    public static final String CANCEL_DOWNLOAD = "com.atakmap.android.layers.wms.CANCEL_DOWNLOAD";
    /**
     * Extended data key for entire job operation
     */
    public static final String JOB_STATUS = "com.atakmap.android.layers.wms.JOB_STATUS";
    /**
     * Extended data key for downloading progress
     */
    public static final String DOWNLOAD_STATUS = "com.atakmap.android.layers.wms.DOWNLOAD_STATUS";
    /**
     * Extended data key for tile status updates
     */
    public static final String TILE_STATUS = "com.atakmap.android.layers.wms.TILE_STATUS";
    /**
     * Extended data key for layer status updates
     */
    public static final String LAYER_STATUS = "com.atakmap.android.layers.wms.LAYER_STATUS";
    /**
     * Extended data key for layer status updates
     */
    public static final String TIME_STATUS = "com.atakmap.android.layers.wms.TIME_STATUS";

    /**
     * Key used to define the upper left coordinate of the region to be cached
     */
    public static final String UPPERLEFT = "ul";
    /**
     * Key used to define the lower right coordinate of the region to be cached
     */
    public static final String LOWERRIGHT = "lr";
    /**
     * Key used to define the geometry of the region to be cached
     */
    public static final String GEOMETRY = "geometry";
    /**
     * Key used to define the resulting name of the layer being cached
     */
    public static final String TITLE = "title";
    /**
     * Key used to optionally define the path for the download cache. If not specified, a new cache
     * will automatically be created in the atak/layers directory on the same storage as the source.
     */
    public static final String CACHE_URI = "cacheUri";
    /**
     * Key used to define the URI of the layer being cached
     */
    public static final String SOURCE_URI = "sourceUri";
    /**
     * Key used to define the minimum resolution for download
     */
    public static final String MIN_RESOLUTION = "minResolution";
    /**
     * Key used to define the maximum resolution for download
     */
    public static final String MAX_RESOLUTION = "maxResolution";
    /**
     * Key used to describe the number of tilesets left to download in the queue
     */
    public static final String QUEUE_SIZE = "queueSize";
    /**
     * Extended data key for progressBar progress
     */
    public static final String PROGRESS_BAR_PROGRESS = "com.atakmap.android.layers.wms.PROGRESS_BAR_PROGRESS";
    /**
     * Extended data key for progressBar progress
     */
    public static final String PROGRESS_BAR_STATUS = "com.atakmap.android.layers.wms.PROGRESS_BAR_STATUS";
    /**
     * Extended data key for progressBar progress
     */
    public static final String PROGRESS_BAR_SET_MAX = "com.atakmap.android.layers.wms.PROGRESS_BAR_SET_MAX";
    /**
     * Extended data key for progressBar progress
     */
    public static final String PROGRESS_BAR_ADJUST_SECONDARY = "com.atakmap.android.layers.wms.PROGRESS_BAR_ADJUST_SECONDARY";

    private static final String SERVICE_NAME = "DownloadAndCacheService";
    private static final String TAG = "DownloadAndCacheService";
    protected String title = "";
    private CacheRequest currentRequest = null;
    private GeoPoint upperLeft = null;
    private GeoPoint lowerRight = null;
    private GeoPoint[] geometry = null;
    protected int queuedDownloads = 0;

    protected int currentProgress = 0;
    protected int secondaryProgress = 0;
    protected int maxProgress = 0;

    public DownloadAndCacheService() {
        super(SERVICE_NAME);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // Notify download canceled
            if (intent.hasExtra(CANCEL_DOWNLOAD)) {
                if (currentRequest != null)
                    currentRequest.canceled = true;
            }

            // Mark download queued
            else if (intent.hasExtra(QUEUE_DOWNLOAD)) {
                queuedDownloads++;
                Log.d(TAG, "tileset added to queue");
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Download in queue ready
        if (intent.hasExtra(QUEUE_DOWNLOAD))
            queueDownload(intent);
    }

    private void queueDownload(Intent intent) {
        this.upperLeft = intent.getParcelableExtra(UPPERLEFT);
        this.lowerRight = intent.getParcelableExtra(LOWERRIGHT);
        Parcelable[] p = (Parcelable[]) intent.getSerializableExtra(GEOMETRY);
        if (p != null) {
            this.geometry = new GeoPoint[p.length];
            for (int i = 0; i < p.length; i++)
                this.geometry[i] = (GeoPoint) p[i];
        }

        final Bundle extras = intent.getExtras();

        double minRes = Double.NaN;
        double maxRes = Double.NaN;
        String uri = null;

        if (extras != null) {

            title = extras.getString(TITLE);
            uri = extras.getString(SOURCE_URI);
            minRes = extras
                    .getDouble(MIN_RESOLUTION, Double.NaN);
            maxRes = extras
                    .getDouble(MAX_RESOLUTION, Double.NaN);
        }

        if (title == null || title.equals("")) {
            Log.e(TAG,
                    "Couldn't start download and cache service because the cache title was empty");
            reportJobStatus(DownloadJob.ERROR);
            return;
        }

        if (uri == null || uri.equals("")) {
            Log.e(TAG,
                    "Couldn't start download and cache service because the layer URI was empty");
            reportJobStatus(DownloadJob.ERROR);
            return;
        }

        if (Double.isNaN(minRes) || Double.isNaN(maxRes)) {
            Log.e(TAG,
                    "Couldn't start download and cache service because the levels were not defined");
            reportJobStatus(DownloadJob.ERROR);
            return;
        }

        if (upperLeft == null) {
            Log.e(TAG,
                    "Couldn't start download and cache service because the upperLeft was not defined");
            reportJobStatus(DownloadJob.ERROR);
            return;
        }

        if (lowerRight == null) {
            Log.e(TAG,
                    "Couldn't start download and cache service because the lowerRight was not defined");
            reportJobStatus(DownloadJob.ERROR);
            return;
        }

        if (this.geometry == null || this.geometry.length < 3) {
            geometry = new GeoPoint[] {
                    new GeoPoint(upperLeft),
                    new GeoPoint(upperLeft.getLatitude(),
                            lowerRight.getLongitude()),
                    new GeoPoint(lowerRight),
                    new GeoPoint(lowerRight.getLatitude(),
                            upperLeft.getLongitude())
            };
        }

        download(intent.getStringExtra(CACHE_URI), uri, minRes, maxRes);
    }

    /**
     * kicks off a download of the selected layers at the selected levels in the selected rectangle
     */
    private void download(String cacheUri, final String layerURI,
            double minRes,
            double maxRes) {

        //
        // TODO test active internet connection. find a way to test the connection when the device
        // is on wifi or when the device is using a tether and the tether has an internet connection

        // download the tiles

        reportJobStatus(DownloadJob.CONNECTING);

        TileClientSpi.Options c = new TileClientSpi.Options();
        c.dnsLookupTimeout = 5000L;

        final int[] terminalJobStatus = new int[] {
                DownloadJob.ERROR
        };

        TileClient client = null;
        try {
            client = TileClientFactory.create(layerURI, null, c);
            if (client == null) {
                Log.e(TAG, "Could not create tile client for  " + layerURI);
                return;
            }

            final TileContainerSpi[] preferredContainerSpi = new TileContainerSpi[] {
                    OSMDroidTileContainer.SPI
            };
            TileContainerFactory
                    .visitCompatibleSpis(
                            new com.atakmap.util.Visitor<Collection<TileContainerSpi>>() {
                                @Override
                                public void visit(
                                        Collection<TileContainerSpi> spis) {
                                    // XXX - preference should come in from tileset create UI or
                                    //       from preferences
                                    final String preferredProvider = "OSMDroid";
                                    TileContainerSpi candidate = null;
                                    TileContainerSpi preferred = null;
                                    for (TileContainerSpi spi : spis) {
                                        if (candidate == null)
                                            candidate = spi;
                                        if (spi.getName().equals(
                                                preferredProvider)) {
                                            preferred = spi;
                                            break;
                                        }
                                    }

                                    // if the preferred provider is compatible, use it,
                                    // otherwise use the first compatible provider
                                    if (preferred != null)
                                        preferredContainerSpi[0] = preferred;
                                    else if (candidate != null)
                                        preferredContainerSpi[0] = candidate;
                                }
                            }, client);

            terminalJobStatus[0] = DownloadJob.COMPLETE;
            if (cacheUri == null) {
                String cacheName = title;
                File dir = FileSystemUtils.getItem("imagery/mobile");
                cacheUri = dir.getAbsolutePath() + File.separator + cacheName
                        + preferredContainerSpi[0].getDefaultExtension();
                Log.d(TAG, "starting download to: " + cacheUri);
            }

            final String offlineCachePath = cacheUri;

            this.currentRequest = new CacheRequest();
            this.currentRequest.cacheFile = new File(offlineCachePath);
            this.currentRequest.preferredContainerProvider = preferredContainerSpi[0]
                    .getName();
            this.currentRequest.expirationOffset = 7L * 24L * 60L * 60L * 1000L;
            this.currentRequest.maxResolution = maxRes;
            this.currentRequest.minResolution = minRes;

            this.currentRequest.maxThreads = NUM_DOWNLOAD_THREADS;
            this.currentRequest.mode = CacheRequest.CacheMode.Append;

            LineString geomString = new LineString(2);
            for (GeoPoint gp : this.geometry)
                geomString.addPoint(gp.getLongitude(), gp.getLatitude());
            this.currentRequest.region = new Polygon(geomString);

            // XXX - I really don't like this -- I understand the reasoning
            //       behind trying to have a more accurate bounds representation
            //       but it seems like a bad idea overall

            // XXX - VC - Commenting this out now with the new arbitrary
            // polygon/route download - We're already performing this
            // calculation in the Map Manager UI and using it for the
            // default resolution selection - If the user has specifically
            // chosen a lower minimum zoom level then I see no reason to throw
            // that out and replace it with the default, especially without
            // prompting the user - Otherwise why even give the user the option
            // to download more tiles in the first place?

            /*Projection proj = MobileImageryRasterLayer2.getProjection(client
                    .getSRID());
            if (proj != null) {
                PointD ulProj = proj.forward(upperLeft, null);
                PointD lrProj = proj.forward(lowerRight, null);
            
                TileMatrix.ZoomLevel[] zoomLevels = client.getZoomLevel();
                double computedMinRes = Double.NaN;
                for (int i = 0; i < zoomLevels.length; i++) {
                    Point minTile = TileMatrix.Util.getTileIndex(client, i,
                            ulProj.x, ulProj.y);
                    Point maxTile = TileMatrix.Util.getTileIndex(client, i,
                            lrProj.x, lrProj.y);
            
                    int total = ((maxTile.y - minTile.y) + 1)
                            * ((maxTile.x - minTile.x) + 1);
                    if (total >= MIN_TILES_PER_LAYER) {
                        computedMinRes = zoomLevels[i].resolution;
                        break;
                    }
                }
            
                if (!Double.isNaN(computedMinRes)
                        && computedMinRes < currentRequest.minResolution)
                    currentRequest.minResolution = computedMinRes;
                else if (Double.isNaN(computedMinRes))
                    currentRequest.minResolution = 0d;
            }*/

            if (currentRequest.minResolution < currentRequest.maxResolution) {
                currentRequest.minResolution = 0d;
                currentRequest.maxResolution = 0d;
            }

            Log.d(TAG, "starting download of " + title + " cache...");

            client.cache(this.currentRequest, new CacheRequestListener() {

                long downloadStart;
                int task = -1;

                @Override
                public void onRequestStarted() {
                    reportJobStatus(DownloadJob.DOWNLOADING);
                    downloadStart = SystemClock.elapsedRealtime();
                }

                @Override
                public void onRequestComplete() {
                    terminalJobStatus[0] = DownloadJob.COMPLETE;
                }

                @Override
                public void onRequestProgress(int taskNum, int numTasks,
                        int taskProgress,
                        int maxTaskProgress, int totalProgress,
                        int maxTotalProgress) {
                    // TODO Auto-generated method stub

                    if (taskNum != this.task) {
                        secondaryProgress += maxTaskProgress;
                        reportProgress(
                                DownloadAndCacheService.PROGRESS_BAR_ADJUST_SECONDARY,
                                maxTaskProgress);
                        this.task = taskNum;

                    }
                    currentProgress = totalProgress;
                    maxProgress = maxTotalProgress;

                    final double tilesPerMS = (double) totalProgress
                            / (double) (SystemClock.elapsedRealtime()
                                    - this.downloadStart);

                    long timeLeft = (long) Math
                            .ceil((maxTotalProgress - totalProgress)
                                    / tilesPerMS / 1000d);
                    reportDownloadStatus(timeLeft,
                            taskProgress + " / " + maxTaskProgress,
                            (taskNum + 1) + "/" + numTasks);
                }

                @Override
                public boolean onRequestError(Throwable t, String message,
                        boolean fatal) {
                    // TODO what if the server does not have the tile for the
                    // location

                    // TODO notify the user that they do not have a network
                    // connection with the option to cancel the DL or retry

                    // for now just notify the user of the error

                    Log.d(TAG,
                            "Lost network connection during map download.");
                    if (queuedDownloads <= 1) {
                        rescanForDownloadedLayer();
                        stopSelf();
                    }

                    StringBuilder logMessage = new StringBuilder(
                            "Error while trying to download from ");
                    logMessage.append(layerURI);
                    if (message != null) {
                        logMessage.append(", ");
                        logMessage.append(message);
                    }
                    Log.e(TAG, logMessage.toString(), t);
                    terminalJobStatus[0] = DownloadJob.ERROR;
                    return false;
                }

                @Override
                public void onRequestCanceled() {
                    terminalJobStatus[0] = DownloadJob.CANCELLED;

                    Log.d(TAG,
                            "Download request has been cancelled.");
                    if (queuedDownloads <= 1) {
                        rescanForDownloadedLayer();
                        stopSelf();
                        maxProgress = 0;
                        secondaryProgress = 0;
                        currentProgress = 0;
                    } else {
                        secondaryProgress = currentProgress;
                    }
                }
            });
        } finally {
            queuedDownloads--;
            this.currentRequest = null;

            Log.d(TAG, "Finished downloading " + title + "errors: "
                    + (terminalJobStatus[0] == DownloadJob.ERROR));
            Log.d(TAG, "tilesets left in queue: " + queuedDownloads);

            // close the client
            if (client != null)
                client.dispose();

            // report job status on termination
            reportJobStatus(terminalJobStatus[0]);
        }

        if (queuedDownloads <= 0) {
            rescanForDownloadedLayer();
            maxProgress = 0;
            secondaryProgress = 0;
            currentProgress = 0;
            stopSelf();
        }
    }

    /**
     * Broadcasts an intent with the time left to download all the tilesets in the queue, the
     * current tile that has been downloaded, and the current layer that is being downloaded.
     */
    protected void reportDownloadStatus(Long time, String tile, String layer) {
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(DOWNLOAD_STATUS, true);

        // Puts the status into the Intent
        localIntent.putExtra(TIME_STATUS, time);
        localIntent.putExtra(TILE_STATUS, tile);
        localIntent.putExtra(LAYER_STATUS, layer);

        localIntent.putExtra(QUEUE_SIZE, queuedDownloads);

        localIntent.putExtra(PROGRESS_BAR_PROGRESS, currentProgress);
        localIntent.putExtra(PROGRESS_BAR_ADJUST_SECONDARY, secondaryProgress);
        localIntent.putExtra(PROGRESS_BAR_SET_MAX, maxProgress);
        localIntent.putExtra(TITLE, title);

        // Broadcasts the Intent to receivers in this app.
        AtakBroadcast.getInstance().sendBroadcast(
                localIntent);
    }

    protected void reportJobStatus(int statusMessage) {
        Intent localIntent = new Intent(BROADCAST_ACTION);

        // Puts the status into the Intent
        localIntent.putExtra(JOB_STATUS, statusMessage);
        localIntent.putExtra(QUEUE_SIZE, queuedDownloads);
        localIntent.putExtra(TITLE, title);

        // Broadcasts the Intent to receivers in this app.
        AtakBroadcast.getInstance().sendBroadcast(localIntent);
    }

    protected void reportProgress(String name, int value) {
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(PROGRESS_BAR_STATUS, true);
        localIntent.putExtra(name, value);
        localIntent.putExtra(TITLE, title);

        // Broadcasts the Intent to receivers in this app.
        AtakBroadcast.getInstance().sendBroadcast(
                localIntent);
    }

    private void rescanForDownloadedLayer() {
        Log.d(TAG, "rescanning for new layers created after caching operation");
        Intent scanIntent = new Intent(
                ScanLayersService.START_SCAN_LAYER_ACTION);
        scanIntent.putExtra("forceReset", true);
        AtakBroadcast.getInstance().sendBroadcast(scanIntent);
    }

    /**
     * Returns if the network is determined to be online.  This does not take into account
     * networks supplied by physical radio connections.
     * @return true if the wifi or cell is online.
     * @deprecated
     */
    @Deprecated
    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) this
                .getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = null;
        if (cm != null)
            netInfo = cm.getActiveNetworkInfo();

        return netInfo != null && netInfo.isConnectedOrConnecting();
    }
}
