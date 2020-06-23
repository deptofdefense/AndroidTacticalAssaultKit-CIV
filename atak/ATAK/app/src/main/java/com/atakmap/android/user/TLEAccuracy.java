
package com.atakmap.android.user;

import com.atakmap.app.R;

//Enums copy/pasted from CoTInfoView responsible for the popup dialog for
// the CAT stuff
public enum TLEAccuracy {
    HIGH,
    MEDIUM,
    LOW;

    public int getResource() {
        if (this == LOW)
            return R.id.tle_accuracy_low;
        if (this == HIGH)
            return R.id.tle_accuracy_high;
        return R.id.tle_accuracy_medium;
    }

    public static TLEAccuracy fromResource(int resource) {
        if (resource == R.id.tle_accuracy_low)
            return LOW;
        if (resource == R.id.tle_accuracy_high)
            return HIGH;
        return MEDIUM;
    }
}
