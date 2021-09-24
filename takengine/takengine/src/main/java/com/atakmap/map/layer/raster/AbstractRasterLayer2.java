package com.atakmap.map.layer.raster;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.log.Log;
import com.atakmap.lang.Objects;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.raster.service.SelectionOptionsCallbackExtension;
import com.atakmap.math.MathUtils;

public abstract class AbstractRasterLayer2 extends AbstractLayer implements RasterLayer2 {

    private final static String TAG = "AbstractRasterLayer2";

    protected Set<OnSelectionChangedListener> selectionChangedListeners;
    protected Set<OnPreferredProjectionChangedListener> preferredProjectionChangedListeners;
    protected Set<OnSelectionVisibleChangedListener> selectionVisibleChangedListeners;
    protected Set<OnSelectionTransparencyChangedListener> selectionTransparencyChangedListeners;
    
    protected String selection;
    protected String autoselectValue;
    protected Set<String> invisibleSelections;
    protected Map<String, Float> selectionTransparency;
    private SelectionOptionsChangedCallbackImpl selectionOptionsImpl;

    protected AbstractRasterLayer2(String name) {
        super(name);
        
        this.selectionChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnSelectionChangedListener, Boolean>());
        this.preferredProjectionChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnPreferredProjectionChangedListener, Boolean>());
        this.selectionVisibleChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnSelectionVisibleChangedListener, Boolean>());
        this.selectionTransparencyChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnSelectionTransparencyChangedListener, Boolean>());
        
        this.selection = null;
        this.autoselectValue = null;
        
        this.invisibleSelections = new HashSet<String>();
        this.selectionTransparency = new HashMap<String, Float>();
        
        this.selectionOptionsImpl = new SelectionOptionsChangedCallbackImpl();
        
        this.registerExtension(this.selectionOptionsImpl);
    }

    protected void dispatchOnSelectionOptionsChanged() {
        this.selectionOptionsImpl.dispatchOnSelectionOptionsChanged();
    }

    @Override
    public synchronized void setSelection(String type) {
        Log.d(TAG, "[" + getName() + "] SET SELECTION " + type);
        this.setSelectionImpl(type);

        this.dispatchOnSelectionChangedNoSync();
    }
    
    protected void setSelectionImpl(String type) {
        this.selection = type;
    }

    /**
     * Sets the auto-select value.  This method <B>must</B> be externally
     * synchronized while holding the lock on <code>this</code>.
     * 
     * @param autoselect    The new auto-select value
     */
    protected void setAutoSelectValueNoSync(String autoselect) {
        boolean dispatchSelectionChanged = (this.selection == null);
        if(!Objects.equals(autoselect, this.autoselectValue)) {
            this.autoselectValue = autoselect;
        } else {
            dispatchSelectionChanged = false;
        }
        if(dispatchSelectionChanged)
            this.dispatchOnSelectionChangedNoSync();
    }
    

    @Override
    public synchronized String getSelection() {
        if(!this.isAutoSelect())
            return this.selection;
        else
            return this.autoselectValue;
    }

    @Override
    public synchronized boolean isAutoSelect() {
        return (this.selection == null);
    }

    @Override
    public synchronized void addOnSelectionChangedListener(OnSelectionChangedListener l) {
        this.selectionChangedListeners.add(l);
    }

    @Override
    public synchronized void removeOnSelectionChangedListener(OnSelectionChangedListener l) {
        this.selectionChangedListeners.remove(l);
    }

    protected void dispatchOnSelectionChangedNoSync() {
        for(OnSelectionChangedListener l : this.selectionChangedListeners)
            l.onSelectionChanged(this);
    }

    @Override
    public synchronized void addOnPreferredProjectionChangedListener(OnPreferredProjectionChangedListener l) {
        this.preferredProjectionChangedListeners.add(l);
    }

    @Override
    public synchronized void removeOnPreferredProjectionChangedListener(OnPreferredProjectionChangedListener l) {
        this.preferredProjectionChangedListeners.remove(l);
    }
    
    protected void dispatchOnPreferredProjectionChangedNoSync() {
        for(OnPreferredProjectionChangedListener l : this.preferredProjectionChangedListeners)
            l.onPreferredProjectionChanged(this);
    }
    
    // Selection Visibility

    @Override
    public synchronized void setVisible(String selection, boolean visible) {
        if(visible)
            this.invisibleSelections.remove(selection);
        else
            this.invisibleSelections.add(selection);
        this.dispatchOnSelectionVisibleChangedNoSync();
    }

    @Override
    public synchronized boolean isVisible(String selection) {
        return !this.invisibleSelections.contains(selection);
    }
    
    @Override
    public synchronized void addOnSelectionVisibleChangedListener(OnSelectionVisibleChangedListener l) {
        this.selectionVisibleChangedListeners.add(l);
    }
    
    @Override
    public synchronized void removeOnSelectionVisibleChangedListener(OnSelectionVisibleChangedListener l) {
        this.selectionVisibleChangedListeners.remove(l);
    }
    
    protected void dispatchOnSelectionVisibleChangedNoSync() {
        for(OnSelectionVisibleChangedListener l : this.selectionVisibleChangedListeners)
            l.onSelectionVisibleChanged(this);
    }
    
    // Selection Transparency
    
    @Override
    public synchronized float getTransparency(String selection) {
        final Float value = this.selectionTransparency.get(selection);
        if(value == null)
            return 1f;
        return value.floatValue();
    }

    @Override
    public synchronized void setTransparency(String selection, float value) {
        this.selectionTransparency.put(selection, Float.valueOf(MathUtils.clamp(value, 0f, 1f)));
        for(OnSelectionTransparencyChangedListener l : this.selectionTransparencyChangedListeners)
            l.onTransparencyChanged(this);
    }

    @Override
    public synchronized void addOnSelectionTransparencyChangedListener(OnSelectionTransparencyChangedListener l) {
        this.selectionTransparencyChangedListeners.add(l);
    }

    @Override
    public void removeOnSelectionTransparencyChangedListener(OnSelectionTransparencyChangedListener l) {
        this.selectionTransparencyChangedListeners.remove(l);
    }
    
    /**************************************************************************/
    
    private class SelectionOptionsChangedCallbackImpl implements SelectionOptionsCallbackExtension {

        private Set<OnSelectionOptionsChangedListener> selectionOptionsChangedListeners;
        
        public SelectionOptionsChangedCallbackImpl() {
            this.selectionOptionsChangedListeners = Collections.<OnSelectionOptionsChangedListener>newSetFromMap(new IdentityHashMap<OnSelectionOptionsChangedListener, Boolean>());
        }

        synchronized void dispatchOnSelectionOptionsChanged() {
            for(OnSelectionOptionsChangedListener l : this.selectionOptionsChangedListeners)
                l.onSelectionOptionsChanged(AbstractRasterLayer2.this);
        }

        @Override
        public synchronized void addOnSelectionOptionsChangedListener(OnSelectionOptionsChangedListener l) {
            this.selectionOptionsChangedListeners.add(l);
        }

        @Override
        public synchronized void removeOnSelectionOptionsChangedListener(OnSelectionOptionsChangedListener l) {
            this.selectionOptionsChangedListeners.remove(l);
        }
    }
}
