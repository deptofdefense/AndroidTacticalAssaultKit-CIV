
package com.atakmap.android.cotdetails;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import android.widget.LinearLayout;

import com.atakmap.android.attachment.AttachmentBroadcastReceiver;
import com.atakmap.android.contact.ContactDetailDropdown;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.contact.ContactUtil;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.List;

public class CoTInfoBroadcastReceiver extends DropDownReceiver implements
        MapEventDispatchListener, OnStateListener, OnClickListener,
        PointMapItem.OnPointChangedListener {

    private static final String TAG = "CoTInfoBroadcastReceiver";
    public static final String COTINFO_DETAILS = "com.atakmap.android.cotdetails.COTINFO";

    protected CoTInfoView civ;

    protected PointMapItem targetPMI;
    private final MapGroup cotDetailsGroup;
    protected final SharedPreferences _prefs;
    // Default to send CoT only, but also support sending attachments
    // via Mission Package Tool
    boolean bUseMissionPackage;
    // if sent via Mission Package, receiver will generate this action
    String onReceiveAction;

    private PointMapItem pending;

    public CoTInfoBroadcastReceiver(final MapView mapView) {
        super(mapView);
        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        cotDetailsGroup = mapView.getRootGroup().addGroup("CoT Details");

        LayoutInflater inflater = LayoutInflater.from(mapView.getContext());
        civ = (CoTInfoView) inflater.inflate(R.layout.cotinfodrop, null);
        civ.initialize(getMapView());
        civ.updateDeviceLocation(null);
        mapView.getSelfMarker().addOnPointChangedListener(this);
        final MapEventDispatcher dispatcher = getMapView()
                .getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_PERSIST, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_REFRESH, this);

        // Retain this drop-down when another is opened on top of it
        setRetain(true);
    }

    synchronized void register(ExtendedInfoView eiv) {
        Log.d(TAG, "register: " + eiv);
        unregister(eiv);
        LinearLayout _extendedCotInfo = civ
                .findViewById(R.id.extendedCotInfo);
        _extendedCotInfo.addView(eiv);
    }

    synchronized void unregister(ExtendedInfoView eiv) {
        Log.d(TAG, "unregister: " + eiv);
        LinearLayout _extendedCotInfo = civ
                .findViewById(R.id.extendedCotInfo);
        _extendedCotInfo.removeView(eiv);
    }

    @Override
    public void disposeImpl() {
        final MapEventDispatcher dispatcher = getMapView()
                .getMapEventDispatcher();
        dispatcher.removeMapEventListener(MapEvent.ITEM_CLICK, this);
        dispatcher.removeMapEventListener(MapEvent.ITEM_PERSIST, this);
        dispatcher.removeMapEventListener(MapEvent.ITEM_REFRESH, this);
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        if (isVisible() && item.getPoint().isValid()) {
            if (civ != null)
                civ.updateDeviceLocation(item);
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String evtType = event.getType();
        if (evtType.equals(MapEvent.ITEM_REMOVED))
            Log.d(TAG, "calling remove"
                    + event.getItem().getUID() + " " + isVisible());

        if (!isVisible() || !(event.getItem() instanceof PointMapItem))
            return;

        PointMapItem pmi = (PointMapItem) event.getItem();
        if (evtType.equals(MapEvent.ITEM_CLICK)) {
            if (!ContactUtil.isTakContact(pmi)) {
                targetPMI = pmi;
                setSelected(targetPMI, "asset:/icons/outline.png");
                civ.setMarker(targetPMI);
            } else {
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(ContactDetailDropdown.CONTACT_DETAILS)
                                .putExtra("targetUID", pmi.getUID()));
            }
        } else if (pmi == civ._marker
                && (evtType.equals(MapEvent.ITEM_PERSIST)
                        || evtType.equals(MapEvent.ITEM_REFRESH))) {
            civ.postRefreshMarker();
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        switch (action) {
            case COTINFO_DETAILS:

                PointMapItem temp = findTarget(
                        intent.getStringExtra("targetUID"));
                if (temp == null || ContactUtil.isTakContact(temp)
                        || ATAKUtilities.isSelf(getMapView(), temp)) {
                    //allow ContactDetailDropdown to handle
                    Log.d(TAG,
                            "isContact: " + intent.getStringExtra("targetUID"));
                    return;
                }

                final Bundle extras = intent.getExtras();
                if (extras != null) {
                    bUseMissionPackage = extras
                            .getBoolean("UseMissionPackageToSend");
                    onReceiveAction = extras
                            .getString("onReceiveAction");
                }
                targetPMI = temp;

                if (isVisible()) {
                    setSelected(targetPMI, "asset:/icons/outline.png");
                    civ.setMarker(targetPMI);
                    return;
                }

                civ.setOnSendClickListener(this);

                setSelected(targetPMI, "asset:/icons/outline.png");
                final boolean success = civ.setMarker(targetPMI);

                if (isClosed() && success) {
                    showDropDown(civ, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                            FULL_WIDTH,
                            HALF_HEIGHT, this);
                } else if (!isVisible() && success) {
                    if (DropDownManager.getInstance().isTopDropDown(this)) {
                        // Hidden but not closed - un-hide pane
                        DropDownManager.getInstance().unHidePane();
                    } else {
                        // opening a new drop down will inevitably close the old drop down and
                        // clear out the civ.setMarker above.
                        pending = targetPMI;

                        closeDropDown();
                    }
                }

                break;
            case "com.atakmap.android.cotdetails.COTINFO_SETTYPE":
                String type = intent.getStringExtra("type");
                setType(type);
                _prefs.edit().putString("lastCoTTypeSet", type).apply();
                break;
        }
    }

    @Override
    public void onClick(View v) {
        if (targetPMI == null)
            return;

        int cotStaleSeconds = 300;
        try {
            cotStaleSeconds = Integer.parseInt(_prefs.getString(
                    "cotDefaultStaleSeconds",
                    String.valueOf(cotStaleSeconds)));
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "cotDefaultStaleSeconds error", nfe);
            cotStaleSeconds = 300;
        }
        targetPMI.setMetaInteger("cotDefaultStaleSeconds", cotStaleSeconds);

        final String uid = targetPMI.getUID();

        // no need to ask user if we know to use Mission Package Tool
        if (bUseMissionPackage) {
            Intent sendMissionPackage = new Intent();
            sendMissionPackage
                    .setAction(AttachmentBroadcastReceiver.SEND_ATTACHMENT);
            sendMissionPackage.putExtra("uid", uid);
            sendMissionPackage.putExtra("UseMissionPackage", true);
            if (!FileSystemUtils.isEmpty(onReceiveAction)) {
                sendMissionPackage.putExtra("onReceiveAction", onReceiveAction);
            }
            AtakBroadcast.getInstance().sendBroadcast(sendMissionPackage);

            closeDropDown();
            return;
        }

        // Prompt the user to include marker attachments
        promptSendAttachments(targetPMI, onReceiveAction, new Runnable() {
            @Override
            public void run() {
                // Include attachments
                closeDropDown();
            }
        }, new Runnable() {
            @Override
            public void run() {
                // Just send CoT marker
                sendCoT(uid);
            }
        });
    }

    protected void sendCoT(final String uid) {
        // Make sure the object is shared since the user hit "Send".
        if (targetPMI != null)
            targetPMI.setMetaBoolean("shared", true);

        Intent contactList = new Intent();
        contactList.setAction(ContactPresenceDropdown.SEND_LIST);
        contactList.putExtra("targetUID", uid);
        AtakBroadcast.getInstance().sendBroadcast(
                contactList);
    }

    public void setType(String type) {
        targetPMI.setType(type);
        targetPMI.refresh(this.getMapView().getMapEventDispatcher(), null,
                this.getClass());
        civ.setType(type);
    }

    @Override
    protected boolean onBackButtonPressed() {
        if (civ != null)
            civ.onBackButtonPressed();
        return false;
    }

    @Override
    public void onDropDownSelectionRemoved() {
        // the selected item was removed while the drop down was open, close the drop down and 
        // do not recreate the marker.

        getMapView().post(new Runnable() {
            @Override
            public void run() {
                civ.cleanup(false);
            }
        });
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (civ != null)
            civ.onVisible(v);
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        if (civ != null)
            civ.cleanup(true);
        clearAssociatedMapItems();

        // since this drop down shares the same CoTInfoView on the back end, closures and opens
        // might manipulate the infoview out of order.
        if (pending != null) {
            civ.setMarker(pending);
            pending = null;

            showDropDown(civ, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                    FULL_WIDTH,
                    HALF_HEIGHT, this);
        }
    }

    private void clearAssociatedMapItems() {
        //cotDetailsGroup.getItemCount();
        cotDetailsGroup.clearItems();
    }

    protected PointMapItem findTarget(final String targetUID) {
        PointMapItem pointItem = null;
        if (targetUID != null) {
            MapGroup rootGroup = getMapView().getRootGroup();
            MapItem item = rootGroup.deepFindUID(targetUID);
            if (item instanceof PointMapItem) {
                pointItem = (PointMapItem) item;
            }
        }
        return pointItem;
    }

    /**
     * Prompt the user with the option to include marker attachments
     * @param marker Marker to send
     * @param onReceiveAction Optional receive callback
     * @param onApprove Callback to fire when including attachments
     * @param onDeny Callback to fire when not including attachments
     */
    public static void promptSendAttachments(final MapItem marker,
            final String onReceiveAction, final Runnable onApprove,
            final Runnable onDeny) {

        // No marker = no action
        if (marker == null)
            return;

        // Make sure the object is shared since the user hit "Send".
        marker.setMetaBoolean("shared", true);

        final String uid = marker.getUID();
        final List<File> attachments = AttachmentManager.getAttachments(uid);
        if (FileSystemUtils.isEmpty(attachments)) {
            //no attachments, just send CoT
            if (onDeny != null)
                onDeny.run();
            return;
        }

        // Need context or else we can't display the dialog
        Context context = MapView.getMapView() != null
                ? MapView.getMapView().getContext()
                : null;
        if (context == null) {
            if (onDeny != null)
                onDeny.run();
            return;
        }

        if (FileSystemUtils.isEmpty(attachments)) {
            if (onDeny != null)
                onDeny.run();
            return;
        }

        // ask if user would like to include attachments
        AlertDialog.Builder adb = new AlertDialog.Builder(context);
        adb.setMessage(R.string.include_attachments);
        adb.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {

                        // Make sure file sharing is enabled
                        if (!MissionPackageMapComponent.getInstance()
                                .checkFileSharingEnabled()) {
                            if (onDeny != null)
                                onDeny.run();
                            return;
                        }

                        // use Mission Package Tools if more than one attachment
                        // only case we don't is a for a single image attachment (send
                        // CoT)
                        if (attachments.size() > 1) {
                            Intent sendMissionPackage = new Intent();
                            sendMissionPackage
                                    .setAction(
                                            AttachmentBroadcastReceiver.SEND_ATTACHMENT);
                            sendMissionPackage.putExtra("uid", uid);
                            sendMissionPackage.putExtra(
                                    "UseMissionPackage", true);
                            if (!FileSystemUtils.isEmpty(onReceiveAction))
                                sendMissionPackage.putExtra("onReceiveAction",
                                        onReceiveAction);
                            AtakBroadcast.getInstance().sendBroadcast(
                                    sendMissionPackage);
                        } else {
                            // send single file
                            Intent sendMissionPackage = new Intent();
                            sendMissionPackage.putExtra("uid", uid);
                            sendMissionPackage.putExtra("filepath",
                                    attachments.get(0).getAbsolutePath());

                            //If it is a single image prompt for resolution
                            if (ImageDropDownReceiver.ImageFileFilter
                                    .accept(attachments.get(0).getParentFile(),
                                            attachments.get(0).getName()))
                                sendMissionPackage.setAction(
                                        ImageDropDownReceiver.IMAGE_SELECT_RESOLUTION);
                            else
                                sendMissionPackage.setAction(
                                        AttachmentBroadcastReceiver.SEND_ATTACHMENT);

                            AtakBroadcast.getInstance().sendBroadcast(
                                    sendMissionPackage);
                        }
                        if (onApprove != null)
                            onApprove.run();
                    }
                });
        adb.setNegativeButton(R.string.no,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (onDeny != null)
                            onDeny.run();
                    }
                });
        adb.show();
    }
}
