
package com.atakmap.android.maps;

/**
 * Engine resource reference that points to an Android base64 data.
 */
public class Base64MapDataRef extends MapDataRef {

    private String _data;

    /**
     * Create a new Base64MapDataRef
     * 
     * @param data the base 64 data
     */
    public Base64MapDataRef(String data) {
        _data = data;
    }

    /**
     * Get the base 64 data
     * 
     * @return the data 
     */
    public String getBase64Data() {
        return _data;
    }

    /**
     * Get a human readable representation of the reference
     */
    public String toString() {
        return "base64: " + _data;
    }


    @Override
    public String toUri() {
        return toUri(_data);
    }

    public static String toUri(String data) {
        return "base64://" + data;
    }
}
