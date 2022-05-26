
package com.atakmap.android.util;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.lang.reflect.Field;

public class ResUtils {

    private static final String TAG = "ResUtils";

    /***
     *
     * @param name - name of drawable resource id ie, "ic_nav_radio"
     * @return the resource id
     */
    public static Integer getDrawableResID(String name) {
        try {
            Class res = R.drawable.class;
            Field field = res.getField(name);
            return field.getInt(null);
        } catch (Exception e) {
            Log.e(TAG, "Failure to get drawable id.", e);
        }
        return null;
    }

    /***
     *
     * @param name - name of string resource id ie, "nav_radio_title"
     * @return the resource id
     */
    public static Integer getStringsID(String name) {
        try {
            Class res = R.string.class;
            Field field = res.getField(name);
            return field.getInt(null);
        } catch (Exception e) {
            Log.e(TAG, "Failure to get string id.", e);
        }
        return null;
    }
}
