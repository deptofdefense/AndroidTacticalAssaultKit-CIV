
package com.atakmap.android.image.quickpic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.Toast;

import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.image.ExifHelper;
import com.atakmap.android.image.ImageActivity;
import com.atakmap.android.image.ImageContainer;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import com.atakmap.annotations.ModifierApi;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Support capturing and sharing Quick Pics
 * 
 * 
 */
public class QuickPicReceiver extends BroadcastReceiver {
    static public final String TAG = "QuickPicReceiver";

    public static final String QUICK_PIC = "com.atakmap.android.image.quickpic.QUICK_PIC";
    public static final String QUICK_PIC_CAPTURED = "com.atakmap.android.image.quickpic.QUICK_PIC_CAPTURED";
    public static final String QUICK_PIC_RECEIVED = "com.atakmap.android.image.quickpic.QUICK_PIC_RECEIVED";
    public static final String QUICK_PIC_VIEW = "com.atakmap.android.image.quickpic.QUICK_PIC_VIEW";
    public static final String QUICK_PIC_MOVE = "com.atakmap.android.image.quickpic.QUICK_PIC_MOVE";

    public static final String QUICK_PIC_IMAGE_TYPE = "b-i-x-i";

    public static final String CAMERA_CHOOSER_PREF = "quickpic.camera_chooser";

    /**
     * Intent/Action to generate once image is captured
     */
    static private final ActionBroadcastData _callbackIntent = new ActionBroadcastData(
            QUICK_PIC_CAPTURED, null);

    private MapView _mapView;
    private static volatile MapGroup _quickPicMapGroup;
    private final AtakPreferences preferences;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public QuickPicReceiver(Context context, MapView mapView) {
        _mapView = mapView;

        _quickPicMapGroup = _mapView.getRootGroup().findMapGroup("Quick Pic");
        if (_quickPicMapGroup == null) {
            synchronized (QuickPicReceiver.class) {
                if (_quickPicMapGroup == null) {
                    _quickPicMapGroup = new DefaultMapGroup("Quick Pic");
                    _quickPicMapGroup.setMetaString("overlay", "quickpic");
                    _quickPicMapGroup.setMetaBoolean("permaGroup", true);
                    _quickPicMapGroup.setMetaBoolean("addToObjList", false);
                    _mapView.getRootGroup().addGroup(_quickPicMapGroup);
                }
            }
        }

        preferences = new AtakPreferences(mapView.getContext());
    }

    public static MapGroup getMapGroup() {
        return _quickPicMapGroup;
    }

    public void dispose() {
        _mapView = null;
    }

    @Override
    public void onReceive(Context ignoreCtx, Intent intent) {

        final Context context = _mapView.getContext();

        // Start quick pic activity
        if (QUICK_PIC.equals(intent.getAction())) {
            Log.d(TAG, "Running QUICK_PIC");
            String uid = UUID.randomUUID().toString();
            File img = ImageDropDownReceiver
                    .createAndGetPathToImageFromUID(uid,
                            "jpg");
            if (img != null) {
                File dir = img.getParentFile();

                chooser(_mapView, preferences, img, uid, dir, _callbackIntent);
            }
        }

        // Quick pic has been captured
        else if (QUICK_PIC_CAPTURED.equals(intent.getAction())) {
            Log.d(TAG, "Running QUICK_PIC_CAPTURED");

            String uid = intent.getStringExtra("uid");
            String path = intent.getStringExtra("path");

            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Cannot QUICK_PIC_CAPTURED with no UID");
                Toast.makeText(context,
                        context.getString(R.string.quickpic_no_image_captured),
                        Toast.LENGTH_LONG)
                        .show();
                return;
            }

            if (FileSystemUtils.isEmpty(path)) {
                Log.w(TAG, "Cannot QUICK_PIC_CAPTURED with no path");
                Toast.makeText(context,
                        context.getString(R.string.quickpic_no_image_captured),
                        Toast.LENGTH_LONG)
                        .show();
                return;
            }

            // Get image location
            TiffImageMetadata exif = ExifHelper.getExifMetadata(new File(
                    FileSystemUtils.sanitizeWithSpacesAndSlashes(path)));
            GeoPoint gp = exif != null ? ExifHelper.getLocation(exif) : null;
            if (gp == null) {
                // Use self marker position
                Marker self = _mapView.getSelfMarker();
                if (self != null && self.getGroup() != null)
                    gp = self.getPoint();
            }
            if (gp == null) {
                // Finally fallback to the center of the map
                gp = _mapView.getCenterPoint().get();
            }

            new PlacePointTool.MarkerCreator(gp)
                    .setUid(uid)
                    .setType(QUICK_PIC_IMAGE_TYPE)
                    .showCotDetails(false)
                    .placePoint();

            // if location not available, focus the map on the image location
            // otherwise it should already be in view...
            if (gp.equals(GeoPoint.ZERO_POINT)) {
                Log.w(TAG, "No location available, Quick Pic placed at 0,0");
                Toast.makeText(
                        context,
                        _mapView.getResources().getString(
                                R.string.quickpic_no_location_tip),
                        Toast.LENGTH_LONG).show();
            }

            // display the image, if user clicks send, then leverage Mission Package Tool
            intent = new Intent("com.atakmap.android.images.NEW_IMAGE");
            intent.putExtra("path", path);
            intent.putExtra("uid", uid);
            AtakBroadcast.getInstance().sendBroadcast(intent);

            // bundle some instructions in case user sends the map item
            intent = new Intent(ImageDropDownReceiver.IMAGE_DISPLAY)
                    .putExtra("uid", uid)
                    .putExtra("UseMissionPackageToSend", false)
                    .putExtra("onReceiveAction", QUICK_PIC_RECEIVED);
            AtakBroadcast.getInstance().sendBroadcast(intent);

            intent = new Intent(
                    "com.atakmap.android.maps.COT_RECENTLYPLACED");
            intent.putExtra("uid", uid);
            AtakBroadcast.getInstance().sendBroadcast(intent);

            Log.d(TAG, "Sent QUICK_PIC_CAPTURED Intent: " + path);
        }

