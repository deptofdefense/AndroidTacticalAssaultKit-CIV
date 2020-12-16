
package com.atakmap.android.imagecapture;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.text.format.DateFormat;
import android.widget.Toast;

import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.tilecapture.imagery.MapItemCapturePP;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.opengl.GLMapSurface;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdalconst.gdalconstConstants;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Image capture tool class
 * @deprecated Use {@link MapItemCapturePP} instead for much better
 * performance and results
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class ImageCapture extends AbstractLayer {

    private static final String TAG = "ImageCapture";

    protected final MapView _mapView;
    protected GLCaptureTool _glSubject;
    protected final File _outDir;
    protected final String _toolbarId;

    public ImageCapture(MapView mapView, File outFile, String toolbarId,
            List<MapItem> items) {
        super("Image Capture");

        _mapView = mapView;
        _outDir = outFile;
        _toolbarId = toolbarId;

        _mapView.pushStack(MapView.RenderStack.TARGETING);
        _mapView.addLayer(MapView.RenderStack.TARGETING, this);
    }

    public ImageCapture(MapView mapView, File outFile, String toolbarId) {
        this(mapView, outFile, toolbarId, null);
    }

    public String getToolbarId() {
        return _toolbarId;
    }

    public ImageCapturePP getPostProcessor() {
        return new ImageCapturePP(_glSubject, 1);
    }

    /**
     * Return a GL instance of the image capture rasterizer
     * @return New GLImageCapture instance
     */
    public GLImageCapture getImageCapture() {
        GLMapSurface surface = _glSubject.getSurface();
        int yOffset = surface.getHeight() - getHeight();
        return new GLImageCapture(_glSubject, surface,
                (int) surface.getX(),
                (int) surface.getY() + yOffset, getWidth(),
                getHeight());
    }

    /**
     * Return the bounds of the final capture
     * @return Capture bounds
     */
    public GeoBounds getBounds() {
        GeoBounds bounds = new GeoBounds(_glSubject.lowerLeft,
                _glSubject.upperRight);
        bounds.setWrap180(_glSubject.continuousScrollEnabled);
        return bounds;
    }

    public boolean useCustomRenderer() {
        return false;
    }

    public boolean showMapImagery() {
        return true;
    }

    public List<MapItem> getAllItems() {
        return new ArrayList<>(
                getAllItems(_mapView.getRootGroup(), null));
    }

    public Set<MapItem> getAllItems(MapGroup group, FOVFilter filter) {
        Set<MapItem> all = new HashSet<>();
        for (MapItem mi : group.getItems()) {
            if (mi.getVisible() && (filter == null || filter.accept(mi)))
                all.add(mi);
        }
        for (MapGroup mg : group.getChildGroups())
            all.addAll(getAllItems(mg, filter));

        return all;
    }

    public List<MapItem> getItems(GeoBounds bounds) {
        FOVFilter filter = new FOVFilter(bounds);
        Set<MapItem> ret = getAllItems(_mapView.getRootGroup(), filter);

        // Add features
        MapOverlay fileOverlays = _mapView.getMapOverlayManager()
                .getOverlay("fileoverlays");
        if (fileOverlays instanceof MapOverlayParent) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("visibleOnly", "true");
            for (MapOverlay ov : ((MapOverlayParent) fileOverlays)
                    .getOverlays()) {
                DeepMapItemQuery query = ov.getQueryFunction();
                if (query == null)
                    continue;
                Collection<MapItem> items = query.deepFindItems(
                        bounds, metadata);
                ret.addAll(items);
            }
        }

        return new ArrayList<>(ret);
    }

    public List<Layer> getLayers() {
        List<Layer> retval = new LinkedList<>();
        retval.addAll(_mapView.getLayers(MapView.RenderStack.BASEMAP));
        retval.addAll(_mapView.getLayers(MapView.RenderStack.MAP_LAYERS));
        retval.addAll(_mapView.getLayers(MapView.RenderStack.RASTER_OVERLAYS));
        for (int i = 0; i < retval.size(); i++) {
            Layer l = retval.get(i);
            if (l instanceof MultiLayer) {
                // Remove any feature (geometry) layers
                // Geometry is always rendered in post-processing
                List<Layer> multiLayers = ((MultiLayer) l).getLayers();
                MultiLayer copy = new MultiLayer(l.getName());
                for (Layer l2 : multiLayers) {
                    if (!(l2 instanceof FeatureLayer))
                        copy.addLayer(l2);
                }
                retval.remove(i);
                retval.add(i, copy);
            } else if (l instanceof FeatureLayer)
                retval.remove(i--);
        }
        return retval;
    }

    public MapView getMapView() {
        return _mapView;
    }

    public int getWidth() {
        return _mapView.getWidth();
    }

    public int getHeight() {
        return _mapView.getHeight();
    }

    public File getOutputDirectory() {
        return _outDir;
    }

    @Override
    public String getName() {
        return "";
    }

    public boolean kmzOnly() {
        return true;
    }

    public boolean snapToGrid() {
        return false;
    }

    synchronized void setGLSubject(GLCaptureTool subject) {
        _glSubject = subject;
    }

    public synchronized void startCapture() {
        //hijack the target bubble render stack
        if (_glSubject != null)
            _glSubject.beginCapture();
    }

    /**
     * Create the working tile set used to store captured imagery
     * Sub-classes can override this to change the dimensions or other metadata
     * of the working tile set
     *
     * @param driver Data set driver (GTiff)
     * @param file The file for this data set
     * @param w Default full width of the data set (can be safely modified)
     * @param h Default full height of the data set (can be safely modified)
     * @return Newly created data set (null if failed)
     */
    public Dataset createDataset(Driver driver, File file, int w, int h) {
        String path = file.getAbsolutePath();
        if (!IOProviderFactory.isDefault()) {
            path = VSIFileFileSystemHandler.PREFIX + path;
        }
        return driver.Create(path, w, h, 3,
                getTIFFOptions());
    }

    /**
     * Tile has been captured by the capture tool
     * Please free byte buffer 'rgba' here
     *
     * @param rgba Raw bytes of captured imagery
     * @param x Tile x-coordinate (unscaled by width)
     * @param y Tile y-coordinate (unscaled by height)
     * @param width Width of tile
     * @param height Height of tile
     * @param ds Working tile dataset
     */
    public void onCaptureTile(ByteBuffer rgba, int x, int y, int width,
            int height, Dataset ds) {
        // OpenGL returns a vertically-flipped version of the imagery
        // So we need to correct this before saving
        int len = rgba.limit();
        ByteBuffer vflip = Unsafe.allocateDirect(len);
        long sPtr = Unsafe.getBufferPointer(rgba);
        long dPtr = Unsafe.getBufferPointer(vflip);
        int rowLen = width * 4;
        for (int i = 0; i < len; i += rowLen)
            Unsafe.memcpy(dPtr + len - rowLen - i, sPtr + i, rowLen);

        // Save corrected tile to raster
        ds.WriteRaster_Direct(x * width, y * height, width, height,
                width, height, gdalconstConstants.GDT_Byte, vflip,
                null, 4, width * 4, 1);
        ds.delete();
        Unsafe.free(vflip);
        Unsafe.free(rgba);
    }

    public String getProcessingMessage() {
        return _mapView.getContext().getString(R.string.imgcap_processing_msg);
    }

    public void onCancelCapture() {
        toast(_mapView.getContext().getString(R.string.imgcap_cancel_msg));
    }

    public void drawOverlays() {
        // Draw extra overlays here
    }

    public synchronized void cancel() {
        if (_glSubject != null)
            _glSubject.cancelRender();
        setSaving(false);
    }

    public void dispose() {
        if (_glSubject != null)
            _glSubject.unregister();
    }

    public void saveResult(final ImageCapturePP postDraw,
            final TiledCanvas canvas) {
        String date = DateFormat.format("yyyyMMdd'T'HHmmss",
                CoordinatedTime.currentDate()).toString();
        String outName = getName();
        if (outName == null || outName.isEmpty())
            outName = postDraw.getTitle()
                    .replace("\n", " ") + "_" + date;
        outName = FileSystemUtils.sanitizeFilename(outName);

        String format = CapturePrefs.get(
                CapturePrefs.PREF_FORMAT, ".jpg");
        final String path = outName;
        final File tmpDir = new File(getOutputDirectory(), path);
        final File outImage = new File(getOutputDirectory(), path + format);
        saveResult(postDraw, canvas, outImage, tmpDir, true);
    }

    public void saveResult(final ImageCapturePP postDraw,
            final TiledCanvas canvas,
            final File outImage, final File tmpDir, final boolean bDialog) {
        setSaving(true);
        if (postDraw == null || canvas == null) {
            toast("Failed to save capture.");
            cancel();
            return;
        }

        ProgressDialog saveProgress = null;

        if (bDialog) {
            saveProgress = new ProgressDialog(
                    _mapView.getContext());
            saveProgress
                    .setMessage("Saving capture to: \n" + tmpDir);
            saveProgress.setIndeterminate(false);
            saveProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            saveProgress.setCancelable(true);
            saveProgress.setMax(canvas.getTileCount());
            saveProgress.show();

            saveProgress
                    .setOnCancelListener(
                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    toast("Continuing capture save in background...");
                                }
                            });
        }

        final ProgressDialog fSaveProgress = saveProgress;

        new Thread(new Runnable() {
            @Override
            public void run() {
                saveResult(postDraw, canvas, outImage, tmpDir, fSaveProgress);
            }
        }, TAG + "-SaveResult").start();
    }

    protected void saveResult(ImageCapturePP postDraw, TiledCanvas canvas,
            File outImage, File tmpDir, final ProgressDialog pd) {
        postDraw.setProgressCallback(null);
        postDraw.setDrawRes(CapturePrefs.get(CapturePrefs.PREF_RES, 1));
        canvas.postDraw(postDraw, new ImageCapturePP.ProgressCallback() {
            @Override
            public void onProgress(final int prog) {
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (pd != null && pd.isShowing())
                            pd.setProgress(prog);
                    }
                });
            }
        });
        try {
            saveImage(outImage, canvas);
            saveKMZ(outImage, tmpDir, postDraw);
        } finally {
            cancel();
            if (pd != null) {
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                    }
                });
            }
        }
    }

    protected void saveImage(File outImage, TiledCanvas canvas) {
        String format = CapturePrefs.get(CapturePrefs.PREF_FORMAT, ".jpg");
        boolean png = format.equals(".png");
        Bitmap.CompressFormat comp = png ? Bitmap.CompressFormat.PNG
                : Bitmap.CompressFormat.JPEG;
        canvas.copyToFile(outImage, comp, png ? 100
                : CapturePrefs.get(CapturePrefs.PREF_IMG_QUALITY, 90));
        FileSystemUtils.delete(canvas.getFile());
    }

    protected void saveKMZ(File outImage, File tmpDir,
            ImageCapturePP postDraw) {
        if (IOProviderFactory.exists(tmpDir)
                || IOProviderFactory.mkdir(tmpDir)) {
            String name = outImage.getName();
            String path = outImage.getAbsolutePath();
            path = path.substring(0, path.lastIndexOf("."));
            writeDocFormat(postDraw.getBounds(), name, tmpDir);
            File kmzFile = new File(path + ".kmz");
            try {
                FileSystemUtils.copyFile(outImage, new File(tmpDir,
                        name));
                FileSystemUtils.zipDirectory(tmpDir, kmzFile);
            } catch (IOException e) {
                Log.e(TAG, "Failed to compress to KMZ output " + kmzFile, e);
            }
            FileSystemUtils.deleteDirectory(tmpDir, false);
            if (kmzOnly())
                FileSystemUtils.delete(outImage);
            toast("Saved image capture: " + kmzFile.getPath());
        } else {
            toast("Failed to save survey image capture.");
            Log.w(TAG,
                    "Failed to create temp directory for saved result: "
                            + tmpDir);
        }
    }

    // Mechanism to prevent re-opening the tool while saving is busy
    protected static boolean _saveBusy = false;

    protected synchronized static void setSaving(boolean active) {
        _saveBusy = active;
    }

    public synchronized static boolean isSaving() {
        return _saveBusy;
    }

    public void writeDocFormat(GeoBounds bounds, String fName, File dir) {
        fName = CotEvent.escapeXmlText(fName);
        double N = bounds.getNorth(), S = bounds.getSouth(),
                E = bounds.getEast(), W = bounds.getWest();
        if (bounds.crossesIDL()) {
            E -= 360;
            double max = Math.max(E, W);
            W = Math.min(E, W);
            E = max;
        }
        String docSkel = "<?xml version='1.0' encoding='UTF-8'?>"
                +
                "<kml xmlns='http://www.opengis.net/kml/2.2' xmlns:gx='http://www.google.com/kml/ext/2.2' xmlns:kml='http://www.opengis.net/kml/2.2' xmlns:atom='http://www.w3.org/2005/Atom'>"
                + "<GroundOverlay>"
                + "<name>"
                + fName
                + "</name>"
                + "<Icon>"
                + "<href>"
                + fName
                + "</href>"
                + "<viewBoundScale>0.75</viewBoundScale>"
                + "</Icon>"
                + "<gx:LatLonQuad>"
                + "<coordinates>"
                + W + "," + S + ",0 "
                + E + "," + S + ",0 "
                + E + "," + N + ",0 "
                + W + "," + N + ",0"
                + "</coordinates>"
                + "</gx:LatLonQuad>"
                + "</GroundOverlay>"
                + "</kml>";
        try {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(
                    IOProviderFactory.getOutputStream(new File(dir, "doc.kml")),
                    FileSystemUtils.UTF8_CHARSET.name()));
            out.println(docSkel);
            out.close();
        } catch (IOException ioe) {
            Log.d(TAG, "error occurred writing the doc.xml file", ioe);
        }
    }

    /**
     * Options used to write out the temporary TIFF image
     * for storing the entire capture
     * Default options are designed to keep file size low
     * at the cost of a bit more processing time during capture
     * @return Array of key=value options
     */
    protected String[] getTIFFOptions() {
        return new String[] {
                "TILED=YES",
                "COMPRESS=DEFLATE",
                "ZLEVEL=9"
        };
    }

    protected void toast(final String txt) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(_mapView.getContext(),
                        txt, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public boolean isDisplayDialog() {
        return true;
    }
}
