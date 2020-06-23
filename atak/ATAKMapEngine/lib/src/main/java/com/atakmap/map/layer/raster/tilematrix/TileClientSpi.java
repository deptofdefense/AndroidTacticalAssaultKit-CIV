package com.atakmap.map.layer.raster.tilematrix;

public interface TileClientSpi {
    public static class Options {
        public final static Options defaults = new Options(1L, 3000L);

        public long dnsLookupTimeout;
        public long connectTimeout;

        public Options() {
            dnsLookupTimeout = defaults.dnsLookupTimeout;
            connectTimeout = defaults.connectTimeout;
        }
        
        private Options(long dnsLookupTimeout, long connectTimeout) {
            this.dnsLookupTimeout = dnsLookupTimeout;
            this.connectTimeout = connectTimeout;
        }
    }

    
    public String getName();
    public TileClient create(String path, String offlineCachePath, Options opts);
    public int getPriority();
}
