
package com.atakmap.android.rubbersheet.data;

import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.android.rubbersheet.maps.RubberImage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple serializable data container for a rubber sheet
 */
public class RubberImageData extends AbstractSheetData {

    private static final String TAG = "RubberImageData";

    public static final Set<String> EXTS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "bmp", "gif", "tif", "tiff",
                    "ntf", "nitf", "nsf", "kmz"));

    protected RubberImageData() {
    }

    public RubberImageData(File f) {
        super(f);
    }

    public RubberImageData(RubberImage rs) {
        super(rs);
    }

    protected RubberImageData(JSONObject o) throws JSONException {
        super(o);
    }

    /**
     * Check if a file is a valid image or GRG file
     * @param f File
     * @return True if valid
     */
    public static boolean isSupported(File f) {
        String ext = getExtension(f);
        if (EXTS.contains(ext)) {
            if (ext.equals("kmz"))
                return KmzLayerInfoSpi.containsTag(f,
                        KmzLayerInfoSpi.GROUND_OVERLAY);
            return true;
        }
        return false;
    }
}
