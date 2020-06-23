
package com.atakmap.android.widgets;

import com.atakmap.map.layer.AbstractLayer;

public class WidgetsLayer extends AbstractLayer {

    private final LayoutWidget root;

    public WidgetsLayer(String name, LayoutWidget root) {
        super(name);

        this.root = root;
    }

    public LayoutWidget getRoot() {
        return this.root;
    }
}
