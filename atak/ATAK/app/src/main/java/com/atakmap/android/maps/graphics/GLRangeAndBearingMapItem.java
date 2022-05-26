
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.hittest.HitTestResult;

/**
 * GL override for R&B line - currently only used for hit testing correction
 */
public class GLRangeAndBearingMapItem extends GLArrow2 {

    protected final RangeAndBearingMapItem _subject;

    public GLRangeAndBearingMapItem(MapRenderer surface,
            RangeAndBearingMapItem arrow) {
        super(surface, arrow);
        _subject = arrow;
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        HitTestResult result = super.hitTestImpl(renderer, params);
        if (result == null || result.type != HitTestResult.Type.POINT)
            return result;

        // Redirect to appropriate end point
        PointMapItem hit;
        if (result.index == 0)
            hit = _subject.getPoint1Item();
        else
            hit = _subject.getPoint2Item();

        return new HitTestResult(hit, hit.getPoint());
    }
}
