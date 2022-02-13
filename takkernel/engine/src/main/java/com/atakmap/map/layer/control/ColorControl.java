package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

/**
 * Applies specified color modulation to the content.
 * 
 * @author Developer
 */
public interface ColorControl extends MapControl {
    public void setColor(int color);
    public int getColor();
}
