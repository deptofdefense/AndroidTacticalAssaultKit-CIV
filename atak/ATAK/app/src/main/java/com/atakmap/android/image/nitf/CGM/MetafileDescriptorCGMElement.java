
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum MetafileDescriptorCGMElement {
    UNUSED_0(0),
    METAFILE_VERSION(1),
    METAFILE_DESCRIPTION(2),
    VDC_TYPE(3),
    INTEGER_PRECISION(4),
    REAL_PRECISION(5),
    INDEX_PRECISION(6),
    COLOUR_PRECISION(7),
    COLOUR_INDEX_PRECISION(8),
    MAXIMUM_COLOUR_INDEX(9),
    COLOUR_VALUE_EXTENT(10),
    METAFILE_ELEMENT_LIST(11),
    METAFILE_DEFAULTS_REPLACEMENT(12),
    FONT_LIST(13),
    CHARACTER_SET_LIST(14),
    CHARACTER_CODING_ANNOUNCER(15),
    NAME_PRECISION(16),
    MAXIMUM_VDC_EXTENT(17),
    SEGMENT_PRIORITY_EXTENT(18),
    COLOUR_MODEL(19),
    COLOUR_CALIBRATION(20),
    FONT_PROPERTIES(21),
    GLYPH_MAPPING(22),
    SYMBOL_LIBRARY_LIST(23),
    PICTURE_DIRECTORY(24);

    private final int elementCode;

    MetafileDescriptorCGMElement(int ec) {
        elementCode = ec;
    }

    public static MetafileDescriptorCGMElement getElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    public int getElementCode() {
        return elementCode;
    }

    public String toString() {
        return name().concat("(").concat(String.valueOf(elementCode))
                .concat(")");
    }
}
