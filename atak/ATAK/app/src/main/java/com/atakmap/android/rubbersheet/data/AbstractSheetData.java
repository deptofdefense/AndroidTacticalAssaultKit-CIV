
package com.atakmap.android.rubbersheet.data;

import android.graphics.Color;

import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.locale.LocaleUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.UUID;

/**
 * Simple serializable data container for a rubber sheet
 */
public class AbstractSheetData {

    private static final String TAG = "AbstractSheetData";

    public String label, remarks;
    public File file;
    public GeoPoint[] points;
    public int alpha = 255;
    public int strokeColor = Color.WHITE;
    public double strokeWeight = 4.0d;
    public boolean visible = true;

    protected AbstractSheetData() {
    }

    public AbstractSheetData(File f) {
        this.file = f;
        this.label = f.getName();
    }

    public AbstractSheetData(AbstractSheet rs) {
        // The name of the rubber sheet
        this.label = rs.getTitle();

        // Remarks
        this.remarks = rs.getMetaString("remarks", null);

        // Reference file
        this.file = rs.getFile();

        // Rectangle corner points
        GeoPoint[] p = rs.getPoints();
        if (p != null && p.length >= 4)
            this.points = new GeoPoint[] {
                    p[0], p[1], p[2], p[3]
            };

        // Visibility state
        this.visible = rs.getVisible();

        // Alpha value
        this.alpha = rs.getAlpha();

        // Shape attributes
        this.strokeColor = rs.getStrokeColor();
        this.strokeWeight = rs.getStrokeWeight();
    }

    public String getUID() {
        return UUID.nameUUIDFromBytes(file.getAbsolutePath().getBytes())
                .toString();
    }

    public static String getExtension(File file) {
        String ext = file.getName().toLowerCase(LocaleUtil.getCurrent());
        return ext.substring(ext.lastIndexOf(".") + 1);
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(label)
                && file != null && IOProviderFactory.exists(file)
                && IOProviderFactory.isFile(file)
                && points != null && points.length == 4;
    }

    public JSONObject toJSON() {
        if (!isValid())
            return null;
        JSONObject o = new JSONObject();
        try {
            o.put("label", this.label);
            o.put("file", this.file);
            o.put("alpha", this.alpha);
            o.put("strokeColor", this.strokeColor);
            o.put("strokeWeight", this.strokeWeight);
            o.put("visible", this.visible);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < 4; i++)
                arr.put(this.points[i].toStringRepresentation());
            o.put("points", arr);
            if (!FileSystemUtils.isEmpty(this.remarks))
                o.put("remarks", this.remarks);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize", e);
            return null;
        }
        return o;
    }

    protected AbstractSheetData(JSONObject o) throws JSONException {
        this.file = new File(o.getString("file"));

        this.label = o.has("label") ? o.getString("label")
                : this.file.getName();

        if (o.has("alpha"))
            this.alpha = o.getInt("alpha");

        if (o.has("strokeColor"))
            this.strokeColor = o.getInt("strokeColor");

        if (o.has("strokeWeight"))
            this.strokeWeight = o.getDouble("strokeWeight");

        if (o.has("visible"))
            this.visible = o.getBoolean("visible");

        if (o.has("points")) {
            JSONArray arr = o.getJSONArray("points");
            if (arr.length() == 4) {
                this.points = new GeoPoint[4];
                for (int i = 0; i < 4; i++)
                    this.points[i] = GeoPoint.parseGeoPoint(arr.getString(i));
            }
        }

        if (o.has("remarks"))
            this.remarks = o.getString("remarks");
    }

    public static AbstractSheetData create(JSONObject o) {
        if (o == null || !o.has("file"))
            return null;
        try {
            File f = new File(o.getString("file"));
            if (!IOProviderFactory.exists(f))
                return null;
            String ext = getExtension(f);
            if (RubberModelData.EXTS.contains(ext))
                return new RubberModelData(o);
            else if (RubberImageData.EXTS.contains(ext))
                return new RubberImageData(o);
        } catch (Exception e) {
            Log.e(TAG, "Failed to deserialize", e);
        }
        return null;
    }
}
