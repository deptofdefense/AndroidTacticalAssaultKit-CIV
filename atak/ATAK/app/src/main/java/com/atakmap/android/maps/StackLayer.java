
package com.atakmap.android.maps;

import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.ProxyLayer;

import java.util.LinkedList;

public class StackLayer extends ProxyLayer {

    protected final LinkedList<Layer> stack;

    public StackLayer(String name, Layer base) {
        super(name, base);

        this.stack = new LinkedList<>();
    }

    public synchronized void push(Layer layer) {
        if (layer == null)
            throw new NullPointerException();
        this.stack.addLast(this.subject);
        super.set(layer);
    }

    public synchronized void pop() {
        if (this.stack.size() < 1)
            throw new IllegalStateException();
        super.set(this.stack.removeLast());
    }

    public synchronized int size() {
        return this.stack.size() + 1;
    }

    /**************************************************************************/
    // Proxy Layer

    @Override
    public synchronized void set(Layer layer) {
        if (layer == null)
            throw new NullPointerException();
        super.set(layer);
    }
}
