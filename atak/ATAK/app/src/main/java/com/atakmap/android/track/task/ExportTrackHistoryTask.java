
package com.atakmap.android.track.task;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.gpx.Gpx;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.android.routes.RouteKmlIO;
import com.atakmap.android.track.BreadcrumbReceiver;
import com.atakmap.android.track.TrackHistoryDropDown;
import com.atakmap.android.track.TrackLogKMLSerializer;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.crumb.CrumbPoint;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Schema;
import com.ekito.simpleKML.model.SimpleField;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;
import com.ekito.simpleKML.model.TimeStamp;
import com.ekito.simpleKML.model.Track;

import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipOutputStream;

/**
 * Simple background task to export track history KMLs
 *
 * 
 */
public class ExportTrackHistoryTask extends AsyncTask<Void, String, String> {

    private static final String TAG = "ExportTrackHistoryTask";

    private static final String DELIMITER = ",";
    private static final String NEW_LINE = "\n";
    private static final String HEADER = "UID, CallSign, TimeStamp (unix epoch), Latitude (dec deg), Longitude (dec deg), HAE Altitude (m), "
            + "Circular Error (m), Linear Error (m), Bearing, Speed (m/s), Pt Source, Alt Source, AsText(Point_Geom)";

    private final MapView mapView;
    private final Context context;
    private final SharedPreferences prefs;
    private final ExportTrackParams params;
    private final boolean exportKMLTimestampsPref;

    /**
     * CoTEvent, if format is "ATAK Route"
     */
    private CotEvent routeEvent;
    private final MapGroup routeGroup;

    /**
     * ctor
     *
     * @param params
     */
    public ExportTrackHistoryTask(MapView view, ExportTrackParams params) {
        this.mapView = view;
        this.context = view.getContext();
        this.params = params;
        this.routeGroup = view.getRootGroup().findMapGroup("Route");

        this.prefs = PreferenceManager.getDefaultSharedPreferences(
                this.context);
        this.exportKMLTimestampsPref = prefs.getBoolean(
                "track_kml_export_timestamps", false);
    }

