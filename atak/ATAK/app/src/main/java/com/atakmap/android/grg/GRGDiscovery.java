
package com.atakmap.android.grg;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.GenericLayerScanner;
import com.atakmap.android.layers.LayerScanner;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;

import java.io.File;
import java.util.HashSet;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;

class GRGDiscovery implements Runnable, LayerScanner.Callback {

    /**
     * Extension blacklist for GRG discovery. All extensions are upper-case.
     */
    final static Set<String> EXTENSION_BLACKLIST = new HashSet<>();
    static {
        EXTENSION_BLACKLIST.add("DBF");
        EXTENSION_BLACKLIST.add("GIF");
        EXTENSION_BLACKLIST.add("JPG");
        EXTENSION_BLACKLIST.add("JPEG");
        EXTENSION_BLACKLIST.add("PRJ");
        EXTENSION_BLACKLIST.add("SBN");
        EXTENSION_BLACKLIST.add("SBX");
        EXTENSION_BLACKLIST.add("SHP");
        EXTENSION_BLACKLIST.add("SHX");
        //EXTENSION_BLACKLIST.add("XML");
    }

    public static final String TAG = "GRGDiscovery";

    /** Temporary file directory to use. */
    private final Context context;
    private final LocalRasterDataStore grgDatabase;
    private LayerScanner currentScanner;

    GRGDiscovery(Context context, LocalRasterDataStore grgDatabase) {
        this.context = context;
        this.grgDatabase = grgDatabase;
    }

    public void cancel() {
        LayerScanner cs;
        synchronized (this) {
            cs = currentScanner;
        }
        if (cs != null)
            cs.cancel();
    }

    @Override
    public void run() {
        this.grgDatabase.beginBatch();
        try {
            // XXX - refresh on demand only?
            this.grgDatabase.refresh();

            final LayerScanner grgScanner = new Scanner();
            synchronized (this) {
                currentScanner = grgScanner;
            }
            Thread t = new Thread(grgScanner);
            t.setName(grgScanner.getName() + "-scanner");
            t.setPriority(Thread.NORM_PRIORITY);

            grgScanner.prepare(this);
            t.start();

            try {
                t.join();
                synchronized (this) {
                    currentScanner = null;
                }
            } catch (InterruptedException ignored) {
            }
        } finally {
            this.grgDatabase.endBatch();
        }
    }

    /**************************************************************************/
    // Layer Scanner Callback

    @Override
    public void layerDiscovered(DatasetDescriptor layer) {
        Log.d(TAG, "Layer discovered: " + layer.getName());
    }

    @Override
    public void debug(String msg) {
        Log.d(TAG, msg);
    }

    @Override
    public void error(String msg) {
        Log.e(TAG, msg);

        Intent i = new Intent("com.atakmap.android.maps.ERROR_LOADING_LAYERS");
        i.putExtra("message", msg);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    @Override
    public LocalRasterDataStore getLayersDatabase() {
        return this.grgDatabase;
    }

    @Override
    public Context getContext() {
        return this.context;
    }

    @Override
    public int getNotificationFlags() {
        return NOTIFY_ERROR;
    }

    /**************************************************************************/

    private static String getExtension(File file) {
        final String name = file.getName();
        final int idx = name.lastIndexOf('.');
        if (idx < 0)
            return "";
        return name.substring(idx + 1);
    }

    /**************************************************************************/

    private static class Scanner extends GenericLayerScanner {

        protected Scanner() {
            super("GRG");
        }

        @Override
        protected File[] getScanDirs() {
            return getDefaultScanDirs("grg", false);
        }

        @Override
        protected int checkFile(int depth, File f) {
            if (IOProviderFactory.isFile(f)
                    && !EXTENSION_BLACKLIST.contains(getExtension(f)
                            .toUpperCase(LocaleUtil.getCurrent())))
                return ACCEPT;
            else if (IOProviderFactory.isDirectory(f)
                    && MCIAGRGLayerInfoSpi.isMCIAGRG(f))
                return ACCEPT;
            else if (IOProviderFactory.isDirectory(f))
                return DELAY;
            else
                return REJECT;
        }

        @Override
        protected String getProviderHint(int depth, File f) {
            String hint = null;
            if (MCIAGRGLayerInfoSpi.isMCIAGRG(f))
                hint = "mcia-grg";
            return hint;
        }

        @Override
        public void reset() {
        }
    }
}
