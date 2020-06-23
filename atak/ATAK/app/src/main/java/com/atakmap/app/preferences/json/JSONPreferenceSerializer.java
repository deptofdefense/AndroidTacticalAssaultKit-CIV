
package com.atakmap.app.preferences.json;

import org.json.JSONObject;

/**
 * Preference that can be serialized to and from JSON
 */
public interface JSONPreferenceSerializer {

    /**
     * Serialize content to JSON in a given root element
     * @param root Root element (where the preference output is added)
     * @return True if handled, false if not
     */
    boolean writeJSON(JSONObject root);

    /**
     * Read content from JSON root element
     * @param root Root element (where the preference input is read)
     * @return True if handled, false if not
     */
    boolean readJSON(JSONObject root);
}
