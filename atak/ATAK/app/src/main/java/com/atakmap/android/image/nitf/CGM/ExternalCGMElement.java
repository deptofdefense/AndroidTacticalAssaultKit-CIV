
package com.atakmap.android.image.nitf.CGM;

import androidx.annotation.NonNull;

/**
 *
 */
public enum ExternalCGMElement {
    UNUSED_0(0),
    MESSAGE(1),
    APPLICATION_DATA(1);

    private final int elementCode;

    ExternalCGMElement(int ec) {
        elementCode = ec;
    }

    public static ExternalCGMElement getElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    public int getElementCode() {
        return elementCode;
    }

    @NonNull
    public String toString() {
        return name().concat("(").concat(String.valueOf(elementCode))
                .concat(")");
    }
}
