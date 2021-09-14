
package com.atakmap.android.helloworld.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.widget.Toast;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.imagecapture.TiledCanvas;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.tilecapture.TileCapture;
import com.atakmap.android.tilecapture.imagery.ImageryCaptureParams;
import com.atakmap.android.tilecapture.imagery.ImageryCaptureTask;
import com.atakmap.android.tilecapture.imagery.MapItemCapturePP;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.File;

/**
 * Demonstrates how to take a high quality map screenshot using
 * {@link TileCapture}, {@link ImageryCaptureTask} and {@link MapItemCapturePP}
 */
public class MapScreenshotExample implements ImageryCaptureTask.Callback {

    private static final String TAG = "MapScreenshotExample";

    private final MapView _mapView;
    private final Context _context, _plugin;

    public MapScreenshotExample(MapView mapView, Context plugin) {
        _mapView = mapView;
        _context = mapView.getContext();
        _plugin = plugin;
    }

    /**
     * Start the map capture workflow
     * 1) First capture the map imagery
     * 2) Then capture the map items and paste them onto the imagery layer
     * 3) Save the finished image to a JPEG file
     */
    public void start() {

        // Determine the 4 points on the map to use as the capture area of
        // interest. For this example we'll simply use the user's current map
        // view focus. You can use any area on the map, regardless of whether
        // it's on screen or not.
        int w = _mapView.getWidth(), h = _mapView.getHeight();
        GeoPoint[] quad = new GeoPoint[] {
                _mapView.inverse(new PointF(0, 0)).get(), // Top-left corner
                _mapView.inverse(new PointF(w, 0)).get(), // Top-right corner
                _mapView.inverse(new PointF(w, h)).get(), // Bottom-right corner
                _mapView.inverse(new PointF(0, h)).get() // Bottom-left corner
        };

        // Create a tile capture instance for capturing map imagery using the
        // bounds of the quad we created.
        TileCapture tc = TileCapture.create(GeoBounds.createFromPoints(quad));
        if (tc == null) {
            // This will only occur if the map view hasn't been created yet
            Log.e(TAG, "Failed to create tile capture instance!");
            return;
        }

        // Setup imagery capture parameters
        ImageryCaptureParams cp = new ImageryCaptureParams();

        // Working output directory
        cp.outputDirectory = FileSystemUtils.getItem("tmp");

        // Area of interest quad we created above
        cp.points = quad;

        // Flag quad as a closed shape
        cp.closedPoints = true;

        // Fit the imagery to the quad
        cp.fitToQuad = true;

        // Make sure the imagery has this exact aspect ratio
        cp.fitAspect = (float) w / h;

        // Capture resolution scale
        // 1 = same as the map display
        // 2 = double the map display
        // 3 = 3x map display
        // etc...
        cp.captureResolution = 1;

        // Set the tile resolution level based on the height of the map view
        cp.level = tc.calculateLevel(cp.points, h, cp.captureResolution);

        // Begin capturing map imagery
        // Once finished, the onFinishCapture method below is called
        new ImageryCaptureTask(tc, cp, this).execute();
    }

    /**
     * The map imagery capture step has finished
     * @param tc Tile capture instance we created above
     * @param cp Imagery capture parameters we created above
     * @param imageryFile The output file containing the imagery (TIFF format)
     */
    @Override
    public void onFinishCapture(TileCapture tc, ImageryCaptureParams cp,
            File imageryFile) {

        // Dispose the tile capture reader now that we no longer need it
        // This frees some memory used later on
        tc.dispose();

        // Make sure the imagery file was properly created
        if (!FileSystemUtils.isFile(imageryFile)) {
            Log.e(TAG, "Failed to capture imagery to file: " + imageryFile);
            return;
        }

        // Map item capture post-processor
        // This will render the map items to the imagery layer
        MapItemCapturePP postDraw = new MapItemCapturePP(_mapView, tc, cp);

        // Create a tiled canvas processor
        // This is used to write each post-processed tile onto an image
        // without loading the entire image into memory at once.
        // This implementation is useful for very large image captures (3x+).
        TiledCanvas canvas = new TiledCanvas(imageryFile, tc.getTileWidth(),
                tc.getTileHeight());

        // Draw map items to the imagery canvas
        canvas.postDraw(postDraw, null);

        // Save the finished image to a JPEG file
        File jpegFile = new File(imageryFile.getParent(),
                "HelloWorld-Screenshot-Example.jpg");
        canvas.copyToFile(jpegFile, Bitmap.CompressFormat.JPEG, 100);

        // Delete the imagery buffer since we no longer need it
        FileSystemUtils.delete(imageryFile);

        // Notify the user their screenshot is finished
        Toast.makeText(_context, _plugin.getString(
                R.string.map_screenshot_finished,
                FileSystemUtils.prettyPrint(jpegFile)),
                Toast.LENGTH_LONG).show();
    }
}
