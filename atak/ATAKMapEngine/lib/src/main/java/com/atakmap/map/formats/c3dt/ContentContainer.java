package com.atakmap.map.formats.c3dt;

public interface ContentContainer extends ContentSource {
    void put(String uri, byte[] data, long version);
}
