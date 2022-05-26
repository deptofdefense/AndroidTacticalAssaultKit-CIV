package com.atakmap.map.layer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public abstract class AbstractLayer implements Layer2 {

    protected Set<OnLayerVisibleChangedListener> layerVisibleChangedListeners;
    protected String name;
    protected boolean visible;
    private Set<Extension> extensions;
    
    protected AbstractLayer(String name) {
        this.name = name;
        this.visible = true;
        this.layerVisibleChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnLayerVisibleChangedListener, Boolean>());
        this.extensions = Collections.newSetFromMap(new IdentityHashMap<Extension, Boolean>());
    }
    
    /**
     * Registers the specified extension with the layer.
     * 
     * <P>This method should <B>ONLY</B> be invoked from within the constructor.
     * 
     * @param e The extension to be registered.
     */
    protected void registerExtension(Extension e) {
        this.extensions.add(e);
    }
    
    protected void setName(String name) {
        this.name = name;
    }

    @Override
    public synchronized void setVisible(boolean visible) {
        this.visible = visible;
        this.dispatchOnVisibleChangedNoSync();
    }

    @Override
    public synchronized boolean isVisible() {
        return this.visible;
    }

    @Override
    public synchronized void addOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
        this.layerVisibleChangedListeners.add(l);
    }

    @Override
    public synchronized void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
        this.layerVisibleChangedListeners.remove(l);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public synchronized <T extends Extension> T getExtension(Class<T> clazz) {
        for(Extension e : this.extensions)
            if(clazz.isAssignableFrom(e.getClass()))
                return clazz.cast(e);
        return null;
    }

    protected void dispatchOnVisibleChangedNoSync() {
        for(OnLayerVisibleChangedListener l : this.layerVisibleChangedListeners)
            l.onLayerVisibleChanged(this);
    }
}
