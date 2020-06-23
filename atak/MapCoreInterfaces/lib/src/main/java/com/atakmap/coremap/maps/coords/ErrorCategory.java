
package com.atakmap.coremap.maps.coords;

public enum ErrorCategory {
    CAT1(1, "CAT1"),
    CAT2(2, "CAT2"),
    CAT3(3, "CAT3"),
    CAT4(4, "CAT4"),
    CAT5(4, "CAT5"),
    CAT6(6, "CAT6");

    private final String name;
    private final int value;

    ErrorCategory(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    /**
     * Given a circular error in meters, provide the correct error category
     * based on JP 3-09.3 Close Air Support
     */
    public static ErrorCategory getCategory(double circularError) {

        if (circularError <= 6.0)
            return CAT1;
        else if (circularError <= 15.0)
            return CAT2;
        else if (circularError <= 30.0)
            return CAT3;
        else if (circularError <= 91.0)
            return CAT4;
        else if (circularError <= 305.0)
            return CAT5;
        else
            return CAT6;
    }
}
