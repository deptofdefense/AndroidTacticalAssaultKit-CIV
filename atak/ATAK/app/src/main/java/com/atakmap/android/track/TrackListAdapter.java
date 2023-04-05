
package com.atakmap.android.track;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.task.GetTrackDetailsTask;
import com.atakmap.android.track.task.GetTrackHistoryTask;
import com.atakmap.android.track.ui.TrackUser;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TrackListAdapter extends BaseAdapter implements
        GetTrackHistoryTask.Callback {
    enum SortType {
        NAME,
        TIME
    }

    private static final String TAG = "TrackListAdapter";

    protected final ArrayList<TrackDetails> results = new ArrayList<>();

    private final ArrayList<TrackDetails> selected = new ArrayList<>();
    private final MapView mapView;
    private TrackHistoryDropDown dropDown;
    private boolean _bMultiSelectEnabled;
    private TrackUser _currentUser;

    /**
     * maintain sorting state
     */
    private SortType _sort;
    private boolean _sortReversed;

    TrackListAdapter(MapView mapView, TrackHistoryDropDown dropDown) {
        this.mapView = mapView;
        this.dropDown = dropDown;
        this._sort = SortType.TIME;
        this._sortReversed = false;
        this._bMultiSelectEnabled = false;
        this._currentUser = null;
    }

    public void dispose() {
        // make sure to clean up track detail listeners
        for (TrackDetails td : results)
            td.dispose();

        results.clear();
        selected.clear();
        dropDown = null;
        _currentUser = null;
    }

    public TrackHistoryDropDown getDropDown() {
        return this.dropDown;
    }

    /**
     * Clear results and kickoff async task
     * @param user
     */
    void refresh(final TrackUser user, final boolean bHideTemp,
            final boolean bDisplayAll) {
        clear();
        _currentUser = user;
        setMultiSelectEnabled(false);
        if (user != null) {
            new GetTrackHistoryTask(mapView.getContext(),
                    user.getUid(), bHideTemp, bDisplayAll, this).execute();
        }
    }

    /**
     * Set specified list of tracks
     * @param user
     */
    void refresh(final TrackUser user, final List<TrackPolyline> tracks) {
        clear();
        _currentUser = user;
        setMultiSelectEnabled(false);

        onComplete(user.getUid(), tracks);
    }

    /**
     * Clear results and kickoff async task
     * @param user
     * @param trackIDs
     */
    void refresh(final TrackUser user, final int[] trackIDs) {
        clear();
        _currentUser = user;
        setMultiSelectEnabled(false);

        new GetTrackHistoryTask(mapView.getContext(),
                user.getUid(), trackIDs, this).execute();
    }

    public TrackUser getCurrentUser() {
        return _currentUser;
    }

    public void setMultiSelectEnabled(boolean bEnabled) {
        // set flag to display checkboxes and hide buttons per row, disable onclick select...

        _bMultiSelectEnabled = bEnabled;
        if (!bEnabled && !FileSystemUtils.isEmpty(selected)) {
            selected.clear();
        }

        requestRedraw();
    }

    public boolean getMultiSelectEnabled() {
        return _bMultiSelectEnabled;
    }

    @Override
    public void onComplete(String uid, List<TrackPolyline> polylines) {
        if (FileSystemUtils.isEmpty(polylines)) {
            Log.w(TAG, "No tracks for: " + uid);
            //TODO toast?
            return;
        }

        //now wrap for UI
        for (TrackPolyline polyline : polylines) {
            addResult(new TrackDetails(mapView, polyline));
        }

        Log.d(TAG, "Added tracks: " + polylines.size() + " for " + uid);
    }

    /**
     * Prevents double adding, causes adapter to be redrawn
     * 
     * @param result
     */
    synchronized public void addResult(TrackDetails result) {
        if (result == null) {
            Log.d(TAG, "Tried to add NULL result.  Ignoring!");
            return;
        }

        for (TrackDetails c : results) {
            if (c.equals(result))
                return;
        }

        //Log.d(TAG, "Adding track details: " + result.toString());
        results.add(result);
        //selected.add(result);
        sortResults(this._sort, false);
        requestRedraw();
    }

    /**
     * Causes adapter to be redrawn
     * 
     * @param result
     */
    synchronized public void removeResult(TrackDetails result) {
        result.removePolyline();
        results.remove(result);
        selected.remove(result);
        requestRedraw();
    }

    synchronized public TrackDetails getResult(int position) {
        return (TrackDetails) getItem(position);
    }

    synchronized TrackDetails[] getSelectedResults(
            SortType sort) {
        // gather and sort selected items
        List<TrackDetails> temp = new ArrayList<>(selected);
        switch (sort) {
            case TIME: {
                Collections
                        .sort(temp, Collections.reverseOrder(timeComparator));
            }
                break;
            case NAME: {
                Collections
                        .sort(temp, Collections.reverseOrder(nameComparator));
            }
                break;
        }
        // return as an array
        TrackDetails[] selectedContacts = new TrackDetails[temp
                .size()];
        return temp.toArray(selectedContacts);
    }

    @Override
    public int getCount() {
        return results.size();
    }

    @Override
    public Object getItem(int position) {
        return results.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        TrackListViewHolder h = row != null ? (TrackListViewHolder) row.getTag()
                : null;
        if (h == null) {
            h = new TrackListViewHolder(mapView, this);
            h.root = row = LayoutInflater.from(mapView.getContext()).inflate(
                    R.layout.trackhistory_tracklist_item, mapView, false);
            h.root.setOnClickListener(h);
            h.root.setOnLongClickListener(h);

            h.callsign = row.findViewById(
                    R.id.trackhistory_list_item_callsign);
            h.curTrack = row.findViewById(
                    R.id.trackhistory_list_item_currentTrack);
            h.startTime = row.findViewById(
                    R.id.trackhistory_list_item_timeAgo);
            h.elapsedTime = row.findViewById(
                    R.id.trackhistory_list_item_time);
            h.distance = row.findViewById(
                    R.id.trackhistory_list_item_distance);
            h.icon = row.findViewById(
                    R.id.trackhistory_list_item_icon);
            h.titleBtn = row.findViewById(
                    R.id.trackhistory_list_item_titleBtn);
            h.titleBtn.setOnClickListener(h);

            h.selectedCB = row.findViewById(
                    R.id.trackhistory_list_item_selected);
            h.viewCB = row.findViewById(
                    R.id.trackhistory_list_item_viewOnMap);
            h.viewCB.setOnCheckedChangeListener(h);

            h.detailsBtn = row.findViewById(
                    R.id.trackhistory_list_item_btnDetails);
            h.detailsBtn.setOnClickListener(h);

            h.exportBtn = row.findViewById(
                    R.id.trackhistory_list_item_btnExport);
            h.exportBtn.setOnClickListener(h);

            h.deleteBtn = row.findViewById(
                    R.id.trackhistory_list_item_btnDelete);
            h.deleteBtn.setOnClickListener(h);

            row.setTag(h);
        }
        h.track = results.get(position);

        h.callsign.setText(h.track.getUserCallsign());
        h.callsign.setTextColor(h.track.getColor());

        ATAKUtilities.SetUserIcon(mapView, h.icon, h.track.getUserUID());

        h.titleBtn.setText(h.track.getTitle());

        MapItem groupItem = dropDown.getTrackMapGroup().deepFindUID(
                h.track.getTrackUID());
        boolean visible = groupItem != null && groupItem.getVisible();
        h.viewCB.setOnCheckedChangeListener(null);
        h.viewCB.setChecked(visible);
        h.viewCB.setOnCheckedChangeListener(h);

        boolean selected = _bMultiSelectEnabled && this.selected
                .contains(h.track);
        h.selectedCB.setOnCheckedChangeListener(null);
        h.selectedCB.setChecked(selected);
        h.selectedCB.setOnCheckedChangeListener(h);

        // XXX - setSelected doesn't work here for some reason
        h.root.setBackgroundResource(selected || visible
                ? R.drawable.trackhistory_list_item_background_selected
                : R.drawable.trackhistory_list_item_background_normal);

        if (_bMultiSelectEnabled) {
            h.selectedCB.setVisibility(View.VISIBLE);
            h.detailsBtn.setVisibility(View.GONE);
            h.exportBtn.setVisibility(View.GONE);
            h.deleteBtn.setVisibility(View.GONE);
        } else {
            h.selectedCB.setVisibility(View.GONE);
            h.detailsBtn.setVisibility(View.VISIBLE);
            h.exportBtn.setVisibility(View.VISIBLE);
            h.deleteBtn.setVisibility(View.VISIBLE);
        }

        //Log.i(TAG, " building view for index " + position + "; Track: "
        //        + result.getTitle());

        if (h.track.isCurrentTrack()) {
            h.curTrack.setVisibility(View.VISIBLE);
            if (FileSystemUtils.isEquals(h.track.getUserUID(),
                    MapView.getDeviceUid()))
                h.curTrack.setText(R.string.current_active_Track);
            else
                h.curTrack.setText(mapView.getContext().getString(
                        R.string.most_recent_Track)
                        + h.track.getUserCallsign());
        } else
            h.curTrack.setVisibility(TextView.GONE);

        // Start time
        long millisNow = new CoordinatedTime().getMilliseconds();
        long millisAgo = millisNow - h.track.getStartTime();
        if (h.track.getStartTime() > 0) {
            h.startTime.setVisibility(View.VISIBLE);
            h.startTime.setText(MathUtils.GetTimeRemainingOrDateString(
                    millisNow, millisAgo, true));
        } else
            h.startTime.setVisibility(View.INVISIBLE);

        // Elapsed time
        long elapsed = h.track.getTimeElapsedLong();
        if (elapsed > 0) {
            h.elapsedTime.setVisibility(View.VISIBLE);
            h.elapsedTime.setText(MathUtils.GetTimeRemainingString(h.track
                    .getTimeElapsedLong()));
        } else
            h.elapsedTime.setVisibility(View.INVISIBLE);

        // Track distance
        double dist = h.track.getDistanceDouble();
        if (dist > 0) {
            int units = Span.ENGLISH;
            try {
                units = Integer.parseInt(dropDown.getRoutePrefs().getString(
                        "rab_rng_units_pref", String.valueOf(Span.ENGLISH)));
            } catch (Exception ignore) {
            }
            h.distance.setVisibility(View.VISIBLE);
            h.distance.setText(TrackDetails.getDistanceString(dist, units));
        } else
            h.distance.setVisibility(View.INVISIBLE);

        return row;
    }

    public void requestRedraw() {
        try {
            ((Activity) mapView.getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        } catch (ClassCastException e) {
            Log.e(TAG, "error: ", e);
        }
    }

    /**
     * Perform specified sort Optionally force reversal if sort type matches (e.g. user sorted on
     * that type/column a second time)
     */
    synchronized void sortResults(SortType sort, boolean bRequestReverse) {

        // see if we are already sorted this way...
        if (sort.equals(_sort)) {
            // same sort type
            if (bRequestReverse) {
                // sort (maybe user resorted), then reverse
                _sortReversed = !_sortReversed;
            } //else {
              // sort (maybe a new file added or removed), no reverse
              //_sortReversed = _sortReversed; // no-op, stays the same, just here for readability
              //}
        } else {
            // new sort type, just sort
            // no need to reverse even if requested
            _sortReversed = false;
        }

        _sort = sort;
        // Log.d(TAG, "Sorting by: " + sort.toString());

        switch (sort) {
            case TIME: {
                Collections.sort(results, timeComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(results,
                            Collections.reverseOrder(timeComparator));
                }
            }
                break;
            case NAME:
            default: {
                Collections.sort(results, nameComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(results,
                            Collections.reverseOrder(nameComparator));
                }
            }
                break;
        }

        // redraw the view
        requestRedraw();
    }

    static private final Comparator<TrackDetails> nameComparator = new Comparator<TrackDetails>() {
        @Override
        public int compare(TrackDetails c1, TrackDetails c2) {
            return c1.getTitle().compareToIgnoreCase(c2.getTitle());
        }
    };

    static private final Comparator<TrackDetails> timeComparator = new Comparator<TrackDetails>() {
        @Override
        public int compare(TrackDetails c1, TrackDetails c2) {
            return Long.compare(c2.getStartTime(), c1.getStartTime());
        }
    };

    public void select(TrackDetails result) {
        if (!selected.contains(result))
            selected.add(result);
        result.setVisible(true);
        if (dropDown != null)
            dropDown.setSelectAll(selected.size() == results.size());
        requestRedraw();
    }

    public void unselect(TrackDetails result) {
        selected.remove(result);
        result.setVisible(false);
        if (dropDown != null)
            dropDown.setSelectAll(selected.size() == results.size());
        requestRedraw();
    }

    public void toggleTrackPolyline(TrackDetails track, boolean show) {
        MapItem gi = dropDown.getTrackMapGroup().deepFindUID(
                track.getTrackUID());
        if (show && gi == null) {
            if (track.hasPoints()) {
                //we have the full track, go ahead and display
                track.showPolyline(dropDown.getTrackMapGroup());
                //zoom map to view it
                ATAKUtilities.scaleToFit(track.getPolyline());
            } else {
                //query points from DB and then display
                new GetTrackDetailsTask(mapView, track,
                        new GetTrackDetailsTask.Callback() {
                            @Override
                            public void onComplete(TrackDetails track) {
                                if (track == null) {
                                    Log.w(TAG, "Failed to query track details");
                                    return;
                                }

                                track.showPolyline(dropDown.getTrackMapGroup());
                                ATAKUtilities.scaleToFit(track.getPolyline());
                                requestRedraw();
                            }
                        }, dropDown.getRoutePrefs().getBoolean(
                                "elevProfileInterpolateAlt", true))
                                        .execute();
            }
        } else if (gi != null)
            track.removePolyline();
        requestRedraw();
    }

    public void selectAll() {
        selected.clear();
        for (TrackDetails i : results) {
            selected.add(i);
            i.setVisible(true);
        }
        requestRedraw();
    }

    public void unSelectAll() {
        selected.clear();
        for (TrackDetails i : results)
            i.setVisible(false);
        requestRedraw();
    }

    protected void clear() {
        selected.clear();
        results.clear();
        requestRedraw();
    }
}
