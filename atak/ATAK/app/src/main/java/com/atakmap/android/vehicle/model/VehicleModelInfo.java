
package com.atakmap.android.vehicle.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.ModelProjection;
import com.atakmap.android.rubbersheet.data.create.ModelLoader;
import com.atakmap.android.imagecapture.opengl.GLOffscreenCaptureService;
import com.atakmap.android.vehicle.model.icon.PendingDrawable;
import com.atakmap.android.vehicle.model.icon.VehicleModelCaptureRequest;
import com.atakmap.android.vehicle.model.icon.VehicleOutlineCaptureRequest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cached info pertaining to a specific vehicle model file
 */
public class VehicleModelInfo implements ModelLoader.Callback {

    private static final String TAG = "VehicleModelInfo";

    public static final Comparator<VehicleModelInfo> COMP_NAME = new Comparator<VehicleModelInfo>() {
        @Override
        public int compare(VehicleModelInfo o1, VehicleModelInfo o2) {
            return o1.name.compareTo(o2.name);
        }
    };

    private static final int ICON_SIZE = 200;
    private static final int OUTLINE_SIZE = 512;

    // Loaded on init
    public final File file;
    public final String category, name, fileName;
    public final PointD offset;

    // Loaded on demand
    private ModelInfo info;
    private Model model;
    private List<PointF> outline;
    private boolean loaded;
    private boolean loadingOutline;
    private final List<Runnable> outlineCallbacks = new ArrayList<>();

    // Icon
    private final PendingDrawable icon;
    private boolean iconBusy;

    public VehicleModelInfo(String category, String name, File file,
            PointD offset) {
        this.file = file;
        String fName = file.getName();
        int dotIdx = fName.lastIndexOf('.');
        if (dotIdx > -1)
            fName = fName.substring(0, dotIdx);
        this.fileName = fName;
        this.name = name;
        this.offset = offset;

        if (category == null) {
            File dir = file.getParentFile();
            if (dir == null
                    || FileSystemUtils.isEquals(VehicleModelCache.DIR, dir))
                category = "Default";
            else {
                category = dir.getName();
                category = Character.toUpperCase(category.charAt(0))
                        + category.substring(1);
            }
        }
        this.category = category;

        Drawable placeholder = null;
        MapView mv = MapView.getMapView();
        if (mv != null)
            placeholder = mv.getContext().getDrawable(
                    R.drawable.pointtype_aircraft);
        this.icon = new PendingDrawable(placeholder);
    }

    public VehicleModelInfo(String category, String name, File file) {
        this(category, name, file, null);
    }

    public synchronized void dispose() {
        if (this.model != null)
            this.model.dispose();
        this.model = null;
        this.info = null;
        this.loaded = false;
    }

    public String getUID() {
        return this.category + "/" + this.name;
    }

