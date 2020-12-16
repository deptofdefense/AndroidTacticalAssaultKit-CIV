package android.net;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class Uri {
    private URI impl;
    private Map<String, String> queryParams;

    private Uri(URI impl) {
        if(impl == null)
            throw new NullPointerException();
        this.impl = impl;
        try {
            queryParams = splitQuery(this.impl);
        } catch(UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getScheme() {
        return impl.getScheme();
    }

    public String getPath() {
        return impl.getPath();
    }

    public String getQueryParameter(String param) {
        return  queryParams.get(param);
    }

    public String getHost() {
        return impl.getHost();
    }

    public String getFragment() {
        return impl.getFragment();
    }

    @Override
    public String toString() {
        return this.impl.toString();
    }

    public static Uri fromFile(File f) {
        return new Uri(f.toURI());
    }

    public static Uri parse(String s) {
        return new Uri(URI.create(s));
    }

    private static Map<String, String> splitQuery(URI url) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String query = url.getQuery();
        if(query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        }
        return query_pairs;
    }

    public String getLastPathSegment() {
        final String path = this.impl.getPath();
        if(path == null)
            return null;
        final int sepIdx = path.lastIndexOf('/');
        // if not found, `sepIdx` will be -1; add one to get substring starting
        // after last separator
        return path.substring(sepIdx+1);
    }
}
