
package com.atakmap.android.layers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.layers.wms.DownloadAndCacheService;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.tilematrix.TileClient;
import com.atakmap.map.layer.raster.tilematrix.TileClientFactory;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for downloading tile from map layers
 */
public class LayerDownloader extends BroadcastReceiver {

    private static final String TAG = "LayerDownloader";

    private final MapView _mapView;
    private final Context _context;

    private String _title;
    private double _expandDist;
    private double _minRes, _maxRes;
    private Shape _shape;
    private String _cacheURI, _sourceURI;
    private GeoPoint[] _points;
    private GeoBounds _bounds;
    private GeoPoint _ulPoint, _lrPoint;
    private Geometry _geometry;
    private Callback _callback;

    public LayerDownloader(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    /**
     * Dispose the downloader by resetting the shape and unregistering receiver
     */
    public void dispose() {
        reset();
        setCallback(null);
    }

    /**
     * Set the title of the layer download
     * @param title Title string
     */
    public void setTitle(String title) {
        _title = title;
    }

    /**
     * Set the download shape
     * @param shape Shape
     */
    public void setShape(Shape shape) {
        _shape = shape;
    }

    /**
     * Set the distance to expand the download area (for open polylines)
     * @param dist Distance in meters
     */
    public void setExpandDistance(double dist) {
        _expandDist = dist;
    }

    /**
     * Set the resolution range to download
     * @param minRes Minimum map resolution
     * @param maxRes Maximum map resolution
     */
    public void setResolution(double minRes, double maxRes) {
        _minRes = minRes;
        _maxRes = maxRes;
    }

    /**
     * Set the URI of the tile cache
     * @param uri Cache URI
     */
    public void setCacheURI(String uri) {
        _cacheURI = uri;
    }

    /**
     * Set the map layer source URI
     * @param uri Source URI
     */
    public void setSourceURI(String uri) {
        _sourceURI = uri;
    }

    /**
     * Set the event callback for the downloader
     * @param callback Callback
     */
    public void setCallback(Callback callback) {
        if (callback == null)
            AtakBroadcast.getInstance().unregisterReceiver(this);
        else if (_callback == null) {
            DocumentedIntentFilter f = new DocumentedIntentFilter(
                    DownloadAndCacheService.BROADCAST_ACTION,
                    "Tracks layer download events");
            AtakBroadcast.getInstance().registerReceiver(this, f);
        }
        _callback = callback;
    }

    /**
     * Start the download service for a tile set
     * The shape, title, and source URI must be set prior to calling this
     * @return True if download started
     */
    public boolean startDownload() {
        if (_shape == null) {
            Log.e(TAG, "Failed to start download - shape is null");
            return false;
        }

        if (_title == null) {
            Log.e(TAG, "Failed to start download - title is null");
            return false;
        }

        if (_sourceURI == null) {
            Log.e(TAG, "Failed to start download - layer source URI is null");
            return false;
        }

        GeoPoint[] points = getPoints();
        if (points.length == 0) {
            Log.e(TAG, "Failed to start download - shape has no points");
            return false;
        }

        Intent i = new Intent();
        i.setClass(_context, DownloadAndCacheService.class);
        i.putExtra(DownloadAndCacheService.QUEUE_DOWNLOAD, "");
        i.putExtra(DownloadAndCacheService.TITLE, _title);
        if (_cacheURI != null)
            i.putExtra(DownloadAndCacheService.CACHE_URI, _cacheURI);
        i.putExtra(DownloadAndCacheService.SOURCE_URI, _sourceURI);
        i.putExtra(DownloadAndCacheService.UPPERLEFT, getUpperLeft());
        i.putExtra(DownloadAndCacheService.LOWERRIGHT, getLowerRight());
        i.putExtra(DownloadAndCacheService.GEOMETRY, points);
        i.putExtra(DownloadAndCacheService.MIN_RESOLUTION, _minRes);
        i.putExtra(DownloadAndCacheService.MAX_RESOLUTION, _maxRes);
        _context.startService(i);
        return true;
    }

    /**
     * Stop any active downloads
     */
    public void stopDownload() {
        Intent i = new Intent(_context, DownloadAndCacheService.class);
        i.putExtra(DownloadAndCacheService.CANCEL_DOWNLOAD, "");
        _context.startService(i);
    }

    /**
     * Reset the download shape
     */
    public void reset() {
        _shape = null;
        _geometry = null;
        _points = null;
        _bounds = null;
        _ulPoint = _lrPoint = null;
    }

    /**
     * Calculate the number of tiles that will be downloaded
     * @return Tile count
     */
    public int calculateTileCount() {
        // validate levels and rect bounds
        if (_shape == null || _minRes < _maxRes || _sourceURI == null)
            return 0;

        CacheRequest request = new CacheRequest();
        request.maxResolution = _maxRes;
        request.minResolution = _minRes;
        if (_geometry == null) {
            LineString ls = new LineString(2);
            GeoPoint[] geometry = getPoints();
            if (FileSystemUtils.isEmpty(geometry))
                return 0;
            for (GeoPoint gp : geometry)
                ls.addPoint(gp.getLongitude(), gp.getLatitude());
            _geometry = new Polygon(ls);
        }
        request.region = _geometry;
        request.countOnly = true;

        int numTiles = 0;
        TileClient client = null;
        try {
            client = TileClientFactory.create(_sourceURI, null, null);
            if (client != null)
                numTiles = client.estimateTileCount(request);
        } finally {
            if (client != null)
                client.dispose();
        }

        // if all of the layers were too zoomed out, then it can be covered in one tile
        return numTiles == 0 ? 1 : numTiles;
    }

    /**
     * Get the download bounds
     * @return Bounds
     */
    public GeoBounds getBounds() {
        if (_bounds == null)
            _bounds = GeoBounds.createFromPoints(getPoints(),
                    _mapView.isContinuousScrollEnabled());
        return _bounds;
    }

    /**
     * Get the upper-left bounds point
     * @return Upper-left point
     */
    public GeoPoint getUpperLeft() {
        if (_ulPoint != null)
            return _ulPoint;
        GeoBounds b = getBounds();
        if (b.crossesIDL())
            _ulPoint = new GeoPoint(b.getNorth(), b.getEast());
        else
            _ulPoint = new GeoPoint(b.getNorth(), b.getWest());
        return _ulPoint;
    }

    /**
     * Get the lower-right bounds point
     * @return Lower-right point
     */
    public GeoPoint getLowerRight() {
        if (_lrPoint != null)
            return _lrPoint;
        GeoBounds b = getBounds();
        if (b.crossesIDL())
            _lrPoint = new GeoPoint(_bounds.getSouth(),
                    _bounds.getWest() + 360);
        else
            _lrPoint = new GeoPoint(_bounds.getSouth(), _bounds.getEast());
        return _lrPoint;
    }

    /**
     * Get the list of points that determine which tiles to download
     * @return Points array
     */
    public GeoPoint[] getPoints() {
        if (_points != null)
            return _points;

        if (_shape == null)
            return new GeoPoint[0];

        GeoPoint[] points = _shape.getPoints();
        if (_shape instanceof Rectangle)
            points = Arrays.copyOf(points, 4);

        List<GeoPoint> pointList = new ArrayList<>(points.length);
        if (_mapView.isContinuousScrollEnabled()
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

        boolean closed = _shape instanceof DrawingRectangle
                || MathUtils.hasBits(_shape.getStyle(),
                        Polyline.STYLE_CLOSED_MASK)
                || points[0].equals(points[points.length - 1]);

        // Extrude route by a certain meter distance
        if (!closed && _expandDist > 0) {
            double d = _expandDist;
            double lastDist = 0;
            double lb = 0;
            List<GeoPoint> leftLine = new ArrayList<>();
            List<GeoPoint> rightLine = new ArrayList<>();
            int size = pointList.size();
            for (int i = 0; i < size; i++) {
                GeoPoint c = pointList.get(i);
                GeoPoint n = pointList.get(i == size - 1 ? i - 1 : i + 1);
                double r = GeoCalculations.distanceTo(c, n);
                double a = GeoCalculations.bearingTo(c, n);
                lastDist += r;
                if (i > 1 && i <= size - 2 && lastDist < 1)
                    continue;
                lastDist = 0;
                double b = a;
                if (i > 0 && i < size - 1) {
                    if (Math.abs(lb - b) > 180)
                        b = ((Math.min(b, lb) + 360) + Math.max(b, lb)) / 2;
                    else
                        b = (b + lb) / 2;
                } else if (i == size - 1)
                    b += 180;
                lb = a;
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

        return _points = pointList.toArray(new GeoPoint[0]);
    }

    /* Callback event handling */

    public interface Callback {

        /**
         * Download progress update
         * @param status Download status and progress stats
         */
        void onDownloadStatus(DownloadStatus status);

        /**
         * Job status update
         * @param status Job status
         */
        void onJobStatus(JobStatus status);

        /**
         * The max overall progress value has been changed
         * @param title Layer title
         * @param progress Progress
         */
        void onMaxProgressUpdate(String title, int progress);

        /**
         * The max progress up to the current level has been changed
         * Usually this means the next level has begun downloading
         * @param title Layer title
         * @param progress Progress
         */
        void onLevelProgressUpdate(String title, int progress);
    }

    public static class DownloadStatus {

        // The title of the downloaded layer
        public String title;

        // The number of queued downloads
        public int queuedDownloads;

        // Number of tiles downloaded for the current level / total tiles for current level
        public String tileStatus;

        // Number of levels / total levels
        public String layerStatus;

        // The total number of tiles downloaded so far
        public int tilesDownloaded;

        // The total number of tiles to download
        public int totalTiles;

        // The total number of tiles to download up to the current level
        public int levelTotalTiles;

        // The estimated time left for the download to complete
        public long timeLeft;
    }

    public static class JobStatus {

        // Layer title
        public String title;

        // Job status code (see DownloadJob)
        // Possible values:
        // DownloadJob.CONNECTING - Attempting to initiate connection
        // DownloadJob.DOWNLOADING - Tiles are being downloaded
        // DownloadJob.COMPLETE - Tile download is complete
        // DownloadJob.CANCELLED - Download has been canceled
        // DownloadJob.ERROR - Download error occurred
        public int code;

        // The number of queued layer downloads
        public int queuedDownloads;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (_callback == null) {
            AtakBroadcast.getInstance().unregisterReceiver(this);
            return;
        }

        if (!FileSystemUtils.isEquals(intent.getAction(),
                DownloadAndCacheService.BROADCAST_ACTION))
            return;

        // Current download progress
        if (intent.hasExtra(DownloadAndCacheService.DOWNLOAD_STATUS)) {
            DownloadStatus status = new DownloadStatus();
            status.timeLeft = intent.getLongExtra(
                    DownloadAndCacheService.TIME_STATUS, 0L);
            status.tileStatus = intent.getStringExtra(
                    DownloadAndCacheService.TILE_STATUS);
            status.layerStatus = intent.getStringExtra(
                    DownloadAndCacheService.LAYER_STATUS);
            status.queuedDownloads = intent.getIntExtra(
                    DownloadAndCacheService.QUEUE_SIZE, 0);
            status.tilesDownloaded = intent.getIntExtra(
                    DownloadAndCacheService.PROGRESS_BAR_PROGRESS, 0);
            status.levelTotalTiles = intent.getIntExtra(
                    DownloadAndCacheService.PROGRESS_BAR_ADJUST_SECONDARY, 0);
            status.totalTiles = intent.getIntExtra(
                    DownloadAndCacheService.PROGRESS_BAR_SET_MAX, 0);
            status.title = intent.getStringExtra(DownloadAndCacheService.TITLE);
            _callback.onDownloadStatus(status);
        }

        // Current job status
        else if (intent.hasExtra(DownloadAndCacheService.JOB_STATUS)) {
            JobStatus status = new JobStatus();
            status.code = intent.getIntExtra(
                    DownloadAndCacheService.JOB_STATUS, 0);
            status.title = intent.getStringExtra(
                    DownloadAndCacheService.TITLE);
            status.queuedDownloads = intent.getIntExtra(
                    DownloadAndCacheService.QUEUE_SIZE, 0);
            _callback.onJobStatus(status);
        }

        // Individual progress update
        else if (intent.hasExtra(DownloadAndCacheService.PROGRESS_BAR_STATUS)) {
            String title = intent.getStringExtra(DownloadAndCacheService.TITLE);
            if (intent.hasExtra(DownloadAndCacheService.PROGRESS_BAR_SET_MAX)) {
                _callback.onMaxProgressUpdate(title, intent.getIntExtra(
                        DownloadAndCacheService.PROGRESS_BAR_SET_MAX, 0));
            } else if (intent.hasExtra(
                    DownloadAndCacheService.PROGRESS_BAR_ADJUST_SECONDARY)) {
                _callback.onLevelProgressUpdate(title, intent.getIntExtra(
                        DownloadAndCacheService.PROGRESS_BAR_ADJUST_SECONDARY,
                        0));
            }
        }
    }
}
