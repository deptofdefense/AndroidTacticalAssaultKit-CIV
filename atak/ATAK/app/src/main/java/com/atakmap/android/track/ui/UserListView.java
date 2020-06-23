
package com.atakmap.android.track.ui;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.app.R;

public class UserListView extends LinearLayout {

    public static final String TAG = "UserListView";
    private ListView _listView;

    public UserListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListAdapter(ListAdapter adapter,
            View.OnClickListener titleHeader,
            View.OnClickListener numTracksHeader) {
        final ListView listView = _getListView();
        View header = ((Activity) getContext()).getLayoutInflater()
                .inflate(R.layout.trackhistory_userlist_item_header, null);
        listView.addHeaderView(header);

        TextView headerName = findViewById(
                R.id.trackhistory_userlist_item_callsign_header);
        if (titleHeader != null)
            headerName.setOnClickListener(titleHeader);
        TextView headerNumTracks = findViewById(
                R.id.trackhistory_userlist_item_numTracks_header);
        if (numTracksHeader != null)
            headerNumTracks.setOnClickListener(numTracksHeader);

        listView.setAdapter(adapter);
        listView.post(new Runnable() {
            @Override
            public void run() {
                int index = listView.getFirstVisiblePosition();
                View v = listView.getChildAt(0);
                int top = (v == null) ? 0 : v.getTop();
                listView.setSelectionFromTop(index, top);
            }
        });
    }

    ListView _getListView() {
        if (_listView == null) {
            _listView = findViewById(R.id.trackhistory_users_list);
        }
        return _listView;
    }
}
