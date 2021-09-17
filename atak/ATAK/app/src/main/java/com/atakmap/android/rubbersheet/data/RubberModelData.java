
package com.atakmap.android.rubbersheet.data;

import android.net.Uri;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.create.AbstractCreationTask;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.rubbersheet.ui.dialog.ImportModelDialog;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.model.ModelInfoFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Simple serializable data container for a rubber sheet
 */
public class RubberModelData extends AbstractSheetData {

    private static final String TAG = "RubberModelData";

    public static final Set<String> EXTS = new HashSet<>(
            Arrays.asList("zip", "obj", "dae", "kmz"));

    // Model projection (ENU by default)
    public ModelProjection projection = ModelProjection.ENU;

    // Center of the model
    public GeoPointMetaData center;

    // Scale factor [x, y, z]
    public double[] scale;

    // Rotation in degrees [pitch, yaw, roll]
    public double[] rotation;

    // Dimensions in meters [width, length, height]
    public double[] dimensions;

    // Sub-model directory
    public String subModel;

    protected RubberModelData() {
    }

    public RubberModelData(File f) {
        super(f);
    }

    public RubberModelData(RubberModel rm) {
        super(rm);
        this.subModel = rm.getSubModelURI();
        this.projection = rm.getProjection();
        this.center = rm.getCenter();
        this.scale = rm.getModelScale();
        this.rotation = rm.getModelRotation();
        this.dimensions = rm.getModelDimensions();
    }

    @Override
    public String getUID() {
        if (this.subModel != null)
            return UUID.nameUUIDFromBytes(this.subModel.getBytes()).toString();
        return super.getUID();
    }

    @Override
    public JSONObject toJSON() {
        JSONObject o = super.toJSON();
        if (o == null)
            return null;
        try {
            o.put("subModel", this.subModel);
            o.put("projection", this.projection);
            o.put("center", this.center.toString());
            putJSONArray(o, "scale", this.scale);
            putJSONArray(o, "rotation", this.rotation);
            putJSONArray(o, "dimensions", this.dimensions);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize", e);
            return null;
        }
        return o;
    }

    protected RubberModelData(JSONObject o) throws JSONException {
        super(o);
        if (o.has("subModel"))
            this.subModel = o.getString("subModel");
        if (o.has("projection"))
            this.projection = ModelProjection.valueOf(
                    o.getString("projection"));
        if (o.has("center")) {
            GeoPoint center = GeoPoint.parseGeoPoint(o.getString("center"));
            this.center = GeoPointMetaData.wrap(center);
            GeoPointMetaData alt = GeoPointMetaData.wrap(center);
            RubberSheetUtils.getAltitude(alt);
            if (Double.compare(alt.get().getAltitude(),
                    center.getAltitude()) != 0)
                this.center.setAltitudeSource(GeoPointMetaData.USER);
            else
                this.center = alt;
        }
        if (o.has("scale")) {
            this.scale = new double[] {
                    1, 1, 1
            };
            readJSONArray(o, "scale", this.scale);
        }
        if (o.has("rotation")) {
            this.rotation = new double[3];
            readJSONArray(o, "rotation", this.rotation);
        }
        if (o.has("dimensions")) {
            this.dimensions = new double[3];
            readJSONArray(o, "dimensions", this.dimensions);
        }
    }

    private static void putJSONArray(JSONObject o, String key,
            double[] srcArray) throws JSONException {
        if (srcArray != null) {
            JSONArray arr = new JSONArray();
            for (Double d : srcArray) {
                if (Double.isNaN(d))
                    d = 0d;
                arr.put(d);
            }
            o.put(key, arr);
        }
    }

    private static void readJSONArray(JSONObject o, String key,
            double[] destArr) throws JSONException {
        JSONArray arr = o.getJSONArray(key);
        for (int i = 0; i < arr.length() && i < destArr.length; i++)
            destArr[i] = arr.getDouble(i);
    }

    /**
     * Check if a file is a valid model file
     * @param f File
     * @return True if valid
     */
    public static boolean isSupported(File f) {
        // First check for a valid extension
        String ext = getExtension(f);
        if (!EXTS.contains(ext))
            return false;

        // Shallow model check
        try {
            return ModelInfoFactory.isSupported(Uri.fromFile(f));
        } catch (Exception e) {
            Log.e(TAG, "Invalid model file: " + f);
        }
        return false;
    }

    public static class SupportTask extends ProgressTask {

        private final File _file;
        private final AbstractCreationTask.Callback _callback;

        public SupportTask(MapView mapView, File f,
                AbstractCreationTask.Callback cb) {
            super(mapView);
            _file = f;
            _callback = cb;
        }

        @Override
        protected String getProgressMessage() {
            return _context.getString(R.string.reading_model, _file.getName());
        }

        @Override
        protected int getProgressStages() {
            return 1;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return isSupported(_file);
        }

        @Override
        protected void onPostExecute(Object ret) {
            super.onPostExecute(ret);
            if (ret == Boolean.TRUE) {
                new ImportModelDialog(_mapView).show(_file, _callback);
            } else {
                Log.d(TAG, "Ignoring unsupported file: " + _file);
                Toast.makeText(_context, _context.getString(
                        R.string.file_not_supported, _file.getName()),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
