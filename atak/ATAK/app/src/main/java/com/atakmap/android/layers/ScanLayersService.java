
package com.atakmap.android.layers;

import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;

import android.content.BroadcastReceiver;
import android.content.Context;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class ScanLayersService extends BroadcastReceiver {

    private static final String TAG = "ScanLayersService";

    private static final int WAIT_FOR_CANCEL_MS = 5000;

    private static int count = 1;
    private static final Set<LayerScanner> scanners = new LinkedHashSet<>();
    private static ScanThread currentScanThread = null;

    private static final String[] ROOT_DIRS = FileSystemUtils.findMountPoints();

    private final static Set<LayerScanner.Spi> SCANNER_SPIS = new HashSet<>();

    public final static String STOP_SCAN_LAYER_ACTION = "com.atakmap.android.layers.SCAN_LAYERS_STOP";
    public final static String START_SCAN_LAYER_ACTION = "com.atakmap.android.layers.SCAN_LAYERS_START";

    private final Object dbLock = new Object();

    private static ScanLayersService _instance;

    private final LayerScanner.Callback layersCallback = new LayerScanner.Callback() {
        @Override
        public void layerDiscovered(DatasetDescriptor layer) {
            ScanLayersService.this.layerDiscovered(layer);
        }

        @Override
        public LocalRasterDataStore getLayersDatabase() {
            return LayersMapComponent.getLayersDatabase();
        }

        @Override
        public void error(String msg) {
            Log.e(TAG, msg);

            Intent i = new Intent(
                    "com.atakmap.android.maps.ERROR_LOADING_LAYERS");
            i.putExtra("message", msg);
            AtakBroadcast.getInstance().sendBroadcast(i);
        }

        @Override
        public void debug(String msg) {
            Log.d(TAG, count + ": " + msg);
        }

        @Override
        public int getNotificationFlags() {
            return NOTIFY_ERROR | NOTIFY_PROGRESS;
        }

        @Override
        public Context getContext() {
            return com.atakmap.android.maps.MapView.getMapView().getContext();
        }
    };

    public synchronized void registerScannerSpi(LayerScanner.Spi spi) {
        SCANNER_SPIS.add(spi);
        final LayerScanner scanner = spi.create();

        scanners.add(scanner);
    }

    public static String[] getRootDirs() {
        return ROOT_DIRS;
    }

    public static String getInternalRoot() {
        return FileSystemUtils.getRoot().getPath();
    }

    private ScanLayersService() {
        Log.d(TAG, "creating ScanLayersService");
        DocumentedIntentFilter dif = new DocumentedIntentFilter();
        dif.addAction(START_SCAN_LAYER_ACTION);
        dif.addAction(STOP_SCAN_LAYER_ACTION);
        AtakBroadcast.getInstance().registerReceiver(this, dif);

        // listen to the external media mounted intent
        DocumentedIntentFilter mm = new DocumentedIntentFilter(
                Intent.ACTION_MEDIA_MOUNTED);
        AtakBroadcast.getInstance().registerSystemReceiver(this, mm);

    }

    static synchronized public ScanLayersService getInstance() {
        if (_instance == null)
            _instance = new ScanLayersService();
        return _instance;
    }

    public void destroy() {
        synchronized (ScanLayersService.class) {
            cancel(-1);
            try {
                AtakBroadcast.getInstance().unregisterReceiver(this);
            } catch (Exception e) {
                Log.d(TAG, "error unregistering", e);
            }
            try {
                AtakBroadcast.getInstance().unregisterSystemReceiver(this);
            } catch (Exception e) {
                Log.d(TAG, "error unregistering", e);
            }
            _instance = null;
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {

        if (intent != null)
            Log.d(TAG, "received: " + intent.getAction());

        synchronized (ScanLayersService.class) {

            if (currentScanThread != null) {
                Log.d(TAG, "-- Cancelling an any existing scan --");
                currentScanThread.cancel();
                try {
                    currentScanThread.join(WAIT_FOR_CANCEL_MS);
                } catch (InterruptedException ignore) {
                    // ignore
                }
                currentScanThread = null;
            }

            boolean forceReset = false;

            if (intent != null) {
                if (STOP_SCAN_LAYER_ACTION.equals(intent.getAction())) {
                    Log.d(TAG, "-- Received a shutdown intent --");
                    return;
                } else {
                    // intent can be null
                    forceReset = intent.getBooleanExtra("forceReset", false);
                }
            }

            currentScanThread = new ScanThread(scanners, forceReset);
            currentScanThread.start();
        }
    }

    private void layerDiscovered(final DatasetDescriptor tsInfo) {
        Log.d(TAG,
                count + ": Discovered layer "
                        + tsInfo.getName());
    }

    private void progress(String msg) {
        // we're not going to spam logcat with progress updates, only post the intent

        Intent i = new Intent(
                LayersManagerBroadcastReceiver.ACTION_LAYER_LOADING_PROGRESS);
        i.putExtra(LayersManagerBroadcastReceiver.EXTRA_PROGRESS_MESSAGE, msg);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * Cancels the scan thread. If the scan thread is not active or
     * <code>join</code> is less than <code>0</code>, this method will return
     * immediately. If <code>join</code> is greater than or equal to
     * <code>0</code>, the scan thread will join per the contract of
     * {@link Thread#join(long)} using <code>join</code> as the argument.
     * 
     * @param join  The join in milliseconds. If less than <code>0</code> this
     *              method will not wait for the scan thread to complete.
     *              
     * @return  <code>true</code> if the scan thread is no longer running before
     *          this method returns, <code>false</code> otherwise.
     */
    public boolean cancel(long join) {

        synchronized (ScanLayersService.class) {
            if (currentScanThread == null)
                return true;

            // mark the scan thread as canceled
            currentScanThread.cancel();
            // cancel each individual scanner
            for (LayerScanner scanner : scanners)
                scanner.cancel();

            // if requested, join the scan thread
            if (join >= 0) {
                try {
                    currentScanThread.join(join);
                } catch (InterruptedException ignored) {
                }
            }

            return (currentScanThread.thread == null
                    || !currentScanThread.thread
                            .isAlive());
        }
    }

    private class ScanThread implements Runnable {
        private final boolean needReset;
        private volatile boolean canceled;
        private Thread thread;
        private final Set<LayerScanner> scanners;

        ScanThread(Set<LayerScanner> scanners, boolean needReset) {
            this.scanners = scanners;
            this.needReset = needReset;

            this.canceled = false;
        }

        public void cancel() {
            this.canceled = true;
        }

        public void start() {
            this.thread = new Thread(this);
            this.thread.setName("ScanLayersService Thread");
            this.thread.start();
        }

        public void join(final long ms) throws InterruptedException {
            this.thread.join(ms);
        }

        @Override
        public void run() {
            Set<Thread> scanThreads = new HashSet<>(scanners.size());

            Iterator<LayerScanner> scannerIter = this.scanners.iterator();
            LayerScanner scanner;
            Thread t;
            while (scannerIter.hasNext()) {
                scanner = scannerIter.next();
                scanner.prepare(ScanLayersService.this.layersCallback);
                if (this.needReset)
                    scanner.reset();
                t = new Thread(scanner);
                t.setName(scanner.getName());
                t.setPriority(Thread.NORM_PRIORITY);
                scanThreads.add(t);
            }

            synchronized (dbLock) {
                try {
                    final LocalRasterDataStore dataStore = LayersMapComponent
                            .getLayersDatabase();
                    dataStore.refresh();

                    Iterator<Thread> threadIter = scanThreads.iterator();
                    while (threadIter.hasNext())
                        threadIter.next().start();

                    threadIter = scanThreads.iterator();
                    while (threadIter.hasNext()) {
                        try {
                            threadIter.next().join();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    if (this.canceled) {
                        // clean
                    }
                } catch (Exception e) {
                    // The reason the error is likely occuring is because the native loader is not 
                    // initialized.   The native loader is initialized by ATAKActivity.
                    // Having said that, this should only be running while ATAKActivity is running.
                    // Please see ATAK-8391 and ATAK-8498
                    Log.e(TAG, "====a very bad error has occurred====", e);
                }
            }

            ++count;
        }
    }

}
