
package com.atakmap.android.routes.nav;

import android.content.Intent;
import android.util.Pair;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.conversions.ConversionFactors;

import java.util.Objects;

/**
 * This class is responsible for managing the state (e.g. speed, units, eta, etc.) for purposes of navigation.
 *
 * Note: This class is thread-safe.
 */

public class RoutePanelViewModel {

    //-------------------- Direction Flags ---------------------------
    public static final int STRAIGHT = 1;
    public static final int SHARP_LEFT = 2;
    public static final int LEFT = 3;
    public static final int SLIGHT_LEFT = 4;
    public static final int SLIGHT_RIGHT = 5;
    public static final int RIGHT = 6;
    public static final int SHARP_RIGHT = 7;
    public static final int START = 8;
    public static final int END = 9;

    //-------------------- Speed Units ---------------------------
    public static final int MILES_PER_HOUR = 0;
    public static final int KILOMETERS_PER_HOUR = 1;
    public static final int KNOTS = 2;

    //NOTE:: The distance unit value corresponds to the speed units value on purpose.

    //NOTE: These are private because we don't want to support differing distance/speed units at this time.

    //-------------------- Distance Units ---------------------------
    private static final int FEET_MILES = 0;
    private static final int METERS_KILOMETERS = 1;
    private static final int NAUTICAL_MILES = 2;

    //-------------------- Fields and Properties ---------------------------

    private final Object syncRoot = new Object();
    private final Object listenerSyncRoot = new Object();

    private RouteNavigationStateListener listener = null;

    public RouteNavigationStateListener getListener() {
        synchronized (listenerSyncRoot) {
            return listener;
        }
    }

    public void setListener(RouteNavigationStateListener listener) {
        synchronized (listenerSyncRoot) {
            this.listener = listener;
        }
    }

    private int units = 0;

    public int getUnits() {
        synchronized (syncRoot) {
            return units;
        }
    }

    public void setUnits(int units) {

        //Ensure we have a valid value
        if (units < 0)
            units *= -1;

        units %= 3;

        double currentSpeedInMetersPerSecond = 0;
        double currentDistanceToNextWaypoint = 0;
        double currentDistanceToVdo = 0;

        synchronized (syncRoot) {
            if (units == this.units)
                return;

            this.units = units;

            //NOTE: We can safely do this, because we have the lock.
            currentDistanceToNextWaypoint = distanceToNextWaypoint;
            currentSpeedInMetersPerSecond = speedInMetersPerSecond;
            currentDistanceToVdo = distanceToVdo;
        }

        //Need to update speed and distance
        Pair<Double, String> formattedSpeed = getFormattedSpeed(
                currentSpeedInMetersPerSecond, units);
        Pair<Double, String> formattedDistanceToNextWaypoint = getFormattedDistance(
                currentDistanceToNextWaypoint, units);
        Pair<Double, String> formattedDistanceToVdo = getFormattedDistance(
                currentDistanceToVdo, units);

        fireOnSpeedChanged(formattedSpeed.first, formattedSpeed.second);
        fireOnDistanceToNextWaypointChanged(
                formattedDistanceToNextWaypoint.first,
                formattedDistanceToNextWaypoint.second);
        fireOnDistanceToVdoChanged(formattedDistanceToVdo.first,
                formattedDistanceToVdo.second);

    }

    private double speedInMetersPerSecond = 0;

    public double getSpeedInMetersPerSecond() {
        synchronized (syncRoot) {
            return speedInMetersPerSecond;
        }
    }

    public void setSpeedInMetersPerSecond(double speedInMetersPerSecond) {
        int units = 0;

        synchronized (syncRoot) {
            this.speedInMetersPerSecond = speedInMetersPerSecond;
            units = this.units;
        }

        Pair<Double, String> formattedSpeed = getFormattedSpeed(
                speedInMetersPerSecond, units);
        if (formattedSpeed != null)
            fireOnSpeedChanged(formattedSpeed.first, formattedSpeed.second);
    }

    private double distanceToNextWaypoint = 0;

    public double getDistanceToNextWaypoint() {
        synchronized (syncRoot) {
            return distanceToNextWaypoint;
        }
    }

    public void setDistanceToNextWaypoint(double distanceToNextWaypoint) {
        int units = 0;

        synchronized (syncRoot) {
            this.distanceToNextWaypoint = distanceToNextWaypoint;
            units = this.units;
        }

        Pair<Double, String> formattedDistance = getFormattedDistance(
                distanceToNextWaypoint, units);
        if (formattedDistance != null) {
            fireOnDistanceToNextWaypointChanged(formattedDistance.first,
                    formattedDistance.second);
        }
    }

    private double distanceToVdo = 0;

    public double getDistanceToVdo() {
        synchronized (syncRoot) {
            return distanceToVdo;
        }
    }

