
package com.atakmap.android.importexport;

import com.atakmap.coremap.cot.event.CotEvent;

public interface CotEventMarshal extends Marshal {
    String marshal(CotEvent event);
}
