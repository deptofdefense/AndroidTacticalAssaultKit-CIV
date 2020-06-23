
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum ControlCGMElement {
    UNUSED_0(0),
    VDC_INTEGER_PRECISION(1),
    VDC_REAL_PRECISION(2),
    AUXILIARY_COLOUR(3),
    TRANSPARENCY(4),
    CLIP_RECTANGLE(5),
    CLIP_INDICATOR(6),
    LINE_CLIPPING_MODE(7),
    MARKER_CLIPPING_MODE(8),
    EDGE_CLIPPING_MODE(9),
    NEW_REGION(10),
    SAVE_PRIMITIVE_CONTEXT(11),
    RESTORE_PRIMITIVE_CONTEXT(12),
    UNUSED_13(13),
    UNUSED_14(14),
    UNUSED_15(15),
    UNUSED_16(16),
    PROTECTION_REGION_INDICATOR(17),
    GENERALIZED_TEXT_PATH_MODE(18),
    MITRE_LIMIT(19),
    TRANSPARENT_CELL_COLOUR(20);

    private final int elementCode;

    ControlCGMElement(int ec) {
        elementCode = ec;
    }

    public static ControlCGMElement getElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    public String toString() {
        return name().concat("(").concat(String.valueOf(elementCode))
                .concat(")");
    }
}
