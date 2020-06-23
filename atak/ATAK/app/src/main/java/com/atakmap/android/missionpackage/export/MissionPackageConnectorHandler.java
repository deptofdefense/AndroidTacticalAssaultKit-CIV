
package com.atakmap.android.missionpackage.export;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactConnectorManager;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Handle Mission Package connectors
 * Currently allows user to select a mission package from a list of existing packages
 * TODO support other quick data send? e.g. a route, marker, GRG, or KML file, etc
 *
 *
 */
public class MissionPackageConnectorHandler extends
        ContactConnectorManager.ContactConnectorHandler {

    private final static String TAG = "MissionPackageConnectorHandler";
    private final Context _context;

    public MissionPackageConnectorHandler(Context context) {
        _context = context;
    }

    @Override
    public boolean isSupported(String type) {
        return FileSystemUtils.isEquals(type,
                MissionPackageConnector.CONNECTOR_TYPE);
    }

    @Override
    public boolean hasFeature(
            ContactConnectorManager.ConnectorFeature feature) {
        return feature == ContactConnectorManager.ConnectorFeature.Presence;
    }

    @Override
    public String getName() {
        return _context.getString(R.string.mission_package_name);
    }

    @Override
    public String getDescription() {
        return _context.getString(R.string.app_name) + " provides "
                + _context.getString(R.string.mission_package_name)
                + " support";
    }

    @Override
    public Object getFeature(String connectorType,
            ContactConnectorManager.ConnectorFeature feature,
            String contactUID, String connectorAddress) {
        if (feature == ContactConnectorManager.ConnectorFeature.Presence) {
            Contact c = Contacts.getInstance().getContactByUuid(contactUID);
            if (c != null)
                return c.getUpdateStatus();
        }

        return null;
    }

    @Override
    public boolean handleContact(String connectorType, String contactUID,
            String address) {
        if (FileSystemUtils.isEmpty(contactUID)) {
            Log.w(TAG, "Unable to handleContact: " + contactUID + ", "
                    + address);
            return false;
        }

        Log.d(TAG, "handleContact: " + contactUID + ", " + address);
        //TODO is this OK on UI thread? Pulls from DB, does XML serializatinon for all mission packages
        return sendMissionPackage(contactUID);
    }

    private boolean sendMissionPackage(final String contactUID) {
        //pull list of available packages
        final MissionPackageAdapter adapter = new MissionPackageAdapter(
                _context);
        if (adapter.getCount() == 0) {
            Log.d(TAG, "No packages to send");
            Toast.makeText(_context, "No " + _context.getString(
                    R.string.mission_package_name) + "s available",
                    Toast.LENGTH_SHORT).show();
            return true;
        }

        //allow user to select a package
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setIcon(R.drawable.ic_menu_missionpackage);
        b.setTitle(_context.getString(R.string.choose_mission_package));
        b.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MissionPackageManifest m = (MissionPackageManifest) adapter
                        .getItem(which);
                if (m == null || !m.isValid()) {
                    Log.w(TAG, "Failed to select mission package");
                    return;
                }

                //send selected package
                MissionPackageApi.SendUIDs(_context, m, (Class) null,
                        new String[] {
                                contactUID
                }, true);
            }
        });
        b.show();
        return true;
    }
}
