
package com.atakmap.android.missionpackage.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.android.hierarchy.HierarchyListItem.Sort;
import com.atakmap.android.hierarchy.SortSpinner;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.http.datamodel.MissionPackageQueryResult;
import com.atakmap.android.missionpackage.ui.MissionPackageQueryResultListAdapter.SortType;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Based largely on FileTransferLogView
 */
public class MissionPackageQueryResultView extends LinearLayout implements
        OnItemClickListener, OnItemLongClickListener {

    private static final String TAG = "MissionPackageQueryResultView";

    private MissionPackageQueryResultListAdapter _adapter;
    private ListView _list;
    private String _serverConnectString;

    // Views that are hidden when search is active
    private static final int[] nonSearchViews = new int[] {
            R.id.title_layout,
            R.id.iconMissionPackageQueryResultsIcon,
            R.id.btnMissionPackageQueryResultsRefresh,
            R.id.sort_spinner
    };

    public MissionPackageQueryResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void refresh(List<MissionPackageQueryResult> available,
            String serverConnectString) {
        _serverConnectString = serverConnectString;
        if (_list == null) {
            _list = findViewById(
                    R.id.missionpackage_queryresults_listView);
            _list.setOnItemClickListener(this);
            _list.setOnItemLongClickListener(this);
        }

        TextView serverTitle = findViewById(
                R.id.iconMissionPackageQueryResultsServer);
        serverTitle.setText(_serverConnectString);

        final SortSpinner sortSpinner = findViewById(
                R.id.sort_spinner);
        List<Sort> sorts = new ArrayList<>();
        for (SortType s : SortType.values())
            sorts.add(new MissionPackageQueryResultListAdapter.Sort(
                    getContext(), s));
        sortSpinner.setSortModes(sorts);
        sortSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos,
                    long id) {
                MissionPackageQueryResultListAdapter.Sort sort = (MissionPackageQueryResultListAdapter.Sort) sortSpinner
                        .getItemAtPosition(pos);
                if (sort != null)
                    _adapter.sort(sort.getSortType(), true);
            }

        });

        ImageButton buttonRefresh = findViewById(
                R.id.btnMissionPackageQueryResultsRefresh);
        buttonRefresh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder b = new AlertDialog.Builder(getContext());
                b.setTitle(R.string.refresh);
                b.setIcon(R.drawable.sync_original);
                b.setMessage(R.string.refresh_mission_packages);
                b.setCancelable(false);
                b.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int id) {
                                Log.d(TAG, "Refreshing...");
                                clearResults();
                                AtakBroadcast.getInstance().sendBroadcast(
                                        new Intent(
                                                MissionPackageReceiver.MISSIONPACKAGE_QUERY)
                                                        .putExtra(
                                                                "serverConnectString",
                                                                _serverConnectString));
                            }
                        });
                b.setNegativeButton(R.string.no, null);
                b.show();
            }
        });

        final EditText searchTxt = findViewById(R.id.search_txt);
        searchTxt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                InputMethodManager imm = (InputMethodManager) view.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (b)
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                else
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        });
        ImageButton searchBtn = findViewById(R.id.search_btn);
        searchBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSearch(searchTxt.getVisibility() == View.GONE);
            }
        });

        searchTxt.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (searchTxt.getVisibility() == View.VISIBLE) {
                    if (s != null) {
                        String str = s.toString();
                        if (str != null) {
                            _adapter.search(
                                    str.toLowerCase(LocaleUtil.getCurrent()));
                        }
                    }
                }
            }

        });

        _adapter = new MissionPackageQueryResultListAdapter(getContext());
        _adapter.reset(available, _serverConnectString);
        _list.setAdapter(_adapter);
    }

    @Override
    public void onItemClick(AdapterView<?> parentView, View childView,
            int pos, long id) {
        _adapter.showDialog(_adapter.getResult(pos));
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parentView, View childView,
            int pos, long id) {
        _adapter.showDialog(_adapter.getResult(pos));
        return true;
    }

    public boolean onBackButtonPressed() {
        return toggleSearch(false);
    }

    private void clearResults() {
        toggleSearch(false);
        _adapter.reset(new ArrayList<MissionPackageQueryResult>(),
                _serverConnectString);
    }

    private boolean toggleSearch(boolean on) {
        EditText searchTxt = findViewById(R.id.search_txt);
        if (on == (searchTxt.getVisibility() == View.VISIBLE))
            return false;
        for (int id : nonSearchViews) {
            View v = findViewById(id);
            if (v != null)
                v.setVisibility(on ? View.GONE : View.VISIBLE);
        }
        if (on) {
            searchTxt.setText("");
            searchTxt.setVisibility(View.VISIBLE);
            searchTxt.requestFocus();
        } else {
            searchTxt.setVisibility(View.GONE);
            searchTxt.clearFocus();
            _adapter.search(null);
        }
        return true;
    }
}
