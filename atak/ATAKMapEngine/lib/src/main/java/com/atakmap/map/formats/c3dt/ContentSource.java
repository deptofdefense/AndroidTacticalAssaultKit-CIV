package com.atakmap.map.formats.c3dt;

public interface ContentSource {
    interface OnContentChangedListener {
        void onContentChanged(ContentSource client);
    }

    byte[] getData(String uri, long[] version);
    void addOnContentChangedListener(OnContentChangedListener l);
    void removeOnContentChangedListener(OnContentChangedListener l);

    void connect();
    void disconnect();
}
