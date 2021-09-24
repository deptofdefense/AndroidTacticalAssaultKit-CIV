
package com.atakmap.android.medline;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.cot.CotModificationManager;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.text.SimpleDateFormat;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.TimeZone;
import java.util.UUID;

public class MedicalLineBroadcastReceiver
        extends DropDownReceiver implements OnStateListener {

    public static final String TAG = "MedicalLineBroadcastReceiver";

    private final ModificationCallback mcb = new ModificationCallback();

    private final MedLineView _mlv;
    private final MapGroup _mapGroup;
    private boolean reselect = false;

    public MedicalLineBroadcastReceiver(final MapView mapView) {
        super(mapView);
        _mapGroup = mapView.getRootGroup().findMapGroup("CASEVAC");
        _mlv = MedLineView.getInstance(getMapView());
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onReceive(Context arg0, Intent intent) {

        PointMapItem target = null;

        if (intent.getAction().equals("com.atakmap.android.MED_LINE")) {
            String targetUID = intent.getExtras().getString("targetUID");
            if (targetUID != null)
                target = (PointMapItem) getMapView().getMapItem(targetUID);
            if (target == null)
                target = createMarker(intent.getExtras());
            if (target == null)
                return;

            // TODO need to make use of com.atakmap.android.cot.CotModificationManager;
            // so that if CoT is received during someone else editing - it is resolved
            // correctly.

            if (DropDownManager.getInstance().isTopDropDown(this)) {
                if (target != _mlv.getMarker()) {
                    closeDropDown();
                    reselect = true;
                } else {
                    if (!isVisible())
                        DropDownManager.getInstance().unHidePane();
                    return;
                }
            } else if (!isClosed()) {
                closeDropDown();
                reselect = true;
            }

            CotModificationManager.register(targetUID, mcb);

            if (_mlv.setMarker(target)) {
                showDropDown(_mlv.getView(), THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                        FULL_WIDTH, HALF_HEIGHT, this);
                setRetain(true);
                setSelected(target, "asset:/icons/medmission.png");
                save = true;
            }
        }
    }

    boolean save = true;

    @Override
    public void onDropDownSelectionRemoved() {
        save = false;
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width,
            double height) {
    }

    @Override
    public void onDropDownClose() {

        CotModificationManager.unregister(_mlv.getMarker().getUID());

        // TODO Auto-generated method stub
        //if we are opening a different dropdown
        //do not shutdown, shutdown has already been
        //called
        if (_mlv.getReopening())
            _mlv.toggleReopening();
        else
            _mlv.shutdown(save);

        //sendInternalCoTEvent(_mlv.getMarker());
        if (reselect) {
            CotModificationManager.register(_mlv.getMarker().getUID(), mcb);
            setSelected(_mlv.getMarker(), "asset:/icons/medmission.png");
            reselect = false;
        }

    }

    private Marker createMarker(final Bundle extras) {

        String uid = UUID.randomUUID().toString();
        Log.d(TAG, "wrap medevac marker: " + uid);

        if (extras.containsKey("uid")
                && !extras.getString("uid", "").trim().equals("")) {
            uid = extras.getString("uid");
        }

        Marker mark = new Marker(uid);

        final String callsign;

        if (extras.getString("callsign") == null) {
            Log.d(TAG, "no callsign set, setting the default to Med9Line");
            SimpleDateFormat sdf = new SimpleDateFormat("dd'.'HHmmss",
                    LocaleUtil.getCurrent());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String prefix = getMapView().getSelfMarker().getMetaString(
                    "callsign", "med9line");
            callsign = prefix + "."
                    + sdf.format(new CoordinatedTime().getMilliseconds());
        } else {
            callsign = extras.getString("callsign");
        }

        mark.setStyle(mark.getStyle() | Marker.STYLE_MARQUEE_TITLE_MASK);
        final String geoString = extras.getString("point");
        if (geoString == null) {
            Log.e(TAG, "geopoint is null (extras:point) return");
            return null;
        }

        mark.setPoint(GeoPoint.parseGeoPoint(geoString));
        mark.setTitle(callsign);
        mark.setMetaString("entry", "user");
        mark.setMetaBoolean("readiness", false);
        mark.setType(extras.getString("type"));
        mark.setMetaString("callsign", callsign);
        mark.setMetaBoolean("removable", true);
        mark.setMetaBoolean("editable", true);
        mark.setMovable(true);
        if (extras.containsKey("archive"))
            mark.setMetaBoolean("archive", extras.getBoolean("archive"));
        // TODO: add line number that must be changed in the actions xml
        _mapGroup.addItem(mark);
        mark.setMetaString("how", extras.getString("how"));
        mark.setMetaString("menu", "menus/damaged_friendly.xml");

        mark.setMetaBoolean("archive", true);
        mark.persist(getMapView().getMapEventDispatcher(), null,
                this.getClass());
        return mark;
    }

    public class ModificationCallback extends
            CotModificationManager.ModificationAction {

        private AlertDialog activeDialog = null;

        @Override
        public boolean checkAndPrompt(final CotEvent event,
                final Bundle bundle) {
            final String fromExtra = bundle.getString("from");

            if (fromExtra != null && !fromExtra.contentEquals("internal")) {
                MapItem m = _mlv.getMarker();
                if (m != null && m.getUID().equals(event.getUID())) {
                    //Prompt the user to accept or reject the changes
                    showAcceptDialog(event, bundle);
                    return true;
                }
            }
            return false;
        }

        /**
         * Function to show a dialog where the user can accept or reject changes to a marker
         *
         * @param event  the cot event.
         * @param bundle the bundle which accompanies the cot event.
         */
        public synchronized void showAcceptDialog(final CotEvent event,
                final Bundle bundle) {

            final MapView _mapView = MapView.getMapView();

            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    if (activeDialog != null) {
                        activeDialog.dismiss();
                        activeDialog = null;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            _mapView.getContext());
                    builder.setMessage(
                            _mapView.getContext().getString(
                                    com.atakmap.app.R.string.marker_open_warning)
                                    +
                                    _mapView.getContext().getString(
                                            com.atakmap.app.R.string.accept_changes));

                    builder.setNegativeButton(com.atakmap.app.R.string.reject,
                            null);
                    builder.setPositiveButton(com.atakmap.app.R.string.accept,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {

                                    final PointMapItem mi = _mlv.getMarker();
                                    _mlv.shutdown(false);
                                    approved(event, bundle);
                                    _mlv.setMarker(mi);
                                    dialog.dismiss();
                                }
                            });
                    activeDialog = builder.create();
                    activeDialog.show();
                }
            });
        }

    }
}