        // Quick-pic has been received from another user
        else if (QUICK_PIC_RECEIVED.equals(intent.getAction())) {
            String sender = intent.getStringExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDERCALLSIGN);
            if (sender == null) {
                Log.w(TAG, "Received invalid attachment sender callsign");
                return;
            }

            MissionPackageManifest manifest = intent
                    .getParcelableExtra(
                            MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST);
            if (manifest == null || manifest.isEmpty()) {
                Log.w(TAG, "Received invalid attachment manifest");
                return;
            }

            int notificationid = intent.getIntExtra(
                    MissionPackageApi.INTENT_EXTRA_NOTIFICATION_ID,
                    -1);
            if (notificationid < 1) {
                Log.w(TAG, "Received invalid attachment notificationid");
                return;
            }

            // pull out the custom params identifying the map item the attachment is associated
            // with
            NameValuePair p = manifest.getConfiguration().getParameter(
                    "callsign");
            if (p == null || !p.isValid()) {
                Log.w(TAG, "Received invalid attachment, missing callsign");
                return;
            }
            String callsign = p.getValue();

            p = manifest.getConfiguration().getParameter("uid");
            if (p == null || !p.isValid()) {
                Log.w(TAG, "Received invalid attachment, missing uid");
                return;
            }
            String uid = p.getValue();

            // now build and update the ongoing notification
            String message = sender + context.getString(R.string.sent)
                    + callsign;

            // zoom map and view image
            Intent notificationIntent = new Intent();
            notificationIntent.setAction(QUICK_PIC_VIEW);
            notificationIntent.putExtra("uid", uid);
            notificationIntent.putExtra("focusmap", true);

            // Downloaded file transfer successfully...
            NotificationUtil.getInstance().postNotification(notificationid,
                    R.drawable.ic_menu_quickpic, NotificationUtil.WHITE,
                    context.getString(R.string.quick_pic_download), message,
                    notificationIntent, true);

