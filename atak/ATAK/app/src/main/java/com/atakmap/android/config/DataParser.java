
package com.atakmap.android.config;

import android.graphics.Color;

import org.w3c.dom.Node;

public class DataParser {

    static final int TYPE_NONE = 0;
    static final int TYPE_STRING = 1;
    static final int TYPE_BOOLEAN = 2;

    public static String parseStringElem(Node elemNode, String fallback) {
        String r = fallback;
        if (elemNode != null) {
            r = parseStringText(elemNode.getFirstChild(), fallback);
        }
        return r;
    }

    public static String parseStringText(Node textNode, String fallback) {
        String r = fallback;
        if (textNode != null) {
            r = textNode.getNodeValue();
        }
        return r;
    }

    public static boolean parseBoolean(String value, boolean fallback) {
        boolean r = fallback;
        try {
            r = Boolean.parseBoolean(value);
        } catch (Exception ex) {
            // ignore
        }
        return r;
    }

    public static boolean parseBooleanText(Node textNode, boolean fallback) {
        boolean r = fallback;
        if (textNode != null) {
            String value = textNode.getNodeValue();
            try {
                r = Boolean.parseBoolean(value);
            } catch (Exception ex) {
                // nothing
            }
        }
        return r;
    }

    public static float parseFloatText(Node textNode, float fallback) {
        float r = fallback;
        if (textNode != null) {
            String value = textNode.getNodeValue();
            try {
                r = Float.parseFloat(value);
            } catch (Exception ex) {
                // nothing
            }
        }
        return r;
    }

    public static int parseColorText(Node textNode, int fallback) {
        int r = fallback;
        if (textNode != null) {
            try {
                r = Color.parseColor(textNode.getNodeValue());
            } catch (Exception ex) {
                // ignore
            }
        }
        return r;
    }
}
