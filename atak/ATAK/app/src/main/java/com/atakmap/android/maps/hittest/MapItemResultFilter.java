
package com.atakmap.android.maps.hittest;

import com.atakmap.android.maps.MapItem;
import com.atakmap.map.hittest.HitTestResultFilter;

/**
 * A {@link HitTestResultFilter} that only accepts map items
 */
public class MapItemResultFilter implements HitTestResultFilter {

    @Override
    public boolean acceptClass(Class<?> cl) {
        return MapItem.class.isAssignableFrom(cl);
    }

    @Override
    public boolean acceptSubject(Object subject) {
        return subject instanceof MapItem;
    }
}
