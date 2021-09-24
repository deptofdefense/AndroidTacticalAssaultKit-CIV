
package com.atakmap.android.user;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.image.quickpic.QuickPicReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.medline.MedLineView;
import com.atakmap.android.user.icon.SpotMapReceiver;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.atakmap.app.R;

public class PlacePointTool {

    public static final String TAG = "PlacePointTool";

    private static MapGroup usericonGroup = null, missionGroup = null,
            casevacGroup = null;
    private static Map<String, MapGroup> userGroups;

    private static final ThreadLocal<SimpleDateFormat> d_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("d", LocaleUtil.getCurrent());
        }
    };
    private static final ThreadLocal<SimpleDateFormat> HHmm_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HHmmss", LocaleUtil.getCurrent());
        }
    };

    public static void init(MapGroup usericonG, MapGroup missionG,
            MapGroup casevacG,
            Map<String, MapGroup> userG) {
        usericonGroup = usericonG;
        missionGroup = missionG;
        casevacGroup = casevacG;
        userGroups = userG;
    }

    public static class MarkerCreator {

        private boolean disableAutomaticIcon = false;

        private boolean addToGroup = true;
        private boolean showEditor = true;
        private boolean showNineLine = false;
        private boolean showMedNineLine = false;
        private boolean showFiveLine = false;
        private boolean showNewRadial = false;
        private String action = null;
        private boolean nevercot = false;
        private boolean archive = true;

        private MapGroup mapGroup = null;
        private String friendlyUID = null;

        private boolean readiness = true;
        private GeoPointMetaData point = null;
        private int color = Color.WHITE;
        private int textColor = Color.WHITE;
        private String type = null;
        private String uid = UUID.randomUUID().toString();
        private String how = "h-g-i-g-o";
        private String iconsetPath = null;
        private String callsign = null;
        private String prefix = null;
        private final Map<String, String> other = new HashMap<>();

        /**
         * Build a marker creator with a point supplied.
         * @param point the point for the MarkerCreator.
         */
        public MarkerCreator(final GeoPoint point) {
            if (point == null)
                this.point = GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);
            else
                this.point = GeoPointMetaData.wrap(point);
        }

        /**
         * Build a marker with a more robust GeoPointMetaData point.
         * @param point the point for the marker.
         */
        public MarkerCreator(final GeoPointMetaData point) {
            if (point == null)
                this.point = GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);
            else
                this.point = point;
        }

        public MarkerCreator(String fromUid) {
            final MapItem item = MapView.getMapView().getMapItem(fromUid);
            if (item != null) {
                if (item instanceof PointMapItem)
                    this.point = ((PointMapItem) item).getGeoPointMetaData();
                else if (item instanceof Shape)
                    this.point = ((Shape) item).getCenter();
                else if (item instanceof AnchoredMapItem)

                    this.point = ((AnchoredMapItem) item).getAnchorItem()
                            .getGeoPointMetaData();

            }
        }

        /** 
         * Ability to set meta string properties on the Marker prior to its creation.
         * @param key the key for the string value
         * @param value the value to be added to the marker prior to persisting
         */
        public MarkerCreator setMetaString(String key, String value) {
            other.put(key, value);
            return this;
        }

        /**
         * Sets the readiness flag for the marker.  In ATAK, this does not visually
         * change the icon but allows for a plugin to do so.
         * @param readiness true or false
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setReadiness(final boolean readiness) {
            this.readiness = readiness;
            return this;
        }

        /**
         * Sets the type of the marker based on the CoT 2525 types or custom types
         * used in ATAK
         * @param type the custom type
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setType(final String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the uid of the marker.
         * @param uid the universally unique identifier
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setUid(final String uid) {
            if (uid != null && !uid.isEmpty())
                this.uid = uid;
            return this;
        }

        /**
         * Sets the CoT how for a marker such as m-g or h-i-g-o
         * @param how the how for the CoT
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setHow(final String how) {
            if (!how.isEmpty())
                this.how = how;
            return this;
        }

        /**
         * For markers that when placed automatically show the five line when placed,
         * show the five line based on the boolean
         * @param show if true the five line will be shown.
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setShowFiveLine(final boolean show) {
            this.showFiveLine = show;
            if (show) {
                this.showEditor = false;
                this.showMedNineLine = false;
                this.showNineLine = false;
            }
            return this;
        }

        /**
         * The iconset path to use when rendering the icon for the marker
         * @param iconsetPath the iconset path in the appropriate iconset format - usually
         *                    asset://[uid]/directory/name
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setIconPath(String iconsetPath) {
            if (iconsetPath != null && !iconsetPath.isEmpty())
                this.iconsetPath = iconsetPath;
            return this;
        }

        /**
         * If the flag is set to true, ATAK will make no effort to set an icon or change 
         * an icon for the marker dropped.   The user is completely responsible for
         * controlling the icon.
         * This feature is dangerous and requires the plugin to fully manage the icon lifecycle.
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator disableAutomaticIcon(boolean disable) {
            disableAutomaticIcon = disable;
            return this;
        }

        /**
         * Defaults to false and indicates if this marker should never be turned into CoT
         * @param persist true means it should never turn into CoT.   This is different from
         * archive which indicates that a marker can be turned into CoT.
         * @return the MarkerCreator for chaining of calls
         */
        public MarkerCreator setNeverPersist(boolean persist) {
            this.nevercot = persist;
            return this;
        }

        /**
         * Defaults to true and signals if this marker should be saved to the database
         * This is different from nevercot since it allows for a marker to be turned into
         * cursor on target but does not allow it to be persisted.
         * @param archive true for saving
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setArchive(boolean archive) {
            this.archive = archive;
            return this;
        }

        public MarkerCreator setCallsign(String callsign) {
            if (callsign != null) {
                callsign = callsign.trim();
                if (!callsign.isEmpty())
                    this.callsign = callsign;
            }
            return this;
        }

        public MarkerCreator setPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Indicates the color of the marker.
         * @param color used to multiply against the existing colors of the icon
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator setColor(final int color) {
            this.color = color;
            return this;
        }

        public MarkerCreator setTextColor(int color) {
            this.textColor = color;
            return this;
        }

        /**
         * The ability to disable the editor from displaying when a marker is dropped.
         * @param showEditor if showEditor is false, the editor is not shown when the marker is dropped.
         * @return the MarkerCreator for reuse in a chained call
         */
        public MarkerCreator showCotDetails(final boolean showEditor) {
            this.showEditor = showEditor;
            return this;
        }

        public MarkerCreator setShowNineLine(boolean showNineLine) {
            this.showNineLine = showNineLine;
            if (showNineLine) {
                this.showEditor = false;
                this.showMedNineLine = false;
                this.showFiveLine = false;
            }
            return this;
        }

        /**
         * The ability to perform an action on the newly created marker after it is dropped.
         */
        public MarkerCreator setAction(String action) {
            this.action = action;
            return this;
        }

        public MarkerCreator setShowNewRadial(boolean showNewRadial) {
            this.showNewRadial = showNewRadial;
            if (showNewRadial) {
                this.showEditor = false;
                this.showMedNineLine = false;
                this.showNineLine = false;
                this.showFiveLine = false;
            }
            return this;
        }

        public MarkerCreator setShowNineLine(boolean showNineLine,
                String friendlyUID) {
            this.showNineLine = showNineLine;
            this.friendlyUID = friendlyUID;
            if (showNineLine) {
                this.showEditor = false;
                this.showMedNineLine = false;
            }
            return this;
        }

        public MarkerCreator setShowMedNineLine(boolean showMedNineLine) {
            this.showMedNineLine = showMedNineLine;
            if (showMedNineLine) {
                this.showEditor = false;
                this.showNineLine = false;
            }
            return this;
        }

        //TODO: missing readiness
        //                right now the goto tool doesn't work with this new batch of code'
        //        fix it

        public Marker placePoint() {
            //Log.d(TAG, "Placing point");
            if (usericonGroup == null || missionGroup == null
                    || userGroups == null) {
                Log.e(TAG,
                        "You must first initialize the PlacePointTool before creating a Marker.");
                return null;
            }
            Marker marker = null;
            MapView mv = MapView.getMapView();
            final MapItem item = mv.getMapItem(uid);
            if (item instanceof Marker) {
                marker = (Marker) item; //Found the marker - so just update
                addToGroup = false;
            }
            if (marker == null) { //Marker doesn't exist - Make it
                marker = createMarker();
            }

            if (point == null)
                point = GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);
            if (marker.getPoint() != GeoPoint.ZERO_POINT)
                point = marker.getGeoPointMetaData();
            marker.setPoint(point);

            if (nevercot)
                marker.setMetaBoolean("nevercot", true);

            marker.setMetaBoolean("readiness", readiness);
            if (archive)
                marker.setMetaBoolean("archive", true);

            // On creation of a marker, record the producer UID and the
            // type at that moment - although the type could change in the future
            // it is best to know what the producer was when the item was produced.
            Marker self = mv.getSelfMarker();
            marker.setMetaString("parent_uid", self.getUID());
            marker.setMetaString(
                    "parent_type",
                    mv.getMapData()
                            .getString("deviceType", "a-f-G"));
            marker.setMetaString("parent_callsign", mv.getDeviceCallsign());
            marker.setMetaString("production_time",
                    new CoordinatedTime().toString());

            if (type == null)
                type = marker.getMetaString("type", "a-u-G");
            marker.setType(type);

            marker.setMetaInteger("color", color);
            marker.setTextColor(textColor);

            // flag a marker as interesting at this time.  this will be used by the offscreen
            // search mechanism to weed out offscreen markers that are close but may no longer be
            // interesting.   New markers are interesting....
            marker.setMetaLong("offscreen_interest",
                    SystemClock.elapsedRealtime());

            if (!FileSystemUtils.isEmpty(iconsetPath)) {
                marker.setMetaString(UserIcon.IconsetPath, iconsetPath);
            }

            if (addToGroup) {
                mapGroup = getMapGroup();
                if (mapGroup != null && mapGroup.getParentGroup() == null) {
                    if (mapGroup.getFriendlyName().equals("Mission"))
                        mv.getRootGroup().addGroup(missionGroup);
                }
            }

            if (callsign == null) {
                if (marker.getType().equals("b-r-f-h-c")) {
                    SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(
                                    mv.getContext());
                    callsign = prefs.getString(
                            MedLineView.PREF_MEDLINE_CALLSIGN,
                            mv.getDeviceCallsign());
                } else {
                    final String type = marker.getType();
                    callsign = genCallsign(type);
                }
            }
            if (mapGroup != null) {
                if (prefix != null) {
                    int count = getCount(prefix,
                            mapGroup.deepFindItems("type", type));
                    callsign = prefix + " " + count;
                } else if (callsign != null) {
                    int count = 0;
                    List<MapItem> items = mapGroup.deepFindItems("type", type);
                    for (MapItem mi : items) {
                        String cs = mi.getMetaString("callsign", "");
                        if (cs != null && (cs.equals(callsign)
                                || cs.startsWith(callsign)
                                        && cs.charAt(
                                                callsign.length()) == '.')) {
                            count++;
                        }
                    }
                    if (count > 0)
                        callsign = callsign + "." + count;
                }
            }
            marker.setMetaString("callsign", callsign);
            updateCallsign(marker);

            if (disableAutomaticIcon) {
                // adapting is what we call it when an icon is automatically assigned to 
                // a marker - see IconsMapAdapter::adaptMarkerIcon(Marker)
                marker.setMetaBoolean("adapt_marker_icon", false);
            }

            if (addToGroup && mapGroup != null)
                mapGroup.addItem(marker);

            if (type.equals("b-m-p-w")) { // This is a waypoint
                showEditor = false;
            }

            if (showEditor) {
                if (!type.equals("b-m-p-s-p-loc")) {
                    Intent detailEditor = new Intent();
                    detailEditor
                            .setAction(
                                    CoTInfoBroadcastReceiver.COTINFO_DETAILS);
                    detailEditor.putExtra("targetUID", uid);
                    AtakBroadcast.getInstance().sendBroadcast(detailEditor);
                }
            }

            if (showNineLine) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(
                                mv.getContext());

                boolean showCAS = prefs.getString(
                        "autostart_nineline_type", "cas").equals("cas");

                Intent detailEditor = new Intent();
                detailEditor.setAction(showCAS ? "com.atakmap.baokit.NINE_LINE"
                        : "com.staudertech.cff.CFF_MISSION");
                detailEditor.putExtra("targetUID", uid);
                detailEditor.putExtra("friendlyUID", friendlyUID);
                AtakBroadcast.getInstance().sendBroadcast(detailEditor);
            }

            if (showFiveLine) {
                Intent detailEditor = new Intent();
                detailEditor.setAction("com.staudertech.cff.CFF_MISSION");
                detailEditor.putExtra("targetUID", uid);
                detailEditor.putExtra("friendlyUID", friendlyUID);
                AtakBroadcast.getInstance().sendBroadcast(detailEditor);
            }

            if (showMedNineLine) {
                Intent detailEditor = new Intent();
                detailEditor.setAction("com.atakmap.android.MED_LINE");
                detailEditor.putExtra("targetUID", uid);
                AtakBroadcast.getInstance().sendBroadcast(detailEditor);
            }

            if (showNewRadial) {
                Intent focus = new Intent();
                focus.setAction("com.atakmap.android.maps.FOCUS");
                focus.putExtra("uid", uid);

                Intent showMenu = new Intent();
                showMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
                showMenu.putExtra("uid", uid);

                Intent showDetails = new Intent();
                showDetails.setAction("com.atakmap.android.maps.SHOW_DETAILS");
                showDetails.putExtra("uid", uid);

                ArrayList<Intent> intents = new ArrayList<>();
                intents.add(focus);
                intents.add(showMenu);
                intents.add(showDetails);
                // broadcast intent
                AtakBroadcast.getInstance().sendIntents(intents);
            }

            if (action != null) {
                Intent intent = new Intent();
                intent.setAction(action);
                intent.putExtra("uid", uid);
                intent.putExtra("targetUID", uid);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }

            // allow for setting of string, string values within a Marker prior to calling refresh 
            // and persist.
            for (Map.Entry<String, String> es : other.entrySet())
                marker.setMetaString(es.getKey(), es.getValue());

            marker.refresh(mv.getMapEventDispatcher(), null,
                    this.getClass());
            marker.persist(mv.getMapEventDispatcher(), null,
                    this.getClass());

            return marker;
        }

        private Marker createMarker() {
            Marker m = new Marker(uid);
            m.setStyle(m.getStyle()
                    | Marker.STYLE_MARQUEE_TITLE_MASK);

            // Came from the user
            m.setMetaString("entry", "user");

            // Full mutability
            m.setMetaBoolean("editable", true);
            m.setMovable(true);
            m.setMetaBoolean("removable", true);
            m.setMetaString("how", how);
            return m;
        }

        private MapGroup getMapGroup() {
            MapGroup mg;
            if (UserIcon.IsValidIconsetPath(iconsetPath, false, MapView
                    .getMapView().getContext())) {
                mg = UserIcon.GetOrAddSubGroup(usericonGroup, iconsetPath,
                        MapView.getMapView().getContext());
                if (mg == null)
                    mg = usericonGroup;
            } else if (type.startsWith("b-r-f-h-c")) {
                mg = casevacGroup;
            } else if (type.startsWith("a-u")) {
                mg = userGroups.get("Unknown");
            } else if (type.startsWith("a-h")) {
                mg = userGroups.get("Hostile");
            } else if (type.startsWith("a-n")) {
                mg = userGroups.get("Neutral");
            } else if (type.startsWith("a-f")) {
                mg = userGroups.get("Friendly");
            } else if (type.equals(SpotMapReceiver.SPOT_MAP_POINT_COT_TYPE)) {
                mg = SpotMapReceiver.getSpotGroup();
            } else if (type.equals("b-m-p-s-p-loc")) {
                mg = missionGroup;
            } else if (type.equals("b-m-p-s-p-op")) {
                mg = missionGroup;
            } else if (type.equals("b-m-p-c-cp") || type.equals("b-m-p-c-ip")) {
                mg = MapView.getMapView().getRootGroup()
                        .deepFindMapGroup("Airspace");
            } else if (type.startsWith("b-m-p")) {
                mg = userGroups.get("Waypoint");
            } else if (type.startsWith("b-m-r")) {
                mg = userGroups.get("Route");
            } else if (type.equals("u-d-p")) {
                mg = DrawingToolsMapComponent.getGroup();
            } else if (type.equals(QuickPicReceiver.QUICK_PIC_IMAGE_TYPE)) {
                mg = QuickPicReceiver.getMapGroup();
            } else {
                mg = userGroups.get("Other");
            }
            return mg;
        }
    }

    public static int getCount(String prefix, List<MapItem> items) {
        String[] splitPrefix = prefix.split(" ");
        int num = 1;
        int j = 0;

        if (items != null && !items.isEmpty()) {
            boolean match = true;
            int[] numUsed = new int[items.size()];
            // Do this to find the lowest used number for the group
            for (MapItem item : items) {
                if (item instanceof Marker) {
                    String tTitle = item.getTitle();
                    String[] n = tTitle.split(" ");
                    if (splitPrefix.length == n.length - 1) {
                        for (int i = 0; i < splitPrefix.length; i++) {
                            if (!splitPrefix[i].equalsIgnoreCase(n[i])) {
                                match = false;
                                break;
                            }
                        }
                    } else {
                        match = prefix.isEmpty();
                    }
                    if (match) {
                        try {
                            numUsed[j] = Integer.parseInt(n[n.length - 1]);
                        } catch (NumberFormatException e) {
                            // The title has been edited
                            numUsed[j] = 0;
                        }
                    }

                    j++;
                    match = true;
                }
            }
            Arrays.sort(numUsed);
            for (int aNumUsed : numUsed) {
                if (num == aNumUsed) {
                    num++;
                }
            }
        }
        return num;
    }

    public static int getHighestNumbered(String prefix, List<MapItem> items) {
        String[] splitPrefix = prefix.split(" ");
        int num = 1;
        int j = 0;
        boolean matched = false;

        if (!items.isEmpty()) {
            boolean match = true;
            int[] numUsed = new int[items.size()];
            // Do this to find the lowest used number for the group
            for (MapItem item : items) {
                if (item instanceof Marker) {
                    String tTitle = item.getTitle();
                    String[] n = tTitle.split(" ");
                    if (splitPrefix.length == n.length - 1) {
                        for (int i = 0; i < splitPrefix.length; i++) {
                            if (!splitPrefix[i].equalsIgnoreCase(n[i])) {
                                match = false;
                                break;
                            }
                        }
                    } else {
                        match = false;
                    }
                    if (match) {
                        try {
                            matched = true;
                            numUsed[j] = Integer.parseInt(n[n.length - 1]);
                        } catch (NumberFormatException e) {
                            // The title has been edited
                            numUsed[j] = 0;
                        }
                    }

                    j++;
                    match = true;
                }
            }
            Arrays.sort(numUsed);
            num = numUsed[numUsed.length - 1];
        }
        if (matched) {
            return num + 1;
        } else {
            return 0;
        }
    }

    public static void updateCallsign(Marker marker) {
        if (marker.hasMetaValue("callsign")) {
            String callsign = marker.getMetaString("callsign", "");
            if (!marker.getType().equals("b-r-f-h-c")) {
                marker.setTitle(callsign);
            } else {
                if (marker.getMetaString("title", null) != null) {
                    marker.setTitle(
                            marker.getMetaString("title", null));
                } else {
                    Date d = CoordinatedTime.currentDate();
                    String med_title = "MED"
                            + "." + d_sdf.get().format(d)
                            + "." + HHmm_sdf.get().format(d);
                    marker.setTitle(med_title);
                }
            }

        }
    }

    /**
     * Based on the CoT type that is passed in, the callsign will be 
     * generated in a fashion that is consistent across all of the TAK
     * codebase. 
     * @param type if the type is null, it will use the device callsign.
     * otherwise it will inspect the type and use a designated string as 
     * as the prefix.
     */
    public static String genCallsign(final String type) {
        final Date d = CoordinatedTime.currentDate();

        final MapView _mapView = MapView.getMapView();

        String prefix = _mapView.getDeviceCallsign();

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(MapView.getMapView().getContext());
        boolean legacyPointDropNaming = prefs.getBoolean(
                "legacyPointDropNaming", false);

        if (!legacyPointDropNaming && type != null) {
            String cType = type.toLowerCase(LocaleUtil.getCurrent());
            if (cType.startsWith("a-h")) {
                prefix = ResourceUtil.getString(_mapView.getContext(),
                        R.string.civ_tgtPrefix, R.string.tgtPrefix);
            } else if (cType.startsWith("a-f")) {
                prefix = "F";
            } else if (cType.startsWith("a-u")) {
                prefix = "U";
            } else if (cType.startsWith("a-n")) {
                prefix = "N";
            } else if (cType.startsWith("b-m-p-s-m")) {
                prefix = "S";
            } else if (cType.startsWith("b-r-f-h-c")) {
                prefix = "MED";
            }
        }
        return prefix
                + "." + d_sdf.get().format(d)
                + "." + HHmm_sdf.get().format(d);
    }
}