    /**
     * Get a drawable icon for this vehicle
     * Note: The drawable returned may show a generic placeholder icon while
     * the real vehicle icon is busy being generated. This should auto-update
     * once finished if used in a view. You can also use
     * {@link Drawable#setCallback(Drawable.Callback)} to listen for the update
     * event.
     * @return Pending vehicle icon
     */
    public synchronized PendingDrawable getIcon() {
        // Cached icon
        if (!this.icon.isPending() || this.iconBusy)
            return this.icon;

        // Load from file
        Bitmap bmp = getCachedIcon();
        if (bmp != null) {
            this.icon.setBitmap(bmp);
            return this.icon;
        }

        // Generate and save icon
        this.iconBusy = true;
        VehicleModelCaptureRequest req = getIconRequest();
        req.setCallback(new VehicleModelCaptureRequest.Callback() {
            @Override
            public void onCaptureFinished(File file, final Bitmap bmp) {
                MapView mv = MapView.getMapView();
                if (mv == null)
                    return;
                mv.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (VehicleModelInfo.this) {
                            if (bmp != null)
                                icon.setBitmap(bmp);
                            else
                                Log.w(TAG,
                                        "Failed to generate icon for " + name);
                            iconBusy = false;
                        }
                    }
                });
            }
        });
        GLOffscreenCaptureService.getInstance().request(req);
        return this.icon;
    }

    /**
     * Get the capture request used to create an icon for this vehicle
     * @return Vehicle model capture request (without callback)
     */
    public VehicleModelCaptureRequest getIconRequest() {
        VehicleModelCaptureRequest req = new VehicleModelCaptureRequest(this);
        req.setOutputFile(getCachedIconFile());
        req.setOutputSize(ICON_SIZE, false);
        req.setStrokeEnabled(true);
        req.setStrokeWidth(2);
        req.setStrokeColorContrast(true);
        return req;
    }

    /**
     * Get the file this vehicle icon uses in cache
     * @return Icon file cache
     */
    public File getCachedIconFile() {
        String fileName = FileSystemUtils.sanitizeWithSpacesAndSlashes(
                this.category + File.separator + this.fileName + ".png");
        return new File(VehicleModelCache.ICON_DIR, fileName);
    }

    /**
     * Get the cached vehicle icon
     * @return Icon bitmap or null if doesn't exist
     */
    public Bitmap getCachedIcon() {
        File iconFile = getCachedIconFile();
        if (IOProviderFactory.exists(iconFile)) {
            try (FileInputStream fis = IOProviderFactory
                    .getInputStream(iconFile)) {
                return BitmapFactory.decodeStream(fis);
            } catch (IOException e) {
                // `bmp` remains `null`
            }
        }
        return null;
    }

    /**
     * Get a URI for this vehicle's icon
     * @return Icon URI
     */
    public String getIconURI() {
        return "vehicle://" + category + "/" + name;
    }

    public synchronized ModelInfo getInfo() {
        checkLoaded();
        return this.info;
    }

    public synchronized Model getModel() {
        checkLoaded();
        return this.model;
    }

    /**
     * Get points which make up an outline of this vehicle
     * @param cb Callback to fire once points are finished loading
     *          (if they need to be loaded)
     * @return List of points or null if loading
     */
    public synchronized List<PointF> getOutline(Runnable cb) {
        // Already loaded points
        if (this.outline != null)
            return this.outline;

        // Register callback when loading is finished
        if (cb != null)
            this.outlineCallbacks.add(cb);

        // Already in the process of loading points
        if (this.loadingOutline)
            return null;

        // Start generating points
        this.loadingOutline = true;
        VehicleOutlineCaptureRequest req = new VehicleOutlineCaptureRequest(
                this, OUTLINE_SIZE);
        req.setCallback(new VehicleOutlineCaptureRequest.Callback() {
            @Override
            public void onCaptureFinished(List<PointF> points) {
                List<Runnable> callbacks;
                synchronized (VehicleModelInfo.this) {
                    outline = points;
                    loadingOutline = false;
                    callbacks = new ArrayList<>(outlineCallbacks);
                    outlineCallbacks.clear();
                }
                for (Runnable cb : callbacks)
                    cb.run();
            }
        });
        GLOffscreenCaptureService.getInstance().request(req);
        return null;
    }

    /**
     * Check if this model is loaded and do so if needed
     */
    private void checkLoaded() {
        // Don't load when we don't need to
        if (this.loaded)
            return;

        // Copy the file from assets if we haven't already
        if (!IOProviderFactory.exists(file)
                || IOProviderFactory.length(file) == 0) {
            Log.d(TAG, "Copying vehicle model from assets: " + file.getName());
            if (!VehicleModelAssetUtils.copyAssetToFile(file)) {
                Log.e(TAG, "Failed to find model file: " + file);
                FileSystemUtils.delete(file);
                return;
            }
        }

        // Build transformation matrix if an offset was specified
        Matrix transform = null;
        if (this.offset != null) {
            // Values are negated so that X = right, Y = forward, and Z = up
            transform = Matrix.getTranslateInstance(-this.offset.x,
                    -this.offset.y, -this.offset.z);
        }

        // Load the model data
        Log.d(TAG, "Loading vehicle model: " + file);
        ModelLoader loader = new ModelLoader(this.file, null,
                ModelProjection.ENU_FLIP_YZ, this);
        loader.setTransform(transform);
        if (!loader.load()) {
            Log.e(TAG, "Failed to load vehicle model: " + file);
            FileSystemUtils.delete(file);
            return;
        }

        // Finished
        this.loaded = this.info != null && this.model != null;
    }

    @Override
    public void onLoad(ModelInfo info, Model model) {
        this.info = info;
        this.model = model;
    }

    @Override
    public void onProgress(int progress) {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public String toString() {
        return getUID();
    }
}
