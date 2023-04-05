
package com.atakmap.android.track;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.preference.PreferenceManager;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.task.CreateTracksTask;
import com.atakmap.android.track.task.DeleteTracksTask;
import com.atakmap.android.track.task.ExportTrackHistoryTask;
import com.atakmap.android.track.task.ExportTrackParams;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.maps.CrumbTrail;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.track.crumb.Crumb;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * <code>BreadcrumbReceiver</code> Manages "self" breadcrumb and track history.
 * Track segments are created when ATAK is restarted or user explicitly
 * initiates a new track segment. Timestamped crumbs are logged for self and
 * any node for which the user turns on breadcrumbs. IO/Persistence is managed
 * by <code>CrumbDatabase</code>.
 */
public class BreadcrumbReceiver extends BroadcastReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "BreadcrumbReceiver";
    public static final String TRACK_HISTORY_MAPGROUP = "Track History";

    public static final String TOGGLE_BREAD = "com.atakmap.android.bread.TOGGLE_BREAD";
    public static final String COLOR_CRUMB = "com.atakmap.android.bread.COLOR_CRUMB";

    public static final int DEFAULT_LINE_COLOR = -16776961;
    public static final String DEFAULT_LINE_STYLE = "Arrows";

    public final static int[] TRACK_COLORS = new int[] {
            Color.RED,
            -32768, // Orange
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            -8388353, // Purple
            Color.WHITE
    };

    private final MapView view;
    private final Context _context;
    private final MapGroup _trackGroup;
    private boolean logCrumbs;
    private final SharedPreferences prefs;
    private CrumbDatabase crumbDatabase;
    private Timer _crumbTrailTimer;
    private static final int CRUMB_TRAIL_RATE = 1000; //millis
    private boolean _disposed = false;

    /**
     * Polyline to track self beyond fading bread crumbs
     */
    private EditablePolyline persistentSelfPolyline = null;

    /**
     * The flag mirrors the pref to enable/disable this feature
     */
    private boolean bPersistentSelfTrack;
    private final Object persistentLock = new Object();

    // Trails that need to be created for markers - pushed off to limiting thread
    private final List<PointMapItem> _trailsToPersist = new ArrayList<>();

    public BreadcrumbReceiver(MapView view, MapGroup trackGroup) {
        this.view = view;
        _context = view.getContext();
        crumbDatabase = CrumbDatabase.instance();
        _trackGroup = trackGroup;

        prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        prefs.registerOnSharedPreferenceChangeListener(this);

        logCrumbs = prefs.getBoolean("toggle_log_tracks", true);
        bPersistentSelfTrack = prefs.getBoolean("track_infinite", false);

        // save crumbs from last execution of ATAK out to new track segment KML
        createTrackSegment(getTrackTitle(prefs), this.view.getDeviceCallsign(),
                MapView.getDeviceUid(), false);

        _crumbTrailTimer = new Timer("CrumbTrailTimerThread");
        _crumbTrailTimer.schedule(_crumbTrailTask, 0,
                CRUMB_TRAIL_RATE);
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        if (key.equals("toggle_log_tracks")) {
            logCrumbs = sharedPreferences.getBoolean(
                    "toggle_log_tracks", true);
            Log.d(TAG, "toggle_log_tracks: " + logCrumbs);
            logSelfCrumbs(logCrumbs);
        } else if (key.equals("track_infinite")) {
            bPersistentSelfTrack = sharedPreferences.getBoolean(
                    "track_infinite", false);
            Log.d(TAG, "bPersistentSelfTrack: " + bPersistentSelfTrack);

            if (bPersistentSelfTrack) {
                addPersistentSelfTrack();
            } else {
                clearPersistentSelfTrack(true);
            }
        }
    }

    public static String getTrackTitle(SharedPreferences prefs) {
        String sDate = KMLUtil.KMLDateTimeFormatter.get().format(
                CoordinatedTime.currentDate()).replace(':', '-');
        return prefs.getString("track_prefix", "track_") + sDate;
    }

    private final TimerTask _crumbTrailTask = new TimerTask() {
        @Override
        public void run() {
            //Log.d(TAG, "Track task running");

            if (crumbDatabase == null) {
                Log.w(TAG, "Crumb database not available...");
                return;
            }

            //loop all current trails and get crumb
            List<CrumbTrail> trails = getTrails(view.getRootGroup());

            //Log.d(TAG, "Track task running with trail count: " + trails.size());

            for (CrumbTrail trail : trails) {
                if (trail.getTracking())
                    trail.crumb(crumbDatabase);
            }

            //Log.d(TAG, "Track task ended");
        }
    };

    /**
     * Get all crumb trails in a group hierarchy
     * This is less efficient than explicit tracking via intent, but plugin/tool
     * devs don't have to depend on intents or registration interfaces
     * @param root Root group to begin search
     * @return List of crumb trails
     */
    private List<CrumbTrail> getTrails(MapGroup root) {
        List<CrumbTrail> trails = new ArrayList<>();
        if (root == null)
            return trails;
        Collection<MapItem> items = root.getItems();
        for (MapItem mi : items) {
            if (mi instanceof CrumbTrail)
                trails.add((CrumbTrail) mi);
        }
        Collection<MapGroup> groups = root.getChildGroups();
        for (MapGroup group : groups)
            trails.addAll(getTrails(group));
        return trails;
    }

    // Thread which handles creating new tracks in case we get spammed
    // with toggle intents
    private final LimitingThread _createTracks = new LimitingThread(
            "CrumbTrailCreateThread", new Runnable() {
                @Override
                public void run() {
                    if (!_disposed) {
                        // Create any pending tracks
                        List<PointMapItem> toAdd;
                        synchronized (_trailsToPersist) {
                            toAdd = new ArrayList<>(_trailsToPersist);
                            _trailsToPersist.clear();
                        }
                        if (!toAdd.isEmpty())
                            new CreateTracksTask(view, toAdd, false).execute();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            });

    /**
     * Toggle breadcrumbs for Self marker (this device)
     * @param log true if you want to log self crumbs
     */
    private void logSelfCrumbs(boolean log) {

        PointMapItem item = ATAKUtilities.findSelf(view);
        if (item != null) {
            PointMapItem pointItem = item;
            CrumbTrail trail = pointItem.getCrumbTrail();

            if (log) {
                Log.d(TAG, "START LOGGING self");

                // setup Bread File
                if (trail == null) {
                    Log.d(TAG, "START LOGGING NEW CRUMB TRAIL for self UID: "
                            + pointItem.getUID());
                    trail = new CrumbTrail(view, pointItem, prefs, UUID
                            .randomUUID().toString());
                    pointItem.setCrumbTrail(trail);
                    pointItem.getGroup().addItem(trail);
                    CrumbTrail.toggleCrumbTrail(trail);
                    trail.addCrumbLogListener(selfLogListener);
                }
                trail.setTracking(true);
            } else {
                if (trail != null) {
                    Log.d(TAG, "STOP LOGGING self");
                    CrumbTrail.toggleCrumbTrail(trail);
                    pointItem.getGroup().removeItem(trail);
                    pointItem.setCrumbTrail(null);
                    trail.removeCrumbLogListener(selfLogListener);
                    clearPersistentSelfTrack(true);
                    trail.setTracking(false);
                }
            }
        }
    }

    /**
     * Listens for self crumbs to be created. If configured, adds them to
     * an editable polyline
     */
    private final CrumbTrail.CrumbLogListener selfLogListener = new CrumbTrail.CrumbLogListener() {
        @Override
        public void logCrumb(Crumb c) {
            if (!bPersistentSelfTrack) {
                //Log.d(TAG, "Skipping persistent self track");
                clearPersistentSelfTrack(true);
                return;
            }

            if (c == null || c.getPoint() == null || !c.getPoint().isValid()) {
                Log.w(TAG, "Cannot log invalid crumb");
                return;
            }

            addPersistentSelfTrack();

            //TODO reduce/sample polyline periodically?
            persistentSelfPolyline.addPoint(c.getGeoPointMetaData());
            //TODO reduce some logging after testing
            Log.d(TAG, "Extending persistent self polyline, size: "
                    + persistentSelfPolyline.getNumPoints());
        }
    };

    private void addPersistentSelfTrack() {
        synchronized (persistentLock) {
            if (persistentSelfPolyline == null) {
                Log.d(TAG, "Creating persistent self polyline");
                persistentSelfPolyline = new EditablePolyline(view,
                        MapView.getDeviceUid() + "-persistentTrack");
                persistentSelfPolyline.setTitle("Persistent Self Track");
                persistentSelfPolyline.setType("b-m-t-h");
                persistentSelfPolyline.setMetaString("iconUri",
                        ATAKUtilities.getResourceUri(
                                com.atakmap.app.R.drawable.ic_track));
                persistentSelfPolyline.setMetaBoolean("removable", false);
                persistentSelfPolyline.setMovable(false);
                persistentSelfPolyline.setMetaBoolean("touchable", false);
                persistentSelfPolyline.setMetaBoolean("ignoreMenu", true);
                persistentSelfPolyline.setStrokeColor(Color.BLUE);
                persistentSelfPolyline.setStrokeWeight(3d);

                //pull current track from DB
                TrackPolyline current = crumbDatabase.getMostRecentTrack(
                        MapView.getDeviceUid());
                if (current != null && current.getPoints() != null
                        && current.getPoints().length > 0) {
                    persistentSelfPolyline
                            .setPoints(current.getMetaDataPoints());
                }

                //see if crumbs are currently on
                boolean bVisible = false;
                MapItem item = ATAKUtilities
                        .findSelf(BreadcrumbReceiver.this.view);
                if (item != null) {
                    bVisible = item.getMetaBoolean("tracks_on", false);
                }
                persistentSelfPolyline.setVisible(bVisible);
            }

            if (_trackGroup != null
                    && !_trackGroup.containsItem(persistentSelfPolyline))
                _trackGroup.addItem(persistentSelfPolyline);
        }
    }

    private void clearPersistentSelfTrack(boolean bRemove) {
        //Log.d(TAG, "removePersistentSelfTrack");

        synchronized (persistentLock) {
            if (persistentSelfPolyline == null)
                return;

            //clear the points
            persistentSelfPolyline.clear();

            if (bRemove)
                persistentSelfPolyline.removeFromGroup();
        }
    }

    /**
     * Toggle breadcrumbs for the specified Map Item UID
     * @param uid the uid to toggle bread crumbs on or off for
     */
    private void toggleBreadcrumbs(String uid) {
        MapItem item = view.getRootGroup().deepFindUID(uid);
        if (!(item instanceof PointMapItem))
            return;
        PointMapItem pointItem = (PointMapItem) item;

        CrumbTrail trail = pointItem.getCrumbTrail();
        if (trail == null) {
            Log.d(TAG,
                    "TOGGLE LOGGING NEW CRUMB TRAIL for UID: "
                            + pointItem.getUID());

            trail = new CrumbTrail(view, pointItem, prefs, UUID
                    .randomUUID().toString());
            pointItem.setCrumbTrail(trail);
            pointItem.getGroup().addItem(trail);

            if (!ATAKUtilities.isSelf(view, pointItem)) {
                //start new track segment when tracking is initiated for this UID
                synchronized (_trailsToPersist) {
                    _trailsToPersist.add(pointItem);
                }
                _createTracks.exec();
            } else {
                //this is self track, make persistent line visible
                synchronized (persistentLock) {
                    if (bPersistentSelfTrack
                            && persistentSelfPolyline != null) {
                        persistentSelfPolyline.setVisible(true);
                    }
                }
            }
            trail.setTracking(true);
            pointItem.setMetaBoolean("tracks_on", true);
        } else {
            boolean vis = CrumbTrail.toggleCrumbTrail(trail);
            pointItem.setMetaBoolean("tracks_on", vis);

            if (vis) {
                Log.d(TAG, "TOGGLE LOGGING CRUMB TRAIL ON for UID: "
                        + pointItem.getUID());
                trail.setTracking(true);
            } else {
                //do not stop processing self trail just b/c visibility was turned off
                if (!ATAKUtilities.isSelf(view, pointItem)) {
                    //some other trail, stop updating DB and UI
                    Log.d(TAG, "TOGGLE LOGGING TRAIL OFF for UID: "
                            + pointItem.getUID());
                    trail.setTracking(false);
                }
            }

            //toggle the persistent self track line
            if (ATAKUtilities.isSelf(view, pointItem)) {
                synchronized (persistentLock) {
                    Log.d(TAG,
                            "Toggle Persistent Self Track visibility Self: "
                                    + vis);
                    if (bPersistentSelfTrack
                            && persistentSelfPolyline != null) {
                        persistentSelfPolyline.setVisible(vis);
                    }
                }
            }
        }
    }

    private void promptCrumbColor(final PointMapItem item) {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.breadcrumb_color);
        ColorPalette palette = new ColorPalette(_context,
                item.getMetaInteger("crumbColor", Color.BLACK));
        b.setView(palette);
        final AlertDialog d = b.create();
        ColorPalette.OnColorSelectedListener l = new ColorPalette.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                d.dismiss();
                item.setMetaInteger("crumbColor", color);
                CrumbTrail trail = item.getCrumbTrail();
                if (trail != null)
                    trail.setColor(color);
            }
        };
        palette.setOnColorSelectedListener(l);
        d.show();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(TOGGLE_BREAD)) {
            String uid = intent.getStringExtra("uid");
            if (uid != null)
                toggleBreadcrumbs(uid);
        } else if (action.equals(COLOR_CRUMB)) {
            String uid = intent.getStringExtra("uid");
            if (FileSystemUtils.isEmpty(uid))
                return;
            MapItem item = view.getMapItem(uid);
            if (!(item instanceof PointMapItem))
                return;
            PointMapItem pointItem = (PointMapItem) item;
            promptCrumbColor(pointItem);
        } else if (action
                .equals("com.atakmap.android.location.LOCATION_INIT")) {
            logSelfCrumbs(logCrumbs);
        } else if (action
                .equals("com.atakmap.android.bread.CREATE_TRACK_SEGMENT")) {
            String title = intent.getStringExtra("track_title");
            String uid = intent.getStringExtra("uid");
            String callsign = intent.getStringExtra("callsign");
            createTrackSegment(title, callsign, uid, true);
        } else if (action.equals("com.atakmap.android.bread.DELETE_TRACK")) {
            List<Integer> trackIdList = new ArrayList<>();
            int trackid = intent.getIntExtra(CrumbDatabase.META_TRACK_DBID, -1);
            if (trackid >= 0)
                trackIdList.add(trackid);
            int[] trackids = intent.getIntArrayExtra(
                    CrumbDatabase.META_TRACK_DBIDS);
            if (trackids != null && trackids.length > 0) {
                for (int ti : trackids) {
                    if (ti >= 0)
                        trackIdList.add(ti);
                }
            }
            boolean showProg = intent.getBooleanExtra("showProgress", false);
            new DeleteTracksTask(this.view, trackIdList, showProg).execute();
        } else if (action
                .equals("com.atakmap.android.bread.UPDATE_TRACK_METADATA")) {
            int track_dbid = intent.getIntExtra("track_dbid", -1);
            if (track_dbid < 0) {
                Log.e(TAG, "Failed to parse track id, cannot update metadata");
                return;
            }

            String name = intent.getStringExtra("name");
            String color = intent.getStringExtra("color");
            String style = intent.getStringExtra("linestyle");

            if (name != null && name.length() > 0 && crumbDatabase != null) {
                crumbDatabase.setTrackName(track_dbid, name);
            }

            if (color != null && color.length() > 0 && crumbDatabase != null) {
                crumbDatabase.setTrackColor(track_dbid, color);
            }

            if (style != null && style.length() > 0 && crumbDatabase != null) {
                crumbDatabase.setTrackStyle(track_dbid, style);
            }
        } else if (action
                .equals(TrackHistoryDropDown.EXPORT_TRACK_HISTORY)) {
            ExportTrackParams params = intent
                    .getParcelableExtra("exportparams");
            if (params == null || !params.isValid()) {
                Log.w(TAG, "Cannot export track history without params");
                return;
            }

            //start notification
            NotificationUtil.getInstance().postNotification(
                    params.getNotificationId(),
                    com.atakmap.android.util.ATAKConstants.getIconId(),
                    NotificationUtil.WHITE,
                    _context.getString(R.string.exporting_tracks),
                    _context.getString(R.string.exporting_tracks_to)
                            + params.getFormat(),
                    _context.getString(R.string.exporting_tracks_to)
                            + params.getFormat(),
                    null, false);

            Log.d(TAG, "Exporting Track History: " + params);
            new ExportTrackHistoryTask(view, params).execute();
        } else if (TrackHistoryDropDown.CLEAR_TRACKS
                .equals(intent.getAction())) {
            final String uid = intent.getStringExtra("uid");

            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setIcon(R.drawable.ic_track_clear);
            b.setTitle(R.string.confirm_clear);
            b.setMessage(R.string.clear_tracks);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (FileSystemUtils.isEquals(MapView.getDeviceUid(),
                                    uid)) {
                                //clear persistent line, for self only
                                Log.d(TAG,
                                        "Clearing persistent tracks for self marker");
                                clearPersistentSelfTrack(false);
                            }

                            //clear crumbs for any UID
                            clearCrumbs(uid);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }
    }

    final ClearContentRegistry.ClearContentListener dataMgmtReceiver = new ClearContentRegistry.ClearContentListener() {
        @Override
        public void onClearContent(boolean clearmaps) {
            Log.d(TAG, "Clearing breadcrumbs");
            //stop logging
            logSelfCrumbs(false);

            //stop all crumb trails/threads
            if (_crumbTrailTimer != null) {
                Log.d(TAG, "Killing breadcrumb task");
                _crumbTrailTimer.cancel();
                _crumbTrailTimer.purge();
                _crumbTrailTimer = null;
            }

            //clear crumbs DB
            if (crumbDatabase != null) {
                crumbDatabase.deleteAll();
                crumbDatabase = null;
            }
        }
    };

    private void clearCrumbs(String uid) {
        List<CrumbTrail> trails = getTrails(view.getRootGroup());
        for (CrumbTrail trail : trails) {
            if (trail != null && trail.getTarget() != null
                    && FileSystemUtils.isEquals(uid, trail.getTarget()
                            .getUID())) {
                Log.d(TAG, "Clearing crumbs for marker: " + uid);
                trail.clearAllCrumbs();
                return;
            }
        }

        Log.w(TAG, "No crumbs found for marker: " + uid);
    }

    private void createTrackSegment(String trackTitle, String userTitle,
            String userUid, boolean bStitch) {
        if (crumbDatabase == null) {
            Log.w(TAG, "Crumb DB not available, cannot create track segment");
            return;
        }

        crumbDatabase.createSegment(new CoordinatedTime().getMilliseconds(),
                getNextColor(prefs),
                trackTitle, DEFAULT_LINE_STYLE, userTitle, userUid, bStitch);
    }

    public static int getNextColor(SharedPreferences preferences) {
        if (preferences == null)
            return DEFAULT_LINE_COLOR;

        int color = Integer.parseInt(preferences.getString(
                "track_history_default_color",
                String.valueOf(DEFAULT_LINE_COLOR)));

        // determine color for next track
        if (preferences.getBoolean("toggle_rotate_track_colors", true)) {
            // rotate to next color, first find last color used
            int lastColorUsed = preferences.getInt("track_last_color",
                    DEFAULT_LINE_COLOR);
            int lastColorIndex = -1;

            // locate it in color array
            for (int i = 0; i < TRACK_COLORS.length; i++) {
                if (TRACK_COLORS[i] == lastColorUsed) {
                    lastColorIndex = i;
                    break;
                }
            }

            // if by chance lastColorUsed not found, just go to 0
            if (lastColorIndex < 0)
                lastColorIndex = 0;

            // move to next color
            color = TRACK_COLORS[(lastColorIndex + 1) % TRACK_COLORS.length];
        }

        // now update prefs for last color used
        Editor editor = preferences.edit();
        editor.putInt("track_last_color", color);
        editor.apply();

        return color;
    }

    public static int setServerTrack(String callsign, String uid,
            long startTime, Track track,
            SharedPreferences prefs) {
        //TODO ensure no thread safety issues
        CrumbDatabase db = CrumbDatabase.instance();
        return db.setServerTrack(callsign, uid, startTime, track, prefs);
    }

    public void dispose() {
        if (_crumbTrailTimer != null) {
            _crumbTrailTimer.cancel();
            _crumbTrailTimer.purge();
            _crumbTrailTimer = null;
        }
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        _disposed = true;
        _createTracks.dispose(false);
    }
}
