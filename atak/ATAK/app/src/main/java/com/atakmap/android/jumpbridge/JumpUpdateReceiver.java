
package com.atakmap.android.jumpbridge;

import java.util.ArrayList;
import java.util.List;

import android.util.Pair;

import com.atakmap.android.jumpbridge.Options.FieldOptions;
import com.atakmap.android.jumpbridge.Options.Unit;
import com.atakmap.android.maps.Marker;

/**
 * This class is used to get realtime jump information from JumpMaster for use with plugins
 */
public abstract class JumpUpdateReceiver {

    private final List<Pair<FieldOptions, Unit>> configMap = new ArrayList<>();

    /**
     * Add a field to listen for updates to.
     * 
     * @param fo - the field to be notified about (ex. FieldOptions.ALTITUDE)
     * @param u - the unit to use for that field (ex. Unit.FEET_AGL)
     */
    public void addField(FieldOptions fo, Unit u) {
        //check to see if that field and unit pair is already being listened for
        for (Pair<FieldOptions, Unit> pair : configMap) {
            if (pair.first == fo && pair.second == u) {
                return;
            }
        }

        configMap.add(new Pair<>(
                fo, u));
    }

    /**
     * Remove a field to the list of fields to give updates to.
     * 
     * @param fo - the field to be notified about (ex. FieldOptions.ALTITUDE)
     * @param u - the unit to use for that field (ex. Unit.FEET_AGL)
     */
    public void removeField(FieldOptions fo, Unit u) {
        for (Pair<FieldOptions, Unit> pair : configMap) {
            if (pair.first == fo && pair.second == u) {
                configMap.remove(pair);
                return;
            }
        }
    }

    /**
     * Returns the fields with units that this JumpUpdateReceiver instance is listening for
     */
    public List<Pair<FieldOptions, Unit>> getFields() {
        return configMap;
    }

    /**
     * This method is called when a Jump Starts
     * 
     * @param dip - the primary DIP marker
     */
    public abstract void jumpStarted(Marker dip, double startPlannedHeading);

    /**
     * This method is called when a Jump Ends
     */
    public abstract void jumpEnded();

    /**
     * This method is called when an update has been received for a field
     * this receiver has subscribed to.
     * 
     * @param fo - the field that's value was updated
     * @param val - the string value of the field without the units string
     */
    public abstract void updateField(FieldOptions fo, Unit u, String val);

    /**
     * This method will be called when the planned jumper heading is changed, due to a planned turn
     */
    public abstract void updatePlannedJumpHeading(double heading);
}
