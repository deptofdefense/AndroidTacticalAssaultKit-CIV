package com.atakmap.map.layer.raster.service;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class DefaultOnlineImageryExtension implements OnlineImageryExtension {
    private boolean offlineOnlyMode;
    private long cacheAutoRefreshInterval;
    private Set<OnOfflineOnlyModeChangedListener> offlineOnlyModeChangedListeners;
    private Set<CacheRefreshListener> cacheRefreshListeners;
    
    public DefaultOnlineImageryExtension() {
        this(false, 0L);
    }
    
    public DefaultOnlineImageryExtension(boolean offlineOnlyMode, long cacheAutoRefreshInterval) {
        this.offlineOnlyMode = offlineOnlyMode;
        this.cacheAutoRefreshInterval = cacheAutoRefreshInterval;
        
        this.offlineOnlyModeChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnOfflineOnlyModeChangedListener, Boolean>());
        this.cacheRefreshListeners = Collections.newSetFromMap(new IdentityHashMap<CacheRefreshListener, Boolean>());
    }
    
    @Override
    public synchronized void setOfflineOnlyMode(boolean offlineOnly) {
        if(offlineOnly != this.offlineOnlyMode) {
            this.offlineOnlyMode = offlineOnly;
            for(OnOfflineOnlyModeChangedListener l : this.offlineOnlyModeChangedListeners)
                l.onOfflineOnlyModeChanged(this, offlineOnly);
        }
    }

    @Override
    public synchronized boolean isOfflineOnlyMode() {
        return this.offlineOnlyMode;
    }

    @Override
    public synchronized void refreshCache() {
        for(CacheRefreshListener l : this.cacheRefreshListeners)
            l.onManualRefreshRequested(this);
    }

    @Override
    public synchronized void setCacheAutoRefreshInterval(long milliseconds) {
        if(milliseconds != this.cacheAutoRefreshInterval) {
            this.cacheAutoRefreshInterval = milliseconds;
            for(CacheRefreshListener l : this.cacheRefreshListeners)
                l.onAutoRefreshIntervalChanged(this, this.cacheAutoRefreshInterval);
        }
    }

    @Override
    public synchronized long getCacheAutoRefreshInterval() {
        return this.cacheAutoRefreshInterval;
    }

    @Override
    public synchronized void addOnOfflineOnlyModeChangedListener(OnOfflineOnlyModeChangedListener l) {
        this.offlineOnlyModeChangedListeners.add(l);
    }

    @Override
    public synchronized void removeOnOfflineOnlyModeChangedListener(OnOfflineOnlyModeChangedListener l) {
        this.offlineOnlyModeChangedListeners.remove(l);
    }

    @Override
    public synchronized void addCacheRefreshListener(CacheRefreshListener l) {
        this.cacheRefreshListeners.add(l);
    }

    @Override
    public synchronized void removeCacheRefreshListener(CacheRefreshListener l) {
        this.cacheRefreshListeners.remove(l);
    }
}