    public void setDistanceToVDO(double distanceToVDO) {
        int units = 0;

        synchronized (syncRoot) {
            this.distanceToVdo = distanceToVDO;
            units = this.units;
        }

        Pair<Double, String> formattedDistance = getFormattedDistance(
                distanceToVDO, units);
        if (formattedDistance != null) {
            fireOnDistanceToVdoChanged(formattedDistance.first,
                    formattedDistance.second);
        }
    }

    private Pair<Integer, String> cue = null;

    public Pair<Integer, String> getCue() {
        synchronized (syncRoot) {
            return cue;
        }
    }

    public void setCue(Pair<Integer, String> cue) {
        synchronized (syncRoot) {
            if (this.cue != null && cue != null
                    && Objects.equals(this.cue.first, cue.first) &&
                    this.cue.second.equals(cue.second))
                return; //No change

            this.cue = cue;
        }

        if (cue != null) {
            fireOnNavigationCueReceived(cue.first, cue.second);
        } else {
            fireOnNavigationCueCleared();
        }
    }

    private double averageSpeedInMetersPerSecond = 0;

    public double getAverageSpeedInMetersPerSecond() {
        synchronized (syncRoot) {
            return averageSpeedInMetersPerSecond;
        }
    }

    public void setAverageSpeedInMetersPerSecond(
            double averageSpeedInMetersPerSecond) {
        double distanceToNextWP = 0;
        double distanceToVdo = 0;

        synchronized (syncRoot) {
            this.averageSpeedInMetersPerSecond = averageSpeedInMetersPerSecond;
            distanceToNextWP = this.distanceToNextWaypoint;
            distanceToVdo = this.distanceToVdo;
        }

        String formattedCheckpointEta = getFormattedEstimatedTimeOfArrival(
                distanceToNextWP, averageSpeedInMetersPerSecond);

        String formattedVdoEta = getFormattedEstimatedTimeOfArrival(
                distanceToVdo, averageSpeedInMetersPerSecond);

        fireOnEstimatedTimeOfArrivalToNextCheckpointChanged(
                formattedCheckpointEta);
        fireOnEstimatedTimeOfArrivalToVdoChanged(formattedVdoEta);
    }

    //-------------------- Methods ---------------------------

    public Pair<Double, String> getFormattedSpeed(
            double speedInMetersPerSecond, int units) {

        String speedUnits = getSpeedUnitsText(units);
        double conversionFactor = 0;

        switch (units) {
            case 0:
                conversionFactor = ConversionFactors.METERS_PER_S_TO_MILES_PER_H;
                break;
            case 1:
                conversionFactor = ConversionFactors.METERS_PER_S_TO_KILOMETERS_PER_H;
                break;
            case 2:
                conversionFactor = ConversionFactors.METERS_PER_S_TO_KNOTS;
                break;
            default:
                return null;
        }

        return new Pair<>((double) Math.round(conversionFactor
                * speedInMetersPerSecond),
                speedUnits);
    }

    public Pair<Double, String> getFormattedDistance(double distanceInMeters,
            int units) {

        switch (units) {
            case 0:
                double miles = ConversionFactors.METERS_TO_MILES
                        * distanceInMeters;

                if (miles >= 10) {
                    return new Pair<>(round(miles, 0),
                            getDistanceUnitsText(units, false));
                } else if (miles >= .1) {
                    return new Pair<>(round(miles, 1),
                            getDistanceUnitsText(units, false));
                } else {
                    return new Pair<>(
                            round(ConversionFactors.METERS_TO_FEET
                                    * distanceInMeters, 0),
                            getDistanceUnitsText(units, true));
                }
            case 1:
                if (distanceInMeters >= 10000) {
                    return new Pair<>(round(
                            ConversionFactors.METERS_TO_KM * distanceInMeters,
                            0),
                            getDistanceUnitsText(units, false));
                } else if (distanceInMeters >= 1000) {
                    return new Pair<>(round(
                            ConversionFactors.METERS_TO_KM * distanceInMeters,
                            1),
                            getDistanceUnitsText(units, false));
                } else {
                    return new Pair<>(round(distanceInMeters, 0),
                            getDistanceUnitsText(units, true));
                }
            case 2:
                double nm = ConversionFactors.METERS_TO_NM * distanceInMeters;

                if (nm > 10) {
                    return new Pair<>(round(nm, 0),
                            getDistanceUnitsText(units, false));
                } else {
                    return new Pair<>(round(nm, 1),
                            getDistanceUnitsText(units, false));
                }
            default:
                return null;
        }

    }

