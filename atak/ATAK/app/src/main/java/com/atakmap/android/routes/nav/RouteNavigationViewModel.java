
package com.atakmap.android.routes.nav;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Pair;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteNavigationManager;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener for Route Events such as Off Route and ArrivedAtPoint.
 */
public class RouteNavigationViewModel
        implements RouteNavigationManager.RouteNavigationManagerEventListener {

    public static final String TAG = RouteNavigationViewModel.class
            .getSimpleName();
    private static final String VIBRATE_AT_CHECKPOINT_KEY = "route_vibrate_at_checkpoint";

    private static final double BEARING_COUPLING_DISTANCE = 100;
    private static final double MANEUVER_CAPTURE_BACKWARD_DISTANCE = 10;
    private static final double MANEUVER_CAPTURE_FORWARD_DISTANCE = 50;

    private final SharedPreferences _sp;
    private final List<String> speechBacklog = new ArrayList<>();
    private final boolean speechReady = true;
    private final RoutePanelViewModel routePanelViewModel;
    private boolean wasOffRoute = false;

    private final MapView mapView;

    public RouteNavigationViewModel(MapView view,
            RoutePanelViewModel routePanelViewModel) {
        mapView = view;
        final Context context = mapView.getContext();

        _sp = PreferenceManager.getDefaultSharedPreferences(context);

        this.routePanelViewModel = routePanelViewModel;
    }

    @Override
    public void onGpsStatusChanged(
            RouteNavigationManager routeNavigationManager, boolean found) {
        Intent i = new Intent("com.atakmap.android.speak");
        if (found)
            i.putExtra("text", "G P S Found");
        else
            i.putExtra("text", "G P S Lost");

        if (_sp.getBoolean("useRouteVoiceCues", true)) {
            AtakBroadcast.getInstance().sendSystemBroadcast(i,
                    "com.atakmap.app.ALLOW_TEXT_SPEECH");
        }
    }

    @Override
    public void onLocationChanged(
            RouteNavigationManager routeNavigationManager,
            GeoPoint oldLocation, GeoPoint newLocation) {

    }

    @Override
    public void onNavigationObjectiveChanged(
            RouteNavigationManager routeNavigationManager,
            PointMapItem newObjective,
            boolean isFromRouteProgression) {

        GeoPoint location = routeNavigationManager.getLocation();

        if (location == null)
            return;

        boolean isWithinTrigger = routeNavigationManager
                .isPointWithinItemTrigger(location, newObjective)
                && !routeNavigationManager.getIsOffRoute();

        if (isWithinTrigger /*&& isFromRouteProgression*/) {
            Log.d(TAG, "Objective Changed, deferring cue to trigger handler");
            return; //Let the trigger event handle it.
        }

        Log.d(TAG, "Objective Changed Sending Cue To Receiver");

        sendCueToReceiver(routeNavigationManager, newObjective, "",
                isFromRouteProgression, true, false);
    }

    @Override
    public void onOffRoute(RouteNavigationManager routeNavigationManager) {
        if (wasOffRoute) {
            return;
        }

        wasOffRoute = true;
        toastText(null, "Off Route", true);
    }

    @Override
    public void onReturnedToRoute(
            RouteNavigationManager routeNavigationManager) {
        wasOffRoute = false;
        //Nothing to do here.
    }

    @Override
    public void onTriggerEntered(RouteNavigationManager routeNavigationManager,
            PointMapItem item, int triggerIndex) {

        Log.d(TAG, "Trigger Entered: " + triggerIndex);

        sendCueToReceiver(routeNavigationManager, item, "",
                true, true, false);
    }

    @Override
    public void onArrivedAtPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {

        String name = ATAKUtilities.getDisplayName(item);
        String textMsg = "Arrived at " + name;
        name = NavigationCueExpander.expand(name);
        String voiceMsg = "Arrived at " + name;
        boolean purgeSpeech = false;

        Route route = routeNavigationManager.getRoute();

        int hitIndex = route.getIndexOfPoint(item);
        //If it's the last point and it's our objective, end navigation
        if (hitIndex == route.getNumPoints() - 1 /*&&
                                                 routeNavigationManager.getCurrentObjective().first == route
                                                 .getNumPoints() - 1*/) {

            textMsg = "You have arrived at your destination";
            voiceMsg = textMsg;
            purgeSpeech = true;
        }

        toastText(textMsg, voiceMsg, purgeSpeech);
        boolean vibrate = _sp.getBoolean(VIBRATE_AT_CHECKPOINT_KEY, false);

        if (vibrate) {
            Vibrator vibrator = (Vibrator) mapView.getContext()
                    .getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator != null) {
                vibrator.vibrate(500);
            }
        }
    }

    @Override
    public void onDepartedPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {
        //Nothing to do here
    }

    //-------------------- Private Methods ---------------------------

    private List<GeoPoint> computeManeuverGeom(RouteNavigationManager manager,
            PointMapItem item) {
        int itemIndex = manager.getRoute().getIndexOfMarker(item);

        PointMapItem nextItem = manager.getObjectiveAfter(itemIndex);
        PointMapItem previousItem = manager.getObjectiveBefore(itemIndex);

        double forwardDistance = MANEUVER_CAPTURE_FORWARD_DISTANCE;
        double backwardDistance = MANEUVER_CAPTURE_BACKWARD_DISTANCE;

        if (nextItem != null) {
            forwardDistance = manager.getDistanceBetweenTwoPoints(
                    item.getPoint(),
                    nextItem.getPoint());

            forwardDistance = Math.min(forwardDistance,
                    MANEUVER_CAPTURE_FORWARD_DISTANCE);

            if (previousItem != null) {
                backwardDistance = manager.getDistanceBetweenTwoPoints(
                        previousItem.getPoint(),
                        item.getPoint());

                backwardDistance = Math.min(backwardDistance,
                        MANEUVER_CAPTURE_BACKWARD_DISTANCE);
            }
        }

        List<GeoPoint> geom = manager.getSurroundingGeometry(
                item.getPoint(),
                backwardDistance,
                forwardDistance);

        return geom;
    }

    private void sendCueToReceiver(RouteNavigationManager manager,
            PointMapItem item, String cuePrefix, boolean speak,
            boolean appendDistanceToSpeech,
            boolean purgeSpeech) {
        Route route = manager.getRoute();

        // Compute the maneuver
        List<GeoPoint> geom = computeManeuverGeom(manager, item);
        int index = route.getIndexOfPoint(item);
        int maneuver = -1;
        if (index == 0) {
            maneuver = RoutePanelViewModel.START;
            speak = false; //It would sound weird if we spoke this.
        } else if (index == route.getNumPoints() - 1) {
            maneuver = RoutePanelViewModel.END;
        } else {
            maneuver = getManeuver(geom);
        }

        // Setup the cues
        NavigationCue cue = manager.getCueFromItem(item);
        cuePrefix = cuePrefix == null ? "" : cuePrefix.trim();
        String textCue = null;
        String voiceCue = appendDistanceToSpeech
                ? getDistancePrefix(manager) + " "
                : "";

        // If a cue is explicitly included, we'll use that
        if (cue != null) {
            textCue = cue.getTextCue();
            voiceCue = voiceCue + NavigationCueExpander.expand(cue
                    .getVoiceCue());
        }

        // Sanity check the cue we found, or if necessary, build one from the maneuver
        if (textCue == null || textCue.isEmpty()) {
            textCue = getManeuverText(maneuver);

            if (textCue != null) {
                voiceCue = voiceCue + NavigationCueExpander.expand(textCue);
            } else {
                voiceCue = "";
            }
        }

        // Prepend some text to the front of the cue indicating which CP we are tracking to
        String objectiveName = ATAKUtilities.getDisplayName(item);
        String objectivePrefix = mapView.getContext()
                .getString(R.string.next_cp);
        textCue = objectivePrefix + " " + objectiveName + " " + cuePrefix + " "
                + textCue;
        voiceCue = cuePrefix + " " + voiceCue;

        if (speak) {
            toastText(textCue, voiceCue, purgeSpeech);
        }

        routePanelViewModel.setCue(new Pair<>(maneuver,
                textCue.trim()));
    }

    private int getManeuver(List<GeoPoint> points) {
        if (points == null || points.isEmpty()
                || points.size() < 2)
            return -1; // This doesn't make sense, so we can't do anything with it.

        if (points.size() == 2) {
            return RoutePanelViewModel.STRAIGHT; //Only 2 points, possible, but it's a straight line.
        }

        GeoPoint previousPoint, currentPoint, nextPoint, mid = null;
        double greatestVariance = 0;

        for (int i = 1; i < points.size() - 1; i++) {
            previousPoint = points.get(i - 1);
            currentPoint = points.get(i);
            nextPoint = points.get(i + 1);

            double pivotBearing = getPivotBearing(previousPoint, currentPoint,
                    nextPoint);

            pivotBearing = pivotBearing < 180 ? pivotBearing
                    : pivotBearing - 360;
            pivotBearing = Math.abs(pivotBearing);

            if (pivotBearing > greatestVariance) {
                greatestVariance = pivotBearing;
                mid = currentPoint;
            }
        }

        if (mid != null) {
            return getManeuverDescription(getPivotBearing(
                    points.get(0), mid, points.get(points.size() - 1)) % 360);
        } else {
            return RoutePanelViewModel.STRAIGHT;
        }

    }

    private int getManeuver(Route route, int pointIndex) {

        int manuever = -1;

        //Let's make sure we will have a previous point and a next point
        if (pointIndex > 1 && pointIndex < route.getNumPoints() - 1) {
            //TODO:: Implement a find previous/next method that does this more intelligently.
            GeoPoint previousPoint = route.getPoint(pointIndex - 2).get();
            GeoPoint currentPoint = route.getPoint(pointIndex).get();
            GeoPoint nextPoint = route.getPoint(pointIndex + 1).get();

            double accumulatedBearing = getPivotBearing(previousPoint,
                    currentPoint, nextPoint);

            if (pointIndex + 2 <= route.getNumPoints() - 1) {
                GeoPoint nextNextPoint = route.getPoint(pointIndex + 2).get();

                if (nextNextPoint
                        .distanceTo(nextPoint) <= BEARING_COUPLING_DISTANCE) {
                    accumulatedBearing += getPivotBearing(currentPoint,
                            nextPoint,
                            nextNextPoint);
                }
            }

            manuever = getManeuverDescription((accumulatedBearing + 360) % 360);

        } else if (pointIndex == 0) {
            return RoutePanelViewModel.START;
        } else if (pointIndex == route.getNumPoints() - 1) {
            return RoutePanelViewModel.END;
        }

        return manuever;

    }

    private double getBearingTo(GeoPoint from, GeoPoint to) {
        double lat1 = java.lang.Math.toRadians(from.getLatitude());
        double lon1 = java.lang.Math.toRadians(from.getLongitude());

        double lat2 = java.lang.Math.toRadians(to.getLatitude());
        double lon2 = java.lang.Math.toRadians(to.getLongitude());

        double lonDelta = lon2 - lon1;

        double y = java.lang.Math.sin(lonDelta) * java.lang.Math.cos(lat2);
        double x = java.lang.Math.cos(lat1) * java.lang.Math.sin(lat2) -
                java.lang.Math.sin(lat1) * java.lang.Math.cos(lonDelta)
                        * Math.cos(lat2);

        return (java.lang.Math.toDegrees(java.lang.Math.atan2(y, x)) + 360)
                % 360;
    }

    private String getManeuverText(int maneuverDescription) {
        switch (maneuverDescription) {
            case RoutePanelViewModel.STRAIGHT:
                return "Continue Straight";
            case RoutePanelViewModel.SLIGHT_RIGHT:
                return "Take a Slight Right";
            case RoutePanelViewModel.RIGHT:
                return "Turn Right";
            case RoutePanelViewModel.SHARP_RIGHT:
                return "Take a Sharp Right";
            case RoutePanelViewModel.SLIGHT_LEFT:
                return "Take a Slight Left";
            case RoutePanelViewModel.LEFT:
                return "Turn Left";
            case RoutePanelViewModel.SHARP_LEFT:
                return "Take a Sharp Left";
            case RoutePanelViewModel.START:
                return "Start Point";
            case RoutePanelViewModel.END:
                return "VDO";
            default:
                return null;
        }
    }

    private double getPivotBearing(GeoPoint previousPoint, GeoPoint pivotPoint,
            GeoPoint nextPoint) {

        double bearingFromPoint = getBearingTo(pivotPoint, nextPoint);
        double bearingToPoint = getBearingTo(previousPoint, pivotPoint);
        double relativeBearing = ((bearingFromPoint - bearingToPoint) + 360)
                % 360;

        return relativeBearing;

    }

    private int getManeuverDescription(double relativeBearing) {

        if (relativeBearing > 337.5
                || (relativeBearing >= 0 && relativeBearing < 22.5)) {
            return RoutePanelViewModel.STRAIGHT;
        } else if (relativeBearing >= 22.5 &&
                relativeBearing < 67.5) {
            return RoutePanelViewModel.SLIGHT_RIGHT;
        } else if (relativeBearing >= 67.5 &&
                relativeBearing < 112.5) {
            return RoutePanelViewModel.RIGHT;
        } else if (relativeBearing >= 112.5 && relativeBearing < 180) {
            return RoutePanelViewModel.SHARP_RIGHT;
        } else if (relativeBearing <= 337.5 &&
                relativeBearing > 292.5) {
            return RoutePanelViewModel.SLIGHT_LEFT;
        } else if (relativeBearing <= 292.5 &&
                relativeBearing > 247.5) {
            return RoutePanelViewModel.LEFT;
        } else if (relativeBearing <= 247.5 &&
                relativeBearing > 180) {
            return RoutePanelViewModel.SHARP_LEFT;
        } else {
            return RoutePanelViewModel.STRAIGHT;
        }
    }

    private String getDistancePrefix(
            RouteNavigationManager routeNavigationManager) {

        if (routeNavigationManager.getLocation() == null)
            return "";

        int units = routePanelViewModel.getUnits();
        double distance = routeNavigationManager.getDistanceBetweenTwoPoints(
                routeNavigationManager.getLocation(),
                routeNavigationManager.getCurrentObjective().second.getPoint());
        Pair<Double, String> formattedDistance = routePanelViewModel
                .getFormattedDistance(distance, units);

        if (formattedDistance == null)
            return "";

        String rawUnits = formattedDistance.second
                .toLowerCase(LocaleUtil.getCurrent());
        String unitsText = "";

        switch (rawUnits) {
            case "mi":
                unitsText = "miles";
                break;
            case "ft":
                unitsText = "feet";
                break;
            case "km":
                unitsText = "kilometers";
                break;
            case "m":
                unitsText = "meters";
                break;
            case "nm":
                unitsText = "nautical miles";
                break;
        }

        if (distance < 5) {
            return "";
        } else {
            //NOTE: The space before the word 'In' prevents the engine from cutting the word off entirely.
            return " In " + getStringValue(formattedDistance.first) + " "
                    + unitsText;
        }
    }

    private String getStringValue(double dbl) {
        long roundedValue = Math.round(dbl);
        if ((double) roundedValue == dbl) {
            return String.valueOf(roundedValue);
        } else {
            return String.valueOf(dbl);
        }
    }

    private void toastText(final String toastMsg, final String voiceMsg,
            boolean purgeSpokenQueue) {

        if (!voiceMsg.isEmpty()) {
            int strategy = purgeSpokenQueue ? TextToSpeech.QUEUE_FLUSH
                    : TextToSpeech.QUEUE_ADD;

            Intent i = new Intent("com.atakmap.android.speak");
            i.putExtra("text", voiceMsg);
            i.putExtra("strategy", strategy);

            if (_sp.getBoolean("useRouteVoiceCues", true)) {
                if (speechReady) {
                    AtakBroadcast.getInstance().sendSystemBroadcast(i,
                            "com.atakmap.app.ALLOW_TEXT_SPEECH");
                } else {
                    if (purgeSpokenQueue) {
                        speechBacklog.clear();
                    }

                    speechBacklog.add(voiceMsg);
                }
            }
        }
    }

    public void dispose() {

    }

}
