
package com.atakmap.android.util;

import android.os.Bundle;
import android.os.Parcelable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

public class BundleUtils {

    private static final String TAG = "BundleUtils";

    private static String sanitize(String s) {
        if (FileSystemUtils.isEmpty(s))
            return s;

        return s.replaceAll("\n", "\\\\n\\\\t").replaceAll("\t", "")
                .replaceAll("\"", "'");
    }

    public static String bundleToString(Bundle bundle) {
        return bundleToString(bundle, null);
    }

    /**
     * Given a buffer, produce a properly formatted JSON output
     * @param bundle the bundle to convert to JSON
     * @param filter optional list of keys to skip
    */
    public static String bundleToString(Bundle bundle, List<String> filter) {
        StringBuilder out = new StringBuilder();

        if (bundle != null) {
            boolean first = true;
            for (String key : bundle.keySet()) {
                if (filter != null && filter.contains(key)) {
                    Log.d(TAG, "bundleToString skipping: " + key);
                    continue;
                }

                if (!first) {
                    out.append(", ");
                }

                out.append("\"").append(sanitize(key)).append("\"").append(':')
                        .append("\"");

                Object value = bundle.get(key);
                String s;

                if (value instanceof Bundle) {
                    s = "{" + bundleToString((Bundle) value, filter) + "}";
                } else if (value instanceof Bundle[]) {
                    final Bundle[] bundles = (Bundle[]) value;
                    final String[] strBundles = new String[bundles.length];
                    for (int i = 0; i < bundles.length; ++i) {
                        strBundles[i] = "{" + bundleToString(bundles[i], filter)
                                + "}";
                    }
                    s = Arrays.toString(strBundles);
                } else if (value instanceof int[]) {
                    s = Arrays.toString((int[]) value);
                } else if (value instanceof byte[]) {
                    s = Arrays.toString((byte[]) value);
                } else if (value instanceof boolean[]) {
                    s = Arrays.toString((boolean[]) value);
                } else if (value instanceof short[]) {
                    s = Arrays.toString((short[]) value);
                } else if (value instanceof long[]) {
                    s = Arrays.toString((long[]) value);
                } else if (value instanceof float[]) {
                    s = Arrays.toString((float[]) value);
                } else if (value instanceof double[]) {
                    s = Arrays.toString((double[]) value);
                } else if (value instanceof String[]) {
                    s = Arrays.toString((String[]) value);
                } else if (value instanceof CharSequence[]) {
                    s = Arrays.toString((CharSequence[]) value);
                } else if (value instanceof Parcelable[]) {
                    s = Arrays.toString((Parcelable[]) value);
                } else if (value instanceof Exception) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    ((Exception) value).printStackTrace(pw);
                    String trace = sw.toString();
                    // sanitize the string for usage within json
                    s = sanitize(trace);
                } else {
                    s = String.valueOf(value);
                }

                if (s == null)
                    s = "";
                out.append(sanitize(s)).append("\"");

                first = false;
            }
        }

        return out.toString();
    }

}
