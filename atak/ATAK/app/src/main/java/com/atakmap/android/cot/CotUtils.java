
package com.atakmap.android.cot;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

public class CotUtils {

    private final static String TAG = "CotUtils";

    /**
     * The ability to set a time  given an attribute in the CotDetail provided a map item and the
     * metaKey
     * @param editor the CotDetail to be modified
     * @param data the map item
     * @param metaKey the key used from the map item
     * @param attrKey the attribute to set in the CotDetail.
     */
    public static void checkSetTime(CotDetail editor, MapItem data,
            String metaKey,
            String attrKey) {
        if (data.hasMetaValue(metaKey)) {
            long time = data.getMetaLong(metaKey, 0);
            if (time > 0) {
                editor.setAttribute(attrKey,
                        (new CoordinatedTime(time)).toString());
            }
        }
    }

    /**
     * Given a detail, checks to see if the map item contains the long and if so converts it to the
     * desired attribute tag within the element defined by the CotDetail.   If the item does not
     * exist, then no attribute is created within the Cot Detail.
     * @param editor the cot detail
     * @param data the map item
     * @param metaKey the metadata key for the map item
     * @param attrKey the attribute for the corresponding detail.
     */
    public static void checkSetLong(CotDetail editor, MapItem data,
            String metaKey,
            String attrKey) {
        if (data.hasMetaValue(metaKey))
            editor.setAttribute(attrKey,
                    String.valueOf(data.getMetaLong(metaKey, 0)));
    }

    /**
     * Given a detail, checks to see if the map item contains the int and if so converts it to the
     * desired attribute tag within the element defined by the CotDetail.   If the item does not
     * exist, then no attribute is created within the Cot Detail.
     * @param editor the cot detail
     * @param data the map item
     * @param metaKey the metadata key for the map item
     * @param attrKey the attribute for the corresponding detail.
     */
    public static void checkSetInt(CotDetail editor, MapItem data,
            String metaKey,
            String attrKey) {
        if (data.hasMetaValue(metaKey))
            editor.setAttribute(attrKey,
                    String.valueOf(data.getMetaInteger(metaKey, 0)));
    }

    /**
     * Given a detail, checks to see if the map item contains the double and if so converts it to the
     * desired attribute tag within the element defined by the CotDetail.   If the item does not
     * exist, then no attribute is created within the Cot Detail.
     * @param editor the cot detail
     * @param data the map item
     * @param metaKey the metadata key for the map item
     * @param attrKey the attribute for the corresponding detail.
     */
    public static void checkSetDouble(CotDetail editor, MapItem data,
            String metaKey,
            String attrKey) {
        if (data.hasMetaValue(metaKey))
            editor.setAttribute(attrKey,
                    String.valueOf(data.getMetaDouble(metaKey, 0)));
    }

    /**
     * Given a detail, checks to see if the map item contains the String and if so converts it to the
     * desired attribute tag within the element defined by the CotDetail.   If the item does not
     * exist, then no attribute is created within the Cot Detail.
     * @param editor the cot detail
     * @param data the map item
     * @param metaKey the metadata key for the map item
     * @param attrKey the attribute for the corresponding detail.
     */
    public static void checkSetString(CotDetail editor, MapItem data,
            String metaKey,
            String attrKey) {
        if (data.hasMetaValue(metaKey))
            editor.setAttribute(attrKey, data.getMetaString(metaKey, null));
    }

    /**
     * Given a detail, checks to see if the map item contains the boolean and if so converts it to the
     * desired attribute tag within the element defined by the CotDetail.   If the item does not
     * exist, then no attribute is created within the Cot Detail.
     * @param editor the cot detail
     * @param data the map item
     * @param metaKey the metadata key for the map item
     * @param attrKey the attribute for the corresponding detail.
     */
    public static void checkSetBoolean(CotDetail editor, MapItem data,
            String metaKey,
            String attrKey) {
        if (data.hasMetaValue(metaKey))
            editor.setAttribute(attrKey,
                    String.valueOf(data.getMetaBoolean(metaKey, false)));
    }

    /**
     * Provided a marker and a key, convert the string value to a double and set the value in
     * the map item
     * @param m the map item
     * @param key the corresponding key to set
     * @param val the value in string form.
     */
    public static void setDouble(final MapItem m, String key, String val) {
        try {
            if (val != null) {
                m.setMetaDouble(key, Double.parseDouble(val));
            }
        } catch (Exception e) {
            Log.d(TAG, "error setting: " + key + "with " + val);
        }
    }

    /**
     * Provided a marker and a key, convert the string value to a integer and set the value in
     * the map item
     * @param m the map item
     * @param key the corresponding key to set
     * @param val the value in string form.
     */
    public static void setInteger(final MapItem m, String key, String val) {
        try {
            if (val != null) {
                m.setMetaInteger(key, (int) Double.parseDouble(val));
            }
        } catch (Exception e) {
            Log.d(TAG, "error setting: " + key + "with " + val);
        }
    }

    /**
     * Provided a marker and a key, convert the string value to a long and set the value in
     * the map item
     * @param m the map item
     * @param key the corresponding key to set
     * @param val the value in string form.
     */
    public static void setLong(final MapItem m, String key, String val) {
        try {
            if (val != null) {
                m.setMetaLong(key, Long.parseLong(val));
            }
        } catch (Exception e) {
            Log.d(TAG, "error setting: " + key + "with " + val);
        }
    }

    /**
     * Provided a marker and a key, convert the string value to a boolean and set the value in
     * the map item
     * @param m the map item
     * @param key the corresponding key to set
     * @param val the value in string form.
     */
    public static void setBoolean(final MapItem m, String key, String val) {
        try {
            if (val != null) {
                m.setMetaBoolean(key, Boolean.parseBoolean(val));
            }
        } catch (Exception e) {
            Log.d(TAG, "error setting: " + key + "with " + val);
        }
    }

    /**
     * Provided a marker and a key, checks to make sure the string cannot be null.
     * @param m the map item
     * @param key the corresponding key to set
     * @param val the value in string form.
     */
    public static void setString(final MapItem m, String key, String val) {
        try {
            if (val != null) {
                m.setMetaString(key, val);
            }
        } catch (Exception e) {
            Log.d(TAG, "error setting: " + key + "with " + val);
        }
    }

    /*
     * Retrieve the callsign for this event
     * @param event the event to retrieve the callsign from.
     * @return The callsign of this event or null if it doesn't exist
     */
    public static String getCallsign(CotEvent event) {
        String callsign = null;
        CotDetail detail = event.getDetail();
        if (detail != null) {
            CotDetail contact = detail.getFirstChildByName(0, "contact");
            if (contact != null) {
                callsign = contact.getAttribute("callsign");
            }
        }
        return callsign;
    }
}
