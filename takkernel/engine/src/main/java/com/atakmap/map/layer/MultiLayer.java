package com.atakmap.map.layer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.RandomAccess;
import java.util.Set;

/**
 * A layer composed of zero or more child layers. Child layers may be
 * programmatically added, removed and reordered.
 * 
 * @author Developer
 */
public class MultiLayer extends AbstractLayer {

    /**
     * Callback interface to provide notification on programmatic modification
     * of the layer stack.
     * 
     * @author Developer
     */
    public static interface OnLayersChangedListener {
        /**
         * Notifies the user when a layer has been added.
         * 
         * @param parent    The multi layer
         * @param layer     The layer that was added
         */
        public void onLayerAdded(MultiLayer parent, Layer layer);
        
        /**
         * Notifies the user when a layer has been removed.
         * 
         * @param parent    The multi layer
         * @param layer     The layer that was removed
         */
        public void onLayerRemoved(MultiLayer parent, Layer layer);
        
        /**
         * Notifies the user when the position of the layer has been explicitly
         * changed. This callback will <B>NOT</B> be invoked when a layer's
         * position changes due to the addition or removal of other layers.
         * 
         * @param parent        The multi layer
         * @param layer         The layer
         * @param oldPosition   The layer's old position
         * @param newPosition   The layer's new position
         */
        public void onLayerPositionChanged(MultiLayer parent, Layer layer, int oldPosition, int newPosition);
    }
    
    protected Set<OnLayersChangedListener> layersChangedListeners;
    protected List<Layer> layers;
    
    private VisibilityUpdater visibilityUpdater;

    /**
     * Creates a new instance with the specified name
     * 
     * @param name  The layer name
     */
    public MultiLayer(String name) {
        super(name);
        
        this.layers = new ArrayList<Layer>();
        this.layersChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnLayersChangedListener, Boolean>());
        
