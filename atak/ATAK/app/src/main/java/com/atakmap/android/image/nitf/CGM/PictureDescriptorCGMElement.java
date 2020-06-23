
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum PictureDescriptorCGMElement {
    UNUSED_0(0),
    SCALING_MODE(1),
    COLOUR_SELECTION_MODE(2),
    LINE_WIDTH_SPECIFICATION_MODE(3),
    MARKER_SIZE_SPECIFICATION_MODE(4),
    EDGE_WIDTH_SPECIFICATION_MODE(5),
    VDC_EXTENT(6),
    BACKGROUND_COLOUR(7),
    DEVICE_VIEWPORT(8),
    DEVICE_VIEWPORT_SPECIFICATION_MODE(9),
    DEVICE_VIEWPORT_MAPPING(10),
    LINE_REPRESENTATION(11),
    MARKER_REPRESENTATION(12),
    TEXT_REPRESENTATION(13),
    FILL_REPRESENTATION(14),
    EDGE_REPRESENTATION(15),
    INTERIOR_STYLE_SPECIFICATION_MODE(16),
    LINE_AND_EDGE_TYPE_DEFINITION(17),
    HATCH_STYLE_DEFINITION(18),
    GEOMETRIC_PATTERN_DEFINITION(19),
    APPLICATION_STRUCTURE_DIRECTORY(20);

    private final int elementCode;

    PictureDescriptorCGMElement(int ec) {
        elementCode = ec;
    }

    public static PictureDescriptorCGMElement getElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    public String toString() {
        return name().concat("(").concat(String.valueOf(elementCode))
                .concat(")");
    }

}
