package com.atakmap.map.layer.control;

import java.util.Set;

import android.util.Pair;

import com.atakmap.map.MapControl;

public interface AttributionControl extends MapControl {
    public static interface OnAttributionUpdatedListener {
        public void onAttributionUpdated(AttributionControl control);
    }
    
    /**
     * Returns the attribution for the content. Pairs are ordered
     * <I>content-type : attribution-text</I>
     * 
     * @return  Returns the attribution for the content. Pairs are ordered
     *          <I>content-type : attribution-text</I>
     */
    public Set<Pair<String, String>> getContentAttribution();
    
    public void addOnAttributionUpdatedListener(OnAttributionUpdatedListener l);
    public void removeOnAttributionUpdatedListener(OnAttributionUpdatedListener l);
}