        this.visibilityUpdater = new VisibilityUpdater();
    }
    
    /**
     * Adds the specified layer.
     * 
     * @param layer The layer to be added
     */
    public synchronized void addLayer(Layer layer) {
        this.addLayerNoSync(this.layers.size(), layer);
    }
    
    /**
     * Adds the specified layer at the specified position.
     * 
     * @param position  The position. A position of <code>0</code> is the bottom
     *                  of the stack (will be rendered first); a position of
     *                  {@link #getNumLayers()} is the top of the stack (will be
     *                  rendered last.
     * @param layer     The layer to be added
     * 
     * @throws IndexOutOfBoundsException    if <code>index</code> is less than
     *                                      <code>0</code> or greater than
     *                                      {@link #getNumLayers()}.
     */
    public synchronized void addLayer(int position, Layer layer) {
        this.addLayerNoSync(position, layer);
    }
    
    protected void addLayerNoSync(int position, Layer layer) {
        this.layers.add(position, layer);
        this.dispatchOnLayerAddedNoSync(layer);
        
        layer.addOnLayerVisibleChangedListener(this.visibilityUpdater);
        if(!this.visible && layer.isVisible()) {
            this.visible = true;
            this.dispatchOnVisibleChangedNoSync();
        } else if(this.visible && this.layers.size() == 1 && !layer.isVisible()) {
            this.visible = false;
            this.dispatchOnVisibleChangedNoSync();
        }
    }
    
    /**
     * Removes the specified layer
     * 
     * @param layer The layer to be removed
     */
    public synchronized void removeLayer(Layer layer) {
        if(this.layers.remove(layer)) {
            this.dispatchOnLayerRemovedNoSync(Collections.singleton(layer));
            layer.removeOnLayerVisibleChangedListener(this.visibilityUpdater);
            this.updateVisibility();
        }
    }
    
    /**
     * Removes all layers.
     */
    public synchronized void removeAllLayers() {
        LinkedList<Layer> scratch = new LinkedList<Layer>();
        if(this.layers instanceof RandomAccess) {
            for(Layer l : this.layers) {
                l.removeOnLayerVisibleChangedListener(this.visibilityUpdater);
                scratch.add(l);
            }
            this.layers.clear();
        } else {
            Iterator<Layer> iter = this.layers.iterator();
            Layer l;
            while(iter.hasNext()) {
                l = iter.next();
                l.removeOnLayerVisibleChangedListener(this.visibilityUpdater);
                scratch.add(l);
                iter.remove();
            }
        }
        this.dispatchOnLayerRemovedNoSync(scratch);
        scratch.clear();
        
        this.updateVisibility();
    }
    
    /**
     * Sets the position of the layer in the stack. A position of <code>0</code>
     * is the bottom of the stack (will be rendered first); a position of
     * {@link #getNumLayers()}<code> - 1</code> is the top of the stack (will be
     * rendered last.
     * 
     * @param layer     The layer
     * @param position  The index
     * 
     * @throws IndexOutOfBoundsException    if <code>index</code> is less than
     *                                      <code>0</code> or greater than or
     *                                      equal to {@link #getNumLayers()}.
     */
    public synchronized void setLayerPosition(Layer layer, int position) {
        final int oldPos = this.layers.indexOf(layer);
        if(oldPos < 0)
            throw new IndexOutOfBoundsException();
        if(position == oldPos)
            return;

        this.layers.remove(oldPos);
        if(position > oldPos) {
            this.layers.add(position-1, layer);
        } else if(position < oldPos) {
            this.layers.add(position, layer);
        } else {
            throw new IllegalStateException();
        }
        
        this.dispatchOnLayerPositionChanged(layer, oldPos, position);
    }
    
    /**
     * Returns the number of layers in the stack.
     * 
     * @return  The number of layers
     */
    public synchronized int getNumLayers() {
        return this.layers.size();
    }
    
    /**
     * Returns the layer at the specified position in the stack.
     * 
     * @param i The index
     * 
     * @return  The layer at the specified index in the stack.
     * 
     * @throws IndexOutOfBoundsException    if <code>index</code> is less than
     *                                      <code>0</code> or greater than or
     *                                      equal to {@link #getNumLayers()}.
     */
    public synchronized Layer getLayer(int i) {
        return this.layers.get(i);
    }
    
    /**
     * Returns the current layer stack. Modification of the
     * {@link java.util.List List} returned will not result in modification of
     * the actual layer stack.
     * 
     * @return  The current layer stack.
     */
    public synchronized List<Layer> getLayers() {
        return new LinkedList<Layer>(this.layers);
    }
    
    /**
     * Adds the specified {@link OnLayersChangedListener}.
     * 
     * @param l The listener to be added.
     */
    public synchronized void addOnLayersChangedListener(OnLayersChangedListener l) {
        this.layersChangedListeners.add(l);
    }
    
    /**
     * Removes the specified {@link OnLayersChangedListener}.
     * 
     * @param l The listener to be removed.
     */
    public synchronized void removeOnLayersChangedListener(OnLayersChangedListener l) {
        this.layersChangedListeners.remove(l);
    }
    
    protected void dispatchOnLayerAddedNoSync(Layer layer) {
        for(OnLayersChangedListener listener : this.layersChangedListeners)
            listener.onLayerAdded(this, layer);
    }
    
    protected void dispatchOnLayerRemovedNoSync(Collection<Layer> layers) {
        for(Layer layer : layers)
            for(OnLayersChangedListener listener : this.layersChangedListeners)
                listener.onLayerRemoved(this, layer);
    }
    
    protected void dispatchOnLayerPositionChanged(Layer l, int oldPos, int newPos) {
        for(OnLayersChangedListener listener : this.layersChangedListeners)
            listener.onLayerPositionChanged(this, l, oldPos, newPos);
    }
    
    protected synchronized void updateVisibility() {
        // XXX - we will strictly adhere to visibility being true only if one or
        //       more child layers are visible
        boolean childrenVisible = false;
        for(Layer l : this.layers)
            childrenVisible |= l.isVisible();
        
        if(childrenVisible != this.visible) {
            this.visible = childrenVisible;
            this.dispatchOnVisibleChangedNoSync();
        }
    }
    
    /**************************************************************************/
    // Abstract Layer
    
    @Override
    public synchronized void setVisible(boolean visible) {
        for(Layer l : this.layers) {
            l.removeOnLayerVisibleChangedListener(this.visibilityUpdater);
            l.setVisible(visible);
            l.addOnLayerVisibleChangedListener(this.visibilityUpdater);
        }
        this.updateVisibility();
    }
    
    /**************************************************************************/
    
    private class VisibilityUpdater implements OnLayerVisibleChangedListener {

        @Override
        public void onLayerVisibleChanged(Layer layer) {
            MultiLayer.this.updateVisibility();
        }
    }
}
