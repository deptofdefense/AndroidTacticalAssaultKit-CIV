
package com.atakmap.android.track;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.track.task.GetTrackUsersTask;
import com.atakmap.android.track.ui.TrackSearchView;
import com.atakmap.android.track.ui.TrackUser;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UserListAdapter extends BaseAdapter implements
        GetTrackUsersTask.Callback {
    public enum SortType {
        CALLSIGN,
        NUMTRACKS
    }

    private static final String TAG = "UserListAdapter";

    protected final ArrayList<TrackUser> users = new ArrayList<>();
    private final MapView mapView;
    private TrackHistoryDropDown dropDown;

    /**
     * maintain sorting state
     */
    private SortType _sort;
    private boolean _sortReversed;

    public UserListAdapter(MapView mapView, TrackHistoryDropDown dropDown) {
        this.mapView = mapView;
        this.dropDown = dropDown;
        this._sort = SortType.CALLSIGN;
        this._sortReversed = false;
    }

    public void dispose() {
        users.clear();
        dropDown = null;
    }

    /**
     * Clear results and kickoff async task
     *
     *  @param serverConnectString include this parameter to search server and local DB. Null for local DB only search
     */
    void refresh(String serverConnectString) {
        clear();
        new GetTrackUsersTask(mapView.getContext(), this, serverConnectString)
                .execute();
    }

    @Override
    public void onComplete(List<TrackUser> dbUsers) {
        List<TrackUser> trackUsers = TrackSearchView.getTrackUsers(dbUsers,
                true);
        if (FileSystemUtils.isEmpty(trackUsers)) {
            Log.w(TAG, "No track users");
            //TODO toast?
            return;
        }

        //now wrap for UI
        for (TrackUser user : trackUsers) {
            addResult(user);
        }
    }

    /**
     * Prevents double adding, causes adapter to be redrawn
     * 
     * @param result
     */
    synchronized public void addResult(TrackUser result) {
        if (result == null) {
            Log.d(TAG, "Tried to add NULL result.  Ignoring!");
            return;
        }

        for (TrackUser c : users) {
            if (c.equals(result))
                return;
        }

        //Log.d(TAG, "Adding track details: " + result.toString());
        users.add(result);
        sortResults(this._sort, false);
        requestRedraw();
    }

    /**
     * Causes adapter to be redrawn
     * 
     * @param result
     */
    synchronized public void removeResult(TrackUser result) {
        users.remove(result);
        requestRedraw();
    }

    synchronized public TrackUser getResult(int position) {
        return (TrackUser) getItem(position);
    }

    @Override
    public int getCount() {
        return users.size() + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position >= users.size())
            return null;

        return users.get(position);
    }

    @Override
    public long getItemId(int position) {
        if (position >= users.size())
            return "SearchView".hashCode();

        return users.get(position).hashCode();
    }

    @Override
    public View getView(int position, View convertView,
            final ViewGroup parent) {
        if (users.size() <= position) {
            Log.d(TAG, "Creating search button view for position: "
                    + position);
            convertView = buildButtonView(parent);
            return convertView;
        }

        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        convertView = inf.inflate(R.layout.trackhistory_userlist_item, null);

        final TrackUser result = users.get(position);
        Log.i(TAG, " building view for index " + position + "; Track User: "
                + result.getCallsign());

        TextView txtCallsign = convertView
                .findViewById(R.id.trackhistory_userlist_item_callsign);
        txtCallsign.setText(result.getCallsign());

        ImageView imgIcon = convertView
                .findViewById(R.id.trackhistory_userlist_item_icon);
        ATAKUtilities.SetUserIcon(mapView, imgIcon, result.getUid());

        TextView txtNumTracks = convertView
                .findViewById(R.id.trackhistory_userlist_item_numTracks);
        txtNumTracks.setText(result.getNumberTracks()
                + mapView.getContext().getString(R.string.tracks));

        convertView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                show(result);
            }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                show(result);
                return true;
            }
        });

        return convertView;

    }

    private View buildButtonView(final ViewGroup parent) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        final View convertView = inf.inflate(
                R.layout.trackhistory_tracklist_item_searchrow, null);

        CotPortListActivity.CotPort[] servers = dropDown.getServers();
        boolean bAtleastOneServer = servers != null && servers.length > 0;
        boolean bDisplay = bAtleastOneServer;
        Button btnDetails = convertView
                .findViewById(R.id.trackhistory_list_item_serverSearchBtn);
        btnDetails.setVisibility(bDisplay ? Button.VISIBLE : Button.GONE);
        btnDetails.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                //first select server, then async task to pull client list
                ServerListDialog.selectServer(
                        mapView.getContext(),
                        mapView.getContext().getString(
                                R.string.select_track_server),
                        dropDown.getServers(),
                        new ServerListDialog.Callback() {
                            @Override
                            public void onSelected(
                                    TAKServer server) {
                                if (server == null) {
                                    Log.d(TAG, "No configured server selected");
                                    return;
                                }

                                refresh(server.getConnectString());
                            }
                        });
            }
        });

        return convertView;
    }

    private void show(TrackUser result) {
        Log.d(TAG, "Selected: " + result.toString());

        //zoom map
        MapItem item = mapView.getRootGroup().deepFindUID(result.getUid());
        if (item != null) {
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent("com.atakmap.android.maps.ZOOM_TO_LAYER")
                            .putExtra("uid", result.getUid()));
        }

        if (result.getNumberTracks() > 0)
            dropDown.showTrackListView(result);
        else
            dropDown.showTrackSearchView(result, true);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private void requestRedraw() {
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
            }
            //else {
            //    // sort (maybe a new file added or removed), no reverse
            //    _sortReversed = _sortReversed; // no-op, stays the same, just here for readability
            //}
        } else {
            // new sort type, just sort
            // no need to reverse even if requested
            _sortReversed = false;
        }

        _sort = sort;
        // Log.d(TAG, "Sorting by: " + sort.toString());

        switch (sort) {
            case NUMTRACKS: {
                Collections.sort(users, numTracksComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(users,
                            Collections.reverseOrder(numTracksComparator));
                }
            }
                break;
            case CALLSIGN:
            default: {
                Collections.sort(users, nameComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(users,
                            Collections.reverseOrder(nameComparator));
                }
            }
                break;
        }

        // redraw the view
        requestRedraw();
    }

    static private final Comparator<TrackUser> nameComparator = new Comparator<TrackUser>() {
        @Override
        public int compare(TrackUser c1, TrackUser c2) {
            return c1.getCallsign().compareToIgnoreCase(c2.getCallsign());
        }
    };

    static private final Comparator<TrackUser> numTracksComparator = new Comparator<TrackUser>() {
        @Override
        public int compare(TrackUser c1, TrackUser c2) {
            if (c2.getNumberTracks() == c1.getNumberTracks())
                return 0;

            return c2.getNumberTracks() < c1.getNumberTracks() ? -1 : 1;
        }
    };

    protected void clear() {
        users.clear();
        this.notifyDataSetChanged();
    }
}