    @Override
    protected String doInBackground(Void... unused) {
        if (params == null || !params.isValid()) {
            Log.w(TAG, "Unable to export with no Export params");
            return null;
        }

        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG, "Crumb DB not available, export show track history");
            return null;
        }

        if (params.isCurrentTrack()) {
            int currentTrackId = db.getCurrentSegmentId(
                    MapView.getDeviceUid(),
                    CrumbDatabase.SEG_COLUMN_TIMESTAMP);
            if (currentTrackId < 0) {
                Log.w(TAG, "Unable to determine current track ID");
                return null;
            }

            int[] ids = new int[1];
            ids[0] = currentTrackId;
            params.setTrack_dbids(ids);
            Log.d(TAG, "Exporting current track: " + ids[0]);
        } else if (params.hasMillis()) {
            //TODO this gets tracks, rather than crumbs, so the export may include crumbs in
            //those tracks that are outside the time range
            long now = CoordinatedTime.currentDate().getTime();
            List<TrackPolyline> tracks = db.getTracks(
                    params.getUid(),
                    (now - params.getMillisToExport()), now);
            if (!FileSystemUtils.isEmpty(tracks)) {
                int[] ids = new int[tracks.size()];
                for (int i = 0; i < tracks.size(); i++) {
                    int trackId = tracks.get(i).getMetaInteger(
                            CrumbDatabase.META_TRACK_DBID, -1);
                    ids[i] = trackId;
                }
                params.setTrack_dbids(ids);
                Log.d(TAG, "Exporting " + ids.length + " tracks for time: "
                        + params.getMillisToExport());
            } else {
                Log.w(TAG,
                        "No tracks matching time: "
                                + params.getMillisToExport());
            }
        }

        if (!params.hasTrackIds()) {
            Log.w(TAG, "No track IDs to query");
            return null;
        }

        switch (params.getFormat()) {
            case "KML":
                Log.d(TAG,
                        "Exporting Track History KML, track count="
                                + params.getTrackIdCount());

                if (params.bForceKmlTimestamps || exportKMLTimestampsPref) {
                    return exportTrackKMLwithTimestamps(params.getName(),
                            params.getFilePath(), params.getTrack_dbids());
                } else {
                    return exportTrackKMLnoTimestamps(params.getName(),
                            params.getFilePath(), params.getTrack_dbids());
                }
            case "KMZ":
                Log.d(TAG,
                        "Exporting Track History KMZ, track count="
                                + params.getTrackIdCount());
                if (params.bForceKmlTimestamps || exportKMLTimestampsPref) {
                    return exportTrackKMZwithTimestamps(params.getUid(),
                            params.getName(),
                            params.getFilePath(), params.getTrack_dbids());
                } else {
                    return exportTrackKMZnoTimestamps(params.getName(),
                            params.getFilePath(), params.getTrack_dbids());
                }
            case "CSV":
                Log.d(TAG,
                        "Exporting Track History CSV, track count="
                                + params.getTrackIdCount());
                return exportTrackCSV(params.getUid(), params.getName(),
                        params.getFilePath(), params.getTrack_dbids());
            case "GPX":
                Log.d(TAG,
                        "Exporting Track History GPX, track count="
                                + params.getTrackIdCount());
                return exportTrackGPX(params.getName(),
                        params.getFilePath(), params.getTrack_dbids());
            default:
                Log.d(TAG, "Exporting Track History Route, track count="
                        + params.getTrackIdCount());

                if (routeGroup == null) {
                    Log.e(TAG,
                            "Unable to export route without Route map group");
                    return null;
                }

                final Route route = exportTrackRoute(params.getName(),
                        params.getTrack_dbids(), routeGroup);
                if (route == null) {
                    Log.e(TAG, "Unable to export empty route");
                    return null;
                }

                // dispatch map event so RouteMapReceiver gets it
                if (route.getGroup() == null)
                    routeGroup.addItem(route);

                mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        // Make sure we persist the new route
                        route.persist(mapView.getMapEventDispatcher(), null,
                                getClass());
                    }
                });

                routeEvent = CotEventFactory.createCotEvent(route);
                if (routeEvent == null || !routeEvent.isValid()) {
                    Log.e(TAG, "Unable to export valid route CoT");
                    return null;
                }

                CotMapComponent.getInternalDispatcher().dispatch(routeEvent);
                return route.getTitle();
        }
    }

    @Override
    protected void onPostExecute(String result) {

        if (params == null || !params.isValid()) {
            Log.w(TAG, "Unable to post export with no Export params");
            return;
        }

        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG,
                    "Crumb DB not available, cannot show track history (post execute)");
            return;
        }

        Context ctx = mapView.getContext();
        if (FileSystemUtils.isEmpty(result)) {
            Log.w(TAG, "Failed to export params");

            //give a good error message for common error
            if (params.isCurrentTrack()) {
                TrackPolyline current = db.getMostRecentTrack(
                        ExportTrackHistoryTask.this.params.getUid());
                if (current == null || current.getPoints() == null
                        || current.getPoints().length < 1) {
                    NotificationUtil.getInstance().postNotification(
                            params.getNotificationId(),
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            ctx.getString(R.string.no_tracks_to_export)
                                    + params.getFormat(),
                            ctx.getString(R.string.gps_lock_confirm),
                            ctx.getString(R.string.gps_lock_confirm));
                    if (this.params.hasCallbackAction()) {
                        Log.d(TAG, "Sending failure callback: "
                                + this.params.getCallbackAction());
                        Intent intent = new Intent(
                                this.params.getCallbackAction());
                        intent.putExtra("exportparams", this.params);
                        intent.putExtra("success", false);
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                    }
                    return;
                }
            }

            NotificationUtil.getInstance().postNotification(
                    params.getNotificationId(),
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "Tracks Export Failed: " + params.getName(),
                    "Failed to export tracks to "
                            + ExportTrackHistoryTask.this.params
                                    .getFormat(),
                    "Failed to export tracks to "
                            + ExportTrackHistoryTask.this.params
                                    .getFormat());

            if (this.params.hasCallbackAction()) {
                Log.d(TAG,
                        "Sending failure callback: "
                                + this.params.getCallbackAction());
                Intent intent = new Intent(this.params.getCallbackAction());
                intent.putExtra("exportparams", this.params);
                intent.putExtra("success", false);
                AtakBroadcast.getInstance()
                        .sendBroadcast(
                                intent);
            }
            return;
        }

        NotificationUtil.getInstance().postNotification(
                params.getNotificationId(),
                com.atakmap.android.util.ATAKConstants.getIconId(),
                NotificationUtil.WHITE,
                ctx.getString(R.string.exported) + params.getName(),
                ctx.getString(R.string.exported_tracks_to)
                        + ExportTrackHistoryTask.this.params.getFormat(),
                ctx.getString(R.string.exported_tracks_to)
                        + ExportTrackHistoryTask.this.params.getFormat());

        Log.d(TAG, "Exported " + params.getTrackIdCount() + " tracks to "
                + result);
        if (TrackHistoryDropDown.ROUTE
                .equals(ExportTrackHistoryTask.this.params
                        .getFormat())) {
            //special case code for Routes since we already have the CoTEvent ready for sending...
            //TODO where does this code belong...?

            // if successful, result is route name or exported filename
            if (routeEvent == null) {
                Log.d(TAG, "Route Export failed: " + result);
                NotificationUtil.getInstance().postNotification(
                        params.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        ctx.getString(R.string.track_route_export_failed),
                        ctx.getString(R.string.failed_to_export)
                                + ExportTrackHistoryTask.this.params
                                        .getFormat(),
                        ctx.getString(R.string.failed_to_export)
                                + ExportTrackHistoryTask.this.params
                                        .getFormat());
                return;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(R.string.track_route_export);
            b.setIcon(R.drawable.ic_menu_routes);
            b.setMessage(ctx.getString(R.string.exported) + params.getName());
            b.setPositiveButton(R.string.send,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            SendDialog.Builder b = new SendDialog.Builder(
                                    mapView);
                            b.setName(params.getName());
                            b.setIcon(R.drawable.ic_menu_routes);
                            b.addMapItem(routeEvent.getUID());
                            b.show();
                        }
                    });
            b.setNegativeButton(R.string.done, null);
            b.show();
        } else {
            if (this.params.hasCallbackAction()) {
                Log.d(TAG,
                        "Sending success callback: "
                                + this.params.getCallbackAction());
                //update output path as needed and send callback
                this.params.setFilePath(result);
                Intent intent = new Intent(this.params.getCallbackAction());
                intent.putExtra("exportparams", this.params);
                intent.putExtra("success", true);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        }
    }

    private String exportTrackKMLnoTimestamps(String name, String exportPath,
            int[] track_dbids) {
        //first create route
        Route route = exportTrackRoute(name, track_dbids, null);
        if (route == null) {
            Log.e(TAG, "Unable to export empty KML route");
            return null;
        }

        final boolean clampToGround = prefs.getBoolean(
                "kmlExportGroundClamp", false);
        final String kmlExportCheckpointMode = prefs.getString(
                "kmlExportCheckpointMode", "Both");

        RouteKmlIO.CheckpointExportMode mode;
        try {
            mode = RouteKmlIO.CheckpointExportMode
                    .valueOf(kmlExportCheckpointMode);
        } catch (Exception e) {
            Log.w(TAG, "Using default KML Export Checkpoint Mode", e);
            mode = RouteKmlIO.CheckpointExportMode.Both;
        }

        // convert route to KML
        Folder folder = RouteKmlIO.toKml(mapView.getContext(),
                route, mode, clampToGround);
        if (folder == null) {
            Log.w(TAG,
                    "Unable to convert route to KML: "
                            + route.getTitle());
            return null;
        }

        try {
            // write KML out to file
            Kml kml = new Kml();
            kml.setFeature(folder);
            if (!exportPath.endsWith(".kml"))
                exportPath += ".kml";
            RouteKmlIO.write(kml, new File(exportPath));
            Log.d(TAG, route.getTitle() + " route KML exported to: "
                    + exportPath);
            return exportPath;
        } catch (Exception e) {
            Log.e(TAG,
                    "Unable to convert route to KML: "
                            + route.getTitle(),
                    e);
            return null;
        }
    }

    private String exportTrackKMZnoTimestamps(String name, String exportPath,
            int[] track_dbids) {
        Log.d(TAG, "Exporting route tracks to KMZ: " + exportPath);

        //TODO include self icon for track?
        String kmlFile = exportTrackKMLnoTimestamps(name, exportPath,
                track_dbids);
        if (FileSystemUtils.isEmpty(kmlFile)
                || !FileSystemUtils.isFile(kmlFile)) {
            Log.w(TAG, "Cannot zip KMZ w/out KML document");
            return null;
        }

        File kml = new File(kmlFile);
        File kmz = new File(kml.getParentFile(), name + ".kmz");
        try (ZipOutputStream zos = FileSystemUtils.getZipOutputStream(kmz)) {

            //and doc.kml
            FileSystemUtils.addFile(zos, kml, "doc.kml");

            Log.d(TAG, "Exported KMZ track: " + kmz.getAbsolutePath());
            //now clean up temp kml file
            FileSystemUtils.deleteFile(kml);
            return kmz.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create KMZ file", e);
            return null;
        }
    }

    private String exportTrackKMZwithTimestamps(String uid, String name,
            String exportPath,
            int[] track_dbids) {
        Log.d(TAG, "Exporting time tracks to KMZ: " + exportPath);

        //TODO include self icon for track?
        String kmlFile = exportTrackKMLwithTimestamps(name, exportPath,
                track_dbids);
        if (FileSystemUtils.isEmpty(kmlFile)
                || !FileSystemUtils.isFile(kmlFile)) {
            Log.w(TAG, "Cannot zip KMZ w/out KML document");
            return null;
        }

        File kml = new File(kmlFile);
        File kmz = new File(kml.getParentFile(), name + ".kmz");
        try (ZipOutputStream zos = FileSystemUtils.getZipOutputStream(kmz)) {

            //and doc.kml
            FileSystemUtils.addFile(zos, kml, "doc.kml");

            Log.d(TAG, "Exported KMZ track: " + kmz.getAbsolutePath());
            //now clean up temp kml file
            FileSystemUtils.deleteFile(kml);
            return kmz.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create KMZ file", e);
            return null;
        }
    }

    /**
     * Export specified tracks to a single KML file
     *
     * @param name Track name
     * @param track_dbids Track IDs
     * @return path of exported file
     */
    private String exportTrackKMLwithTimestamps(String name, String exportPath,
            int[] track_dbids) {
        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG, "Crumb DB not available, cannot export KML track");
            return null;
        }

        Log.d(TAG, "Exporting time tracks to KML: " + exportPath);

        final boolean clampToGround = prefs.getBoolean(
                "kmlExportGroundClamp", false);

        if (!exportPath.endsWith(".kml"))
            exportPath += ".kml";
        File exportFile = new File(exportPath);

        // setup containers
        Kml combinedKml = new Kml();
        Document document = new Document();
        document.setName(name);
        document.setDescription(name
                + " generated by "
                + ATAKConstants.getVersionName()
                +
                " on: "
                + KMLUtil.KMLDateTimeFormatter.get().format(
                        CoordinatedTime.currentDate()));
        document.setOpen(1);
        combinedKml.setFeature(document);
        List<StyleSelector> styleSelector = new ArrayList<>();
        document.setStyleSelector(styleSelector);
        List<Schema> schemaList = new ArrayList<>();
        document.setSchemaList(schemaList);
        Schema schema = new Schema();
        schemaList.add(schema);
        schema.setId(KMLUtil.ATAK_KML_TRACK_EXTENDED_SCHEMA);
        List<SimpleField> simpleFieldList = new ArrayList<>();
        schema.setSimpleFieldList(simpleFieldList);
        SimpleField simpleField = new SimpleField();
        simpleField.setDisplayName(MapView.getMapView().getContext()
                .getString(R.string.speed));
        simpleField
                .setName(MapView.getMapView().getContext()
                        .getString(R.string.speed)
                        .toLowerCase(LocaleUtil.getCurrent()));
        simpleField.setType("double");
        simpleFieldList.add(simpleField);
        simpleField = new SimpleField();
        simpleField.setDisplayName(MapView.getMapView().getContext()
                .getString(R.string.circular_error));
        simpleField.setName(MapView.getMapView().getContext()
                .getString(R.string.circular_error_abv));
        simpleField.setType("double");
        simpleFieldList.add(simpleField);
        simpleField = new SimpleField();
        simpleField.setDisplayName(MapView.getMapView().getContext()
                .getString(R.string.linear_error));
        simpleField.setName(MapView.getMapView().getContext()
                .getString(R.string.linear_error_Abv));
        simpleField.setType("double");
        simpleFieldList.add(simpleField);

        Folder checkpoints = new Folder();
        checkpoints.setFeatureList(new ArrayList<Feature>());
        Folder tracks = new Folder();
        tracks.setFeatureList(new ArrayList<Feature>());

        document.setFeatureList(new ArrayList<Feature>());
        document.getFeatureList().add(tracks);
        document.getFeatureList().add(checkpoints);

        // get points from each track log
        for (final int trackDbId : track_dbids) {
            // convert to polyline, do not check TimeSpan
            //Get track, but not points, as we need full crumbs below
            TrackPolyline currentPolyline = db.getTrack(trackDbId,
                    false);
            if (currentPolyline == null) {
                Log.w(TAG, "Unable to load Track: " + trackDbId);
                continue;
            }

            String trackStart = KMLUtil.KMLDateTimeFormatter.get().format(
                    new Date(currentPolyline.getMetaLong("timestamp", 0)));

            List<CrumbPoint> currentCrumbs = db.getCrumbPoints(trackDbId);
            if (currentCrumbs == null || currentCrumbs.size() < 1) {
                Log.w(TAG, "Unable to load Track points: " + trackDbId);
                continue;
            }

            //create style
            Style curStyle = getTrackStyle(currentPolyline);

            // dont duplicate styles, some tracks may have same color/style)
            // we need to manually loop and not double add the same style
            // SimpleKML does not implement .equals so cant just use list contains() method
            boolean bFound = false;
            for (StyleSelector ss : styleSelector) {
                if (ss instanceof Style && ss.getId().equals(curStyle.getId()))
                    bFound = true;
            }

            if (!bFound) {
                styleSelector.add(curStyle);
            }

            //create placemark, timestamp per point...
            Track currentTrack = KMLUtil.convertKmlCoords(currentCrumbs,
                    clampToGround);
            if (currentTrack == null) {
                Log.w(TAG, "Unable to load Track KML: " + trackDbId);
                continue;
            }

            if (!KMLUtil.isValid(currentTrack)) {
                Log.w(TAG, "Unable to load invalid Track KML: " + trackDbId);
                continue;
            }

            Placemark trackPlacemark = new Placemark();
            List<Geometry> geometryList = new ArrayList<>();
            geometryList.add(currentTrack);
            trackPlacemark.setGeometryList(geometryList);
            trackPlacemark.setId(currentPolyline.getMetaInteger(
                    CrumbDatabase.META_TRACK_DBID, -1)
                    + currentPolyline.getMetaString("title", "Track"));
            trackPlacemark.setStyleUrl("#" + curStyle.getId());
            trackPlacemark.setName(currentPolyline.getMetaString("title",
                    "Track"));

            //TODO not setting extended data b/c KMLUtil.convertKmlCoords has already done so
            //            List<Data> dataList = new ArrayList<Data>();
            //            Data data = new Data();
            //            data.setName("linestyle");
            //            data.setValue(DEFAULT_LINE_STYLE);
            //            dataList.add(data);
            //            ExtendedData edata = new ExtendedData();
            //            edata.setDataList(dataList);
            //            trackPlacemark.setExtendedData(edata);

            tracks.getFeatureList().add(trackPlacemark);

            String callsign = currentPolyline.getMetaString(
                    CrumbDatabase.META_TRACK_NODE_TITLE, "Node");

            // create waypoint at beginning of segment
            CrumbPoint cwp = currentCrumbs.get(0);
            if (cwp != null && cwp.gp.isValid()) {
                String when = KMLUtil.KMLDateTimeFormatter.get().format(
                        new Date(cwp.timestamp));

                Placemark checkpointPlacemark = new Placemark();
                Point placemarkPoint = new Point();
                placemarkPoint.setCoordinates(KMLUtil.convertKmlCoord(cwp.gpm,
                        clampToGround));
                checkpointPlacemark.setId(UUID.randomUUID().toString());
                checkpointPlacemark.setName(callsign + " " + when);
                checkpointPlacemark.setDescription(MapView.getMapView()
                        .getContext().getString(R.string.start_of_track)
                        + callsign
                        + MapView.getMapView().getContext()
                                .getString(R.string.beginning_at)
                        + trackStart);
                checkpointPlacemark.setStyleUrl("#" + curStyle.getId());
                geometryList = new ArrayList<>();
                geometryList.add(placemarkPoint);
                checkpointPlacemark.setGeometryList(geometryList);
                checkpoints.getFeatureList().add(checkpointPlacemark);

                if (clampToGround)
                    placemarkPoint.setAltitudeMode("clampToGround");
                else
                    placemarkPoint.setAltitudeMode("absolute");

                TimeStamp time = new TimeStamp();
                time.setWhen(when);
                checkpointPlacemark.setTimePrimitive(time);

                //set name/description based on callsign, just take first crumb for now...
                trackPlacemark.setName(callsign + " "
                        + currentPolyline.getMetaString("title", "Track"));
                trackPlacemark.setDescription(MapView.getMapView().getContext()
                        .getString(R.string.track_for)
                        + callsign
                        + MapView.getMapView().getContext()
                                .getString(R.string.beginning_at)
                        + trackStart);

                Log.d(TAG, "Adding KML route checkpoint: " + callsign);
            }
        }

        if (checkpoints.getFeatureList().size() < 1
                && tracks.getFeatureList().size() < 1) {
            Log.w(TAG, "Unable to export at least 1 KML placemark for " + name);
            return null;
        }

        // now export to the ATAK export folder
        try {
            if (!TrackLogKMLSerializer.write(mapView.getContext(), combinedKml,
                    exportFile))
                return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export tracks as KML: " + name, e);
            return null;
        }

        return exportFile.getAbsolutePath();
    }

    private Style getTrackStyle(TrackPolyline item) {
        LineStyle lineStyle = new LineStyle();
        lineStyle.setWidth(4F);
        lineStyle.setColor(KMLUtil.convertKmlColor(item.getStrokeColor()));

        IconStyle iconStyle = new IconStyle();
        iconStyle.setColor(KMLUtil.convertKmlColor(item.getStrokeColor()));

        Style style = new Style();
        style.setLineStyle(lineStyle);
        style.setIconStyle(iconStyle);
        style.setId(KMLUtil.hash(style));
        return style;
    }

    /**
     * Export specified tracks to a single GPX file
     *
     * @param exportPath Output GPX file
     * @param track_dbids Track IDs
     * @return path of exported file
     */
    private String exportTrackGPX(String name, String exportPath,
            int[] track_dbids) {
        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG, "Crumb DB not available, cannot export GPX track");
            return null;
        }

        Log.d(TAG, "Exporting tracks to GPX: " + exportPath);

        if (!exportPath.endsWith(".gpx"))
            exportPath += ".gpx";
        File exportFile = new File(exportPath);

        // setup containers
        Gpx gpx = new Gpx();
        List<GpxTrack> tracks = new ArrayList<>();
        gpx.setTracks(tracks);
        GpxTrack gpxtrack = new GpxTrack();
        gpxtrack.setName(name);
        gpxtrack.setDesc(name
                + " generated by "
                + ATAKConstants.getVersionName()
                +
                " on: "
                + KMLUtil.KMLDateTimeFormatter.get().format(
                        CoordinatedTime.currentDate()));

        final List<GpxTrackSegment> segments = new ArrayList<>();
        gpxtrack.setSegments(segments);
        tracks.add(gpxtrack);

        // add checkpoints as waypoint
        final List<GpxWaypoint> checkpoints = new ArrayList<>();

        // get points from each track log
        for (final int trackDbId : track_dbids) {
            // convert to polyline, do not check TimeSpan
            //Get track but not points, as we need full crumbs, queried below
            TrackPolyline currentPolyline = db.getTrack(trackDbId, false);
            if (currentPolyline == null) {
                Log.w(TAG, "Unable to load Track: " + trackDbId);
                continue;
            }

            List<CrumbPoint> currentCrumbs = db.getCrumbPoints(trackDbId);
            if (currentCrumbs == null || currentCrumbs.size() < 1) {
                Log.w(TAG, "Unable to load Track points: " + trackDbId);
                continue;
            }

            List<GpxWaypoint> points = new ArrayList<>();
            for (CrumbPoint c : currentCrumbs) {
                if (c == null || !c.gp.isValid()) {
                    Log.w(TAG,
                            "Skipping invalid crumb for track: " + trackDbId);
                    continue;
                }

                GpxWaypoint wp = new GpxWaypoint();
                // TODO set any other fields? error?
                wp.setLat(c.gp.getLatitude());
                wp.setLon(c.gp.getLongitude());
                // Note KML and GPX both call for ISO 8601 time formats
                wp.setTime(KMLUtil.KMLDateTimeFormatter.get().format(
                        new Date(c.timestamp)));

                // TODO any elevation conversion?
                double alt = EGM96.getMSL(c.gp);
                if (!c.gp.isAltitudeValid() || Double.isNaN(alt)) {
                    wp.setEle(alt);
                }

                points.add(wp);
            }

            if (points.size() < 1) {
                Log.e(TAG, "Unable to create GPX Segment with no points");
                continue;
            }

            GpxTrackSegment segment = new GpxTrackSegment();
            segment.setPoints(points);
            segments.add(segment);

            // create waypoint at beginning of segment
            // Note this also sets name, etc on the first point of the route in
            // addition to the GPX waypoint
            GpxWaypoint gwp = segment.getWaypoints().get(0);
            gwp.setName(
                    currentPolyline.getMetaString("title", "Track Segment"));
            gwp.setDesc("color="
                    + currentPolyline.getMetaInteger("color", Color.WHITE)
                    +
                    ", style="
                    + currentPolyline.getMetaString("linestyle",
                            BreadcrumbReceiver.DEFAULT_LINE_STYLE));
            checkpoints.add(gwp);
        }

        if (segments.size() < 1) {
            Log.w(TAG, "Unable to export at least 1 GPX Track Segment for "
                    + name);
            return null;
        }

        if (checkpoints.size() > 0) {
            Log.d(TAG,
                    "Creating GPX Route checkpoint count: "
                            + checkpoints.size());
            gpx.setWaypoints(checkpoints);
        }

        try {
            RouteGpxIO.write(gpx, exportFile);
        } catch (Exception e) {
            Log.e(TAG, "Failed to export tracks as GPX: " + name, e);
            return null;
        }

        return exportFile.getAbsolutePath();
    }

    private Route exportTrackRoute(String name, int[] track_dbids,
            MapGroup routeGroup) {
        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG, "Crumb DB not available, cannot export track route");
            return null;
        }

        String routeUID = UUID.randomUUID().toString();
        if (track_dbids.length == 1)
            routeUID = params.getUid() + ".track." + track_dbids[0];

        double toleranceMeters = 1;

        Log.d(TAG, "Exporting tracks as Route: " + name);

        // pull checkpoint name prefix from user prefs
        String prefix = prefs.getString("waypointPrefix", "CP");
        Route route = null;
        if (routeGroup != null) {
            MapItem mi = routeGroup.deepFindUID(routeUID);
            if (mi instanceof Route) {
                route = (Route) mi;
            } else {
                MapGroup group = routeGroup.addGroup(name);
                group.setMetaBoolean("addToObjList", false);
            }
        }
        int color = Integer.parseInt(prefs.getString("defaultRouteColor",
                String.valueOf(Route.DEFAULT_ROUTE_COLOR)));

        // get points from each track log
        long skippedPoints = 0;

        // get points from each track log
        List<PointMapItem> pmiList = new ArrayList<>();
        for (int trackDBindex = 0; trackDBindex < track_dbids.length; trackDBindex++) {
            // convert to polyline, do not check TimeSpan
            final int trackDbId = track_dbids[trackDBindex];

            TrackPolyline currentPolyline = db.getTrack(trackDbId,
                    true);
            if (currentPolyline == null) {
                Log.w(TAG, "Unable to load Track: " + trackDbId);
                continue;
            }

            GeoPointMetaData[] curPoints = currentPolyline.getMetaDataPoints();
            if (curPoints == null || curPoints.length < 1) {
                Log.w(TAG, "Unable to load export points from Track: "
                        + trackDBindex);
                continue;
            }
            color = currentPolyline.getStrokeColor();

            GeoPoint prevPoint = null;
            Marker routePoint;
            boolean firstPoint = true;

            for (int pointIndex = 0; pointIndex < curPoints.length; pointIndex++) {
                // if not first or last point, and within close range of previous point, skip it
                if (!firstPoint && prevPoint != null
                        && !(pointIndex == curPoints.length - 1)) {
                    if (GeoCalculations.distanceTo(prevPoint,
                            curPoints[pointIndex].get()) < toleranceMeters) {
                        skippedPoints++;
                        // Log.d(TAG, "Skipping point " + curPoints[pointIndex].toString() +
                        // " in file: " + filepath);
                        continue;
                    }
                }

                // first point from each track log is a checkpoint
                // last point from last track log is a checkpoint
                if (firstPoint
                        || (trackDBindex == track_dbids.length - 1
                                && pointIndex == curPoints.length - 1)) {
                    firstPoint = false;
                    routePoint = Route.createWayPoint(curPoints[pointIndex],
                            UUID.randomUUID().toString());
                    pmiList.add(routePoint);
                } else {
                    pmiList.add(
                            Route.createControlPoint(
                                    curPoints[pointIndex].get()));
                }

                prevPoint = curPoints[pointIndex].get();
            }

            Log.d(TAG, "Added " + curPoints.length + " points from track log "
                    + trackDbId);
        }

        if (pmiList.size() < 2) {
            Log.w(TAG, "Unable to add at least 2 points to route " + name);
            return null;
        }

        if (route == null) {
            route = new Route(mapView, name, color, prefix, routeUID,
                    routeGroup != null);
        } else {
            route.setTitle(name);
            route.clearPoints();
        }
        route.addMarkers(0, pmiList.toArray(new PointMapItem[0]));
        route.setColor(color);

        Log.d(TAG, "Added " + route.getNumPoints() + " points and skipped "
                + skippedPoints);
        return route;
    }

    private String exportTrackCSV(String uid, String name, String exportPath,
            int[] track_dbids) {
        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG, "Crumb DB not available, cannot export track CSV");
            return null;
        }

        Log.d(TAG, "Exporting tracks to CSV: " + name);

        final boolean bExportHeaders = prefs.getBoolean(
                "track_csv_export_headers", true);

        // setup containers
        if (!exportPath.endsWith(".csv"))
            exportPath += ".csv";
        File exportFile = new File(exportPath);

        try (OutputStream os = IOProviderFactory.getOutputStream(exportFile)) {

            StringBuilder sb = new StringBuilder();

            if (bExportHeaders) {
                sb.append(HEADER + NEW_LINE);
            }

            // get points from each track log
            long lastTimestep = -1;
            for (final int trackDbId : track_dbids) {
                // convert to polyline, do not check TimeSpan
                TrackPolyline currentTrack = db.getTrack(trackDbId, false);
                List<CrumbPoint> currentCrumbs = db.getCrumbPoints(trackDbId);
                if (currentTrack == null || currentCrumbs == null
                        || currentCrumbs.size() < 1) {
                    Log.w(TAG, "Unable to load Track points: " + trackDbId);
                    continue;
                }

                for (CrumbPoint c : currentCrumbs) {
                    if (c == null || !c.gp.isValid()) {
                        Log.w(TAG, "Skipping invalid crumb for track: "
                                + trackDbId);
                        continue;
                    }

                    //skip duplicate timestamps, e.g. from segment stitching
                    if (c.timestamp == lastTimestep) {
                        Log.d(TAG, "Skipping duplicate timestamp: "
                                + lastTimestep);
                        continue;
                    }

                    //write out to file
                    sb.append(currentTrack.getMetaString(
                            CrumbDatabase.META_TRACK_NODE_UID, ""))
                            .append(DELIMITER);
                    sb.append(currentTrack.getMetaString(
                            CrumbDatabase.META_TRACK_NODE_TITLE, ""))
                            .append(DELIMITER);
                    sb.append(c.timestamp).append(DELIMITER);
                    sb.append(c.gp.getLatitude()).append(DELIMITER);
                    sb.append(c.gp.getLongitude()).append(DELIMITER);
                    sb.append(c.gp.isAltitudeValid() ? c.gp.getAltitude() : "")
                            .append(DELIMITER);
                    sb.append(c.gp.getCE()).append(DELIMITER);
                    sb.append(c.gp.getLE()).append(DELIMITER);
                    sb.append(c.bearing).append(DELIMITER);
                    sb.append(c.speed).append(DELIMITER);
                    sb.append(c.gpm.getGeopointSource())
                            .append(DELIMITER);
                    sb.append(c.gpm.getAltitudeSource())
                            .append(DELIMITER);
                    sb.append("MakePoint(").append(c.gp.getLongitude())
                            .append(", ").append(c.gp.getLatitude())
                            .append(", 4326)");
                    sb.append(NEW_LINE);

                    lastTimestep = c.timestamp;
                } //end crumbs loop
            } //end track loop

            os.write(sb.toString().getBytes(FileSystemUtils.UTF8_CHARSET));
        } catch (IOException e) {
            Log.w(TAG, "Error while exporting tracks to CSV", e);
        }

        return exportFile.getAbsolutePath();
    }
}
