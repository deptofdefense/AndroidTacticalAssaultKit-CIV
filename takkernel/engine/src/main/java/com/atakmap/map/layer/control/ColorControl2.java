package com.atakmap.map.layer.control;

public interface ColorControl2 extends ColorControl {
    enum Mode {
        /** content is grayscaled, then selected color is modulated with luminance value */
        Colorize,
        /** color values of content are modulated with selected color */
        Modulate,
        /** selected color replaces color values for content */
        Replace,
    };

    void setMode(Mode mode);
    Mode getMode();
}
