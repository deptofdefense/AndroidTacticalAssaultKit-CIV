
package com.atakmap.android.cot;

import com.atakmap.android.importexport.AbstractCotEventMarshal;
import com.atakmap.coremap.cot.event.CotEvent;

class GenericCotEventMarshal extends AbstractCotEventMarshal {

    GenericCotEventMarshal() {
        super("cot");
    }

    @Override
    protected boolean accept(CotEvent event) {
        return true;
    }

    @Override
    public int getPriorityLevel() {
        return 1;
    }
}
