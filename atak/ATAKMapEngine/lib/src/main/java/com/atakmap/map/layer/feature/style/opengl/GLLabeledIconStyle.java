package com.atakmap.map.layer.feature.style.opengl;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class GLLabeledIconStyle extends GLCompositeStyle {

    public final static GLStyleSpi SPI = new GLStyleSpi() {

        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLStyle create(Pair<Style, Geometry> object) {
            final Style s = object.first;
            final Geometry g = object.second;
            if(s == null || g == null)
                return null;
            if(!(s instanceof CompositeStyle))
                return null;

            final CompositeStyle cs = (CompositeStyle)s;
            if(cs.getNumStyles() != 2)
                return null;
            
            IconPointStyle is = (IconPointStyle)CompositeStyle.find(cs, IconPointStyle.class);
            LabelPointStyle ls = (LabelPointStyle)CompositeStyle.find(cs, LabelPointStyle.class);
            if(is == null || ls == null)
                return null;
            
            return new GLLabeledIconStyle((CompositeStyle)s, is, ls);
        }
    };
    
    private final IconPointStyle iconStyle;
    private final LabelPointStyle labelStyle;

    public GLLabeledIconStyle(CompositeStyle style, IconPointStyle iconStyle, LabelPointStyle labelStyle) {
        super(style);
        
        this.iconStyle = iconStyle;
        this.labelStyle = labelStyle;
    }

}
