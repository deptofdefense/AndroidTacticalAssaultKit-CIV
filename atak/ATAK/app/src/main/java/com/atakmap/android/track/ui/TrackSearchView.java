
package com.atakmap.android.track.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.RangeSeekBar;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.track.TrackHistoryDropDown;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.UserListArrayAdapter;
import com.atakmap.android.track.task.GetTrackSearchTask;
import com.atakmap.android.track.task.GetTrackUsersTask;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.DatePickerFragment;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.comms.TAKServer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.comms.NetConnectString;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.TimeZone;

public class TrackSearchView extends LinearLayout implements
        GetTrackUsersTask.Callback {

    public static final String TAG = "TrackSearchView";

    private RangeSeekBar<Long> seekBar;
    private long searchStart;
    private long searchEnd;

    private TextView textDropDownTitle;
    private Button trackhistory_search_serverBtn;
    private NetConnectString serverConnectString;
    private String selectedUid;
    private String selectedCallsign;
    private Button callsignBtn;
    private ImageView callsignIcon;

    private TextView startMonthTV;
    private TextView startDateTV;
    private TextView startYearTV;
    private TextView endMonthTV;
    private TextView endDateTV;
    private TextView endYearTV;

    private Button searchBtn;
    private CheckBox searchOnServer;

    private SimpleDateFormat _monthFormat;
    private TrackHistoryDropDown _dropDown;
    private ProgressDialog _progressDialogServerSearch;

    public TrackSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void refresh(TrackHistoryDropDown dropDown, TrackUser user,
            boolean bServerSearch) {
        _monthFormat = new SimpleDateFormat("MMM", LocaleUtil.getCurrent());
        _monthFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        _dropDown = dropDown;

        getSearchButton(bServerSearch);
        getCallsign(user);
        getDateRangeSeekBar();

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.setTime(new Date());
        getEndDate(cal);

        cal.add(Calendar.DATE, -7);
        getStartDate(cal);
    }

    private void getStartDate(Calendar cal) {
        int startYear = cal.get(Calendar.YEAR);
        int startMonth = cal.get(Calendar.MONTH);
        int startDayOfMonth = cal.get(Calendar.DAY_OF_MONTH);

        startMonthTV = findViewById(
                R.id.trackhistory_search_startTimeMonth);
        startDateTV = findViewById(
                R.id.trackhistory_search_startTimeDate);
        startYearTV = findViewById(
                R.id.trackhistory_search_startTimeYear);

        startMonthTV.setOnClickListener(startCalListener);
        startDateTV.setOnClickListener(startCalListener);
        startYearTV.setOnClickListener(startCalListener);

        updateStartDate(startYear, startMonth, startDayOfMonth);
    }

    private final OnClickListener startCalListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Date maxDate = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(maxDate);
            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            cal.add(Calendar.DATE, -30);
            Date minDate = cal.getTime();

            DatePickerFragment startFragment = new DatePickerFragment();
            startFragment.init(searchStart, startDatePickedListener,
                    minDate.getTime(), maxDate.getTime());
            startFragment.show(((Activity) getContext()).getFragmentManager(),
                    "startDatePicker");
        }
    };

    private void updateStartDate(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.setTime(new Date(millis));
        updateStartDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
    }

    private void updateStartDate(int year, int month, int dayOfMonth) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        searchStart = cal.getTime().getTime();

        startMonthTV.setText(_monthFormat.format(searchStart));
        startDateTV.setText(String.format(LocaleUtil.getCurrent(), "%02d",
                dayOfMonth));
        startYearTV.setText(String.format(LocaleUtil.getCurrent(), "%04d ",
                year));
    }

    private final DatePickerFragment.DatePickerListener startDatePickedListener = new DatePickerFragment.DatePickerListener() {
        @Override
        public void onDatePicked(int year, int month, int dayOfMonth) {
            //update the calendar label
            updateStartDate(year, month, dayOfMonth);

            //get beginning of that day
            Calendar cal = Calendar.getInstance();
            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            cal.setTime(new Date(searchStart));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            //now update UI
            setSeekBarValues(cal.getTimeInMillis(), -1);
        }
    };

    private void getEndDate(Calendar cal) {
        int endYear = cal.get(Calendar.YEAR);
        int endMonth = cal.get(Calendar.MONTH);
        int endDayOfMonth = cal.get(Calendar.DAY_OF_MONTH);

        endMonthTV = findViewById(
                R.id.trackhistory_search_endTimeMonth);
        endDateTV = findViewById(
                R.id.trackhistory_search_endTimeDate);
        endYearTV = findViewById(
                R.id.trackhistory_search_endTimeYear);

        endMonthTV.setOnClickListener(endCalListener);
        endDateTV.setOnClickListener(endCalListener);
        endYearTV.setOnClickListener(endCalListener);

        updateEndDate(endYear, endMonth, endDayOfMonth);
    }

    private final OnClickListener endCalListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Date maxDate = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(maxDate);
            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            cal.add(Calendar.DATE, -30);
            Date minDate = cal.getTime();

            DatePickerFragment startFragment = new DatePickerFragment();
            startFragment.init(searchEnd, endDatePickedListener,
                    minDate.getTime(), maxDate.getTime());
            startFragment.show(((Activity) getContext()).getFragmentManager(),
                    "endDatePicker");
        }
    };

    private void updateEndDate(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.setTime(new Date(millis));
        updateEndDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
    }

    private void updateEndDate(int year, int month, int dayOfMonth) {
        //end of the day on search end date
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        searchEnd = cal.getTime().getTime();

        endMonthTV.setText(_monthFormat.format(searchEnd));
        endDateTV.setText(String.format(LocaleUtil.getCurrent(), "%02d",
                dayOfMonth));
        endYearTV
                .setText(String.format(LocaleUtil.getCurrent(), "%04d ", year));
    }

    private final DatePickerFragment.DatePickerListener endDatePickedListener = new DatePickerFragment.DatePickerListener() {
        @Override
        public void onDatePicked(int year, int month, int dayOfMonth) {
            //update the clock label
            updateEndDate(year, month, dayOfMonth);

            //get beginning of that day
            Calendar cal = Calendar.getInstance();
            cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            cal.setTime(new Date(searchEnd));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            //now update UI
            setSeekBarValues(-1, cal.getTimeInMillis());
        }
    };

    private void getSearchButton(boolean bServerSearch) {

        //get views
        textDropDownTitle = findViewById(
                R.id.trackhistory_search_title);
        trackhistory_search_serverBtn = findViewById(
                R.id.trackhistory_search_serverBtn);
        serverConnectString = null;
        searchOnServer = findViewById(
                R.id.trackhistory_search_onServer);
        searchBtn = findViewById(R.id.trackhistory_search_button);

        //see if we should default to server search (must be requested, shows first server in list)
        boolean bLocalConfig = true;
        CotPortListActivity.CotPort[] servers = _dropDown.getServers();
        String firstUrl = (servers == null || servers.length < 1) ? ""
                : ServerListDialog.getBaseUrl(servers[0]);

        if (bServerSearch) {
            if (!FileSystemUtils.isEmpty(firstUrl)
                    && !FileSystemUtils.isEmpty(servers)) {
                textDropDownTitle.setText(getContext().getString(
                        R.string.trackHistoryTitleServer));
                serverConnectString = NetConnectString.fromString(servers[0]
                        .getConnectString());
                trackhistory_search_serverBtn.setText(firstUrl);
                trackhistory_search_serverBtn.setVisibility(TextView.VISIBLE);
                searchOnServer.setChecked(true);
                bLocalConfig = false;
            } else {
                Log.d(TAG, "No configured server to search");
                Toast.makeText(getContext(),
                        getContext().getString(R.string.no_configured_server),
                        Toast.LENGTH_LONG).show();
            }
        }

        //fallback to local search
        if (bLocalConfig) {
            textDropDownTitle.setText(getContext().getString(
                    R.string.trackHistoryTitleLocal));
            serverConnectString = null;
            trackhistory_search_serverBtn.setText("");
            trackhistory_search_serverBtn.setVisibility(TextView.GONE);
            searchOnServer.setChecked(false);
        }

        searchOnServer.setEnabled(!FileSystemUtils.isEmpty(firstUrl));

        trackhistory_search_serverBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ServerListDialog.selectServer(getContext(),
                        getContext().getString(R.string.select_track_server),
                        _dropDown.getServers(),
                        new ServerListDialog.Callback() {
                            @Override
                            public void onSelected(
                                    TAKServer server) {
                                if (server == null) {
                                    Log.d(TAG, "No configured server selected");
                                    return;
                                }

                                String url = ServerListDialog
                                        .getBaseUrl(server);
                                if (!FileSystemUtils.isEmpty(url)) {
                                    serverConnectString = NetConnectString
                                            .fromString(server
                                                    .getConnectString());
                                    trackhistory_search_serverBtn.setText(url);
                                } else {
                                    Log.d(TAG,
                                            "No configured server url selected");
                                    Toast.makeText(
                                            getContext(),
                                            getContext()
                                                    .getString(
                                                            R.string.no_server_url_selected),
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            }
        });

        searchBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                //search local DB (or server), and then display details
                if (searchOnServer.isChecked()) {
                    //display progress dialog until response comes back
                    //if users cancels dialog, then ignore response when it comes in...
                    _progressDialogServerSearch = new ProgressDialog(
                            getContext());
                    _progressDialogServerSearch.setTitle(getContext()
                            .getString(R.string.searching_server));
                    _progressDialogServerSearch
                            .setIcon(R.drawable.ic_track_search);
                    _progressDialogServerSearch
                            .setMessage(getContext().getString(
                                    R.string.searching_with_space)
                                    + selectedCallsign
                                    + getContext().getString(
                                            R.string.tracks_on_server));
                    _progressDialogServerSearch.setIndeterminate(true);
                    _progressDialogServerSearch.setCancelable(true);
                    _progressDialogServerSearch.show();
                    //_progressDialogServerSearch.setOnDismissListener();
                    _progressDialogServerSearch
                            .setOnCancelListener(
                                    new DialogInterface.OnCancelListener() {
                                        @Override
                                        public void onCancel(
                                                DialogInterface dialog) {
                                            Log.d(TAG,
                                                    "Server search cancelled");
                                            if (_progressDialogServerSearch != null) {
                                                _progressDialogServerSearch
                                                        .dismiss();
                                                _progressDialogServerSearch = null;
                                            }
                                        }
                                    });

                    //use HTTP Request Service to search server
                    String connectedUrl = trackhistory_search_serverBtn
                            .getText().toString();
                    _dropDown.searchRemoteTracks(connectedUrl,
                            selectedCallsign, selectedUid, searchStart,
                            searchEnd);
                } else {
                    //just search local DB
                    new GetTrackSearchTask(
                            getContext(),
                            selectedCallsign,
                            selectedUid,
                            searchStart,
                            searchEnd,
                            new GetTrackSearchTask.Callback() {
                                @Override
                                public void onComplete(
                                        List<TrackPolyline> tracks) {
                                    if (!FileSystemUtils.isEmpty(tracks)) {
                                        _dropDown
                                                .showTrackListView(
                                                        new TrackUser(
                                                                selectedCallsign,
                                                                selectedUid, 0),
                                                        tracks, true);

                                    } else {
                                        Log.w(TAG, "No tracks for: "
                                                + selectedUid);
                                        Toast.makeText(
                                                getContext(),
                                                getContext()
                                                        .getString(
                                                                R.string.no_tracks_found_for_user),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }).execute();
                }

            }
        });

        searchOnServer
                .setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton compoundButton,
                                    boolean isChecked) {
                                textDropDownTitle.setText(isChecked
                                        ? getContext().getString(
                                                R.string.trackHistoryTitleServer)
                                        : getContext().getString(
                                                R.string.trackHistoryTitleLocal));

                                if (isChecked) {
                                    CotPortListActivity.CotPort[] servers = _dropDown
                                            .getServers();
                                    String firstUrl = (servers == null
                                            || servers.length < 1) ? ""
                                                    : ServerListDialog
                                                            .getBaseUrl(
                                                                    servers[0]);
                                    if (!FileSystemUtils.isEmpty(firstUrl) &&
                                            !FileSystemUtils.isEmpty(servers)) {

                                        serverConnectString = NetConnectString
                                                .fromString(servers[0]
                                                        .getConnectString());
                                        trackhistory_search_serverBtn
                                                .setText(firstUrl);
                                    } else {
                                        Log.d(TAG,
                                                "No configured server to search");
                                        Toast.makeText(
                                                getContext(),
                                                getContext().getString(
                                                        R.string.no_configured_server),
                                                Toast.LENGTH_LONG).show();
                                        trackhistory_search_serverBtn
                                                .setText("");
                                    }

                                    trackhistory_search_serverBtn
                                            .setVisibility(TextView.VISIBLE);
                                } else {
                                    serverConnectString = null;
                                    trackhistory_search_serverBtn
                                            .setVisibility(TextView.GONE);
                                }
                            }
                        });
    }

    public void reset() {
        Log.d(TAG, "reset");
        if (_progressDialogServerSearch != null) {
            _progressDialogServerSearch.dismiss();
            _progressDialogServerSearch = null;
        }
    }

    private void getCallsign(TrackUser user) {
        if (user == null) {
            //default to self
            selectedUid = MapView.getDeviceUid();
            selectedCallsign = MapView.getMapView().getDeviceCallsign();
        } else {
            selectedUid = user.getUid();
            selectedCallsign = user.getCallsign();
        }

        callsignIcon = findViewById(
                R.id.trackhistory_search_callsignIcon);
        ATAKUtilities.SetUserIcon(_dropDown.getMapView(), callsignIcon,
                selectedUid);

        callsignBtn = findViewById(
                R.id.trackhistory_search_callsignBtn);
        callsignBtn.setText(selectedCallsign);
        callsignBtn.setOnClickListener(userListListener);
        callsignIcon.setOnClickListener(userListListener);
    }

    private OnClickListener userListListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (!searchOnServer.isChecked()) {
                //Background task to pull list of users from local DB
                new GetTrackUsersTask(getContext(),
                        TrackSearchView.this, null).execute();
                return;
            }

            //use already selected server
            String connectString = null;
            if (serverConnectString != null) {
                connectString = serverConnectString.toString();
            }
            new GetTrackUsersTask(getContext(),
                    TrackSearchView.this, connectString).execute();
        }
    };

    /**
     * Async task callback which is called with a list of users which have tracks
     * in the local DB
     * @param dbUsers the list of users that have tracks in the local database.
     */
    @Override
    public void onComplete(List<TrackUser> dbUsers) {
        List<TrackUser> trackUsers = getTrackUsers(dbUsers,
                searchOnServer.isChecked());
        TrackUser[] trackUsersArray = new TrackUser[trackUsers.size()];
        trackUsers.toArray(trackUsersArray);
        final UserListArrayAdapter trackUserAdapter = new UserListArrayAdapter(
                getContext(), R.layout.trackhistory_userlist_search_item,
                trackUsersArray, !searchOnServer.isChecked());

        LayoutInflater inflater = LayoutInflater.from(getContext());
        LinearLayout layout = (LinearLayout) inflater.inflate(
                R.layout.trackhistory_userlist_search, null);
        ListView listView = layout
                .findViewById(R.id.trackhistory_userlist_search_list);
        listView.setAdapter(trackUserAdapter);

        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setIcon(R.drawable.ic_track_userlist);
        b.setTitle(getContext().getString(R.string.select_user));
        b.setView(layout);
        b.setNegativeButton(R.string.cancel, null);

        final AlertDialog bd = b.create();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                bd.dismiss();

                TrackUser trackUser = trackUserAdapter.getItem(arg2);
                if (trackUser == null
                        || FileSystemUtils.isEmpty(trackUser.getUid())) {
                    Toast.makeText(
                            getContext(),
                            getContext().getString(R.string.failed_search_user),
                            Toast.LENGTH_SHORT).show();
                    Log.w(TAG,
                            "Failed to search selected items, no UID selected");
                    return;
                }

                selectedUid = trackUser.getUid();
                selectedCallsign = trackUser.getCallsign();
                callsignBtn.setText(selectedCallsign);
                ATAKUtilities.SetUserIcon(_dropDown.getMapView(), callsignIcon,
                        selectedUid);

                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        "com.atakmap.android.maps.ZOOM_TO_LAYER")
                                .putExtra("uid", selectedUid));
            }
        });

        bd.show();
    }

    /**
     * Get list of users which have tracks in local DB
     * Optionally, include all users in the local contact list
     * Always includes "self"
     *
     * @param dbUsers
     * @param bIncludeAllContacts
     * @return the list of track users based on the local database
     */
    public static List<TrackUser> getTrackUsers(final List<TrackUser> dbUsers,
            final boolean bIncludeAllContacts) {
        List<TrackUser> trackUsers = new ArrayList<>();

        //always display those users which have locally stored tracks
        if (!FileSystemUtils.isEmpty(dbUsers)) {
            Log.d(TAG, "Found " + dbUsers.size()
                    + " users with tracks on local device");
            trackUsers.addAll(dbUsers);
        }

        //only need to include other contacts/users if searching on server
        //TODO replace this with a server contact list rather than just who this device has seen online since ATAK restart
        //TODO if checked, and remote user selected, and then unchecked, and remote user has no local tracks, then no results will be found, handle it gracefully...
        if (bIncludeAllContacts) {
            Log.d(TAG, "Getting contacts for server search");
            Contacts contactMgr = Contacts.getInstance();
            IndividualContact[] indivContacts = contactMgr
                    .getIndividualContactsByUuid(
                            contactMgr.getAllIndividualContactUuids());
            if (indivContacts != null && indivContacts.length > 0) {
                Log.d(TAG, "Found " + indivContacts.length
                        + " users in contact list");
                for (IndividualContact indivContact : indivContacts) {
                    if (FileSystemUtils.isEmpty(indivContact.getUID())) {
                        Log.w(TAG, "Skipping invalid Individual contact");
                        continue;
                    }

                    //TODO shouldn't Contacts filter these out?
                    if (ChatManagerMapComponent.isSpecialGroup(indivContact
                            .getName())) {
                        Log.d(TAG, "Skipping 'special' contact: "
                                + indivContact.getName());
                        continue;
                    }

                    TrackUser curTrackUser = new TrackUser(
                            indivContact.getName(), indivContact.getUID(), 0);
                    if (trackUsers.contains(curTrackUser)) {
                        Log.d(TAG,
                                "Skipping individual contact already processed: "
                                        + curTrackUser);
                        continue;
                    }

                    //TODO what is indivContact.getAssociatedMarkerUID()?
                    trackUsers.add(curTrackUser);
                }
            }
        }

        //be sure self is always included
        TrackUser self = new TrackUser(
                MapView.getMapView().getDeviceCallsign(),
                MapView.getDeviceUid(), 0);
        if (!trackUsers.contains(self)) {
            Log.d(TAG, "Adding self track user");
            trackUsers.add(self);
        }

        return trackUsers;
    }

    private void getDateRangeSeekBar() {
        View sliderView = findViewById(R.id.trackhistory_date_resolutionLayout);
        sliderView.setVisibility(View.VISIBLE);

        LinearLayout rangeView = findViewById(
                R.id.trackhistory_date_rangeSeekLayout);

        // create RangeSeekBar as Long for use as date range
        Date maxDate = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(maxDate);
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.DATE, -30);
        Date minDate = cal.getTime();
        seekBar = new RangeSeekBar<>(minDate.getTime(), maxDate.getTime(),
                getContext());
        seekBar.setNotifyWhileDragging(true);
        seekBar.setOnRangeSeekBarChangeListener(
                new RangeSeekBar.OnRangeSeekBarChangeListener<Long>() {
                    @Override
                    public void onRangeSeekBarValuesChanged(RangeSeekBar<?> bar,
                            Long minValue,
                            Long maxValue) {
                        // update the cached values and UI labels
                        setDateRange(minValue, maxValue);
                        updateStartDate(minValue);
                        updateEndDate(maxValue);
                    }
                });

        // add RangeSeekBar to pre-defined layout
        rangeView.addView(seekBar);

        // set the seek bar knobs in the right spots (last week)
        cal.setTime(maxDate);
        cal.add(Calendar.DATE, -7);
        Date initialStartTime = cal.getTime();
        setSeekBarValues(initialStartTime.getTime(), maxDate.getTime());
    }

    /**
     * Update the range seek selected values
     * Also update the text label, and the search start/end values
     * @param minMillis the minimum time in milliseconds since epoch
     * @param maxMillis the maximum time in milliseconds since epoch
     */
    private void setSeekBarValues(long minMillis, long maxMillis) {
        //Log.d(TAG, "setSeekBarValues");
        if (seekBar != null) {
            if (minMillis > 0) {
                //Log.d(TAG, "setSelectedMinValue min: " + minMillis);
                seekBar.setSelectedMinValue(minMillis);
            }
            if (maxMillis > 0) {
                //Log.d(TAG, "setSelectedMaxValue max: " + maxMillis);
                seekBar.setSelectedMaxValue(maxMillis);
            }
        }

        setDateRange(minMillis > 0 ? minMillis : searchStart,
                maxMillis > 0 ? maxMillis : searchEnd);
    }

    /**
     * set the search start/end values
     *
     * @param minValue the minimum time in milliseconds since epoch
     * @param maxValue the maximum time in milliseconds since epoch
    
     */
    private void setDateRange(long minValue, long maxValue) {
        searchStart = minValue;
        searchEnd = maxValue;
    }
}
