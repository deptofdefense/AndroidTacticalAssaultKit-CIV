
package org.json;

import com.atakmap.annotations.DeprecatedApi;

import java.lang.reflect.Array;

@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class JSONUtil {

    /**
     * Given an object, produce an encoded string, where the first character is
     * { B = Boolean, b = byte, C = Character, D = Double,
     *   F = Float, L = Long, I = Integer, s = short, S = String }
     * followed by the stringified representation in the remaining
     * part of the string.
     */
    private static String encode(final Object o) {
        if (o instanceof Boolean)
            return "B" + o;
        else if (o instanceof Byte)
            return "b" + o;
        else if (o instanceof Character)
            return "C" + o;
        else if (o instanceof Double)
            return "D" + o;
        else if (o instanceof Float)
            return "F" + o;
        else if (o instanceof Long)
            return "L" + o;
        else if (o instanceof Integer)
            return "I" + o;
        else if (o instanceof Short)
            return "s" + o;
        else if (o instanceof String)
            return "S" + o;
        else
            return null;
    }

    /**
     * Given an encoded string, where the first character is 
     * { B = Boolean, b = byte, C = Character, D = Double,
     *   F = Float, L = Long, I = Integer, s = short, S = String }
     * followed by the stringified representation in the remaining
     * part of the string.
     * @returns null if there was an invalid encoding encountered in the 
     * first character or the object is not a string.
     *
     * Note: Encoding values are being used because JSON will always
     * attempt to decode a numeric value in the smallest representation
     * possible.   For example a long that fits in a int representation 
     * will be returned as an int.
     */
    private static Object decode(final Object s) {
        if (s instanceof String) {
            final char key = ((String) s).charAt(0);
            final String val = ((String) s).substring(1);
            try {
                switch (key) {
                    case 'B':
                        return Boolean.valueOf(val);
                    case 'b':
                        return Byte.valueOf(val);
                    case 'C':
                        return val.charAt(0);
                    case 'D':
                        return Double.valueOf(val);
                    case 'F':
                        return Float.valueOf(val);
                    case 'L':
                        return Long.valueOf(val);
                    case 'I':
                        return Integer.valueOf(val);
                    case 's':
                        return Short.valueOf(val);
                    case 'S':
                        return val;
                    default:
                        return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Given a JSONObject and a key, unwrap the object using the decoding 
     * rules.    If the decode is unsusccesful, return null.
     * This is strictly a convienence method.
     */
    public static Object unwrap(final JSONObject o, final String key) {
        try {
            String val = o.getString(key);
            return decode(val);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Given a JSONObject that has been previously wrapped using
     * the method from this class, restore the value to either 
     * the coresponding primitive or array of primitives.
     * @return the coresponding primitive or array of primitives, null 
     * if the value could not be unwrapped.
     */
    public static Object unwrap(Object o) {

        if (o instanceof JSONArray) {
            JSONArray jsa = (JSONArray) o;
            int length = jsa.length();
            if (length == 0)
                return null;

            Object rawv = jsa.opt(0);
            if (rawv instanceof String) {
                String s = (String) rawv;
                if (s.length() > 0) {
                    Object v = decode(rawv);
                    if (v == null)
                        return null;
                    if (v instanceof Integer) {
                        int[] retval = new int[length];
                        for (int i = 0; i < length; ++i)
                            retval[i] = (Integer) decode(jsa.opt(i));
                        return retval;
                    } else if (v instanceof Double) {
                        double[] retval = new double[length];
                        for (int i = 0; i < length; ++i)
                            retval[i] = (Double) decode(jsa.opt(i));
                        return retval;
                    } else if (v instanceof Boolean) {
                        boolean[] retval = new boolean[length];
                        for (int i = 0; i < length; ++i)
                            retval[i] = (Boolean) decode(jsa.opt(i));
                        return retval;
                    } else if (v instanceof Long) {
                        long[] retval = new long[length];
                        for (int i = 0; i < length; ++i)
                            retval[i] = (Long) decode(jsa.opt(i));
                        return retval;
                    } else if (v instanceof Byte) {
                        byte[] retval = new byte[length];
                        for (int i = 0; i < length; ++i)
                            retval[i] = (Byte) decode(jsa.opt(i));
                        return retval;
                    } else if (v instanceof Short) {
                        short[] retval = new short[length];
                        for (int i = 0; i < length; ++i)
                            retval[i] = (Short) decode(jsa.opt(i));
                        return retval;
                    } else if (v instanceof String) {
                        String[] retval = new String[length];
                        for (int i = 0; i < length; ++i)
                            retval[i] = (String) decode(jsa.opt(i));
                        return retval;
                    }
                }
            }

        }
        if (o instanceof String)
            return decode(o);
        else
            return o;
    }

    /*
     * Wraps the given object if necessary and produces either a 
     * JSONObject or JSONArray encoded so that it can be decoded to 
     * the appropriate primitive or array of primitives.
     */
    public static Object wrap(final Object o) {
        try {
            if (o.getClass().isArray()) {
                JSONArray jsa = new JSONArray();
                int length = Array.getLength(o);
                for (int i = 0; i < length; i += 1) {
                    Object val = wrap(Array.get(o, i));
                    if (val == null)
                        return null;

                    jsa.put(val);
                }
                return jsa;
            }

            return encode(o);

        } catch (Exception ignored) {
            return null;
        }
    }
}
