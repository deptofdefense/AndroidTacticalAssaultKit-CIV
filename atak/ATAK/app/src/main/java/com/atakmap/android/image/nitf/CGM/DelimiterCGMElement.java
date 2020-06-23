
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum DelimiterCGMElement {
    NO_OP(0),
    BEGIN_METAFILE(1),
    END_METAFILE(2),
    BEGIN_PICTURE(3),
    BEGIN_PICTURE_BODY(4),
    END_PICTURE(5),
    BEGIN_SEGMENT(6),
    END_SEGMENT(7),
    BEGIN_FIGURE(8),
    END_FIGURE(9),
    UNUSED_10(10),
    UNUSED_11(11),
    UNUSED_12(12),
    BEGIN_PROTECTION_REGION(13),
    END_PROTECTION_REGION(14),
    BEGIN_COMPOUND_LINE(15),
    END_COMPOUND_LINE(16),
    BEGIN_COMPOUND_TEXT_PATH(17),
    END_COMPOUND_TEXT_PATH(18),
    BEGIN_TILE_ARRAY(19),
    END_TILE_ARRAY(20),
    BEGIN_APPLICATION_STRUCTURE(21),
    BEGIN_APPLICATION_STRUCTURE_BODY(22),
    END_APPLICATION_STRUCTURE(23);

    private final int elementCode;

    DelimiterCGMElement(int ec) {
        elementCode = ec;
    }

    public int getElementCode() {
        return elementCode;
    }

    public static DelimiterCGMElement getElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    public String toString() {
        return name().concat("(").concat(String.valueOf(elementCode))
                .concat(")");
    }
}
