
package com.atakmap.android.emergency.tool;

public interface EmergencyListener {

    void emergencyStateChanged(Boolean emergencyOn,
            EmergencyType emergencyType);

}
