
package com.atakmap.android.elev;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.Preference;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.atakmap.android.elev.dt2.Dt2FileWatcher;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.map.AtakMapView;
import com.atakmap.math.MathUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;
import java.util.BitSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class isolates all changes for ATAK 4.3.0 so that this set of fixes can be deployed
 * without disturbing the rest of the mapping.
 */
class ElevationDownloader implements AtakMapView.OnMapMovedListener {

    private static final String TAG = "ElevationDownloader";

    // Preference keys
    private static final String PREF_WARNING = "dted.install.warning";
    private static final String PREF_DOWNLOAD_SERVER = "prefs_dted_stream_server";
    private static final String PREF_DTED_STREAM = "prefs_dted_stream";

    // Default server to download tiles from
    private static final String DEFAULT_DOWNLOAD_SERVER = "tak.gov";

    // Number of milliseconds between bulk download attempts
    private static final long DOWNLOAD_INTERVAL = 1000; // 1 second

    // Number of milliseconds to wait before retrying download for failed tiles
    private static final long RETRY_TIMEOUT = 5 * 60 * 1000; // 5 minutes

    private static final int COVERAGE_SIZE = 360 * 180;
    private static final int COVERAGE_SIZE_BYTES = 8098;

    private static final String[] HEMISPHERE_NAMES = new String[] {
            "North East Hemisphere", "North West Hemisphere",
            "South East Hemisphere", "South West Hemisphere"
    };

    private static final String[] HEMISPHERE_FILES = new String[] {
            "dted_ne_hemi.zip", "dted_nw_hemi.zip", "dted_se_hemi.zip",
            "dted_sw_hemi.zip"
    };

    private static ElevationDownloader _instance;

    static ElevationDownloader getInstance() {
        return _instance;
    }

    /**
     * Code coverage file generator is as follows against the unzipped dtedlevel0.zip file
     *
     *          BitSet bs = new BitSet();
     *          FileOutputStream fos = new FileOutputStream("remote_elevation.cov");
     *
     *          int idx = 0;
     *          for (int lng = -180; lng < 180; ++lng) {
     *              for (int lat = -90; lat < 90; ++lat) {
     *                 File f = new File(getRelativePath(0, lat, lng));
     *                 bs.set(idx, f.exists());
     *                 idx++;
     *              }
     *          }
     *          fos.write(bs.toByteArray());
     *          fos.close();
     *
     */
    private final BitSet remote_coverage;

    private final MapView _mapView;
    private final Context _context;
    private final AtakPreferences _prefs;
    private final Dt2FileWatcher _fileCache;
    private final BitSet _downloadQueued, _downloadFailed;
    private final ExecutorService _downloadPool = Executors
            .newFixedThreadPool(5);

    private boolean _active;
    private long _retryTime = -1;

    ElevationDownloader(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = new AtakPreferences(mapView);
        _fileCache = Dt2FileWatcher.getInstance();
        _downloadQueued = new BitSet(COVERAGE_SIZE);
        _downloadFailed = new BitSet(COVERAGE_SIZE);
        _mapView.addOnMapMovedListener(this);
        _active = true;
        _instance = this;

        // Read remote coverage bitset file
        BitSet remoteCoverage;
        try (InputStream is = FileSystemUtils.getInputStreamFromAsset(_context,
                "remote_elevation.cov")) {
            byte[] cov = new byte[COVERAGE_SIZE_BYTES];
            int read = is.read(cov);

            // Ensure correct file size
            if (read < COVERAGE_SIZE_BYTES)
                throw new Exception("Unexpected file size (expected "
                        + COVERAGE_SIZE_BYTES + ", got " + read + ")");

            remoteCoverage = BitSet.valueOf(cov);

            // Ensure correct bit set size
            if (remoteCoverage.size() != _downloadQueued.size())
                throw new Exception("Unexpected coverage size (expected "
                        + _downloadQueued.size() + ", got "
                        + remoteCoverage.size() + ")");
        } catch (Exception e) {
            Log.e(TAG, "error loading code coverage", e);
            remoteCoverage = null;
        }
        remote_coverage = remoteCoverage;

        // trigger for an initial download
        onMapMoved(_mapView, false);
    }

