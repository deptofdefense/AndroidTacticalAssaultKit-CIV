/**
 * assets/license/license-lgpl21.txt
 * source code: 
 *       https://github.com/MKergall/osmbonuspack/tree/master/OSMBonusPack/src/main/java/org/osmdroid/bonuspack/location
 * author: MKergall
 */

package com.atakmap.android.user.geocode;

import android.location.Address;
import android.os.Bundle;

public class GeocodeConverter {
    public static final String TAG = "GeocodeConverter";

    static public boolean isPresent() {
        return true;
    }

    /**
     * @return the whole content of the http request, as a string
     */
    public static String readStream(HttpConnection connection) {
        String result = connection.getContentAsString();
        return result;
    }

    /** sends an http request, and returns the whole content result in a String
     * @param url
     * @param userAgent
     * @return the whole content, or null if any issue.
     */
    public static String requestStringFromUrl(String url, String userAgent) {
        HttpConnection connection = new HttpConnection();
        if (userAgent != null)
            connection.setUserAgent(userAgent);
        connection.doGet(url);
        String result = readStream(connection);
        connection.close();
        return result;
    }

    /**
     * Convert an Address object to a single-line string
     * @param addr Address to get information from
     * @return String representing the address
     */
    public static String getAddressString(Address addr) {
        String ret = "";
        if (addr != null) {
            Bundle extras = addr.getExtras();
            if (extras != null)
                ret = extras.getString("display_name", "");
            if (ret.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int maxIndex = addr.getMaxAddressLineIndex();
                for (int i = 0; i <= maxIndex; i++) {
                    sb.append(addr.getAddressLine(i));
                    if (i < maxIndex)
                        sb.append(" ");
                }
                ret = sb.toString();
            }
        }
        return ret;
    }
}
