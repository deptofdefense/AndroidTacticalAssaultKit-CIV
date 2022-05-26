
package gov.tak.platform.config;

import gov.tak.platform.graphics.Color;

import org.w3c.dom.Node;

import gov.tak.api.annotation.DeprecatedApi;

public class DataParser {

    static final int TYPE_NONE = 0;
    static final int TYPE_STRING = 1;
    static final int TYPE_BOOLEAN = 2;

    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    static int getDataType(Node node) {
        int type = TYPE_NONE;

        if (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
            String n = node.getNodeName();
            if (n.equals("string")) {
                type = TYPE_STRING;
            } else if (n.equals("boolean")) {
                type = TYPE_BOOLEAN;
            }
        }

        return type;
    }

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

    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    public static boolean parseBooleanElem(Node elemNode, boolean fallback) {
        boolean r = fallback;
        if (elemNode != null) {
            r = parseBooleanText(elemNode.getFirstChild(), fallback);
        }
        return r;
    }

    /**
     * Given an Object, get the boolean value
     *
     * @param value
     * @param fallback
     * @return
     */
    public static boolean getBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean)
            return ((Boolean) value).booleanValue();
        return parseBoolean(value.toString(), fallback);
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

    public static Object parseFloat(String textValue, float v) {
        try {
            return Float.parseFloat(textValue);
        } catch (Exception ignored) {
        }
        return v;
    }

    public static Object parseColor(String textValue, int i) {
        try {
            return Color.parseColor(textValue);
        } catch (Exception ignored) {
        }
        return i;
    }
}
