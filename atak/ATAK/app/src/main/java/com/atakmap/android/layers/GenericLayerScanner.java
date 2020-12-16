
package com.atakmap.android.layers;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class GenericLayerScanner extends LayerScanner {

    public static final String TAG = "GenericLayerScanner";

    /**************************************************************************/

    protected final static int ACCEPT = 0;
    protected final static int REJECT = -1;
    protected final static int DELAY = 1;

    /**************************************************************************/

    protected GenericLayerScanner(String name) {
        super(name);
    }

    protected abstract File[] getScanDirs();

    public static String _TAG = "GenericLayerScanner";

    @Override
    public final void run() {
        // scan for files
        File[] scanDirs = this.getScanDirs();

        for (File scanDir : scanDirs) {
            Log.d(TAG, "starting scan of: " + scanDir);
            if (IOProviderFactory.exists(scanDir)
                    && IOProviderFactory.isDirectory(scanDir))
                this.scanDirectory(0, scanDir);
        }
    }

    /**
     * Scans the specified directory. Any files that already reside in the data
     * store are skipped as they are considered valid.
     * 
     * @param depth The recursion depth
     * @param dir   The directory to scan
     */
    protected void scanDirectory(int depth, File dir) {
        // bug out if interrupted
        if (this.canceled) {
            this.debug("unwinding from interrupt...");
            return;
        }

        File[] files = IOProviderFactory.listFiles(dir);

        if (files != null) {
            this.debug("Scanning dir: " + dir + "...");

            for (File f : files) {
                // bug out if interrupted
                if (this.canceled) {
                    this.debug("unwinding from interrupt...");
                    return;
                }

                switch (this.checkFile(depth, f)) {
                    case REJECT:
                        continue;
                    case DELAY:
                        this.scanDirectory(depth + 1, f);
                        break;
                    case ACCEPT: {
                        if (!this.database.contains(f)) {
                            boolean success = false;
                            LayersNotificationManager.notifyImportStarted(f);
                            try {
                                success = this.database
                                        .add(f,
                                                this.getProviderHint(depth, f),
                                                new LayersManagerBroadcastReceiver.DatasetIngestCallback(
                                                        f));
                            } catch (IOException e) {
                                Log
                                        .e(TAG, "error: ", e);
                            } finally {
                                LayersNotificationManager.notifyImportComplete(
                                        f, success);
                            }
                            /*
                            if (!success) {
                                error("Failed to Load: "
                                        + f.getName()
                                        +
                                        " The file should be corrected or removed.");
                            
                            }
                            */
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }
        }
    }

    /**
     * @param f the file to check
     * @return one of {@link #ACCEPT}, {@link #REJECT}, or {@link #DELAY}.
     */
    protected abstract int checkFile(int depth, File f);

    protected abstract String getProviderHint(int depth, File f);

    /**************************************************************************/

    /**
     * Returns an array of scanning directories based on the specified subdirectory using the result
     * of {@link com.atakmap.android.layers.ScanLayersService#getRootDirs()} as the parent
     * directories.
     * 
     * @param subdir the subdirectory name to append to the known root directories.
     * @param ignoreInternal specify if internal directories should be ignored.
     * @return the array of Files representing the directories to be scanned.
     */
    protected static File[] getDefaultScanDirs(String subdir,
            boolean ignoreInternal) {
        final String[] rootDirs = ScanLayersService.getRootDirs();
        final String internalRoot = ScanLayersService.getInternalRoot();

        final List<File> files = new ArrayList<>(rootDirs.length);

        for (String rootDir : rootDirs) {
            if (!ignoreInternal || !rootDir.equalsIgnoreCase(internalRoot)) {
                files.add(new File(rootDir, subdir));
            }
        }

        final File[] retval = new File[files.size()];
        files.toArray(retval);
        return retval;
    }
}
