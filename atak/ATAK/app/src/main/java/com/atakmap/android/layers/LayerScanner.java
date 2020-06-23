
package com.atakmap.android.layers;

import android.content.Context;

import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;

public abstract class LayerScanner implements Runnable {

    private Callback callback;

    protected final String name;
    protected boolean canceled;
    protected LocalRasterDataStore database;

    protected LayerScanner(String name) {
        this.name = name;
    }

    /**
     * Invoked immediately before the enclosing {@link java.lang.Thread} is started.
     * 
     * @param callback The interface to be used for logging, error, etc callbacks.
     */
    public void prepare(Callback callback) {
        this.callback = callback;
        this.database = this.callback.getLayersDatabase();
        this.canceled = false;
    }

    /**
     * Subclasses should invoke when a layer is discovered while scanning.
     * 
     * @param layer The layer that was discovered.
     */
    protected void layerDiscovered(DatasetDescriptor layer) {
        this.callback.layerDiscovered(layer);
    }

    /**
     * Subclasses should invoke to log an error message with the owning
     * {@link com.atakmap.android.layers.ScanLayersService} instance.
     * 
     * @param msg The error message
     */
    protected void error(String msg) {
        this.callback.error("[" + this.getName() + "] " + msg);
    }

    /**
     * Subclasses should invoke to log a debug message with the owning
     * {@link com.atakmap.android.layers.ScanLayersService} instance.
     * 
     * @param msg The debug message
     */
    protected void debug(String msg) {
        this.callback.debug("[" + this.getName() + "] " + msg);
    }

    protected void progress(String msg) {
        //this.callback.progress("[" + this.getName() + "] " + msg);
    }

    /**
     * Instructs the scanner to terminate scanning as soon as possible. This method returns
     * immediately.
     */
    public final void cancel() {
        this.canceled = true;
        this.cancelImpl();
    }

    /**
     * Subclasses may override this method to perform any additional activities that are required
     * for cancelling the scan.
     * <P>
     * <B>IMPORTANT:</B> This method should not block.
     */
    protected void cancelImpl() {
    }

    /**
     * Instructs the scanner to reset any state or cache information prior to initiating a scan.
     */
    public abstract void reset();

    /**
     * Returns the name of the scanner.
     * 
     * @return The name of the scanner.
     */
    public final String getName() {
        return this.name;
    }

    /**************************************************************************/
    // Runnable

    @Override
    public abstract void run();

    /**************************************************************************/

    public interface Spi {
        LayerScanner create();
    }

    /**
     * Interface that may be implemented to receive error, debugging, etc callbacks from this layer
     * scanner. Instances of this interface should be provided to the layer scanner via the
     * prepare() method.
     */
    public interface Callback {
        int NOTIFY_ERROR = 0x01;
        int NOTIFY_PROGRESS = 0x02;

        /**
         * Invoked when a new layer has been discovered by this LayerScanner and added to the
         * LayersDatabase.
         * 
         * @param layer the layer that has been added
         */
        void layerDiscovered(DatasetDescriptor layer);

        /**
         * Invoked when this LayerScanner has a debug message to display.
         * 
         * @param msg the message to display.
         */
        void debug(String msg);

        /**
         * Invoked when this LayerScanner has an error message to display.
         * 
         * @param msg the message to display.
         */
        void error(String msg);

        //public void progress(String msg);

        /**
         * Returns the {@link LocalRasterDataStore} instance that the scanner will be populating. The
         * scanner may use this reference to validate currency against the current catalog, mark
         * layers as valid or delete layers when an update is required.
         * 
         * @return The {@link LocalRasterDataStore} instance that the scanner is working to populate.
         */
        LocalRasterDataStore getLayersDatabase();

        int getNotificationFlags();

        Context getContext();
    }
}
