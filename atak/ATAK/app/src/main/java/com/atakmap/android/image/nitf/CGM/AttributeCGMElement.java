
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum AttributeCGMElement {
    UNUSED_0(0),
    LINE_BUNDLE_INDEX(1),
    LINE_TYPE(2),
    LINE_WIDTH(3),
    LINE_COLOUR(4),
    MARKER_BUNDLE_INDEX(5),
    MARKER_TYPE(6),
    MARKER_SIZE(7),
    MARKER_COLOUR(8),
    TEXT_BUNDLE_INDEX(9),
    TEXT_FONT_INDEX(10),
    TEXT_PRECISION(11),
    CHARACTER_EXPANSION_FACTOR(12),
    CHARACTER_SPACING(13),
    TEXT_COLOUR(14),
    CHARACTER_HEIGHT(15),
    CHARACTER_ORIENTATION(16),
    TEXT_PATH(17),
    TEXT_ALIGNMENT(18),
    CHARACTER_SET_INDEX(19),
    ALTERNATE_CHARACTER_SET_INDEX(20),
    FILL_BUNDLE_INDEX(21),
    INTERIOR_STYLE(22),
    FILL_COLOUR(23),
    HATCH_INDEX(24),
    PATTERN_INDEX(25),
    EDGE_BUNDLE_INDEX(26),
    EDGE_TYPE(27),
    EDGE_WIDTH(28),
    EDGE_COLOUR(29),
    EDGE_VISIBILITY(30),
    FILL_REFERENCE_POINT(31),
    PATTERN_TABLE(32),
    PATTERN_SIZE(33),
    COLOUR_TABLE(34),
    ASPECT_SOURCE_FLAGS(35),
    PICK_IDENTIFIER(36),
    LINE_CAP(37),
    LINE_JOIN(38),
    LINE_TYPE_CONTINUATION(39),
    LINE_TYPE_INITIAL_OFFSET(40),
    TEXT_SCORE_TYPE(41),
    RESTRICTED_TEXT_TYPE(42),
    INTERPOLATED_INTERIOR(43),
    EDGE_CAP(44),
    EDGE_JOIN(45),
    EDGE_TYPE_CONTINUATION(46),
    EDGE_TYPE_INITIAL_OFFSET(47),
    SYMBOL_LIBRARY_INDEX(48),
    SYMBOL_COLOUR(49),
    SYMBOL_SIZE(50),
    SYMBOL_ORIENTATION(51);

    private final int elementCode;

    AttributeCGMElement(int ec) {
        elementCode = ec;
    }

    public static AttributeCGMElement getElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    public String toString() {
        return name().concat("(").concat(String.valueOf(elementCode))
                .concat(")");
    }

}
