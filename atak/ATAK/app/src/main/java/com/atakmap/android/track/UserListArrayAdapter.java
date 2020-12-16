
package com.atakmap.android.track;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.track.ui.TrackUser;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;

/**
 * Display a list of track users
 * 
 * 
 */
public class UserListArrayAdapter extends ArrayAdapter<TrackUser> {

    private final Context context;
    private final int layoutResourceId;
    private final TrackUser[] data;
    private final boolean bLocalSearch;

    public UserListArrayAdapter(Context context, int layoutResourceId,
            TrackUser[] data, boolean bLocalSearch) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
        this.bLocalSearch = bLocalSearch;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView,
            @NonNull ViewGroup parent) {
        View row = convertView;
        TrackUserHolder holder;

        if (row == null) {
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);

            holder = new TrackUserHolder();
            holder.imgIcon = row
                    .findViewById(R.id.trackhistory_userlist_search_item_icon);
            holder.txtTitle = row
                    .findViewById(
                            R.id.trackhistory_userlist_search_item_callsign);
            holder.numTracks = row
                    .findViewById(
                            R.id.trackhistory_userlist_search_item_numTracks);

            row.setTag(holder);
        } else {
            holder = (TrackUserHolder) row.getTag();
        }

        TrackUser trackUser = data[position];
        holder.txtTitle.setText(trackUser.getCallsign());
        holder.numTracks.setText("" + trackUser.getNumberTracks() + " tracks");
        holder.numTracks.setVisibility(bLocalSearch ? TextView.VISIBLE
                : TextView.INVISIBLE);

        ATAKUtilities.SetUserIcon(MapView.getMapView(), holder.imgIcon,
                trackUser.getUid());
        return row;
    }

    private static class TrackUserHolder {
        ImageView imgIcon;
        TextView txtTitle;
        TextView numTracks;
    }
}
