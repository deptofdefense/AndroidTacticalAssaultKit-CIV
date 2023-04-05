
package com.atakmap.app;

import java.util.List;
import java.util.Properties;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.importfiles.sort.ImportMissionPackageSort;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.update.AppMgmtActivity;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.preferences.CallSignAndDeviceFragment;
import com.atakmap.app.preferences.MyPreferenceFragment;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.comms.CotService;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.app.CredentialsDialog;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.net.CertificateEnrollmentClient;

/**
 * Device startup wizard, displayed at startup.
 * Once accepted, checks for credentials for streaming connections
 */
class DeviceSetupWizard implements CredentialsDialog.Callback {

    private static final String TAG = "DeviceSetupWizard";

    private final ATAKActivity _context;
    private final MapView _mapView;
    private final SharedPreferences _controlPrefs;

    /**
     * Store state of wizard, so we can maintain integrity of wizard steps e.g. user click twice
     * before UI events can be processed.
     *
     * Stores the last page processed, so starts with 0 and changes to 1 after the user has made
     * his selection on the first page
     */
    private int wizardPage;
    private final int wizardPageTotal;

    DeviceSetupWizard(ATAKActivity context, MapView mapView,
            SharedPreferences controlPrefs) {
        this._context = context;
        this._mapView = mapView;
        this._controlPrefs = controlPrefs;

        wizardPage = 0;
        wizardPageTotal = 2;
    }

    /**
     * Builds the EULA prompt for the system.
     * @param force true to force the device to run even if it has already been run before.
     */
    void init(boolean force) {
        if (!_controlPrefs.getBoolean("PerformedDeviceSetupWizard", false)
                || force) {
            wizardPage = 0;
            init_settings();
            SharedPreferences.Editor editor = _controlPrefs.edit();
            editor.putBoolean("PerformedDeviceSetupWizard", true);
            editor.putBoolean("PerformedLegacyPrompt", true);
            editor.apply();
        } else {
            init_creds();
            if (!_controlPrefs.getBoolean("PerformedLegacyPrompt", false)) {
                toolbarPrompt();
                _controlPrefs.edit().putBoolean("PerformedLegacyPrompt", true)
                        .apply();
            }
        }
    }

