package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

public interface ModelControl extends MapControl {
    public final static int WIREFRAME = 0x01;
    public final static int TEXTURE = 0x02;

    public void setWireFrameColor(int color);
    public int getWireFrameColor();

    public void setVisible(int mask);
    public int getVisible();
}