    void dispose() {
        _active = false;
        _downloadPool.shutdown();
        _downloadScanner.dispose(false);
        _mapView.removeOnMapMovedListener(this);
    }

    /**
     * A dialog telling the user that elevation might not be available on this device and that is it
     * pretty important.  If we can get automatic downloading working in this class based on the current map position,
     * then we might not need to even have this method.
     */
    public void displayInformation(Context context) {
        HintDialogHelper.showHint(context,
                "Install Elevation Data",
                "Elevation Data is very important to the proper functioning of any mapping tool.  Click 'OK' to choose what elevation data you want. \n\nIf you want more, you can download it under Settings",
                PREF_WARNING,
                new HintDialogHelper.HintActions() {
                    @Override
                    public void postHint() {
                        chooseElevationToDownload(_context);
                    }

                    public void preHint() {
                    }

                });

    }

    /**
     * Support the ability to download elevation data based on the hemisphere.
     * @param context Map view or preference context
     */
    private void chooseElevationToDownload(final Context context) {
        final ListView listView = new ListView(context);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_multiple_choice,
                HEMISPHERE_NAMES));
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(listView);
        builder.setCancelable(false);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listView.isItemChecked(0);
                        startDownload(context, listView);
                    }
                });

        ((Activity) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.show();
            }
        });
    }

    private void startDownload(Context context, ListView listView) {

        final ProgressDialog progressDialog = new ProgressDialog(context);
        final DownloadThread dt = new DownloadThread(context, progressDialog,
                listView);

        progressDialog
                .setMessage(context.getString(R.string.downloading_message));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setButton(_context.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dt.cancel();
                    }
                });

        progressDialog.show();
        dt.start();
    }

    private class DownloadThread extends Thread {

        private final ProgressDialog dialog;
        private final ListView listView;
        private final Context context;

        private boolean cancelled = false;

        DownloadThread(Context context, ProgressDialog dialog,
                ListView listView) {
            this.dialog = dialog;
            this.listView = listView;
            this.context = context;
        }

        /**
         * Stop the downloading process for the elevation data
         */
        public void cancel() {
            cancelled = true;
        }

        private void setProgress(final int progress, final int max,
                final String message) {
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (max == -1) {
                        setIndeterminate(dialog, true);
                    } else {
                        setIndeterminate(dialog, false);
                        dialog.setMax(100);
                        dialog.setProgress(
                                (int) Math
                                        .floor((progress / (float) max) * 100));
                    }
                    dialog.setMessage(message);
                }
            });
        }

        @Override
        public void run() {
            final String server = _prefs.get(PREF_DOWNLOAD_SERVER,
                    DEFAULT_DOWNLOAD_SERVER);

            final byte[] buffer = new byte[8096 * 2];

            int len, off;
            int count = 0;
            for (int i = 0; i < HEMISPHERE_FILES.length; ++i) {
                if (cancelled || !_active)
                    return;

                final String file = HEMISPHERE_FILES[i];
                final String name = HEMISPHERE_NAMES[i];

                if (!listView.isItemChecked(i))
                    continue;
                count++;
                final String srcPath = "https://" + server + "/elevation/DTED/"
                        + file;
                final File dstPathZip = FileSystemUtils.getItem("DTED/" + file);

                final String message = "Downloading: " + name + "(" + count
                        + " of " + listView.getCheckedItemCount() + ")";
                setProgress(0, 100, message);

                InputStream is = null;
                OutputStream os = null;
                try {
                    //Log.d(TAG, "copying from: " + srcPath + " to " + dstPathZip);
                    URL u = new URL(srcPath);
                    URLConnection conn = u.openConnection();
                    final int fileLength = conn.getContentLength();
                    setProgress(0, fileLength, message);
                    is = new BufferedInputStream(conn.getInputStream());
                    off = 0;
                    os = new FileOutputStream(dstPathZip.getAbsolutePath());
                    while ((len = is.read(buffer)) >= 0) {
                        os.write(buffer, 0, len);
                        off += len;
                        setProgress(off, fileLength, message);
                        if (cancelled)
                            return;
                    }
                    os.flush();
                    setProgress(-1, -1, "Decompressing: " + name
                            + "(" + count
                            + " of " + listView.getCheckedItemCount() + ")");
                    FileSystemUtils.unzip(
                            new File(dstPathZip.getAbsolutePath()),
                            dstPathZip.getParentFile(), true);

                } catch (Exception e) {
                    Log.d(TAG, "error occurred: ", e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                        }
                    }
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Exception ignored) {
                        }
                    }
                    FileSystemUtils.delete(dstPathZip);
                }
            }

            // Dismiss progress dialog
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (dialog != null) {
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }
    }

    private static void setIndeterminate(ProgressDialog dialog,
            boolean isIndeterminate) {
        dialog.setIndeterminate(isIndeterminate);
        if (isIndeterminate) {
            dialog.setProgressNumberFormat(null);
            dialog.setProgressPercentFormat(null);
        } else {
            dialog.setProgressNumberFormat("%1d/%2d");
            NumberFormat percentInstance = NumberFormat.getPercentInstance();
            percentInstance.setMaximumFractionDigits(0);
            dialog.setProgressPercentFormat(percentInstance);
        }
    }

    /**
     * Check if we need to download DTED 0 tiles over a given region
     * @param bounds Geo bounds to check
     */
    private void checkDownloadTiles(GeoBounds bounds) {

        // Pull server host and whether this feature is enabled from prefs
        final String server = _prefs.get(PREF_DOWNLOAD_SERVER,
                DEFAULT_DOWNLOAD_SERVER);
        final boolean userActive = _prefs.get(PREF_DTED_STREAM, true);

        // Feature is disabled
        if (!userActive || remote_coverage == null)
            return;

        // Get current DTED map bounds
        int[] b = getDownloadBounds(bounds);
        if (b == null)
            return;
        int minLng = b[0], minLat = b[1], maxLng = b[2], maxLat = b[3];

        // Skip request if the user is zoomed out and attempting to download
        // way too many tiles at once
        int latSpan = (maxLat - minLat) + 1;
        int lngSpan = (maxLng - minLng) + 1;
        int totalTiles = latSpan * lngSpan;
        if (totalTiles <= 0 || totalTiles > 100)
            return;

        // Clear download failure cache if enough time has passed
        synchronized (this) {
            if (_retryTime >= 0 && System.currentTimeMillis() >= _retryTime) {
                _retryTime = -1;
                _downloadFailed.clear();
            }
        }

        BitSet coverage = _fileCache.getCoverage(0);

        for (int lat = minLat; lat <= maxLat; lat++) {
            for (int lng = minLng; lng <= maxLng; lng++) {
                if (!_active)
                    return;

                // IDL wrap
                int lngWrapped = lng;
                if (lngWrapped >= 180)
                    lngWrapped -= 360;
                else if (lngWrapped < -180)
                    lngWrapped += 360;

                int idx = Dt2FileWatcher.getCoverageIndex(lat, lngWrapped);

                // Make sure the server has coverage for this tile
                if (!remote_coverage.get(idx))
                    continue;

                // Check if the current tile is already downloaded,
                // being downloaded, or failed to download
                // Reads are intentionally un-synchronized for best performance
                if (coverage.get(idx) || _downloadQueued.get(idx)
                        || _downloadFailed.get(idx))
                    continue;

                // Kick off download
                downloadTile(server, lat, lngWrapped, lng);
            }
        }
    }

    /**
     * Downloads a specific tile from the defined server for the first cache
     * miss. Try again after 5 minutes if failed.
     * @param server Stream server hostname
     * @param lat the latitude
     * @param lng the longitude
     * @param lngUnwrapped Unwrapped longitude
     */
    private void downloadTile(String server, final int lat, final int lng,
            final int lngUnwrapped) {
        final String file = Dt2FileWatcher.getRelativePath(0, lat, lng);
        final File tileFile = FileSystemUtils.getItem("DTED/" + file);
        final File zipFile = new File(tileFile.getAbsolutePath() + ".zip");
        final String url = "https://" + server + "/elevation/DTED/" + file
                + ".zip";
        final int tileIdx = Dt2FileWatcher.getCoverageIndex(lat, lng);

        // Flag tile as being downloaded so it isn't requested again
        // until finished or failed
        synchronized (this) {
            _downloadQueued.set(tileIdx, true);
        }

        // Begin download
        _downloadPool.execute(new Runnable() {
            @Override
            public void run() {

                if (!_active)
                    return;

                // Check if the user is still focused over the AOI
                // and the downloader is still active
                int[] b = getDownloadBounds(_mapView.getBounds());
                boolean inAOI = b != null && lngUnwrapped >= b[0]
                        && lngUnwrapped <= b[2]
                        && lat >= b[1] && lat <= b[3];
                if (!_active || !inAOI) {
                    // Cancel download request
                    synchronized (ElevationDownloader.this) {
                        _downloadQueued.set(tileIdx, false);
                    }
                    return;
                }

                InputStream is = null;
                OutputStream os = null;
                try {
                    File parent = zipFile.getParentFile();
                    synchronized (ElevationDownloader.this) {
                        // This is sync'd so we don't attempt (and fail) to mkdirs
                        // when the directory was just created by another thread
                        if (!IOProviderFactory.exists(parent)
                                && !IOProviderFactory.mkdirs(parent))
                            throw new FileNotFoundException(
                                    "Failed to make directory: " + parent);
                    }
                    //Log.d(TAG, "Downloading " + url);
                    final URL u = new URL(url);
                    URLConnection conn = u.openConnection();
                    is = new BufferedInputStream(conn.getInputStream());
                    os = new FileOutputStream(zipFile);
                    FileSystemUtils.copy(is, os);
                    FileSystemUtils.unzip(zipFile, parent, true);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to download tile " + url, e);
                } finally {
                    FileSystemUtils.delete(zipFile);
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                        }
                    }
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Exception ignored) {
                        }
                    }
                }

                // Update state
                boolean success = FileSystemUtils.isFile(tileFile);
                if (success) {
                    _fileCache.refreshCache(tileFile);
                    //Log.d(TAG, "Download finished for " + url);
                }
                synchronized (ElevationDownloader.this) {
                    if (!success) {
                        _downloadFailed.set(tileIdx, true);
                        if (_retryTime < 0)
                            _retryTime = System.currentTimeMillis()
                                    + RETRY_TIMEOUT;
                    }
                    _downloadQueued.set(tileIdx, false);
                }
            }
        });
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        _downloadScanner.exec();
    }

    // Checks for tiles to download once per second
    private final LimitingThread _downloadScanner = new LimitingThread(
            TAG + "-Scanner", new Runnable() {
                @Override
                public void run() {
                    if (_active) {
                        checkDownloadTiles(_mapView.getBounds());
                        try {
                            Thread.sleep(DOWNLOAD_INTERVAL);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            });

    /**
     * Setup a preference button for downloading elevation data.   This localizes the changes for
     * ElevationOverlaysPreferenceFragment
     * @param prefActivity the activity used for the mechanism for downloading the datasets.
     * @param p the preference
     */
    void setupPreferenceDownloader(final Activity prefActivity,
            final Preference p) {
        p.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        chooseElevationToDownload(prefActivity);
                        return true;
                    }
                });
    }

    /**
     * Get the tile boundaries for download
     * @param bounds Geo bounds
     * @return [min lng, min lat, max lng, max lat]
     */
    private static int[] getDownloadBounds(GeoBounds bounds) {
        double w = bounds.getWest();
        double e = bounds.getEast();
        double n = bounds.getNorth();
        double s = bounds.getSouth();

        // Invalid bounds
        if (Double.isNaN(w) || Double.isNaN(e) || Double.isNaN(n)
                || Double.isNaN(s))
            return null;

        // Unwrap longitude west of IDL
        if (bounds.crossesIDL()) {
            e = w + 360;
            w = bounds.getEast();
        }

        int minLng = (int) Math.floor(w);
        int minLat = (int) Math.floor(s);
        int maxLng = (int) Math.floor(e);
        int maxLat = (int) Math.floor(n);

        // Clamp latitude
        minLat = MathUtils.clamp(minLat, -90, 90);
        maxLat = MathUtils.clamp(maxLat, -90, 90);

        return new int[] {
                minLng, minLat, maxLng, maxLat
        };
    }

}
