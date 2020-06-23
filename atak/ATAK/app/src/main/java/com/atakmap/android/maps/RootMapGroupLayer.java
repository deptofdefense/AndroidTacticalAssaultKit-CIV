
package com.atakmap.android.maps;

import com.atakmap.map.layer.AbstractLayer;

public class RootMapGroupLayer extends AbstractLayer {

    private final RootMapGroup subject;

    public RootMapGroupLayer(RootMapGroup subject) {
        super("Map Items");

        this.subject = subject;
    }

    public RootMapGroup getSubject() {
        return this.subject;
    }
}
