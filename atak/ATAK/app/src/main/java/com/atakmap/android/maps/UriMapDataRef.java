
package com.atakmap.android.maps;

public final class UriMapDataRef extends MapDataRef {
    private final String _uri;

    public UriMapDataRef(String uri) {
        _uri = uri;
    }

    @Override
    public String toUri() {
        return _uri;
    }
}
