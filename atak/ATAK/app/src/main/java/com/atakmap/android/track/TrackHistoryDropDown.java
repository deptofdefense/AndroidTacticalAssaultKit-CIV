
package com.atakmap.android.track;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.importexport.ExportMarshal;
import com.atakmap.android.importexport.ExporterManager;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.elevation.RouteElevationBroadcastReceiver;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.http.PostUserTracksOperation;
import com.atakmap.android.track.http.PostUserTracksRequest;
import com.atakmap.android.track.http.QueryUserTracksOperation;
import com.atakmap.android.track.http.QueryUserTracksRequest;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.task.ExportTrackParams;
import com.atakmap.android.track.task.GetTrackDetailsTask;
import com.atakmap.android.track.ui.TrackDetailsView;
import com.atakmap.android.track.ui.TrackExportDialog;
import com.atakmap.android.track.ui.TrackListView;
import com.atakmap.android.track.ui.TrackSearchView;
import com.atakmap.android.track.ui.TrackUser;
import com.atakmap.android.track.ui.UserListView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.app.CotPortListActivity.CotPort;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Track History Dropdown to allow user to select which track history to display
 * 
 * 
 */
public class TrackHistoryDropDown extends DropDownReceiver implements
        TrackChangedListener, RequestManager.RequestListener,
        View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        DropDown.OnStateListener {

    protected static final String TAG = "TrackHistoryDropDown";

    static final String TRACK_HISTORY = "com.atakmap.android.track.TRACK_HISTORY";
    static final String DELETE_TRACK = "com.atakmap.android.track.DELETE_TRACK";
    static final String TRACK_USERLIST = "com.atakmap.android.track.TRACK_USERLIST";
    static final String TRACK_SEARCH = "com.atakmap.android.track.TRACK_SEARCH";
    static final String CLEAR_TRACKS = "com.atakmap.android.track.CLEAR_TRACKS";
    public static final String TRACKSEXPORTED = "com.atakmap.android.toolbars.self.TRACKSEXPORTED";
    public static final String SERVERTRACKSEXPORTED = "com.atakmap.android.toolbars.self.SERVERTRACKSEXPORTED";
    public static final String EXPORT_TRACK_HISTORY = "com.atakmap.android.bread.EXPORT_TRACK_HISTORY";

    private int curNotificationId = 94350;

    public static final String ROUTE = "Route";
    public static final String KML = "KML";
    public static final String TAKServer = "TAK Server";

    private final List<DropDownReceiver> _subDropDowns = new ArrayList<>();

    private final Context _context;
    private final SharedPreferences _prefs;
    private final MapGroup _trackMapGroup;

    private final TrackListAdapter _trackListAdapter;
    private TrackListView _trackListView;
    private ActionBarView _trackListToolbar;

    private TrackDetailsView _trackDetailsView;
    private SubTrackDropDown _trackDetailsDropDown;
    private TrackSearchView _trackSearchView;

    private UserListView _userListView;
    private final UserListAdapter _userListAdapter;

    private boolean bTrackListReverseSortOrder;
    private boolean bUserListReverseSortOrder;

    private CotStreamListener _serverListener;

    // ATAK Track History Requests
    public final static int REQUEST_TYPE_GET_USER_TRACK;
    public final static int REQUEST_TYPE_POST_USER_TRACK;

    static {
        REQUEST_TYPE_GET_USER_TRACK = NetworkOperationManager
                .register(
                        "com.atakmap.android.track.http.QueryUserTracksOperation",
                        new com.atakmap.android.track.http.QueryUserTracksOperation());

        REQUEST_TYPE_POST_USER_TRACK = NetworkOperationManager
                .register(
                        "com.atakmap.android.track.http.PostUserTracksOperation",
                        new com.atakmap.android.track.http.PostUserTracksOperation());
    }

    public TrackHistoryDropDown(MapView mapView, MapGroup trackGroup) {
        super(mapView);
        _context = mapView.getContext();
        _trackMapGroup = trackGroup;

        //TODO any concurrency issues with CrumbDatabase?
        _trackListAdapter = new TrackListAdapter(mapView, this);
        _userListAdapter = new UserListAdapter(mapView, this);
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _serverListener = new CotStreamListener(_context, TAG, null);
        setRetain(true);
    }

    @Override
    public void disposeImpl() {
        if (_trackListAdapter != null)
            _trackListAdapter.dispose();
        if (_userListAdapter != null)
            _userListAdapter.dispose();
        if (_serverListener != null) {
            _serverListener.dispose();
            _serverListener = null;
        }
    }

    /**
     * Sets toolbar and button handlers. Assumes all track history toolbars have some subet
     * of the button list used in this method
     *
     * @param resource
     * @return
     */
    private synchronized ActionBarView getToolbarView(int resource) {
        LayoutInflater inflater = LayoutInflater.from(_context);
        ActionBarView toolbarView = (ActionBarView) inflater.inflate(
                resource, null);

        ImageButton buttonTrackCallsigns = toolbarView
                .findViewById(R.id.buttonTrackCallsigns);
        if (buttonTrackCallsigns != null)
            buttonTrackCallsigns.setOnClickListener(this);

        ImageButton buttonTrackSearch = toolbarView
                .findViewById(R.id.buttonTrackSearch);
        if (buttonTrackSearch != null)
            buttonTrackSearch.setOnClickListener(this);

        ImageButton buttonTrackList = toolbarView
                .findViewById(R.id.buttonTrackList);
        if (buttonTrackList != null)
            buttonTrackList.setOnClickListener(this);

        ImageButton buttonTrackStartNewTrack = toolbarView
                .findViewById(R.id.buttonTrackStartNewTrack);
        if (buttonTrackStartNewTrack != null)
            buttonTrackStartNewTrack.setOnClickListener(this);

        ImageButton buttonTrackMultiSelect = toolbarView
                .findViewById(R.id.buttonTrackMultiSelect);
        if (buttonTrackMultiSelect != null) {
            buttonTrackMultiSelect.setOnClickListener(this);
        }

        ImageButton buttonTrackListClear = toolbarView
                .findViewById(R.id.buttonTrackListClear);
        if (buttonTrackListClear != null)
            buttonTrackListClear.setOnClickListener(this);

        ImageButton buttonTrackExport = toolbarView
                .findViewById(R.id.buttonTrackExport);
        if (buttonTrackExport != null)
            buttonTrackExport.setOnClickListener(this);

        ImageButton buttonTrackProfile = toolbarView
                .findViewById(R.id.buttonTrackProfile);
        if (buttonTrackProfile != null)
            buttonTrackProfile.setOnClickListener(this);

        return toolbarView;
    }

    public static void promptNewTrack(final SharedPreferences prefs,
            final MapView mapView, final boolean showDetails) {

        // This method must be called on the UI thread
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    promptNewTrack(prefs, mapView, showDetails);
                }
            });
            return;
        }

        if (!prefs.getBoolean("toggle_log_tracks", true)) {
            Toast.makeText(
                    mapView.getContext(),
                    mapView.getContext().getString(
                            R.string.enable_track_logging),
                    Toast.LENGTH_LONG).show();
            return;
        }

        String defaultName = BreadcrumbReceiver
                .getTrackTitle(prefs);
        final EditText editName = new EditText(mapView
                .getContext());
        editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editName.setText(defaultName);

        new AlertDialog.Builder(mapView.getContext())
                .setIcon(R.drawable.ic_track_new)
                .setTitle(
                        mapView.getResources().getString(
                                R.string.enter_new_track_name))
                .setView(editName)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog, int i) {
                                dialog.dismiss();
                                String name = editName.getText()
                                        .toString();
                                if (FileSystemUtils.isEmpty(name)) {
                                    Log.d(TAG,
                                            "No name specified for new track");
                                    Toast.makeText(
                                            mapView
                                                    .getContext(),
                                            (mapView.getResources()
                                                    .getString(
                                                            R.string.no_name_track_cancelled)),
                                            Toast.LENGTH_LONG)
                                            .show();
                                    return;
                                }

                                createNewTrackSegment(name, prefs, mapView,
                                        showDetails);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearTracksOnMap() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setIcon(R.drawable.ic_track_clear);
        b.setTitle(R.string.confirm_clear);
        b.setMessage(R.string.clear_tracks);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                if (_trackMapGroup != null)
                    _trackMapGroup.clearItems();

                if (_trackListAdapter != null)
                    _trackListAdapter.requestRedraw();

                Log.d(TAG, "Cleared tracks on map");
                Toast.makeText(_context, _context.getString(
                        R.string.clearing_tracks), Toast.LENGTH_LONG).show();
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    public static void createNewTrackSegment(String title,
            SharedPreferences prefs, MapView mapView, boolean showDetails) {
        if (FileSystemUtils.isEmpty(title)) {
            Log.w(TAG, "Cannot create segment without title");
            return;
        }

        Log.d(TAG, "Creating New Self Track: " + title);

        CrumbDatabase.instance().createSegment(
                new CoordinatedTime().getMilliseconds(),
                BreadcrumbReceiver.getNextColor(prefs),
                title, BreadcrumbReceiver.DEFAULT_LINE_STYLE,
                mapView.getDeviceCallsign(), MapView.getDeviceUid(), true);

        mapView.post(new Runnable() {
            @Override
            public void run() {

                Toast.makeText(mapView.getContext(),
                        mapView.getContext()
                                .getString(R.string.new_track_segment),
                        Toast.LENGTH_LONG).show();
            }
        });

        if (showDetails)
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(TRACK_HISTORY));
    }

    /**
     * Refresh the list view
     * @param track
     */
    @Override
    public void onTrackChanged(TrackDetails track) {
        if (_trackListAdapter != null) {
            _trackListAdapter.requestRedraw();
        }
    }

    public void showTrackListView(final TrackUser user) {
        showTrackListView(user, true, false);
    }

    void showTrackListView(final TrackUser user, final boolean bHideTemp,
            final boolean bDisplayAll) {
        if (isVisible())
            closeDropDown();

        //when main drop down is opened, also set the toolbar
        setRetain(true);
        showDropDown(getTrackListView(user, bHideTemp, bDisplayAll),
                HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, this);
    }

    /**
     * Show list of tracks for specified user UID
     * If UID is null, then reuse adapter's current UID if set. Otherwise, use self/device UID
     *
     * @param user the track user
     * @param bHideTemp if the temporary tracks should be hidden
     * @param bDisplayAll
     * @return
     */
    private synchronized View getTrackListView(final TrackUser user,
            final boolean bHideTemp, final boolean bDisplayAll) {
        LayoutInflater inflater = LayoutInflater
                .from(_context);
        _trackListView = (TrackListView) inflater.inflate(
                R.layout.trackhistory_tracklist,
                null);

        bTrackListReverseSortOrder = false;

        _trackListView.setListAdapter(_trackListAdapter, this);
        _trackListView.setDoneClickListener(this);
        _trackListView.setCancelClickListener(this);
        _trackListView.setSelectAllListener(this);
        _trackListView.setHideTempListener(this);
        _trackListView.setDisplayAllListener(this);

        //use UID if provided
        TrackUser finalUser = user;
        if (finalUser == null) {
            //see if we have a UID we are already viewing
            finalUser = _trackListAdapter.getCurrentUser();
            if (finalUser == null) {
                //fallback on self UID
                finalUser = new TrackUser(getMapView()
                        .getDeviceCallsign(),
                        MapView.getDeviceUid(), 0);
            }
        }

        _trackListView.setHideTempVisible(!bDisplayAll);
        _trackListAdapter.refresh(finalUser, bHideTemp, bDisplayAll);
        return _trackListView;
    }

    /**
     * Show specified list of tracks for specified user UID
     *
     * @param user
     * @param tracks
     * @return
     */
    private synchronized TrackListView getTrackListView(final TrackUser user,
            final List<TrackPolyline> tracks) {
        LayoutInflater inflater = LayoutInflater
                .from(_context);
        _trackListView = (TrackListView) inflater.inflate(
                R.layout.trackhistory_tracklist,
                null);

        bTrackListReverseSortOrder = false;

        _trackListView.setListAdapter(_trackListAdapter, this);
        _trackListView.setDoneClickListener(this);
        _trackListView.setCancelClickListener(this);
        _trackListView.setSelectAllListener(this);
        _trackListView.setHideTempListener(this);
        _trackListView.setDisplayAllListener(this);

        //use UID if provided
        TrackUser finalUser = user;
        if (finalUser == null) {
            //see if we have a UID we are already viewing
            finalUser = _trackListAdapter.getCurrentUser();
            if (finalUser == null) {
                //fallback on self UID
                finalUser = new TrackUser(getMapView()
                        .getDeviceCallsign(),
                        MapView.getDeviceUid(), 0);
            }
        }

        _trackListView.setHideTempVisible(true);
        _trackListAdapter.refresh(finalUser, tracks);
        return _trackListView;
    }

    /**
     * Show specified list of tracks for specified user UID
     * Async task to extract from DB
     *
     * @param user the track user
     * @param tracksIds the array of track identifiers
     * @return the view for a specific user and a set of track identifiers
     */
    private synchronized View getTrackListView(final TrackUser user,
            final int[] tracksIds) {
        LayoutInflater inflater = LayoutInflater
                .from(_context);
        _trackListView = (TrackListView) inflater.inflate(
                R.layout.trackhistory_tracklist,
                null);

        bTrackListReverseSortOrder = false;

        _trackListView.setListAdapter(_trackListAdapter, this);
        _trackListView.setDoneClickListener(this);
        _trackListView.setCancelClickListener(this);
        _trackListView.setSelectAllListener(this);
        _trackListView.setHideTempListener(this);
        _trackListView.setDisplayAllListener(this);
        _trackListView.setHideTempVisible(true);
        _trackListAdapter.refresh(user, tracksIds);
        return _trackListView;
    }

    public void showTrackListView(final TrackUser user,
            final List<TrackPolyline> tracks, final boolean searchResults) {
        if (!isClosed()) {
            // Re-open dropdown on top of stack
            closeDropDown();
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    showTrackListView(user, tracks, searchResults);
                }
            });
            return;
        }

        //when main drop down is opened, also set the toolbar
        setRetain(true);
        TrackListView tlv = getTrackListView(user, tracks);
        if (searchResults) {
            tlv.setTitle(_context.getString(
                    R.string.track_search));
            tlv.setHideTempVisible(false);
            tlv.setDisplayAllVisible(false);
        }
        showDropDown(tlv, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, this);
    }

    public void showTrackListView(final TrackUser user, final int[] tracksIds) {
        if (isVisible())
            closeDropDown();

        //when main drop down is opened, also set the toolbar
        setRetain(true);
        showDropDown(getTrackListView(user, tracksIds), HALF_WIDTH,
                FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, this);
    }

    /**
     * Initiate multi select action
     *
     * @param bExport true to export selections, false to delete selections
     */
    private void beginMultiSelectAction(boolean bExport) {
        if (_trackListView == null) {
            Log.w(TAG, "Cannot multi select without track list view");
            return;
        }

        _trackListView.setTitle(bExport);

        //do not redisplay if already showing track list for some UID
        if (!isVisible()) {
            //continue use of same UID
            showTrackListView(null, false, true);
        }

        _trackListView.setMultiSelectEnabled(true);
        _trackListAdapter.setMultiSelectEnabled(true);
    }

    private void endMultiSelectAction(boolean bCancel) {
        if (_trackListView == null) {
            Log.w(TAG, "Track list not available for endMultiSelectAction");
            return;
        }

        final boolean bExport = _trackListView.getMultiSelectExportMode();
        final Context ctx = _context;
        String action = ctx.getString(bExport ? R.string.export
                : R.string.delete).trim();

        if (bCancel) {
            Log.d(TAG, "Cancelled endMultiSelectAction");
            _trackListView.setMultiSelectEnabled(false);
            _trackListAdapter.setMultiSelectEnabled(false);
            Toast.makeText(ctx, action + ctx.getString(R.string.cancelled),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // if no selections, prompt user
        final TrackDetails[] selected = _trackListAdapter.getSelectedResults(
                TrackListAdapter.SortType.TIME);
        if (FileSystemUtils.isEmpty(selected)) {
            Log.d(TAG,
                    "No tracks selected, cannot peform multi selection action");
            Toast.makeText(ctx, R.string.select_one_track,
                    Toast.LENGTH_LONG).show();
            return;
        }

        //confirm with user first
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(bExport ? R.string.confirm_export
                : R.string.confirm_delete);
        b.setIcon(bExport ? R.drawable.atak_menu_export
                : R.drawable.ic_menu_delete);
        b.setMessage(action + " " + selected.length + ctx.getString(
                R.string.track_question));
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                //Now delete or export
                if (bExport) {
                    Log.d(TAG, "Exporting track count: " + selected.length);
                    new TrackExportDialog(getMapView()).show(selected);
                } else {
                    //TODO background task?
                    Log.d(TAG, "Deleting track count: " + selected.length);
                    int[] trackIds = new int[selected.length];
                    //remove from map
                    int ti = 0;
                    for (TrackDetails t : selected) {
                        trackIds[ti++] = t.getTrackDbId();
                        _trackListAdapter.removeResult(t);
                        t.dispose();
                    }

                    //now remove from DB
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            "com.atakmap.android.bread.DELETE_TRACK")
                                    .putExtra(CrumbDatabase.META_TRACK_DBIDS,
                                            trackIds)
                                    .putExtra("showProgress", true));
                }
                // after selection, hide checkboxes again
                _trackListView.setMultiSelectEnabled(false);
                _trackListAdapter.setMultiSelectEnabled(false);
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private void promptRemoveTrack(final TrackPolyline trackLine) {
        if (trackLine == null)
            return;

        final int trackId = trackLine.getMetaInteger(
                CrumbDatabase.META_TRACK_DBID, -1);
        if (trackId == -1)
            return;

        String trackName = ATAKUtilities.getDisplayName(trackLine);

        //confirm with user first
        final Context ctx = _context;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(R.string.confirm_delete);
        b.setIcon(R.drawable.ic_menu_delete);
        b.setMessage(ctx.getString(R.string.are_you_sure_delete)
                + trackName + "'?");
        b.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        trackLine.removeFromGroup();
                        //now remove from DB
                        AtakBroadcast
                                .getInstance()
                                .sendBroadcast(
                                        new Intent(
                                                "com.atakmap.android.bread.DELETE_TRACK")
                                                        .putExtra(
                                                                CrumbDatabase.META_TRACK_DBID,
                                                                trackId));
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private void showUserListView() {
        ActionBarReceiver.getInstance().setToolView(
                getToolbarView(R.layout.trackhistory_userlist_toolbar));
        SubTrackDropDown ddr = new SubTrackDropDown();
        ddr.setRetain(true);
        ddr.show(getUserListView(), THIRD_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, R.layout.trackhistory_userlist_toolbar, null);
    }

    private synchronized View getUserListView() {
        LayoutInflater inflater = LayoutInflater
                .from(_context);
        _userListView = (UserListView) inflater.inflate(
                R.layout.trackhistory_userlist, null);

        _userListView.setListAdapter(_userListAdapter, this, this);
        _userListAdapter.refresh(null);
        return _userListView;
    }

    public void showTrackDetailsView(TrackDetails track, int trackDbId,
            boolean bReQuery, boolean hideOnClose) {
        if (_trackDetailsDropDown != null && !_trackDetailsDropDown.isClosed())
            _trackDetailsDropDown.closeDropDown();
        // drop down solely created for the purposes of managing the query list view
        _trackDetailsDropDown = new SubTrackDropDown();
        _trackDetailsDropDown.setRetain(true);
        if (track == null)
            getTrackDetailsView(trackDbId, _trackDetailsDropDown, hideOnClose);
        else
            getTrackDetailsView(track, track.getTrackDbId(), bReQuery,
                    _trackDetailsDropDown, hideOnClose);
        _trackDetailsDropDown.show(_trackDetailsView, 0.4, FULL_HEIGHT,
                FULL_WIDTH, HALF_HEIGHT, 0, _trackDetailsView);
    }

    public void showTrackDetailsView(TrackDetails track, boolean bReQuery,
            boolean hideOnClose) {
        showTrackDetailsView(track, track.getTrackDbId(), bReQuery,
                hideOnClose);
    }

    private synchronized View getTrackDetailsView(TrackDetails track,
            int trackDbId, boolean bReQuery, DropDownReceiver dropDown,
            boolean hideOnClose) {
        LayoutInflater inflater = LayoutInflater.from(getMapView()
                .getContext());
        _trackDetailsView = (TrackDetailsView) inflater.inflate(
                R.layout.trackhistory_details, getMapView(), false);

        _trackDetailsView.init(getMapView(), _trackMapGroup, track, this,
                dropDown, hideOnClose);
        if (bReQuery || track == null)
            new GetTrackDetailsTask(getMapView(),
                    track, trackDbId, _trackDetailsView,
                    _prefs.getBoolean("elevProfileInterpolateAlt", true))
                            .execute();

        return _trackDetailsView;
    }

    private synchronized View getTrackDetailsView(int trackDbId,
            DropDownReceiver dropDown, boolean hideOnClose) {
        return getTrackDetailsView(null, trackDbId, true, dropDown,
                hideOnClose);
    }

    public void showTrackSearchView(TrackUser user, boolean bServerSearch) {
        ActionBarReceiver.getInstance().setToolView(
                getToolbarView(R.layout.trackhistory_search_toolbar));
        SubTrackDropDown ddr = new SubTrackDropDown();
        ddr.setRetain(true);
        ddr.show(getTrackSearchView(user, bServerSearch), HALF_WIDTH,
                FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT,
                R.layout.trackhistory_search_toolbar, null);
    }

    private synchronized View getTrackSearchView(TrackUser user,
            boolean bServerSearch) {
        LayoutInflater inflater = LayoutInflater
                .from(_context);
        _trackSearchView = (TrackSearchView) inflater.inflate(
                R.layout.trackhistory_search, null);
        _trackSearchView.refresh(this, user, bServerSearch);

        return _trackSearchView;
    }

    public void closeAll() {
        for (DropDownReceiver ddr : _subDropDowns) {
            if (!ddr.isClosed())
                ddr.closeDropDown();
        }
        _subDropDowns.clear();
        closeDropDown();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
        if (_trackListAdapter != null)
            _trackListAdapter.clear();
        if (ActionBarReceiver.getInstance().getToolView() == _trackListToolbar)
            ActionBarReceiver.getInstance().setToolView(null);
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        //if popped off stack, then reload toolbar for this dropdown
        if (v) {
            if (_trackListToolbar == null)
                _trackListToolbar = getToolbarView(
                        R.layout.trackhistory_tracklist_toolbar);
            ActionBarReceiver.getInstance().setToolView(_trackListToolbar);
        }
    }

    private class SubTrackDropDown extends DropDownReceiver
            implements DropDown.OnStateListener {

        private DropDown.OnStateListener _closeListener;
        private int _toolbarId = 0;
        private ActionBarView _toolbar;

        SubTrackDropDown() {
            super(TrackHistoryDropDown.this.getMapView());
            setAssociationKey("atakTrackOptions");
        }

        @Override
        public void onReceive(Context c, Intent i) {
        }

        @Override
        public void disposeImpl() {
        }

        public void show(View contentView, double lwFraction,
                double lhFraction, double pwFraction, double phFraction,
                int toolbarId, DropDown.OnStateListener onCloseListener) {
            _closeListener = onCloseListener;
            _toolbarId = toolbarId;
            _subDropDowns.add(this);
            super.showDropDown(contentView, lwFraction, lhFraction,
                    pwFraction, phFraction, this);
        }

        @Override
        public void onDropDownSelectionRemoved() {
            if (_closeListener != null)
                _closeListener.onDropDownSelectionRemoved();
        }

        @Override
        public void onDropDownClose() {
            _subDropDowns.remove(this);
            if (_toolbar == ActionBarReceiver.getInstance().getToolView())
                ActionBarReceiver.getInstance().setToolView(null);
            if (_closeListener != null)
                _closeListener.onDropDownClose();
        }

        @Override
        public void onDropDownSizeChanged(double width, double height) {
            if (_closeListener != null)
                _closeListener.onDropDownSizeChanged(width, height);
        }

        @Override
        public void onDropDownVisible(boolean v) {
            if (v && _toolbarId != 0)
                ActionBarReceiver.getInstance().setToolView(
                        _toolbar = getToolbarView(_toolbarId));
            if (_closeListener != null)
                _closeListener.onDropDownVisible(v);
        }
    }

    public static void exportTrack(MapView mapView, TrackDetails track) {
        new TrackExportDialog(mapView).show(track);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Toolbar - Show track user callsign list
        if (id == R.id.buttonTrackCallsigns) {
            showUserListView();
        }

        // Toolbar - Search tracks
        else if (id == R.id.buttonTrackSearch) {
            //default to self/local search
            showTrackSearchView(null, false);
        }

        // Toolbar - Show track list
        else if (id == R.id.buttonTrackList) {
            showTrackListView(new TrackUser(getMapView()
                    .getDeviceCallsign(), MapView.getDeviceUid(), 0));
        }

        // Toolbar - Start new track
        else if (id == R.id.buttonTrackStartNewTrack) {
            promptNewTrack(_prefs, getMapView(), false);
        }

        else if (id == R.id.trackhistory_list_item_serverSearchBtn) {
            if (_trackListAdapter != null)
                showTrackSearchView(_trackListAdapter.getCurrentUser(), true);
        }

        // Start track multi-select
        else if (id == R.id.buttonTrackMultiSelect) {
            if (_trackListAdapter != null
                    && _trackListAdapter.getMultiSelectEnabled()) {
                Log.d(TAG, "Already in multiselect mode, ending");
                endMultiSelectAction(true);
                return;
            }
            Resources r = _context.getResources();
            TileButtonDialog d = new TileButtonDialog(getMapView());
            d.addButton(r.getDrawable(R.drawable.export_menu_default),
                    r.getString(R.string.export));
            d.addButton(r.getDrawable(R.drawable.ic_menu_delete),
                    r.getString(R.string.delete_no_space));
            d.show(R.string.multiselect_dialogue, true);
            d.setOnClickListener(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    beginMultiSelectAction(which == 0);
                }
            });
        }

        // Track list sorting
        else if (id == R.id.trackhistory_list_item_callsign_header
                || id == R.id.trackhistory_list_item_distance_header) {
            bTrackListReverseSortOrder = !bTrackListReverseSortOrder;
            _trackListAdapter.sortResults(
                    id == R.id.trackhistory_list_item_callsign_header
                            ? TrackListAdapter.SortType.NAME
                            : TrackListAdapter.SortType.TIME,
                    bTrackListReverseSortOrder);
        }

        // Track list done/cancel
        else if (id == R.id.trackhistory_list_done
                || id == R.id.trackhistory_list_cancel) {
            endMultiSelectAction(id == R.id.trackhistory_list_cancel);
        }

        // User list sorting
        else if (id == R.id.trackhistory_userlist_item_callsign_header
                || id == R.id.trackhistory_userlist_item_numTracks_header) {
            bUserListReverseSortOrder = !bUserListReverseSortOrder;
            _userListAdapter.sortResults(
                    id == R.id.trackhistory_userlist_item_callsign_header
                            ? UserListAdapter.SortType.CALLSIGN
                            : UserListAdapter.SortType.NUMTRACKS,
                    bUserListReverseSortOrder);
        }

        // Clear tracks
        else if (id == R.id.buttonTrackListClear) {
            clearTracksOnMap();
        }

        // Export tracks
        else if (id == R.id.buttonTrackExport && _trackDetailsView != null) {
            TrackHistoryDropDown.exportTrack(getMapView(),
                    _trackDetailsView.getTrack());
        }

        else if (id == R.id.buttonTrackProfile && _trackDetailsView != null) {
            TrackDetails track = _trackDetailsView.getTrack();
            if (track.getCount() < 2) {
                Toast.makeText(_context,
                        _context.getString(
                                R.string.track_no_2_points),
                        Toast.LENGTH_LONG).show();
                return;
            }
            _trackDetailsView.refresh();
            Route route = new RouteTrackWrapper(getMapView(), track);
            RouteElevationBroadcastReceiver.getInstance().setRoute(route);
            RouteElevationBroadcastReceiver.getInstance().setTitle(
                    route.getTitle());
            RouteElevationBroadcastReceiver.getInstance().openDropDown();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
        int id = cb.getId();

        // Track list - toggle select all
        if (id == R.id.trackhistory_list_allSelected) {
            if (isChecked)
                _trackListAdapter.selectAll();
            else
                _trackListAdapter.unSelectAll();
        }

        // Track list - toggle hide temp
        else if (id == R.id.trackhistory_list_hideTemp) {
            _trackListAdapter.refresh(_trackListAdapter.getCurrentUser(),
                    _trackListView.isHideTemp(),
                    _trackListView.isDisplayAll());
        }

        // Track list - toggle display all
        else if (id == R.id.trackhistory_list_displayAll) {
            _trackListView.setHideTempVisible(!_trackListView.isDisplayAll());
            _trackListAdapter.refresh(_trackListAdapter.getCurrentUser(),
                    _trackListView.isHideTemp(),
                    _trackListView.isDisplayAll());
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        // Show track details
        TrackPolyline trackLine = null;
        String uid = intent.getStringExtra("uid");
        if (!FileSystemUtils.isEmpty(uid)) {
            MapItem mi = _trackMapGroup.deepFindUID(uid);
            if (mi instanceof TrackPolyline)
                trackLine = (TrackPolyline) mi;
        }
        if (TRACK_HISTORY.equals(intent.getAction())) {
            int trackDbId = -1;
            if (intent.hasExtra(CrumbDatabase.META_TRACK_DBID)) {
                try {
                    trackDbId = Integer.parseInt(intent.getStringExtra(
                            CrumbDatabase.META_TRACK_DBID));
                } catch (Exception ignore) {
                }
            }
            showTrackDetailsView(null, trackDbId, true, trackLine == null);
        } else if (DELETE_TRACK.equals(intent.getAction())) {
            promptRemoveTrack(trackLine);
        } else if (TRACK_USERLIST.equals(intent.getAction())) {
            showUserListView();
        } else if (TRACK_SEARCH.equals(intent.getAction())) {
            //TODO support UID intent extra?
            showTrackSearchView(null, false);
        } else if (TRACKSEXPORTED.equals(intent.getAction())) {
            ExportTrackParams params = intent
                    .getParcelableExtra("exportparams");
            if (params == null || !params.isValid()) {
                Log.w(TAG, "Track history export params not set");
                return;
            }

            boolean bSuccess = intent.getBooleanExtra("success", false);
            if (!bSuccess) {
                Log.w(TAG, "Export failed: " + params);
                return;
            }

            // if successful, result is route name or exported filename
            if (!FileSystemUtils.isFile(params.getFilePath())) {
                Log.w(TAG, "Export file failed: " + params.getFilePath());
                NotificationUtil.getInstance().postNotification(
                        params.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        params.getFormat()
                                + _context.getString(
                                        R.string.track_export_failed),
                        _context.getString(
                                R.string.failed_export)
                                + params.getFormat(),
                        _context.getString(
                                R.string.failed_export)
                                + params.getFormat());
                return;
            }

            //file was exported, see if user wants to send it...
            Log.d(TAG, "Export successful: " + params);
            sendFile(params);
        } else if (SERVERTRACKSEXPORTED.equals(intent.getAction())) {
            final ExportTrackParams params = intent
                    .getParcelableExtra("exportparams");
            if (params == null || !params.isValid()) {
                Log.w(TAG, "Server Track history export params not set");
                return;
            }

            boolean bSuccess = intent.getBooleanExtra("success", false);
            if (!bSuccess) {
                Log.w(TAG, "Server Export failed: " + params);
                return;
            }

            // if successful, result is route name or exported filename
            final String filePath = params.getFilePath();
            if (!FileSystemUtils.isFile(filePath)) {
                Log.w(TAG, "Server Export file failed: " + filePath);
                NotificationUtil.getInstance().postNotification(
                        params.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        params.getFormat()
                                + _context.getString(
                                        R.string.server_export_failed),
                        _context.getString(
                                R.string.failed_export)
                                + params.getFormat(),
                        _context.getString(
                                R.string.failed_export)
                                + params.getFormat());

                return;
            }

            //Note, currently assume the tracks exported are "self" tracks (for this device)
            final String cotType = _prefs.getString(
                    "locationUnitType",
                    _context.getString(
                            R.string.default_cot_type));
            final String groupName = _prefs.getString("locationTeam", "Cyan");
            final String groupRole = _prefs.getString("atakRoleType",
                    "Team Member");
            final String callbackAction = intent
                    .getStringExtra("callbackAction");

            Log.d(TAG, "Server Export successful: " + params);
            String baseUrl = intent.getStringExtra("baseUrl");
            if (!FileSystemUtils.isEmpty(baseUrl)) {
                Log.d(TAG, "Sending Server Export to: " + baseUrl);

                // send to the specified server
                postUserTracks(baseUrl,
                        getMapView().getDeviceCallsign(), MapView
                                .getDeviceUid(),
                        cotType, groupName, groupRole, filePath,
                        callbackAction, params);
                return;
            }

            final CotPort[] servers = getServers();
            if (servers == null || servers.length == 0) {
                Log.w(TAG, "No Servers to Export tracks: " + params);
                Toast.makeText(
                        _context,
                        _context.getString(
                                R.string.no_servers_connected),
                        Toast.LENGTH_LONG).show();

                sendFile(params);
                return;
            }

            if (servers.length > 1) {
                //select server
                ServerListDialog.selectServer(
                        _context,
                        _context.getString(
                                R.string.select_server),
                        servers,
                        new ServerListDialog.Callback() {
                            @Override
                            public void onSelected(
                                    com.atakmap.comms.TAKServer server) {
                                if (server == null) {
                                    Log.d(TAG, "No configured server selected");
                                    return;
                                }

                                postUserTracks(
                                        ServerListDialog.getBaseUrl(server),
                                        getMapView().getDeviceCallsign(),
                                        MapView.getDeviceUid(),
                                        cotType, groupName, groupRole,
                                        filePath, callbackAction, params);
                            }
                        });
            } else {
                // send to the single server
                postUserTracks(ServerListDialog.getBaseUrl(servers[0]),
                        getMapView().getDeviceCallsign(), MapView
                                .getDeviceUid(),
                        cotType, groupName, groupRole, filePath,
                        callbackAction, params);
            }
        }
    }

    private void sendFile(final ExportTrackParams params) {
        final File file = new File(params.getFilePath());
        final ExportMarshal marshal = ExporterManager.findExporter(
                _context, params.getFormat());
        if (marshal == null) {
            Log.w(TAG, "No Export Marshal available for track export to: "
                    + params.getFormat());
        }

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(params.getFormat() + _context.getString(
                R.string.track_exported));
        b.setIcon(marshal == null ? -1 : marshal.getIconId());
        b.setMessage(_context.getString(R.string.exported) + params.getName()
                + " to " + params.getFilePath());
        b.setPositiveButton(R.string.send,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SendDialog.Builder b = new SendDialog.Builder(
                                getMapView());
                        b.setName(params.getName());
                        b.setIcon(R.drawable.ic_track);
                        b.addFile(file,
                                marshal != null ? marshal.getContentType()
                                        : null);
                        b.show();
                    }
                });
        b.setNegativeButton(R.string.done, null);
        b.show();
    }

    private void postUserTracks(String baseUrl, String callsign, String uid,
            String cotType,
            String groupName, String groupRole, String filePath,
            String callbackAction, ExportTrackParams params) {
        if (FileSystemUtils.isEmpty(baseUrl)) {
            onError(_context.getString(
                    R.string.no_server_selected));
            return;
        }

        //use HTTP request service
        PostUserTracksRequest request = new PostUserTracksRequest(baseUrl,
                curNotificationId++,
                callsign, uid, cotType, groupName,
                groupRole, filePath, callbackAction, params);

        // notify user
        Log.d(TAG, "Server post created for: " + request);

        NotificationUtil.getInstance().postNotification(
                request.getNotificationId(),
                R.drawable.ic_menu_selfhistory, NotificationUtil.WHITE,
                _context.getString(R.string.track_upload_started),
                _context.getString(R.string.posting_tracks) + callsign,
                _context.getString(R.string.posting_tracks) + callsign);

        // Kick off async HTTP request to get file transfer from remote ATAK
        HTTPRequestManager.from(_context).execute(
                request.createPostUserTracksRequest(), this);
    }

    public void setSelectAll(boolean bAllSelected) {
        if (_trackListView != null) {
            _trackListView.setSelectAllChecked(bAllSelected);
        }
    }

    public MapGroup getTrackMapGroup() {
        return _trackMapGroup;
    }

    public void searchRemoteTracks(String baseUrl, String callsign, String uid,
            long searchStart, long searchEnd) {
        if (FileSystemUtils.isEmpty(baseUrl)) {
            onError(_context.getString(
                    R.string.no_server_selected));
            return;
        }

        //use HTTP request service
        QueryUserTracksRequest request = new QueryUserTracksRequest(baseUrl,
                curNotificationId++,
                callsign, uid, searchStart, searchEnd);

        // notify user
        Log.d(TAG,
                "Server query created for: " + request);

        NotificationUtil.getInstance().postNotification(
                request.getNotificationId(),
                R.drawable.ongoing_download, NotificationUtil.BLUE,
                _context.getString(
                        R.string.file_transfer_started),
                _context
                        .getString(R.string.downloading_tracks) + callsign,
                _context
                        .getString(R.string.downloading_tracks) + callsign);

        // Kick off async HTTP request to get file transfer from remote ATAK
        HTTPRequestManager.from(_context).execute(
                request.createQueryUserTracksRequest(), this);
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {
        // HTTP response received successfully
        if (request
                .getRequestType() == TrackHistoryDropDown.REQUEST_TYPE_GET_USER_TRACK) {
            if (resultData == null) {
                onError(_context.getString(
                        R.string.unable_obtain_results));
                return;
            }

            // Get request data
            QueryUserTracksRequest queryRequest = (QueryUserTracksRequest) request
                    .getParcelable(QueryUserTracksOperation.PARAM_QUERY);
            if (queryRequest == null) {
                onError(_context.getString(
                        R.string.unable_obtain_tracks));
                return;
            }

            if (!queryRequest.isValid()) {
                onError(_context.getString(
                        R.string.unable_obtain_validtracks));
                return;
            }

            boolean trackNotFound = resultData.getBoolean(
                    QueryUserTracksOperation.PARAM_TRACKNOTFOUND, false);
            if (trackNotFound) {
                onError(_context.getString(
                        R.string.no_tracks_sever));
                return;
            }

            int[] trackIds = resultData
                    .getIntArray(QueryUserTracksOperation.PARAM_TRACKDBIDS);
            if (trackIds == null || trackIds.length == 0) {
                onError(_context.getString(
                        R.string.unable_obtain_track_list));
                return;
            }

            //dismiss progress dialog
            if (_trackSearchView != null) {
                _trackSearchView.reset();
            }

            //dismiss notification
            Log.d(TAG, "Queried User Tracks: " + queryRequest);
            NotificationUtil.getInstance().clearNotification(
                    queryRequest.getNotificationId());

            //async task to pull list of tracks from DB for UI
            showTrackListView(new TrackUser(queryRequest.getCallsign(),
                    queryRequest.getUid(), 0), trackIds);
        } else if (request
                .getRequestType() == TrackHistoryDropDown.REQUEST_TYPE_POST_USER_TRACK) {
            if (resultData == null) {
                onError(_context.getString(
                        R.string.unable_obtain_results));
                return;
            }

            // Get request data
            PostUserTracksRequest postRequest = (PostUserTracksRequest) request
                    .getParcelable(PostUserTracksOperation.PARAM_QUERY);
            if (postRequest == null) {
                onError(_context.getString(
                        R.string.unable_obtain_tracks));
                return;
            }

            if (!postRequest.isValid()) {
                onError(_context.getString(
                        R.string.unable_obtain_validtracks));
                return;
            }

            Log.d(TAG, "Successfully posted tracks: " + postRequest);
            NotificationUtil.getInstance().postNotification(
                    postRequest.getNotificationId(),
                    R.drawable.ic_menu_selfhistory, NotificationUtil.GREEN,
                    _context.getString(
                            R.string.track_upload_complete),
                    _context.getString(R.string.posted_tracks)
                            + postRequest.getBaseUrl(),
                    _context.getString(R.string.posted_tracks)
                            + postRequest.getBaseUrl());

            if (postRequest.hasCallbackAction()) {
                Log.d(TAG,
                        "Sending server upload callback: "
                                + postRequest.getCallbackAction());
                Intent intent = new Intent(postRequest.getCallbackAction());
                intent.putExtra("success", true);
                if (postRequest.hasCallbackExtra())
                    intent.putExtra("callbackExtra",
                            postRequest.getCallbackExtra());
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        onError(NetworkOperation.getErrorMessage(ce));
    }

    @Override
    public void onRequestDataError(Request request) {
        onError(_context
                .getString(R.string.request_data_error));
    }

    @Override
    public void onRequestCustomError(Request request, Bundle bundle) {
        onError(_context.getString(
                R.string.request_custom_error));
    }

    private void onError(String message) {
        Log.e(TAG, "User Track Download Operation Failed: " + message);
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                _context.getString(
                        R.string.user_track_down_failed),
                message, message);

        if (_trackSearchView != null) {
            _trackSearchView.reset();
        }
    }

    /**
     * Get all connected TAK Servers
     */
    public CotPort[] getServers() {
        List<CotPort> ret = new ArrayList<>();
        CotPort[] servers = _serverListener.getServers();
        if (servers != null) {
            for (CotPort c : servers) {
                if (c.isConnected())
                    ret.add(c);
            }
        }
        return ret.toArray(new CotPort[0]);
    }

    public SharedPreferences getRoutePrefs() {
        return _prefs;
    }
}
