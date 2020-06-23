
package com.atakmap.android.util;

import android.util.Pair;

/**
 * Drop in replacement for org.apache.http.NameValuePair and BasicNameValuePair
 * where it has been used inappropriately.
 */

public class BasicNameValuePair extends Pair<String, String> {

    /**
     * Construct a Name Value Pair
     * @param name the name
     * @param value the value
     */
    public BasicNameValuePair(String name, String value) {
        super(name, value);
    }

    /**
     * Get the name in the name value pair
     * @return the name
     */
    public String getName() {
        return first;
    }

    /**
     * Get the value in the name value pair
     * @return the value
     */
    public String getValue() {
        return second;
    }
}
