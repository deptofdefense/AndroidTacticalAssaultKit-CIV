package com.atakmap.map.layer.raster.service;

import com.atakmap.map.layer.Layer2;

/**
 * 
 * @author Developer
 */
public interface OnlineImageryExtension extends Layer2.Extension {

    public static interface OnOfflineOnlyModeChangedListener {
        public void onOfflineOnlyModeChanged(OnlineImageryExtension ext, boolean offlineOnly);
    }
    
    public static interface CacheRefreshListener {
        public void onManualRefreshRequested(OnlineImageryExtension ext);
        public void onAutoRefreshIntervalChanged(OnlineImageryExtension ext, long millis);
    }
    
    // offline only mode
    /**
     * Toggles offline-only mode. When in offline-only mode, the
     * {@link TileReader} should only pull tiles from the local cache, if one
     * exists.
     * 
     * @param offlineOnly   <code>true</code> if reader should be in
     *                      offline-only mode, <code>false</code> otherwise. 
     */
    public void setOfflineOnlyMode(boolean offlineOnly);
    
    /**
     * Returns a flag indicating whether or not the {@link TileReader} is in
     * offline-only mode. If <code>true</code>, the reader will only return
     * tiles from the local cache (if available). If <code>false</code>, the
     * reader may download tiles from the server as well as utilize an
     * available cache.
     * 
     * @return  <code>true</code> if the reader is in offline-only mode,
     *          <code>false</code> otherwise.
     */
    public boolean isOfflineOnlyMode();

    // cache refresh
    /**
     * Instructs the reader to service the next read for each tile to attempt
     * to download rather than using an existing cached tile.
     * 
     * <P>This method will have no practical effect if the reader is in
     * offline-only mode.
     */
    public void refreshCache();
    
    /**
     * Sets the cache refresh interval for the reader. When the refresh
     * interval is exceeded, the reader should download a new tile from the
     * server rather than utilizing a cached tile.
     * 
     * @param milliseconds  The refresh interval, in milliseconds. A value of
     *                      <code>0</code> indicates that auto-refresh is
     *                      disabled; use of values less than <code>0</code> is
     *                      undefined.
     */
    public void setCacheAutoRefreshInterval(long milliseconds);
    
    /**
     * Returns the auto-refresh interval for the cache.
     * 
     * @return  The refresh interval, in milliseconds. A value of <code>0</code>
     *          indicates that auto-refresh is disabled.
     */
    public long getCacheAutoRefreshInterval();
    
    public void addOnOfflineOnlyModeChangedListener(OnOfflineOnlyModeChangedListener l);
    public void removeOnOfflineOnlyModeChangedListener(OnOfflineOnlyModeChangedListener l);
    
    public void addCacheRefreshListener(CacheRefreshListener l);
    public void removeCacheRefreshListener(CacheRefreshListener l);
}
