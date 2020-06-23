
package com.atakmap.android.maps;

import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MultiLayer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class LayerBinLayer extends MultiLayer {

    private final Map<String, StackLayer> bins;

    public LayerBinLayer(String name) {
        super(name);

        this.bins = new HashMap<>();
    }

    public synchronized MultiLayer addLayerBin(String name) {
        if (this.bins.containsKey(name))
            throw new IllegalArgumentException();
        final StackLayer retval = new StackLayer(name, new MultiLayer(name));
        this.bins.put(name, retval);
        super.addLayer(retval);
        return (MultiLayer) retval.get();
    }

    public synchronized MultiLayer addLayerBin(int position, String name) {
        if (this.bins.containsKey(name))
            throw new IllegalArgumentException();
        final StackLayer retval = new StackLayer(name, new MultiLayer(name));
        this.bins.put(name, retval);
        super.addLayer(position, retval);
        return (MultiLayer) retval.get();
    }

    public synchronized boolean removeBin(String name) {
        final StackLayer bin = this.bins.get(name);
        if (bin == null)
            return false;
        super.removeLayer(bin);
        return true;
    }

    public synchronized MultiLayer getBin(String name) {
        final StackLayer retval = this.bins.get(name);
        if (retval == null)
            return null;
        return (MultiLayer) retval.get();
    }

    public synchronized MultiLayer pushBin(String name) {
        final StackLayer bin = this.bins.get(name);
        if (bin == null)
            throw new IllegalArgumentException();
        bin.push(new MultiLayer(name));
        return (MultiLayer) bin.get();
    }

    public synchronized MultiLayer popBin(String name) {
        final StackLayer bin = this.bins.get(name);
        if (bin == null)
            throw new IllegalArgumentException();
        bin.pop();
        return (MultiLayer) bin.get();
    }

    public synchronized Set<String> getBinNames() {
        LinkedHashSet<String> retval = new LinkedHashSet<>();
        for (Layer l : this.layers) {
            retval.add(l.getName());
        }
        return retval;
    }

    public synchronized int getNumBins() {
        return this.bins.size();
    }

    public synchronized int getBinStackSize() {
        final StackLayer bin = this.bins.get(name);
        if (bin == null)
            throw new IllegalArgumentException();
        return bin.size();
    }

    @Override
    public void addLayer(Layer layer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLayer(int position, Layer layer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeLayer(Layer layer) {
        throw new UnsupportedOperationException();
    }

}
