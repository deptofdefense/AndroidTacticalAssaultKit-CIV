
package com.atakmap.android.layers;

import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;

final class ImageryScanner extends LayerScanner {

    private final static String[] MOBILE_HINTS = new String[] {
            "mobac", "tileset"
    };
    private final static String[] KMZ_HINTS = new String[] {
            KmzLayerInfoSpi.INSTANCE.getType()
    };

    public final static LayerScanner.Spi SPI = new LayerScanner.Spi() {
        @Override
        public LayerScanner create() {
            return new ImageryScanner();
        }
    };

    private final static String TAG = "ImageryScanner";

    private final Set<File> postScanDirs = new HashSet<>();

    private ImageryScanner() {
        super("Imagery");
    }

    @Override
    public void reset() {
        postScanDirs.clear();
    }

    private static int depth = 3;

    /**
     * For the MCE ATAK Integration working group, the ability to set the 
     * scanning depth.   
     * @param _depth is the depth of scanning.   The value should be > 0 or
     * it will not be set.
     */
    public static void setDepth(final int _depth) {
        if (_depth > 0)
            depth = _depth;
    }

    @Override
    public void run() {
        final File[] mounts = FileSystemUtils.getDeviceRoots();

        for (File root : mounts) {
            File imageryBaseDir = FileSystemUtils.getItemOnSameRoot(root,
                    "imagery");
            Log.d(TAG, "scanning: " + imageryBaseDir);
            // do a recursive scan to a depth of 4 levels
            File[] children = IOProviderFactory.listFiles(imageryBaseDir);
            if (children != null) {
                for (File aChildren : children) {
                    if (!aChildren.getName().equals("mobile")) {
                        process(aChildren, 3);
                    } else {
                        boolean retval = processMobile(aChildren, 0);
                        if (!retval)
                            Log.d(TAG,
                                    "unable to process the mobile directory");
                    }
                }
            }

            // we are going to check directories that were ingested in whole for
            // nested KMZ data
            for (File dir : postScanDirs)
                this.checkKmz(dir, 3);
        }
    }

    /**
     * Recursively processes the specified file, adding supported imagery to the
     * data store. Directories that are processed into {@link DatasetDescriptor}
     * instances will be stored in {@link #postScanDirs} for post processing.
     * 
     * @param f     The file
     * @param limit The recursion limit
     * 
     * @return  <code>true</code> if imagery was added to the data store,
     *          <code>false</code> otherwise.
     */
    private boolean process(File f, int limit) {
        Log.v(TAG, "Process : " + f.getAbsolutePath());
        if (this.database.contains(f)) {
            return true;
        } else if (DatasetDescriptorFactory2.isSupported(f)) {
            if (this.tryAdd(f, null)) {
                if (IOProviderFactory.isDirectory(f))
                    postScanDirs.add(f);
                return true;
            }
        }

        boolean retval = false;
        // if we've got a zip at this point, it is not a valid tileset so we
        // will try to access the contents
        if (FileSystemUtils.checkExtension(f, "zip"))
            try {
                f = new ZipVirtualFile(f);
            } catch (Throwable ignored) {
            }

        if (IOProviderFactory.isDirectory(f)) {
            // we've hit the limit; don't recurse further
            if (limit < 1)
                return false;
            File[] c = IOProviderFactory.listFiles(f);
            if (c == null)
                return false;
            // walk the chidren
            for (File aC : c) {
                if (this.canceled)
                    break;
                retval |= process(aC, limit - 1);
            }
        }
        return retval;
    }

    private boolean processMobile(File f, int depth) {
        Log.v(TAG, "Process : " + f.getAbsolutePath());
        boolean supportedDir = false;
        if (DatasetDescriptorFactory2.isSupported(f)) {
            if (!this.database.contains(f)) {
                if (IOProviderFactory.isFile(f)
                        && this.tryAdd(f, MOBILE_HINTS)) {
                    if (IOProviderFactory.isDirectory(f))
                        postScanDirs.add(f);
                    return true;
                } else if (depth > 0 && IOProviderFactory.isDirectory(f)) {
                    supportedDir = true;
                }
            } else {
                return true;
            }
        }

        boolean retval = false;
        if (IOProviderFactory.isDirectory(f)) {
            File[] c = IOProviderFactory.listFiles(f);
            if (c == null)
                return false;
            // walk the chidren
            for (File aC : c) {
                if (this.canceled)
                    break;
                retval |= process(aC, depth + 1);
            }

            // if none of the files were processed as mobile but the directory
            // was found to be supported try doing generic processing to handle
            // as native
            if (!retval && supportedDir) {
                process(f, 3);
            }
        }

        return retval;
    }

    /**
     * Does a recursive check for KMZ data.
     * 
     * @param file  A file
     * @param limit The recursion limit
     */
    private void checkKmz(File file, int limit) {
        if (IOProviderFactory.isDirectory(file)) {
            if (limit < 1)
                return;
            File[] c = IOProviderFactory.listFiles(file);
            if (c == null)
                return;
            for (File aC : c) {
                if (this.canceled)
                    break;
                checkKmz(aC, limit - 1);
            }
        } else if (file.getName().toLowerCase(LocaleUtil.getCurrent())
                .endsWith(".kmz")) {
            this.tryAdd(file, KMZ_HINTS);
        }
    }

    /**
     * Try to add the specified file to the data store.
     * 
     * @param file  The file to be added
     * @param hint  The hint for the provider to process the file; may be
     *              <code>null</code>
     *              
     * @return  <code>true</code> if the file was successfully added,
     *          <code>false</code> otherwise.
     */
    private boolean tryAdd(File file, String[] hint) {
        boolean success = false;
        LayersNotificationManager.notifyImportStarted(file);
        Throwable err = null;
        try {
            Log.i(TAG, "Adding imagery : " + file.getAbsolutePath());
            if (hint != null) {
                for (String aHint : hint) {
                    try {
                        success = this.database
                                .add(
                                        file,
                                        aHint,
                                        new LayersManagerBroadcastReceiver.DatasetIngestCallback(
                                                file));
                        if (success)
                            break;
                    } catch (Throwable t) {
                        if (err == null)
                            err = t;
                    }
                }
            } else {
                success = this.database
                        .add(
                                file,
                                null,
                                new LayersManagerBroadcastReceiver.DatasetIngestCallback(
                                        file));
            }
            if (!success && err != null)
                throw err;

            return success;
        } catch (IOException e) {
            Log.e(TAG, "IO error: ", e);
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "general error: ", t);
            return false;
        } finally {
            LayersNotificationManager.notifyImportComplete(
                    file, success);
        }
    }
}
