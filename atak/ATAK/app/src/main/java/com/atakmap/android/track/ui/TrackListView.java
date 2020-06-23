
package com.atakmap.android.track.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.android.track.TrackHistoryDropDown;
import com.atakmap.android.track.TrackListAdapter;
import com.atakmap.app.R;
import com.atakmap.comms.app.CotPortListActivity;

public class TrackListView extends LinearLayout {

    public static final String TAG = "TrackListView";

    private ListView _listView;
    private CheckBox _selectAll;
    // Cache this to disable/enable when selecting all
    private OnCheckedChangeListener _selectAllListener;
    private TextView _title;
    private Button doneButton;
    private Button cancelButton;
    private CheckBox _hideTemp;
    private CheckBox _displayAll;

    private boolean _multiSelectExportMode;

    public TrackListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        _selectAllListener = null;
    }

    /**
     * Update title for the view
     *
     * @param bExport   true to export tracks, false to delete tracks
     */
    public void setTitle(boolean bExport) {
        _multiSelectExportMode = bExport;
        setTitle(bExport ? "Export" : "Delete");
    }

    public void setTitle(String text) {
        _getTitle().setText(text);
    }

    public boolean getMultiSelectExportMode() {
        return _multiSelectExportMode;
    }

    private TextView _getTitle() {
        if (_title == null) {
            _title = findViewById(R.id.trackhistory_list_title);
        }
        return _title;
    }

    public void setListAdapter(TrackListAdapter adapter,
            TrackHistoryDropDown dropDown) {
        if (dropDown == null)
            return;

        final ListView listView = _getListView();
        LayoutInflater inf = LayoutInflater.from(getContext());
        View header = inf.inflate(R.layout.trackhistory_tracklist_item_header,
                listView, false);
        listView.addHeaderView(header);

        // Search button at the bottom of the list
        View footer = inf.inflate(
                R.layout.trackhistory_tracklist_item_searchrow,
                listView, false);
        CotPortListActivity.CotPort[] servers = dropDown.getServers();
        boolean bAtleastOneServer = servers != null && servers.length > 0;
        boolean bDisplay = !adapter.getMultiSelectEnabled()
                && bAtleastOneServer;
        Button search = footer.findViewById(
                R.id.trackhistory_list_item_serverSearchBtn);
        search.setVisibility(bDisplay ? Button.VISIBLE : Button.GONE);
        search.setOnClickListener(dropDown);
        listView.addFooterView(footer);

        TextView headerName = findViewById(
                R.id.trackhistory_list_item_callsign_header);
        headerName.setOnClickListener(dropDown);
        TextView headerDate = findViewById(
                R.id.trackhistory_list_item_distance_header);
        headerDate.setOnClickListener(dropDown);

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

    public void setDoneClickListener(OnClickListener listener) {
        Button button = _getDoneButton();
        button.setOnClickListener(listener);
    }

    public void setCancelClickListener(OnClickListener listener) {
        Button button = _getCancelButton();
        button.setOnClickListener(listener);
    }

    public void setSelectAllListener(OnCheckedChangeListener listener) {
        _selectAllListener = listener;
        CheckBox selectAll = _getSelectAll();
        selectAll.setOnCheckedChangeListener(listener);
    }

    public void setHideTempListener(OnCheckedChangeListener listener) {
        CheckBox cb = _getHideTemp();
        cb.setOnCheckedChangeListener(listener);
    }

    public void setDisplayAllListener(OnCheckedChangeListener listener) {
        CheckBox cb = _getDisplayAll();
        cb.setOnCheckedChangeListener(listener);
    }

    public void setSelectAllChecked(boolean checked) {
        CheckBox selectAll = _getSelectAll();
        // disable listener callback while update the checkbox
        if (_selectAllListener != null) {
            selectAll.setClickable(false);
            selectAll.setOnCheckedChangeListener(null);
            selectAll.setChecked(checked);
            selectAll.setOnCheckedChangeListener(_selectAllListener);
            selectAll.setClickable(true);
        } else {
            selectAll.setChecked(checked);
        }
    }

    private Button _getDoneButton() {
        if (doneButton == null) {
            doneButton = findViewById(R.id.trackhistory_list_done);
        }
        return doneButton;
    }

    private Button _getCancelButton() {
        if (cancelButton == null) {
            cancelButton = findViewById(R.id.trackhistory_list_cancel);
        }
        return cancelButton;
    }

    private CheckBox _getSelectAll() {
        if (_selectAll == null) {
            _selectAll = findViewById(
                    R.id.trackhistory_list_allSelected);
        }
        return _selectAll;
    }

    private CheckBox _getDisplayAll() {
        if (_displayAll == null) {
            _displayAll = findViewById(
                    R.id.trackhistory_list_displayAll);
        }
        return _displayAll;
    }

    private CheckBox _getHideTemp() {
        if (_hideTemp == null) {
            _hideTemp = findViewById(
                    R.id.trackhistory_list_hideTemp);
        }
        return _hideTemp;
    }

    ListView _getListView() {
        if (_listView == null) {
            _listView = findViewById(R.id.trackhistory_results_list);
        }
        return _listView;
    }

    public void setMultiSelectEnabled(boolean bEnabled) {
        if (bEnabled) {
            _getSelectAll().setVisibility(CheckBox.VISIBLE);
            _getDoneButton().setVisibility(CheckBox.VISIBLE);
            _getCancelButton().setVisibility(CheckBox.VISIBLE);
            _getHideTemp().setVisibility(CheckBox.GONE);
            _getDisplayAll().setVisibility(CheckBox.GONE);
        } else {
            _getSelectAll().setChecked(false);
            _getSelectAll().setVisibility(CheckBox.GONE);
            _getDoneButton().setVisibility(CheckBox.GONE);
            _getCancelButton().setVisibility(CheckBox.GONE);
            _getTitle().setText(R.string.track_list);
            _getHideTemp().setVisibility(CheckBox.VISIBLE);
            _getDisplayAll().setVisibility(CheckBox.VISIBLE);
        }
    }

    public boolean isHideTemp() {
        return _getHideTemp().isChecked();
    }

    public boolean isDisplayAll() {
        return _getDisplayAll().isChecked();
    }

    public void setHideTempVisible(boolean b) {
        _getHideTemp().setVisibility(b ? CheckBox.VISIBLE : CheckBox.INVISIBLE);
    }

    public void setDisplayAllVisible(boolean b) {
        _getDisplayAll().setVisibility(b ? View.VISIBLE : View.INVISIBLE);
    }
}