            Log.d(TAG, "Updated notification for: " + callsign + ", "
                    + manifest);
        }

        // View quick-pic image
        else if (QUICK_PIC_VIEW.equals(intent.getAction())) {

            final String uid = intent.getStringExtra("uid");
            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Unable to View Attachments with no UID");
                return;
            }

            final MapItem item = _mapView.getMapItem(uid);
            if (item == null) {
                Log.w(TAG, "Unable to View Images with no Map Item for UID: "
                        + uid);
                return;
            }

            // optionally focus the map
            if (intent.getBooleanExtra("focusmap", false)) {
                Intent focus = new Intent();
                focus.setAction("com.atakmap.android.maps.FOCUS");
                focus.putExtra("uid", uid);
                AtakBroadcast.getInstance().sendBroadcast(focus);
            }

            Intent display = new Intent(ImageDropDownReceiver.IMAGE_DISPLAY)
                    .putExtra("uid", uid);
            AtakBroadcast.getInstance().sendBroadcast(display);
        }

        // Move quick-pic marker and attached image position metadata
        else if (QUICK_PIC_MOVE.equals(intent.getAction())) {
            String uid = intent.getStringExtra("uid");
            MapItem mi = uid != null ? _mapView.getRootGroup().deepFindUID(uid)
                    : null;
            if (!(mi instanceof Marker))
                return;
            GeoPoint point = GeoPoint.parseGeoPoint(intent
                    .getStringExtra("point"));
            if (point == null || !point.isValid())
                return;
            ((Marker) mi).setPoint(point);
            mi.persist(_mapView.getMapEventDispatcher(), null, getClass());
            List<File> files = AttachmentManager.getAttachments(uid);
            for (File f : files) {
                if (ImageContainer.JPEG_FilenameFilter.accept(null,
                        f.getName())) {
                    TiffImageMetadata exif = ExifHelper.getExifMetadata(f);
                    if (exif == null)
                        continue;
                    TiffOutputSet tos = ExifHelper.getExifOutput(exif);
                    if (tos == null)
                        continue;
                    if (ExifHelper.setPoint(tos, point)) {
                        // Need to rewrite extras or they get clobbered
                        Map<String, Object> extras = new HashMap<>();
                        ExifHelper.getExtras(exif, extras);
                        if (!extras.isEmpty())
                            ExifHelper.putExtras(extras, tos);
                        // Save image
                        ExifHelper.saveExifOutput(tos, f);
                    }
                } else if (ImageContainer.NITF_FilenameFilter.accept(null,
                        f.getName())) {
                    // TODO: Set NITF location
                    // Would need to pull it in a bunch of NITF writer code from Image Markup
                    // Is it worth it?
                }
            }
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    ImageDropDownReceiver.IMAGE_REFRESH)
                            .putExtra("uid", uid));
        }
    }

    /**
     * Chooser for the camera since on Android 11 you can only launch the system camera.
     *
     * @param _mapView the mapview to use
     * @param preferences the preferences
     * @param img the directory or file to capture the image into
     * @param uid the uid associated with the capture
     * @param dir the parent directory of the img
     * @param intent the broadcast data to return when the image is taken.
     */
    public static void chooser(MapView _mapView,
            AtakPreferences preferences,
            File img,
            String uid,
            File dir,
            ActionBroadcastData intent) {

        ImageActivity ia = new ImageActivity(_mapView, img, uid, intent,
                dir != null && (IOProviderFactory.exists(dir)
                        || IOProviderFactory.mkdirs(dir)));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ia.start();
        } else if (!AppMgmtUtils.isInstalled(_mapView.getContext(),
                "com.partech.geocamera")) {
            // blank this out so that when a user uninstalls and reinstalls TAK GeoCam
            // after using the system camera for a while, it then forces the user to be
            // repompted.
            preferences.remove(CAMERA_CHOOSER_PREF);
            ia.start();
        } else {
            String currentCameraApp = preferences.get(CAMERA_CHOOSER_PREF,
                    "Prompt");
            if (currentCameraApp.equals("System")) {
                ia.start();
            } else if (currentCameraApp.equals("TakGeoCam")) {
                ia.useTakGeoCam(true);
                ia.start();
            } else {
                TileButtonDialog tileButtonDialog = new TileButtonDialog(
                        _mapView);
                tileButtonDialog.setTitle(R.string.android_11_warning);
                tileButtonDialog.setMessage(
                        "Android 11 no longer allows for custom camera apps to be seamlessly used by applications.  Do you want to make use of TAK GeoCam or the System Camera application?");
                TileButtonDialog.TileButton tb = tileButtonDialog.createButton(
                        _mapView.getContext().getDrawable(R.drawable.camera),
                        "System Camera");
                tb.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        preferences.set(CAMERA_CHOOSER_PREF, "System");
                        ia.start();
                    }
                });
                tileButtonDialog.addButton(tb);

                tb = tileButtonDialog.createButton(
                        AppMgmtUtils.getAppDrawable(_mapView.getContext(),
                                "com.partech.geocamera"),
                        "TAK GeoCam");
                tb.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        preferences.set(CAMERA_CHOOSER_PREF, "TakGeoCam");
                        ia.useTakGeoCam(true);
                        ia.start();
                    }
                });
                tileButtonDialog.addButton(tb);
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        tileButtonDialog.show();
                    }
                });
            }
        }
    }

}
