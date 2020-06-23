
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum GraphicalPrimitiveCGMElement {
    UNUSED_0(0),
    POLYLINE(1),
    DISJOINT_POLYLINE(2),
    POLYMARKER(3),
    TEXT(4),
    RESTRICTED_TEXT(5),
    APPEND_TEXT(6),
    POLYGON(7),
    POLYGON_SET(8),
    CELL_ARRAY(9),
    GENERALIZED_DRAWING_PRIMITIVE(10),
    RECTANGLE(11),
    CIRCLE(12),
    CIRCULAR_ARC_3_POINT(13),
    CIRCULAR_ARC_3_POINT_CLOSE(14),
    CIRCULAR_ARC_CENTRE(15),
    CIRCULAR_ARC_CENTRE_CLOSE(16),
    ELLIPSE(17),
    ELLIPTICAL_ARC(18),
    ELLIPTICAL_ARC_CLOSE(19),
    CIRCULAR_ARC_CENTRE_REVERSED(20),
    CONNECTING_EDGE(21),
    HYPERBOLIC_ARC(22),
    PARABOLIC_ARC(23),
    NON_UNIFORM_B_SPLINE(24),
    NON_UNIFORM_RATIONAL_B_SPLINE(25),
    POLYBEZIER(26),
    POLYSYMBOL(27),
    BITONAL_TILE(28),
    TILE(29);

    private final int elementCode;

    GraphicalPrimitiveCGMElement(int ec) {
        elementCode = ec;
    }

    public static GraphicalPrimitiveCGMElement getElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    public String toString() {
        return name().concat("(").concat(String.valueOf(elementCode))
                .concat(")");
    }
}