    public String getFormattedEstimatedTimeOfArrival(double distanceInMeters,
            double speedInMetersPerSecond) {

        if (Double.isNaN(distanceInMeters)
                || Double.isNaN(speedInMetersPerSecond))
            return "--";

        //-------------------- NOTE:  These are here for purposes of readability ------------------
        double totalSeconds = Math.round(distanceInMeters
                / speedInMetersPerSecond);

        int hours = (int) (totalSeconds / 3600d);
        int remainder = (int) totalSeconds % 3600;
        int minutes = (int) (remainder / 60d);
        int seconds = remainder % 60;

        if (hours > 999) {
            return "---";
        }

        if (hours > 0) {
            return String.format(LocaleUtil.getCurrent(), "%dh %02dm",
                    hours, minutes);
        } else if (minutes > 0) {
            return String.format(LocaleUtil.getCurrent(), "%dm %02ds",
                    minutes, seconds);
        } else {
            return String.format(LocaleUtil.getCurrent(), "%ds", seconds);
        }
    }

    public String getDistanceUnitsText(int units, boolean smallIncrement) {
        switch (units) {
            case 0:
                return smallIncrement ? "ft" : "mi";
            case 1:
                return smallIncrement ? "m" : "km";
            case 2:
                return "nm";
            default:
                return "Unknown";
        }
    }

    public String getSpeedUnitsText(int units) {
        switch (units) {
            case 0:
                return "MPH";
            case 1:
                return "KMH";
            case 2:
                return "KN";
            default:
                return "Unknown";
        }
    }

    public void toggleUnits() {
        int newUnits = (getUnits() + 1) % 3;
        setUnits(newUnits);
    }

    public void broadcastEndNavIntent() {
        Intent endNavIntent = new Intent();
        endNavIntent.setAction("com.atakmap.android.maps.END_NAV");
        AtakBroadcast.getInstance().sendBroadcast(endNavIntent);
    }

    public void boradcastNextWaypointIntent() {
        Intent nextIntent = new Intent();
        nextIntent.setAction(RouteNavigator.NAV_TO_NEXT_INTENT);
        AtakBroadcast.getInstance().sendBroadcast(nextIntent);
    }

    public void broadcastPreviousWaypointIntent() {
        Intent nextIntent = new Intent();
        nextIntent.setAction(RouteNavigator.NAV_TO_PREV_INTENT);
        AtakBroadcast.getInstance().sendBroadcast(nextIntent);
    }

    //-------------------- Private Methods ---------------------------

    private double round(double dbl, int places) {
        double multiplier = Math.pow(10, places);
        return Math.round(dbl * multiplier) / multiplier;
    }

    //-------------------- Event Publishers ---------------------------

    //NOTE: Need to be careful about deadlocks here.  Notice that the getter syntax is not used for the listener.
    //We do, however, want to block the listener changing while we're trying to fire an event.

    private void fireOnSpeedChanged(double speed, String units) {
        synchronized (listenerSyncRoot) {
            if (listener != null) {
                listener.onSpeedChanged(speed, units);
            }
        }
    }

    private void fireOnDistanceToNextWaypointChanged(double distance,
            String units) {
        synchronized (listenerSyncRoot) {
            if (listener != null) {
                listener.onDistanceToNextWaypointChanged(distance, units);
            }
        }
    }

    private void fireOnDistanceToVdoChanged(double distance, String units) {
        synchronized (listenerSyncRoot) {
            if (listener != null) {
                listener.onDistanceToVdoChanged(distance, units);
            }
        }
    }

    private void fireOnNavigationCueReceived(int direction, String text) {
        synchronized (listenerSyncRoot) {
            if (listener != null) {
                listener.onNavigationCueReceived(direction, text);
            }
        }
    }

    private void fireOnEstimatedTimeOfArrivalToNextCheckpointChanged(
            String eta) {
        synchronized (listenerSyncRoot) {
            if (listener != null) {
                listener.onEstimatedTimeOfArrivalToNextWaypointChanged(eta);
            }
        }
    }

    private void fireOnEstimatedTimeOfArrivalToVdoChanged(String eta) {
        synchronized (listenerSyncRoot) {
            if (listener != null) {
                listener.onEstimatedTimeOfArrivalToVdoChanged(eta);
            }
        }
    }

    private void fireOnNavigationCueCleared() {
        synchronized (listenerSyncRoot) {
            if (listener != null) {
                listener.onNavigationCueCleared();
            }
        }
    }

    /*******************************************************************************
     * Interface for events
     *******************************************************************************/
    public interface RouteNavigationStateListener {
        void onSpeedChanged(double speed, String units);

        void onDistanceToVdoChanged(double distance, String units);

        void onDistanceToNextWaypointChanged(double distance, String units);

        void onNavigationCueReceived(int direction, String text);

        void onEstimatedTimeOfArrivalToNextWaypointChanged(String eta);

        void onEstimatedTimeOfArrivalToVdoChanged(String eta);

        void onNavigationCueCleared();
    }

}
