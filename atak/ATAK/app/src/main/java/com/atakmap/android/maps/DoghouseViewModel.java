
package com.atakmap.android.maps;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.atakmap.coremap.log.Log;
import android.widget.Toast;

import com.atakmap.android.routes.Route;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DoghouseViewModel {

    private static final String TAG = "DoghouseViewModel";
    private final MapGroup _doghouseGroup;
    private final ConcurrentHashMap<String, List<Doghouse>> _cache = new ConcurrentHashMap<>();
    private int _size = 0;
    private final SharedPreferences _prefs;

    DoghouseViewModel(MapGroup doghouseGroup) {
        _doghouseGroup = doghouseGroup;
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(MapView.getMapView().getContext());

        for (int i = 0; i < Doghouse.MAX_FIELDS; i++) {
            String key = "dh_data_row_" + i;
            String value = _prefs.getString(key,
                    Doghouse.DoghouseFields.EMPTY.toString());
            if (value == null) {
                value = Doghouse.DoghouseFields.EMPTY.toString();
            }
            Doghouse.DoghouseFields converted = Doghouse.DoghouseFields
                    .fromString(value);

            if (converted != Doghouse.DoghouseFields.EMPTY) {
                _size++;
            }
        }
    }

    /***************************************** Public API *****************************************/
    /**
     * Add a row to all doghouses.
     * @param key What type of data does this new row represent
     * @return false if Doghouses already have the maximum number of rows or if any
     *          individual Doghouse was unable to add a row; true otherwise.
     */
    public boolean addRow(@Nullable Doghouse.DoghouseFields key) {
        if (_size == Doghouse.MAX_FIELDS) {
            return false;
        }

        if (key == null) {
            key = Doghouse.DoghouseFields.BLANK;
        }

        boolean allSuccessful = true;
        // add a row to all doghouses
        for (Map.Entry<String, List<Doghouse>> entry : _cache.entrySet()) {
            List<Doghouse> doghouses = entry.getValue();
            for (Doghouse dh : doghouses) {
                allSuccessful &= dh.addRow(key);
            }
        }

        if (allSuccessful) {
            // now update the preferences to match
            String prefKey = "dh_data_row_" + _size;
            _prefs.edit()
                    .putString(prefKey, key.toString())
                    .apply();

            // increment the size estimate
            _size++;
        }

        return allSuccessful;
    }

    /**
     * Set the data in `index` to <code>Doghouse.DoghouseFields.BLANK</code> i.e. '--'
     * @param index Which row to set to BLANK
     * @param route The Route whose Doghouses are having the data cleared
     */
    public void clearRow(int index, @NonNull
    final Route route) {
        List<Doghouse> doghouses = getDoghousesForRoute(route);
        if (doghouses != null) {
            for (Doghouse dh : doghouses) {
                dh.clearRow(index);
            }
        }

        String key = "dh_data_row_" + index;
        _prefs.edit()
                .putString(key, Doghouse.DoghouseFields.BLANK.toString())
                .apply();
    }

    /**
     * Return the current value for the number of rows in the Doghouses.
     * This may not reflect the true size since the method is not synchronized hence
     * `peekSize`
     * @return The current value for the number of rows in the Doghouses.
     */
    public int peekSize() {
        return _size;
    }

    /**
     * Put data in the Doghouses.
     * @param route The route having data added to its Doghouses
     * @param key What the data represents
     * @param data An Object who's toString() method returns how the data should be displayed.
     */
    public void put(@NonNull
    final Route route,
            @NonNull Doghouse.DoghouseFields key,
            @Nullable Object data) {
        List<Doghouse> doghouses = _cache.get(route.getUID());
        if (doghouses != null) {
            for (Doghouse dh : doghouses) {
                dh.put(key, data);
            }
        }
    }

    /**
     *
     * @param index
     * @param route
     * @param key
     * @param data
     */
    public void put(int index,
            @NonNull
            final Route route,
            @NonNull Doghouse.DoghouseFields key,
            @Nullable Object data) {
        List<Doghouse> doghouses = _cache.get(route.getUID());
        if (doghouses != null) {
            if (index >= 0 && index < doghouses.size()) {
                Doghouse dh = doghouses.get(index);
                dh.put(key, data);
            }
        }
    }

    /**
     * Remove the last row in all Doghouses.
     */
    public void removeRow() {
        removeRow(_size - 1);
    }

    /**
     * Remove the row at `index` in all Doghouses.
     * @param index Which row to remove
     */
    public void removeRow(int index) {
        // remove the row from each doghouse
        for (Map.Entry<String, List<Doghouse>> entry : _cache.entrySet()) {
            List<Doghouse> doghouses = entry.getValue();
            for (Doghouse dh : doghouses) {
                dh.removeRow(index);
            }
        }

        // update the preferences
        SharedPreferences.Editor editor = _prefs.edit();
        for (int i = index; i < _size; i++) {
            String key = "dh_data_row_" + i;
            String nextKey = "dh_data_row_" + (i + 1);
            editor.putString(
                    key,
                    _prefs.getString(nextKey,
                            Doghouse.DoghouseFields.EMPTY.toString()));
        }
        editor.apply();

        // decrement the size estimate
        _size--;
    }

    /**
     * Reset default values in Doghouses.
     * The defaults include:
     *      1. Turnpoint ID
     *      2. Bearing to Next
     *      3. Distance to Next
     */
    public void resetDoghouses() {
        // reset each doghouse
        for (Map.Entry<String, List<Doghouse>> entry : _cache.entrySet()) {
            List<Doghouse> doghouses = entry.getValue();
            if (doghouses != null) {
                for (Doghouse dh : doghouses) {
                    dh.resetDefaults();
                }
            }
        }

        // we know there are 3 rows by default
        _size = 3;

        // now update the preferences to match
        SharedPreferences.Editor editor = _prefs.edit();
        String keyFormatter = "dh_data_row_%d";
        // empty each row
        for (int i = 0; i < Doghouse.MAX_FIELDS; i++) {
            String key = String.format(Locale.US, keyFormatter, i);
            editor.putString(key, Doghouse.DoghouseFields.EMPTY.toString());
        }
        // then set the defaults in the first 3 rows
        editor.putString("dh_data_row_0",
                Doghouse.DoghouseFields.TURNPOINT_ID.toString());
        editor.putString("dh_data_row_1",
                Doghouse.DoghouseFields.BEARING_TO_NEXT.toString());
        editor.putString("dh_data_row_2",
                Doghouse.DoghouseFields.DISTANCE_TO_NEXT.toString());
        editor.apply();
    }

    /**
     * Change what data the given row represents.
     * @param index The row being updated
     * @param key The new data to represent in the given row
     */
    public void updateRow(int index, Doghouse.DoghouseFields key) {
        if (key == Doghouse.DoghouseFields.EMPTY) {
            removeRow(index);
            return;
        }

        // update the row in each doghouse
        for (Map.Entry<String, List<Doghouse>> entry : _cache.entrySet()) {
            List<Doghouse> doghouses = entry.getValue();
            for (Doghouse dh : doghouses) {
                dh.updateRow(index, key);
            }
        }

        // now update preferences to match
        String prefKey = "dh_data_row_" + index;
        _prefs.edit()
                .putString(prefKey, key.toString())
                .apply();
    }

    /**
     * Show Doghouses.
     */
    public void showDoghouses() {
        _doghouseGroup.setVisible(true);
    }

    /**
     * Hide Doghouses.
     */
    public void hideDoghouses() {
        _doghouseGroup.setVisible(false);
    }

    /**
     * @return true if Doghouses are set to Visible, false otherwise.
     */
    public boolean areDoghousesVisible() {
        return _doghouseGroup.getVisible();
    }

    /*************************************** End Public API ***************************************/

    /**************************************** Package API *****************************************/
    void addDoghouses(Route route) {
        if (route.getNumPoints() - 1 > Doghouse.MAX_DENSITY) {
            Toast.makeText(
                    MapView.getMapView().getContext(),
                    "Route is too long; Doghouses omitted!",
                    Toast.LENGTH_LONG)
                    .show();
            return;
        }

        List<Doghouse> doghouses = getDoghousesForRoute(route);
        if (doghouses == null) {
            doghouses = new LinkedList<>();
        }

        for (int i = 0; i < route.getNumPoints() - 1; i++) {
            Doghouse dh = buildDoghouse(i, route);
            if (dh != null) {
                updateRelativeLocation(i, dh, route);
                dh.setVisible(route.getVisible());
                doghouses.add(dh);
                _doghouseGroup.addItem(dh);
            }
        }

        _cache.put(route.getUID(), doghouses);
    }

    void updateDoghouses(Route route) {
        List<Doghouse> doghouses = _cache.get(route.getUID());
        if (doghouses == null) {
            return;
        }
        ArrayList<GeoPointMetaData> points = route._points;
        int index = 0;
        while ((index + 1) < points.size()) {
            if (index >= doghouses.size()) {
                Doghouse dh = buildDoghouse(index, route);
                if (dh != null) {
                    updateRelativeLocation(index, dh, route);
                    dh.updateNose();
                    doghouses.add(index, dh);
                    _doghouseGroup.addItem(dh);
                }
            } else {
                Doghouse dh = doghouses.get(index);
                GeoPointMetaData source = points.get(index);
                GeoPointMetaData target = points.get(index + 1);
                dh.setSource(source);
                dh.setTarget(target);
                updateRelativeLocation(index, dh, route);
                dh.updateNose();
            }
            index++;
        }
        for (int i = doghouses.size() - 1; i >= index; i--) {
            Doghouse dh = doghouses.get(i);
            doghouses.remove(i);
            dh.destroy();
        }
    }

    void addDoghouse(@NonNull
    final Route route) {
        List<Doghouse> doghouses = getDoghousesForRoute(route);
        if (doghouses != null) {
            Doghouse dh = buildDoghouse(route.getNumPoints() - 2, route);
            doghouses.add(dh);
            _doghouseGroup.addItem(dh);
        }
    }

    @Nullable
    Doghouse getDoghouse(int index, @NonNull
    final Route route) {
        List<Doghouse> doghouses = _cache.get(route.getUID());
        return doghouses != null ? doghouses.get(index) : null;
    }

    @Nullable
    Doghouse getDoghouse(@NonNull String uid) {
        MapItem item = _doghouseGroup.deepFindUID(uid);
        return item != null ? (Doghouse) item : null;
    }

    @Nullable
    List<Doghouse> getDoghousesForRoute(@NonNull
    final Route route) {
        return _cache.get(route.getUID());
    }

    void insertDoghouse(int index, @NonNull
    final Route route) {
        final List<Doghouse> doghouses = getDoghousesForRoute(route);
        if (doghouses != null) {
            if (index >= 0 && index < doghouses.size()) {
                Doghouse dh = buildDoghouse(index, route);
                doghouses.add(index, dh);
                updateDoghouses(index, route);
                _doghouseGroup.addItem(dh);
            }
        }
    }

    void hideDoghousesForRoute(@NonNull
    final Route route) {
        setVisible(route, false);
    }

    void showDoghousesForRoute(@NonNull
    final Route route) {
        setVisible(route, true);
    }

    void setRelativeLocation(Doghouse.DoghouseLocation loc) {
        for (Map.Entry<String, List<Doghouse>> entry : _cache.entrySet()) {
            List<Doghouse> doghouses = entry.getValue();
            if (doghouses == null) {
                return;
            }
            if (loc != Doghouse.DoghouseLocation.OUTSIDE_OF_TURN) {
                for (Doghouse dh : doghouses) {
                    dh.setRelativeLocation(loc);
                }
            } else {
                Route route = DoghouseReceiver.getRouteWithUid(entry.getKey());
                if (route != null) {
                    byte[] turns = computeTurns(route);
                    for (int i = 0; i < doghouses.size(); i++) {
                        byte turn = 0;
                        if (turns != null && (i > 0 && i <= turns.length)) {
                            turn = turns[i - 1];
                        }
                        doghouses.get(i).setRelativeLocation(
                                turn >= 0
                                        ? Doghouse.DoghouseLocation.LEFT_OF_ROUTE
                                        : Doghouse.DoghouseLocation.RIGHT_OF_ROUTE);
                    }
                }
            }
        }
    }

    void removeDoghouse(int index, @NonNull
    final Route route) {
        List<Doghouse> doghouses = _cache.get(route.getUID());
        if (doghouses != null) {
            Doghouse dh = doghouses.remove(index);
            dh.destroy();
            _doghouseGroup.removeItem(dh);
        }
    }

    void removeDoghouse(@NonNull String uid) {
        Doghouse doghouse = getDoghouse(uid);
        boolean stop = false;
        if (doghouse != null) {
            // iterate over all doghouses in the cache and remove the one that matches this
            // we don't know which route it belongs to, so we have to search them all
            for (Map.Entry<String, List<Doghouse>> entry : _cache.entrySet()) {
                List<Doghouse> doghouses = entry.getValue();
                Iterator<Doghouse> iter = doghouses.iterator();
                while (iter.hasNext()) {
                    Doghouse dh = iter.next();
                    if (dh.getUID().equals(uid)) {
                        iter.remove();
                        stop = true; // signal to stop iterating through the cache
                        break; // and break this loop
                    }
                }

                if (stop) {
                    break;
                }
            }

            // now remove it from the map group and destroy it
            _doghouseGroup.removeItem(doghouse);
            doghouse.destroy();
        }
    }

    void removeDoghouses(@NonNull
    final Route route) {
        List<Doghouse> doghouses = _cache.remove(route.getUID());
        if (doghouses != null) {
            for (Doghouse dh : doghouses) {
                dh.destroy();
                _doghouseGroup.removeItem(dh);
            }

            doghouses.clear();
        }
    }

    /************************************** Private Methods ***************************************/
    @Nullable
    private Doghouse buildDoghouse(int index, @NonNull
    final Route route) {
        if (index >= route.getNumPoints()) {
            Log.w(TAG, "Attempt to build doghouse outside route");
            return null;
        }

        GeoPointMetaData source = route.getPoint(index);
        GeoPointMetaData target = route.getPoint(index + 1);

        if (source == null || target == null) {
            Log.w("DoghouseViewModel",
                    "Attempt to add doghouse with null route points");
            return null;
        }

        //Per SOMPE customer doghouse turnpoint ID should start at 2 for the first doghouse
        ///a flying route with doghouses the first point of STTO point is known as the point 1
        // which the doghouse should be labeled for the point of the route being navigated to matches XPLAN Functionality - Scott Auman

        Doghouse dh = new Doghouse(index + 2, source, target);
        dh.setMetaString(Doghouse.META_ROUTE_UID, route.getUID());
        dh.setMetaString(Doghouse.META_ROUTE_LEG, Integer.toString(index));

        return dh;
    }

    /**
     * Classify a turn as Left Turn, No Turn, or Right Turn.
     * @param bearing1 the incident polyline bearing
     * @param bearing2 the resultant polyline bearing
     * @return Left(-1), None(0), Right(1)
     */
    private byte classifyTurn(double bearing1, double bearing2) {
        double diff = constrainAngle(bearing2 - bearing1);
        int result = diff < 0 ? -1 : (diff > 0 ? 1 : 0);
        return (byte) result;
    }

    /**
     * Given a route, classify each of the turns along the way.
     * A turn is either Left Turn (-1), No Turn (0), or Right
     * Turn (1)
     * @param route the route
     * @return an array from [1, len(route) - 2] of turn classifications
     */
    @Nullable
    private byte[] computeTurns(@NonNull
    final Route route) {
        if (route.getNumPoints() <= 2) {
            return null;
        }
        byte[] turns = new byte[route.getNumPoints() - 2];
        for (int i = 1; i < route.getNumPoints() - 1; i++) {
            double lastLegBearing = getLegBearing(i - 1, route);
            double nextlegBearing = getLegBearing(i, route);
            turns[i - 1] = classifyTurn(lastLegBearing, nextlegBearing);
        }

        return turns;
    }

    /**
     * Constrain an angle to [-180, 180).
     *
     * @param x The angle to constrain
     * @return The same angle translated to [-180, 180)
     * 
     */
    private double constrainAngle(double x) {
        x = (x + 180) % 360;
        if (x < 0) {
            x += 360;
        }
        return x - 180;
    }

    private double getLegBearing(int leg, final Route route) {
        GeoPoint source = route.getPoint(leg).get();
        GeoPoint dest = route.getPoint(leg + 1).get();
        return source.bearingTo(dest);
    }

    private void handlePointRemoved(Route route) {
        List<Doghouse> doghouses = getDoghousesForRoute(route);
        if (doghouses != null) {
            // assume the last point is missing
            int missing = doghouses.size() - 1;
            // try to prove otherwise
            for (int i = 0; i < route.getNumPoints() - 1; i++) {
                Doghouse dh = doghouses.get(i);
                PointMapItem pmi = route.getPointMapItem(i);
                String uid = null;
                if (pmi instanceof Route.ControlPointMapItem) {
                    Map<String, Object> uids = pmi
                            .getMetaMap(Doghouse.EXTRA_UID);
                    if (uids != null) {
                        uid = uids.get(pmi.getUID()).toString();
                    }
                } else {
                    uid = pmi.getMetaString(Doghouse.EXTRA_UID, null);
                }

                if (!dh.getUID().equals(uid)) {
                    // then we've found a point and a doghouse that don't line up
                    missing = i;
                    break;
                }
            }

            removeDoghouse(missing, route);
        }
    }

    private void handlePointAdded(Route route) {
        for (int i = 0; i < route.getNumPoints() - 1; i++) {
            boolean needsDoghouse = false;
            PointMapItem pmi = route.getPointMapItem(i);
            if (pmi instanceof Route.ControlPointMapItem) {
                Map<String, Object> uids = pmi.getMetaMap(Doghouse.EXTRA_UID);
                if (uids == null || uids.get(pmi.getUID()) == null) {
                    // this is a control point without a doghouse
                    needsDoghouse = true;
                }
            } else {
                String uid = pmi.getMetaString(Doghouse.EXTRA_UID, null);
                if (uid == null) {
                    needsDoghouse = true;
                }
            }

            if (needsDoghouse) {
                // the last doghouse belongs to the penultimate point in the route
                // so if we added a point elsewhere, then insert a doghouse
                // otherwise we just add one to the end
                if (i < route.getNumPoints() - 2) {
                    insertDoghouse(i, route);
                } else {
                    addDoghouse(route);
                }
            }
        }
    }

    private void setVisible(@NonNull
    final Route route, boolean visible) {
        List<Doghouse> doghouses = _cache.get(route.getUID());
        if (doghouses != null) {
            for (Doghouse dh : doghouses) {
                dh.setVisible(visible);
            }
        }
    }

    private void updateDoghouses(int index, Route route) {
        List<Doghouse> doghouses = _cache.get(route.getUID());
        if (doghouses != null) {

            if (index > 0 && index < doghouses.size() - 1) {
                // update target point of previous doghouse
                Doghouse previous = doghouses.get(index - 1);
                GeoPointMetaData gpmd = route.getPoint(index);
                if (gpmd != null) {
                    previous.setTarget(gpmd);
                } else {
                    Log.e(TAG,
                            "Received null point from route. Could not update doghouses!");
                }

                // update the source of the next doghouse
                Doghouse next = doghouses.get(index + 1);
                if (gpmd != null) {
                    next.setSource(gpmd);
                } else {
                    Log.e(TAG,
                            "Received null point from route. Could not update doghouses!");
                }
            }

            // update the turnpoint id of all doghouses after the new one
            for (int i = index + 1; i < doghouses.size(); i++) {
                Doghouse doghouse = doghouses.get(i);
                doghouse.setTurnpointId(i + 1);
            }
        }
    }

    private void updateRelativeLocation(int index, Doghouse doghouse,
            Route route) {
        final Doghouse.DoghouseLocation pref = Doghouse.DoghouseLocation
                .fromConstant(
                        _prefs.getInt(DoghouseReceiver.RELATIVE_LOCATION, 1));
        Doghouse.DoghouseLocation loc = pref;
        if (pref == Doghouse.DoghouseLocation.OUTSIDE_OF_TURN) {
            byte[] turns = computeTurns(route);
            byte turn = 0;
            if (turns != null && (index > 0 && index <= turns.length)) {
                turn = turns[index - 1];
            }
            loc = turn >= 0
                    ? Doghouse.DoghouseLocation.LEFT_OF_ROUTE
                    : Doghouse.DoghouseLocation.RIGHT_OF_ROUTE;
        }
        doghouse.setRelativeLocation(loc);
    }
}
