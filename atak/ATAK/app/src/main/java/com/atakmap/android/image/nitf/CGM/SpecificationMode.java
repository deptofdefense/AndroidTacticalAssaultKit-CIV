
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum SpecificationMode {
    SCALED, // type: R
    ABSOLUTE, // type: VDC
    FRACTIONAL, // type: R
    MM // type: R
    ;

    public static SpecificationMode getMode(int mode) {
        switch (mode) {
            case 0:
                return ABSOLUTE;
            case 1:
                return SCALED;
            case 2:
                return FRACTIONAL;
            case 3:
                return MM;
            default:
                // default value: Scaled
                return ABSOLUTE;
        }
    }

    public static int value(SpecificationMode mode) {
        switch (mode) {
            case ABSOLUTE:
                return 0;
            case SCALED:
                return 1;
            case FRACTIONAL:
                return 2;
            case MM:
                return 3;
            default:
                // default value: Absolute
                return 0;
        }
    }
}