    private void init_settings() {
        if (wizardPage > 1) {
            Log.d(TAG, "Skipping duplicate wizard event " + (wizardPage + 1)
                    + "/" + wizardPageTotal);
            return;
        }

        String title = _context.getString(R.string.preferences_text422b);
        String message = _context.getString(R.string.choose_config_method);

        Resources r = _mapView.getResources();
        TileButtonDialog d = new TileButtonDialog(_mapView, _context, true);
        d.addButton(r.getDrawable(R.drawable.customize_actionbar_pref_icon),
                "Action Bar Experience");
        d.addButton(r.getDrawable(R.drawable.my_prefs_settings),
                r.getString(R.string.identity_title));
        d.addButton(r.getDrawable(R.drawable.missionpackage_icon),
                r.getString(R.string.mission_package_name));
        d.addButton(r.getDrawable(R.drawable.ic_menu_plugins),
                r.getString(R.string.manage_plugins));
        d.addButton(r.getDrawable(R.drawable.ic_menu_import_file),
                r.getString(R.string.preferences_text4372));
        d.addButton(r.getDrawable(R.drawable.ic_menu_network),
                r.getString(R.string.quick_connect));
        d.addButton(r.getDrawable(R.drawable.ic_menu_settings),
                r.getString(R.string.more_settings));
        d.show(title, message, true, _context.getString(R.string.done));
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                if (which == TileButtonDialog.WHICH_CANCEL) {
                    init_creds();
                } else if (which == 0) {
                    toolbarPrompt();
                } else if (which == 1) {
                    //callsign dialog
                    CallSignAndDeviceFragment.promptIdentity(_context);
                } else if (which == 2) {
                    //import MP
                    ImportMissionPackageSort.importMissionPackage(_context);
                } else if (which == 3) {
                    //plugin loading
                    Intent mgmtPlugins = new Intent(_context,
                            AppMgmtActivity.class);
                    _context.startActivityForResult(mgmtPlugins,
                            ToolsPreferenceFragment.APP_MGMT_REQUEST_CODE);
                } else if (which == 4) {
                    //generic file import (including .prefs)
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(
                                    "com.atakmap.android.importfiles.IMPORT_FILE"));
                } else if (which == 5) {
                    // launch Quick Connect enrollment dialog
                    CertificateEnrollmentClient.getInstance().enroll(
                            MapView.getMapView().getContext(),
                            null, null, null, null,
                            null, true);
                } else if (which == 6) {
                    //open settings
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent("com.atakmap.app.ADVANCED_SETTINGS"));

                }
            }
        });

        wizardPage = 2;
    }

    /**
     * iterate through all the current connections and check to see if credentials
     * are required, but missing.  Prompt user if this is the case.
     * -->These pages are special - there is no way to determine double clicks determined by
     * the wizardPage variable since there can be multiple credentials to enter. :(
     * There might be a pretty way to to it though.
     */
    private void init_creds() {

        List<Properties> cotStreamProperties = CotService
                .loadCotStreamProperties(_context);
        if (cotStreamProperties == null || cotStreamProperties.size() == 0) {
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(_context);
        int renewCertBeforeExpirationDays = sharedPreferences.getInt(
                "renew_cert_before_expiration_days", -1);
        Log.d(TAG, "renewCertBeforeExpirationDays "
                + renewCertBeforeExpirationDays);

        for (Properties properties : cotStreamProperties) {

            final String desc = properties
                    .getProperty("description", "");
            boolean isEnabled = !properties
                    .getProperty("enabled", "1").equals("0");
            boolean useAuth = !properties
                    .getProperty("useAuth", "0").equals("0");
            final String connectString = properties
                    .getProperty(TAKServer.CONNECT_STRING_KEY, "");
            final String cacheCreds = properties
                    .getProperty("cacheCreds", "");
            final boolean enrollForCertificateWithTrust = !properties
                    .getProperty("enrollForCertificateWithTrust", "0")
                    .equals("0");
            final Long expiration = Long.parseLong(
                    properties.getProperty(TAKServer.EXPIRATION_KEY, "-1"));

            NetConnectString ncs = NetConnectString.fromString(connectString);

            boolean launchEnrollment = false;

            //
            // did this connection use certificate enrollment?
            //
            if (enrollForCertificateWithTrust && ncs != null) {
                byte[] clientCert = AtakCertificateDatabase
                        .getCertificateForServer(
                                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                                ncs.getHost());
                AtakAuthenticationCredentials clientCertCredentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_clientPassword,
                                ncs.getHost());

                if (clientCert == null) {
                    clientCert = AtakCertificateDatabase
                            .getCertificate(
                                    AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE);
                    clientCertCredentials = AtakAuthenticationDatabase
                            .getCredentials(
                                    AtakAuthenticationCredentials.TYPE_clientPassword);
                }

                //
                // do we have an expired cert?
                //
                if (clientCert != null
                        && clientCertCredentials != null
                        && clientCertCredentials.password != null) {

                    // if the cert was expired, re-enroll for a new one
                    AtakCertificateDatabase.CertificateValidity validity = AtakCertificateDatabase
                            .checkValidity(clientCert,
                                    clientCertCredentials.password);
                    if (validity == null) {
                        launchEnrollment = true;
                    } else {
                        Log.d(TAG, validity + ", " +
                                validity.daysRemaining() + " days remaining");

                        if (!validity.isValid() ||
                                (renewCertBeforeExpirationDays != -1 &&
                                        !validity.isValid(
                                                renewCertBeforeExpirationDays))) {
                            launchEnrollment = true;
                        }
                    }

                    //
                    // is the cert missing?
                    //
                } else if (clientCert == null
                        || clientCertCredentials == null
                        || clientCertCredentials.password == null) {
                    launchEnrollment = true;
                }

            }

            if (launchEnrollment) {
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        CertificateEnrollmentClient.getInstance()
                                .enroll(
                                        MapView.getMapView()
                                                .getContext(),
                                        desc, connectString,
                                        cacheCreds, expiration, null, true);
                    }
                });

                // if our connection uses authentication, only check for creds if we didnt just
                // launch a re-enrollment... the re-enrollment process will pull credentials and
                // force the user to enter them if expired
            } else if (useAuth && ncs != null) {
                AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                                ncs.getHost());

                String usernameString = null;
                String passwordString = null;

                if (credentials != null) {
                    usernameString = credentials.username;
                    passwordString = credentials.password;
                }

                if (FileSystemUtils.isEmpty(usernameString) ||
                        FileSystemUtils.isEmpty(passwordString)) {

                    CredentialsDialog.createCredentialDialog(desc,
                            connectString, usernameString,
                            passwordString, cacheCreds, expiration, _context,
                            this); //display if credentials are missing
                }
            }
        }
    }

    private int findConnectionIndex(String connectString) {
        final SharedPreferences prefs = _context
                .getSharedPreferences("cot_streams", Context.MODE_PRIVATE);
        int count = prefs.getInt("count", -1);
        if (count != -1) {
            for (int i = 0; i < count; ++i) {

                //
                // find the stream that we just entered credentials for
                //
                final String nextConnectString = prefs.getString(
                        TAKServer.CONNECT_STRING_KEY
                                + i,
                        "");
                if (nextConnectString != null &&
                        nextConnectString.compareTo(connectString) == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    @Override
    public void onCredentialsEntered(final String connectString,
            String cacheCreds,
            String description,
            String username, String password, final Long expiration) {

        final int connectionIndex = findConnectionIndex(connectString);
        if (connectionIndex == -1) {
            Log.e(TAG,
                    "unable to find connection index for : " + connectString);
            return;
        }

        //
        // if the connection is using certificate enrollment, go ahead and re-enroll
        // with our new credentials
        //
        final SharedPreferences prefs = _context
                .getSharedPreferences("cot_streams", Context.MODE_PRIVATE);
        final boolean enrollForCertificateWithTrust = prefs.getBoolean(
                "enrollForCertificateWithTrust" + connectionIndex, false);

        if (enrollForCertificateWithTrust) {
            final String finalDescription = description;
            final String finalCacheCreds = cacheCreds;
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    CertificateEnrollmentClient.getInstance()
                            .enroll(
                                    MapView.getMapView()
                                            .getContext(),
                                    finalDescription, connectString,
                                    finalCacheCreds, expiration, null, true);
                }
            });
        }

        CotMapComponent
                .getInstance()
                .getCotServiceRemote()
                .setCredentialsForStream(
                        connectString,
                        username,
                        password);
    }

    @Override
    public void onCredentialsCancelled(final String connectString) {
        Log.d(TAG, "cancelled out of CredentialsDialog");

        final int connectionIndex = findConnectionIndex(connectString);
        if (connectionIndex == -1) {
            Log.e(TAG,
                    "unable to find connection index for : " + connectString);
            return;
        }

        NetConnectString ncs = NetConnectString.fromString(connectString);
        final String host = ncs.getHost();

        AlertDialog.Builder builder = new AlertDialog.Builder(_context);
        builder.setTitle("Missing Credentials");
        builder.setIcon(R.drawable.ic_secure);
        builder.setMessage("Disable authentication for " + host + "?");
        builder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CotMapComponent
                                .getInstance()
                                .getCotServiceRemote()
                                .setUseAuthForStream(
                                        connectString,
                                        false);
                    }
                });
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    private void toolbarPrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(_context);
        builder.setTitle("Tool Bar Setting");
        builder.setCancelable(false);
        builder.setMessage(
                "Do you want to use the legacy toolbar (overflow on the right side of the screen) or move to the new toolbar (overflow on the left side of the screen)?\n\nYou can change this at any time in Settings->Display Preferences->Tool Bar Customization");
        builder.setPositiveButton("Right Side (Legacy)",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        _controlPrefs.edit()
                                .putBoolean("nav_orientation_right", true)
                                .apply();
                    }
                });
        builder.setNegativeButton("Left Side (New)",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        _controlPrefs.edit()
                                .putBoolean("nav_orientation_right", false)
                                .apply();
                    }
                });
        builder.show();

    }
}
