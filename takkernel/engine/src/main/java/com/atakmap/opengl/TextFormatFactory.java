package com.atakmap.opengl;

import com.atakmap.android.maps.MapTextFormat;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface TextFormatFactory {
    /**
     *
     * @param isBold Flag indicating if the typeface is bold.
     * @param isItalic Flag indicating if the typeface is italic.
     * @param fontSize Font size.
     * @return MapTextFormat object with default typeface and characteristics determined by input parameters.
     */
    MapTextFormat createTextFormat(boolean isBold, boolean isItalic, int fontSize, boolean isUnderline, boolean isStrikethrough);
}
