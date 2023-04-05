
package com.atakmap.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.atakmap.android.data.ClearContentTask;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.http.rest.HTTPRequestService;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.assets.AssetProtocolHandler;
import com.atakmap.android.maps.assets.ResourceProtocolHandler;
import com.atakmap.android.maps.conversion.UnitChangeReceiver;
import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.android.navigation.NavigationCompat;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.network.AtakAuthenticatedConnectionCallback;
import com.atakmap.android.network.AtakWebProtocolHandlerCallbacks;
import com.atakmap.android.network.TakServerHttpsProtocolHandler;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.toolbar.tools.SpecifyLockItemTool;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.AtakLayerDrawableUtil;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.update.AppVersionUpgrade;
import com.atakmap.android.user.CamLockerReceiver;
import com.atakmap.android.user.EnterLocationTool;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.preferences.LocationSettingsActivity;
import com.atakmap.app.preferences.NetworkSettingsActivity;
import com.atakmap.app.preferences.PreferenceControl;
import com.atakmap.app.system.AbstractSystemComponent;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.comms.app.KeyManagerFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.filesystem.RemovableStorageHelper;
import com.atakmap.coremap.filesystem.SecureDelete;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.io.UriFactory;
import com.atakmap.io.WebProtocolHandler;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.CameraController;
import com.atakmap.map.Globe;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.CertificateManager;
import com.atakmap.spatial.SpatialCalculator;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.zip.IoUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ATAKActivity extends MapActivity implements
        OnSharedPreferenceChangeListener {

    public static final String TAG = "ATAKActivity";

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onCreate(final Bundle savedInstanceState) {

        RemovableStorageHelper.init(this);

        _prefs = new AtakPreferences(this);

        // please note - this should never be set to false unless being called as part of 
        // automated testing.
        callSystemExit = _prefs.get("callSystemExit", true);

        // now blank callSystemExit out so that if the automated tests crash or are stopped by the 
        // user this boolean does not continue to be set.
        _prefs.remove("callSystemExit");

        // important second step record the session id
        _prefs.set("core_sessionid", UUID.randomUUID().toString());

        Log.d(TAG, "session identifier: "
                + _prefs.get("core_sessionid", "not set"));

        if (DEVELOPER_MODE) {

            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        setRequestedOrientation(AtakPreferenceFragment.getOrientation(this));

        SystemComponentLoader.initializeEncryption(this);
        SystemComponentLoader.initializeFlavor(this);
        SystemComponentLoader.initializeRaptorApi(this);

        setWarningShown();

        acceptedPermissions = EulaHelper.showEULA(this);
        if (!acceptedPermissions) {
            Log.d(TAG, "eula has not been accepted...");
            onCreateWaitMode();
            return;
        }

        acceptedPermissions = com.atakmap.app.Permissions
                .checkPermissions(this);
        if (!acceptedPermissions) {
            Log.d(TAG, "permissions have not been accepted...");
            onCreateWaitMode();
            return;
        }

        // Support potential asynchronous load each one of the system components
        final AbstractSystemComponent[] components = SystemComponentLoader
                .getComponents();

        for (AbstractSystemComponent component : components) {
            if (component != null) {
                try {
                    if (!component.load(new AbstractSystemComponent.Callback() {
                        @Override
                        public void setupComplete(int condition) {
                            if (condition == AbstractSystemComponent.Callback.FAILED_STOP) {
                                acceptedPermissions = false; // short circuit the shutdown
                                ATAKActivity.this.finish();
                            } else if (condition == AbstractSystemComponent.Callback.FAILED_CONTINUE) {

                                // if the new provider fails, then just make sure that the system is
                                // using the default provider.
                                if (component.getClass().getCanonicalName()
                                        .equals("com.atakmap.android.EncryptionComponent"))
                                    SystemComponentLoader
                                            .disableEncryptionProvider(
                                                    ATAKActivity.this);
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        onCreate(null);
                                    }
                                });
                            } else if (condition == AbstractSystemComponent.Callback.SUCCESSFUL) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        onCreate(null);
                                    }
                                });
                            }
                        }
                    })) {
                        onCreateWaitMode();
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "severe error", e);
                }
            }
        }

        MigrationShim.onMigration(this);

        _prefs.registerListener(this);

        FileSystemUtils.reset();

        AtakBroadcast.init(this);

        // initialize some of the constants
        ATAKConstants.init(this);

        // keeps the GPS alive and provide TTS services
        BackgroundServices.start(this);

        // Initialize the Notifications
        NotificationUtil.getInstance().initialize(this);

        // initialize all of the databases prior to starting ATAK
        String deviceId = LocationMapComponent
                ._determineBestDeviceUID(ATAKActivity.this);
        AtakCertificateDatabase.setDeviceId(deviceId);
        AtakAuthenticationDatabase.setDeviceId(deviceId);

        AtakCertificateDatabase.initialize(this);
        AtakAuthenticationDatabase.initialize(this);

        AtakCertificateDatabase.migrateCertificatesToSqlcipher(this);

        CertificateManager.getInstance().initialize(this);
        CertificateManager
                .setKeyManagerFactory(KeyManagerFactory.getInstance(this));
        AtakAuthenticatedConnectionCallback authCallback = new AtakAuthenticatedConnectionCallback(
                this);
        AtakAuthenticationHandlerHTTP
                .setCallback(authCallback);
        UriFactory.registerProtocolHandler(new WebProtocolHandler(
                new AtakWebProtocolHandlerCallbacks(authCallback)));
        UriFactory.registerProtocolHandler(new TakServerHttpsProtocolHandler());
        UriFactory.registerProtocolHandler(new AssetProtocolHandler(this));
        UriFactory.registerProtocolHandler(new ResourceProtocolHandler(this));

        LocaleUtil.setLocale(getResources().getConfiguration().locale);

        // Allows for an external "default" preference file to be staged or sent to
        // a device so that it can be mass configured through something like the
        // mission package tool.  When restoring a system, this should come first.
        PreferenceControl.getInstance(this).ingestDefaults();

        // force initialization of all of the stuff (even if the orientation is already correct)
        AtakPreferenceFragment.setOrientation(ATAKActivity.this, true);
        AtakPreferenceFragment.setMainWindowActivity(ATAKActivity.this);

        // Calling super.onCreate with a savedInstanceState has not been sucessfull 
        // and usually leads to an IllegalStateException when trying to inflate the 
        // ATAKFragment.  
        //       java.lang.RuntimeException: Unable to start activity ComponentInfo{}: 
        //           android.view.InflateException: Binary XML file line #15: Error inflating class fragment
        //
        // Forcing the savedInstanceState to be null.
        // Potentially in the future we can do something with implementing a onSaveInstanceState method.
        //
        // disabling 
        //      super.onCreate(savedInstanceState);
        // and call 
        //      super.onCreate(null);
        // to discard any saved state.   

        super.onCreate(null);

        // set up visual splash screen
        _newNavView = NavigationCompat.setContentView(this);
        _newNavView.setVisibility(View.GONE);
        boolean portraitMode = _prefs.get("atakControlForcePortrait", false);
        final View splash = View.inflate(ATAKActivity.this,
                portraitMode ? R.layout.atak_splash_port : R.layout.atak_splash,
                null);

        final LinearLayout v = findViewById(R.id.splash);

        setupSplash(splash);
        v.addView(splash);

        NavigationCompat.startSplashProgress(this);

        final Intent launchIntent = getIntent();

        // begin to build the mapview

        // set the volume control widget to handle AUDIO_MEDIA not AUDIO_RING
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);

        v.postDelayed(new Runnable() {
            @Override
            public void run() {
                final LinearLayout mv = findViewById(
                        R.id.map_view);
                mv.addView(_mapView = new MapView(ATAKActivity.this, null));

                ATAKDatabaseHelper.promptForKey(ATAKActivity.this,
                        new ATAKDatabaseHelper.KeyStatusListener() {
                            @Override
                            public void onKeyStatus(final int ks) {
                                // due to the legacy design - pre ATAK 2.0, initialization is required to run 
                                // on the UI thread.
                                v.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (ks == ATAKDatabaseHelper.KEY_STATUS_OK) {
                                            startInitialization(v,
                                                    launchIntent);
                                            v.removeView(splash); // important so that the bitmap is recycled
                                            v.setVisibility(View.GONE);
                                            mv.setVisibility(View.VISIBLE);
                                        } else {
                                            ATAKActivity.this.finish();
                                        }
                                    }
                                });
                            }
                        });
            }
        }, 1500);

    }

    /**
     * This method is called when ATAK is waiting for external stimulus to occur before it can
     * completely startup.   The callee *MUST* only call this from onCreate and immediately after
     * calling, this method, return must be called.
     */
    private void onCreateWaitMode() {
        setContentView(R.layout.atak_splash);
        setupSplash(findViewById(android.R.id.content));
        super.onCreate(null);
    }

    /**
     * Starts the initialization of all of the bits and pieces.   In versions 
     * before 3.9, this was done in the as part of the postDelayed above.   
     * Since the introduction of the encryption capability, this will either 
     * be fired after the key entered for the first time or when the key is 
     * sucessfully retreived.
     */
    private void startInitialization(final View v, final Intent launchIntent) {
        postInitialization();

        // need to show the actionbar, but since it was hidden during
        // mapView initialization, we will need to do a bit of fixup
        // after showing it.
        if (_newNavView != null) {
            _newNavView.setVisibility(View.VISIBLE);
        } else {

            // fix up the action bar.
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        onResume();
                    } catch (IllegalStateException
                            | IllegalArgumentException ignored) {
                        // explicit call to on resume in this thread may fail if the app is shutting
                        // down due to another error.   should be benign just to catch and finish the
                        // thread properly.
                    }
                }
            }, 5);
        }

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(
                "com.atakmap.app.COMPONENTS_CREATED",
                "Once all of the components are created, launch the intent then remove.");
        AtakBroadcast.getInstance().registerReceiver(
                _pluginLoadedRec = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context,
                            Intent intent) {
                        onNewIntent(launchIntent);
                    }
                }, filter);

        filter = new DocumentedIntentFilter();
        filter.addAction(
                "com.atakmap.app.ExportCrashLogsTask",
                "Triggered when the ExportCrashLogsTask is completed");
        AtakBroadcast.getInstance().registerReceiver(
                _exportCrashLogRec = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context,
                            Intent intent) {
                        setupFileLog();
                        // toggle the state so that we can start a new file
                        if (_prefs.get("lognettraffictofile", false)) {
                            _prefs.set("lognettraffictofile", true);
                        }
                    }
                }, filter);

    }

    private void postInitialization() {

        _actionBarReceiver = new ActionBarReceiver(this);

        // In case a user blows away the mobile directory, then we will
        // force redeployment of the wms sources.   Needs to happen before
        // AppVersionUpgrade which tries to create the imagery/mobile directory.

        if (!IOProviderFactory
                .exists(FileSystemUtils.getItem("imagery/mobile"))) {
            Log.d(TAG,
                    "imagery/mobile directory missing, redeploying the map sources");
            _prefs.set("wms_deployed", false);
        }

        AppVersionUpgrade.onUpgrade(this);

        setupFileLog();

        Log.d(TAG,
                ATAKConstants.getVersionName() + " r"
                        + ATAKConstants.getVersionCode() + " starting up...");
        NotificationUtil.getInstance().initialize(this);

        final ProgressDialog progressDialog = ProgressDialog.show(
                ATAKActivity.this,
                getString(R.string.app_name)
                        + getString(R.string.loading),
                getString(R.string.preferences_text417), true);

        // this specifically is the GLMapComponent and the GLWidgetsMapComponent
        loadRequiredAssets();

        ToolbarBroadcastReceiver.getInstance().initialize(_mapView);

        PowerManager pm = (PowerManager) getSystemService(
                Context.POWER_SERVICE);
        _wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "com.atakmap.app:atakWakeLock");
        _wakeLock.acquire();

        DocumentedIntentFilter filter = new DocumentedIntentFilter(
                Intent.ACTION_SHUTDOWN,
                "Listen for the system wide shutdown intent so that finish can be called on ATAK otherwise ATAK might not be able to clean up appropriate items");
        shutdownReceiver = new ShutDownReceiver();
        AtakBroadcast.getInstance().registerSystemReceiver(shutdownReceiver,
                filter);

        new Thread(new Runnable() {
            @Override
            public void run() {

                while (_mapView != null && _mapView.getWidth() <= 0
                        && _mapView.getHeight() <= 0) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }

                //     ATAK-7583 Another MapView NPE
                if (_mapView == null) {
                    finish();
                    return;
                }

                _mapView.post(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            long s = SystemClock.elapsedRealtime();
                            buildATAK();
                            long e = SystemClock.elapsedRealtime();

                            Log.i(TAG, "build app in " + (e - s) + "ms");
                            setupWizard = new DeviceSetupWizard(
                                    ATAKActivity.this, _mapView,
                                    _prefs.getSharedPrefs());
                            setupWizard.init(false);

                        } finally {
                            progressDialog.dismiss();
                            try {
                                com.atakmap.comms.CotServiceRemote
                                        .fireConnect();
                            } catch (Exception e) {
                                Log.e(TAG, "error with CotService", e);
                            }
                        }
                    }
                });
            }
        }, "WidthHeightThread").start();

        DocumentedIntentFilter setupFilter = new DocumentedIntentFilter();
        setupFilter.addAction(
                "com.atakmap.app.DEVICE_SETUP",
                "Initiate device setup wizard");
        AtakBroadcast.getInstance().registerReceiver(
                _setupWizardRec = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context,
                            Intent intent) {
                        if (setupWizard != null) {
                            setupWizard.init(true);
                        }
                    }
                }, setupFilter);

        final int build = Build.VERSION.SDK_INT;
        final String model = android.os.Build.MODEL;
        final String product = android.os.Build.PRODUCT;
        final String fingerprint = android.os.Build.FINGERPRINT;
        Log.d(TAG, "model: " + model + " build: " + build + " product: "
                + product + " fingerprint: " + fingerprint);
        if ((build >= 22) && model.equals("SAMSUNG-SM-G900A")) {
            HintDialogHelper
                    .showHint(
                            _mapView.getContext(),
                            getString(R.string.preferences_text420),
                            getString(R.string.preferences_text421),
                            "s5.lockscreen.issue");

        }

        String sSU = getString(R.string.FalconSA_su);
        File su = new File(sSU);

        if (product.startsWith("cm_") || IOProviderFactory.exists(su)) {
            HintDialogHelper
                    .showHint(
                            _mapView.getContext(),
                            getString(R.string.preferences_text420a),
                            getString(R.string.preferences_text421a),
                            "mediascanner.issue",
                            new HintDialogHelper.HintActions() {
                                @Override
                                public void preHint() {
                                }

                                @Override
                                public void postHint() {
                                    _prefs.set("disable_mediaserver", true);
                                }
                            });

        }
        btFrag = new com.atakmap.android.bluetooth.BluetoothFragment();
        btFrag.onStart();
    }

    /**
     * Bandaid attempt to stop the compatibility warning from showing on Android PI devices.
     * Based on the source code, the field mHiddenApiWarningShown should be set to true
     * indicating that the warning already has been shown.
     * The real issue still exists - 
     *     ATAK-10252 detected problems with api compatibility, g.co/appcompat
     */
    @SuppressLint("PrivateApi")
    private void setWarningShown() {
        try {
            final Class<?> clazz = Class
                    .forName("android.content.pm.PackageParser$Package");
            final Constructor<?> c = clazz.getDeclaredConstructor(String.class);
            c.setAccessible(true);
        } catch (Exception e) {
            Log.e(TAG, "unable to find the constructor", e);
        }

    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences prefs, final String key) {
        if (key == null)
            return;

        if (key.equals("atakDisableSoftkeyIllumination")) {
            AtakPreferenceFragment.setSoftKeyIllumination(this);
        } else if (key.equals("loggingfile_error_only")) {
            fileLogger.setWriteOnlyErrors(_prefs.get(
                    "loggingfile_error_only", false));
        } else if (key.equals("loggingfile")) {
            setupFileLog();
        } else if (key.compareTo("atakControlForcePortrait") == 0 ||
                key.compareTo("atakControlReverseOrientation") == 0) {
            toggleOrientation();
        } else if (key.compareTo("largeActionBar") == 0) {
            invalidateOptionsMenu();
        } else if (key.compareTo("disable_mediaserver") == 0) {
            final String state = prefs.getBoolean(key, false) ? "disable"
                    : "enable";
            Log.d(TAG, "attempt to " + state + " mediaserver");
            Runtime rt = Runtime.getRuntime();
            try {
                Process p = rt
                        .exec(new String[] {
                                "su",
                                "-c",
                                "/system/bin/pm "
                                        + state
                                        + " com.android.providers.media/com.android.providers.media.MediaScannerReceiver"
                });
                p.waitFor();
            } catch (Exception ioe) {
                Log.d(TAG, "error executing media server disable", ioe);
            }
            try {
                Log.d(TAG, "issuing sm set-force-adoptable true");
                java.lang.Process query = Runtime.getRuntime().exec(
                        new String[] {
                                "/system/bin/sm", "has-adoptable"
                        });
                query.waitFor();
                java.io.InputStream is = query.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is));
                String s = reader.readLine();
                Log.d(TAG, "current state of the adoptable: " + s);
                // the locale used for system level commands is always english/us
                if (s != null
                        && s.toLowerCase(LocaleUtil.US).contains("false")) {
                    Log.d(TAG, "issuing force-adoptable command");
                    java.lang.Process proc = Runtime.getRuntime().exec(
                            new String[] {
                                    "su", "-c",
                                    "/system/bin/sm set-force-adoptable true"
                            });
                } else {
                    Log.d(TAG, "no need to issue force-adoptable command");
                }

            } catch (Exception e) {
                Log.d(TAG, "error setting usb drives to be adoptable");
            }
        } else {
            _updateDisplayPrefs();
        }
    }

    /**
     * Marking code that could potentially be spawned off onto another thread
     * provided all of the Handlers are happy
     **/
    private void buildATAK() {
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(
                "com.atakmap.app.QUITAPP",
                "Intent to start the quiting process, if the boolean extra FORCE_QUIT is set, the application will not prompt the user before quitting");
        AtakBroadcast.getInstance().registerReceiver(_quitReceiver, filter);

        filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.app.ADVANCED_SETTINGS");
        filter.addAction("com.atakmap.app.NETWORK_SETTINGS");
        filter.addAction("com.atakmap.app.DEVICE_SETTINGS");
        AtakBroadcast.getInstance().registerReceiver(_advancedSettingsReceiver,
                filter);

        filter = new DocumentedIntentFilter(
                DropDownManager.BACK_PRESS_NOT_HANDLED);
        AtakBroadcast.getInstance().registerReceiver(backReceiver, filter);

        // create Geospatial directories on internal/external SD cards so
        // FileObservers can be initiated as the components startup
        FileSystemUtils.ensureDataDirectory("overlays", true);

        //clean up old tmp files
        SpatialCalculator.initTmpDir(getApplicationContext());
        FileSystemUtils.cleanup();

        try {
            loadAssets();
        } catch (IOException | SAXException e) {
            Log.e(TAG, "error: ", e);
        }

        ClearContentTask.setClearContent(_prefs.getSharedPrefs(), false);

        AtakPreferenceFragment.setSoftKeyIllumination(this);

        // Turns the energy to the lowest setting allowed by the Android OS. Any
        // further reduction
        // in brightness according to what is online will not result in energy
        // savings, just lower
        // brightness - apps achieve this using a tranparent app that is always on
        // front and passes
        // everything off to the app below it.
        // setLowScreenBrightness();

        String forceBrightness = _prefs.get("atakForcedBrightness", "-1");
        if (!forceBrightness.equals("-1")) {
            float brightnessValue = (float) Integer.parseInt(forceBrightness)
                    / 100.0f;
            Log.v(TAG, "forcing brightness level to: " + brightnessValue);
            toggleAutoBrightness(false);
            setBrightness(brightnessValue);
        }

        _focusOnLocationMarker();

        FileSystemUtils.ensureDataDirectory(
                FileSystemUtils.TOOL_DATA_DIRECTORY, false);
        FileSystemUtils.ensureDataDirectory(FileSystemUtils.SUPPORT_DIRECTORY,
                false);
        FileSystemUtils.ensureDataDirectory(FileSystemUtils.CONFIG_DIRECTORY,
                false);
        FileSystemUtils.ensureDataDirectory(FileSystemUtils.EXPORT_DIRECTORY,
                false);
        FileSystemUtils.ensureDataDirectory(FileSystemUtils.DTED_DIRECTORY,
                false);
        FileSystemUtils.ensureDataDirectory("grg", false);
        FileSystemUtils.ensureDataDirectory("imagery", false);
        FileSystemUtils.ensureDataDirectory("imagery/mobile", false);
        FileSystemUtils.ensureDataDirectory("imagery/mobile/mapsources", false);
        FileSystemUtils.ensureDataDirectory("imagecache", false);
        ConfigOptions.setOption("imagery.offline-cache-dir",
                FileSystemUtils.getItem("imagecache").getAbsolutePath());
        FileSystemUtils.ensureDataDirectory("Databases", false);
        FileSystemUtils.ensureDataDirectory("attachments", false);
        FileSystemUtils.ensureDataDirectory(PreferenceControl.DIRNAME, false);
        FileSystemUtils.ensureDataDirectory("fonts", false);
        ConfigOptions.setOption("default-font-atlas-path",
                FileSystemUtils.getItem("fonts").getAbsolutePath());
        ConfigOptions.setOption("default-font-name", "Arial");

        // Copy README file into the root directory
        FileSystemUtils.copyFromAssetsToStorageFile(getApplicationContext(),
                "support/README.txt",
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar
                        + "README.txt",
                true);
        FileSystemUtils.copyFromAssetsToStorageDir(getApplicationContext(),
                "support/license",
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar
                        + "license",
                true);

        File supportFile = new File(FileSystemUtils.getRoot(),
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar
                        + "support.inf");
        if (!supportFile.exists()) {
            File pFile = supportFile.getParentFile();
            if (pFile != null)
                pFile.mkdirs();
            try (OutputStream supportStream = new FileOutputStream(
                    supportFile)) {
                FileSystemUtils.copyFromAssets(
                        getApplicationContext(),
                        "support/support.inf",
                        supportStream);
            } catch (IOException e) {
                Log.d(TAG, "could not copy support.inf to "
                        + supportFile.getPath(), e);
            }
        }

        CoordinatedTime.setLoggerDirectory(FileSystemUtils
                .getItem(FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar
                        + "cottimedebug"));

        extractPrivateResource("wmm_cof", "world-magnetic-model-file", false);

        extractFontsFromAssembly();

        _unitChangeReceiver = new UnitChangeReceiver(_mapView);
        Log.d(TAG, "Created UnitChangeReceiver");
        DocumentedIntentFilter unitFilter = new DocumentedIntentFilter();
        unitFilter.addAction(UnitChangeReceiver.ANGL_ADJUST);
        unitFilter.addAction(UnitChangeReceiver.DIST_ADJUST);
        AtakBroadcast.getInstance().registerReceiver(_unitChangeReceiver,
                unitFilter);

        _handleExternalStorageState();
        _listenToExternalStorageState();

        // register ATAK specific Receivers here
        // TODO: move me
        registerLinkLineReceiver();
        registerZoomReceiver();

        _updateDisplayPrefs();

        // Is this the best way to do this? We need to verify there isn't a prettier
        // way to accomplish this behavior. Setting up all this garbage at the end
        // of the Activity constructor hurts my soul. AS.
        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(CamLockerReceiver.LOCK_CAM);
        intentFilter.addAction(CamLockerReceiver.UNLOCK_CAM);
        // Disable lock if another marker is selected
        intentFilter.addAction("com.atakmap.android.maps.SHOW_MENU");
        intentFilter.addAction(CamLockerReceiver.TOGGLE_LOCK);
        intentFilter.addAction(CamLockerReceiver.TOGGLE_LOCK_LONG_CLICK);
        AtakBroadcast.getInstance().registerReceiver(lockBroadcastReceiver,
                intentFilter);
        _mapView.getMapController()
                .addOnPanRequestedListener(lockBroadcastReceiver);

        DocumentedIntentFilter orientationFilter = new DocumentedIntentFilter();
        orientationFilter
                .addAction("com.atakmap.android.maps.ORIENTATION_TOGGLE");
        AtakBroadcast.getInstance().registerReceiver(orientationReceiver,
                orientationFilter);

        final File basemapDir = FileSystemUtils.getItem("basemap");
        if (IOProviderFactory.exists(basemapDir)) {
            File[] children = IOProviderFactory.listFiles(basemapDir);
            if (children != null) {
                for (File aChildren : children) {
                    Set<DatasetDescriptor> descs = DatasetDescriptorFactory2
                            .create(aChildren, null, null, null);
                    if (descs != null) {
                        GLMapRenderable basemap = GLMapLayerFactory
                                .create3(
                                        _mapView.getGLSurface().getGLMapView(),
                                        descs.iterator().next());
                        if (basemap != null) {
                            _mapView.getGLSurface().getGLMapView()
                                    .setBaseMap(basemap);
                            break;
                        }
                    }
                }
            }
        }

        _mapView.setContinuousScrollEnabled(
                _prefs.get("atakContinuousScrollEnabled", true));

        try {
            DozeManagement.checkDoze(_mapView.getContext());
        } catch (Exception e) {
            Log.d(TAG, "doze check failed");
        }

        // use the generalized constructor for the FauxNavBar
        fnb = new FauxNavBar(this);

        setupActionBar(true);
    }

    private void extractPrivateResource(String resourceName, String option,
            boolean useIOAbstraction) {
        InputStream stream = null;

        try {
            long s = SystemClock.uptimeMillis();
            // load from assets
            Resources r = this.getResources();
            final int id = r.getIdentifier(resourceName, "raw",
                    this.getPackageName());
            if (id != 0) {
                stream = r.openRawResource(id);
            }

            if (stream == null)
                throw new ExceptionInInitializerError();

            File cofFile = new File(this.getFilesDir(), resourceName);
            /*Check if the flag to utilize IOAbstraction is enabled
            * Loading this with Encryption causes invalid returns
            */

            if (useIOAbstraction) {
                FileSystemUtils.copy(stream,
                        IOProviderFactory.getOutputStream(cofFile));
            } else {
                FileSystemUtils.copy(stream,
                        new FileOutputStream(cofFile));
            }
            long e = SystemClock.uptimeMillis();

            Log.v(TAG, "Extracted resource [" + resourceName + "] in " + (e - s)
                    + "ms");

            if (option != null)
                ConfigOptions.setOption(option, cofFile.getAbsolutePath());
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        } finally {
            IoUtils.close(stream);
        }
    }

    private void extractFontsFromAssembly() {
        try {
            String fontResourcesPath = "fonts/font_resources.json";
            File fontResourcesFile = FileSystemUtils.getItem(fontResourcesPath);
            FileSystemUtils.copyFromAssetsToStorageFile(getApplicationContext(),
                    fontResourcesPath, fontResourcesPath, true);
            JSONObject jsonObject = new JSONObject(
                    new String(FileSystemUtils.read(fontResourcesFile),
                            FileSystemUtils.UTF8_CHARSET));
            writeFontAssetsToStorage(jsonObject);
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract fonts from assembly", e);
        }
    }

    private void writeFontAssetsToStorage(JSONObject jsonRootObject)
            throws JSONException {
        JSONArray jsonAtlases = jsonRootObject.getJSONArray("atlases");
        for (int i = 0; i < jsonAtlases.length(); i++) {
            JSONObject jsonAtlas = jsonAtlases.getJSONObject(i);
            String jsonFilename = "fonts/"
                    + jsonAtlas.getString("jsonFilename");
            String binFilename = "fonts/" + jsonAtlas.getString("binFilename");
            FileSystemUtils.copyFromAssetsToStorageFile(getApplicationContext(),
                    binFilename, binFilename, true);
            FileSystemUtils.copyFromAssetsToStorageFile(getApplicationContext(),
                    jsonFilename, jsonFilename, true);
        }
    }

    protected void toggleOrientation() {
        if (_mapView != null)
            setRequestedOrientation(
                    AtakPreferenceFragment.getOrientation(_mapView
                            .getContext()));
    }

    /**
     * Given the current architecture, this is the only way I can see to listen
     * for a track up, north up change
     */
    private BroadcastReceiver orientationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if ("com.atakmap.android.maps.ORIENTATION_TOGGLE".equals(action)) {
                //if it was not set, switch from default landscape to portrait
                boolean portrait = !_prefs.contains("atakControlForcePortrait")
                        || !_prefs.get("atakControlForcePortrait", false);

                //update so pref listener will process the change
                Log.d(TAG, "ORIENTATION_TOGGLE: " + portrait);
                _prefs.set("atakControlForcePortrait", portrait);
                orientationChangeRequestPending = true;
            }
        }
    };

    private LockBroadcastReceiver lockBroadcastReceiver = new LockBroadcastReceiver();

    private class LockBroadcastReceiver extends BroadcastReceiver
            implements AtakMapController.OnPanRequestedListener {

        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();
            if (action == null)
                return;

            /*
             * _lockButton is found and set inside of onCreateOptionsMenu
             */
            // Light up the lock if it's for the self icon
            switch (action) {
                case CamLockerReceiver.LOCK_CAM:
                    if (!intent.getBooleanExtra("toolbarGenerated", false)) {
                        String uid = intent.getStringExtra("uid");
                        if (uid != null && _lockActionMenu != null) {
                            _lockActive = !_lockActive;
                            if (_lockActive) {
                                setLockSelected(true);
                                _currentUID = uid;
                            } else {
                                setLockSelected(false);
                                _currentUID = null;
                            }
                        }
                    }
                    break;
                case CamLockerReceiver.UNLOCK_CAM:
                    unlock();
                    break;
                case "com.atakmap.android.maps.SHOW_MENU":
                    // Turn it off if a marker was touched
                    String uid = intent.getStringExtra("uid");
                    if (uid != null && !uid.equals(_currentUID)
                            && _lockActionMenu != null) {
                        _lockActive = false;
                        setLockSelected(false);
                        _currentUID = null;
                    }
                    break;
                case CamLockerReceiver.TOGGLE_LOCK:
                    _toggleLock(intent.getBooleanExtra("self", true));
                    break;
                case CamLockerReceiver.TOGGLE_LOCK_LONG_CLICK:
                    _toggleLock(false);
                    break;
            }
        }

        private void unlock() {
            if (_lockActionMenu != null) {
                _lockActive = false;
                setLockSelected(false);
                _currentUID = null;
            } else {
                Log.w(TAG, "UNLOCK_CAM lock button not set");
            }
        }

        @Override
        public void onPanRequested() {
            unlock();
        }
    }

    /**
     * Update selection state, and redraw the action bar
     */
    protected void setLockSelected(boolean bSelected) {
        if (_lockActionMenu != null) {
            if (_lockActionMenu.isSelected() != bSelected) {
                _lockActionMenu.setSelected(bSelected);
                invalidateOptionsMenu();
            }
        }
    }

    /**
     * For setting the brightness to work, one must disable the Auto Brightness
     * mode.
     */
    private void toggleAutoBrightness(boolean value) {
        try {
            String mode = Settings.System.SCREEN_BRIGHTNESS_MODE;
            int modevalue = (value)
                    ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
            Settings.System.putInt(getContentResolver(), mode, modevalue);
        } catch (Exception ignored) {
        }
    }

    /**
     * Sets the brightness value of the window using a value between [0..1] if -1
     * is used, the default policy is restored for the window.
     */
    private void setBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (brightness < 0) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else if (brightness < 0.01f) {
            // shuts off the screen, need to fiddle it up by a small amount.
            lp.screenBrightness = 0.01f;
        } else if (brightness > 1) {
            lp.screenBrightness = 1;
        } else {
            lp.screenBrightness = brightness;
        }
        getWindow().setAttributes(lp);

    }

    private OnSharedPreferenceChangeListener _prefsChangedListener;

    private void _updateDisplayPrefs() {
        if (_prefs != null) {
            boolean showLabels = _prefs.get(
                    "atakControlShowLabels", true);
            boolean shortenLabels = _prefs.get(
                    "atakControlShortenLabels", false);
            boolean maximumTexUnits = _prefs.get(
                    "atakControlMaximumTextureUnits", true);
            boolean textureCopy = _prefs.get(
                    "atakControlEnableTextureCopy", true);

            final String model = android.os.Build.MODEL;
            if (model.equals("SM-T230NU")) {
                Log.d(TAG,
                        "cowardly refusing to enable TextureCopies an a known broken platform");
                textureCopy = false;
            }

            if (model.equals("JT-B1")) {
                Log.d(TAG,
                        "cowardly refusing to enable MaximumTextureUnits an a known broken platform");
                maximumTexUnits = false;
            }

            int displayFlags = 0;
            if (!showLabels) {
                displayFlags |= MapView.DISPLAY_NO_LABELS;
            }

            if (!shortenLabels) {
                displayFlags |= MapView.DISPLAY_IGNORE_SHORT_LABELS;
            }
            if (!maximumTexUnits) {
                displayFlags |= MapView.DISPLAY_LIMIT_TEXTURE_UNITS;
            }
            if (!textureCopy) {
                displayFlags |= MapView.DISABLE_TEXTURE_FBO;
            }
            if (_mapView != null)
                _mapView.setDisplayFlags(displayFlags);

            try {
                // developer options defined as 
                //     "overlays-relative-scale-1.0"
                //     "overlays-relative-scale-1.25"
                //     "overlays-relative-scale-1.50"
                //     "overlays-relative-scale-1.75"
                //     "overlays-relative-scale-2.00"
                final String option = _prefs.get(
                        "relativeOverlaysScalingRadioList", "1.0");
                float relativeScale = (float) DeveloperOptions.getDoubleOption(
                        "overlays-relative-scale-" + option,
                        Float.parseFloat(option));
                if (_mapView != null)
                    _mapView.setRelativeScaling(relativeScale);
            } catch (Exception e) {
                Log.d(TAG,
                        "error occurred setting the relative scaling from the developer options.",
                        e);
            }
        }
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        Log.d(TAG, "configuration changed manual orientation changed: "
                + orientationChangeRequestPending);
        Log.d(TAG, "new configuration: " + config);

        super.onConfigurationChanged(config);

        if (_mapView == null)
            return;

        PowerManager powerManager = (PowerManager) getSystemService(
                POWER_SERVICE);
        if (powerManager != null && !powerManager.isInteractive())
            return;

        if (orientationChangeRequestPending) {
            orientationChangeRequestPending = false;

            DropDownManager.getInstance().closeAllDropDowns();

            ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
        }

        invalidateOptionsMenu();

        _mapView.post(new Runnable() {
            @Override
            public void run() {
                final ViewGroup viewGroup = findViewById(
                        android.R.id.content);
                revalidate(viewGroup);

                ToolbarBroadcastReceiver.getInstance().requestLayout();
            }
        });

        setupActionBar(false);

    }

    private void revalidate(View v) {
        if (v instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) v;
            for (int i = 0; i < viewGroup.getChildCount(); ++i) {
                revalidate(viewGroup.getChildAt(i));

            }
        }
        //Log.d(TAG, "revalidating component: " + v);
        v.requestLayout();
    }

    /**
     * @deprecated No longer used
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public void setupActionBar(boolean ifNotSet) {
    }

    /**
     * @deprecated No longer used
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public int getActionBarHeight() {
        return 0;
    }

    /**
     * @deprecated No longer used
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public void fireActionBarListeners() {
    }

    BroadcastReceiver backReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (_prefs.get("atakControlQuitOnBack", false)) {
                startQuitProcess();
            } else {
                _myLocationNoMenu();
            }
        }
    };

    @Override
    public void onBackPressed() {
        if (isTaskRoot()) {
            // When a dropdown is open the back button shouldn't trigger anything other than a close dropdown.             
            Intent backIntent = new Intent("com.android.arrowmaker.BACK_PRESS");
            AtakBroadcast.getInstance().sendBroadcast(backIntent);

        }
    }

    private void registerLinkLineReceiver() {
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(LinkLineReceiver.ACTION);
        MapGroup linkGroup = new DefaultMapGroup("Link Lines");
        String iconUri = "android.resource://"
                + _mapView.getContext().getPackageName()
                + "/" + R.drawable.pairing_line_white;
        _mapView.getMapOverlayManager().addShapesOverlay(
                new DefaultMapGroupOverlay(_mapView, linkGroup, iconUri));
        AtakBroadcast.getInstance().registerReceiver(
                _linkLineReceiver = new LinkLineReceiver(_mapView,
                        _mapView.getRootGroup(),
                        linkGroup),
                filter);
    }

    private void registerZoomReceiver() {
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.map.ZOOM");
        filter.addAction("com.atakmap.android.map.ZOOM_IN");
        filter.addAction("com.atakmap.android.map.ZOOM_OUT");
        AtakBroadcast.getInstance().registerReceiver(
                _zoomReceiver = new ZoomReceiver(_mapView), filter);

    }

    /**
         * Note: Method depends on _mapView and _controlPrefs being initialized
         * before it is called.
     */
    private void _focusOnLocationMarker() {
        // legacy zoom ~4000
        final double ZOOM_LEVEL = 1.0d / 98609.5d;

        AtakMapController ctrl = _mapView.getMapController();
        MapData mapData = _mapView.getMapData();
        if (mapData.containsKey("fineLocation")) {
            GeoPoint location = mapData
                    .getParcelable("fineLocation");
            if (location != null) {
                Log.d(TAG, "using location lat: " + location.getLatitude()
                        + " lon: "
                        + location.getLongitude() + " scale: " + ZOOM_LEVEL);
                ctrl.panZoomTo(location, ZOOM_LEVEL, true);
            }
        } else {
            double lat = _prefs.get("screenViewLat", 0d);
            double lon = _prefs.get("screenViewLon", 0d);
            double alt = _prefs.get("screenViewAlt", 0d);
            double scale = _prefs.get("screenViewMapScale", 0d);
            double rotation = _prefs.get("screenViewMapRotation", 0d);
            double tilt = _prefs.get("screenViewMapTilt", 0d);
            boolean enabled3D = DeveloperOptions.getIntOption(
                    "disable-3D-mode", 0) == 0;

            // XXX - this will need to be revisited with subterranean support

            // if there was no elevation data during the last run and has been
            // subsequently loaded, adjust the altitude to prevent the camera
            // from starting underneath the terrain
            final double localTerrain = ElevationManager.getElevation(lat, lon,
                    null);
            if (Double.isNaN(alt) || alt < localTerrain) {
                if (!Double.isNaN(localTerrain) && localTerrain > 0d)
                    alt = localTerrain;
                double gsd = Globe.getMapResolution(_mapView.getDisplayDpi(),
                        scale);
                double range = MapSceneModel.range(gsd, 45d,
                        _mapView.getHeight());
                scale = Globe.getMapScale(
                        _mapView.getDisplayDpi(),
                        MapSceneModel.gsd(range + alt, 45d,
                                _mapView.getHeight()));
            }
            Log.d(TAG, "using saved screen location lat: " + lat + " lon: "
                    + lon
                    + " scale: " + scale);
            if (lat != 0 || lon != 0 || scale != 0) {
                ctrl.panZoomTo(new GeoPoint(lat, lon, alt), scale, true);
                if (rotation != 0)
                    CameraController.Programmatic.rotateTo(
                            _mapView.getRenderer3(),
                            rotation, true);
                if (tilt != 0 && enabled3D)
                    CameraController.Programmatic.tiltTo(
                            _mapView.getRenderer3(),
                            tilt, true);
            } else {
                Log.d(TAG, "no location found, go to (0,0)");
                CameraController.Programmatic.panTo(_mapView.getRenderer3(),
                        GeoPoint.ZERO_POINT, true);
            }
        }

    }

    @Override
    public void onDestroy() {

        if (!acceptedPermissions) {
            super.onDestroy();
            monitorThread.setDaemon(true); // just in cases ATAK exits early
            monitorThread.start();
            return;
        }

        IOProviderFactory.notifyOnDestroy();

        try {
            BackgroundServices.stopService();
        } catch (Exception e) {
            Log.d(TAG, "error occurred: " + e);
        }

        cancelForeground();
        Log.d(TAG, "shutting down....");

        try {
            super.onDestroy();
        } catch (Exception e) {
            Log.d(TAG, "error occurred: " + e);
        }

        try {
            // stop HTTP (client) request service
            Intent svcIntent = new Intent(HTTPRequestService.class.getName());
            svcIntent.setPackage(this.getPackageName());
            if (stopService(svcIntent)) {
                Log.d(TAG, "Stopped: HTTPRequestService");
            } else {
                Log.w(TAG,
                        "Failed to stop: HTTPRequestService");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to stop: HTTPRequestService", e);
        }

        try {
            com.atakmap.android.layers.ScanLayersService.getInstance()
                    .destroy();
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop: ScanlayerServices", e);
        }

        try {
            // Record the screen positional information prior to doing anything
            // else.
            final MapSceneModel sm = _mapView.getRenderer3().getMapSceneModel(
                    false, MapRenderer2.DisplayOrigin.UpperLeft);
            GeoPoint focus = sm.mapProjection.inverse(sm.camera.target, null);
            double lat = (focus != null) ? focus.getLatitude()
                    : _mapView.getLatitude();
            double lon = (focus != null) ? focus.getLongitude()
                    : _mapView.getLongitude();
            double alt = (focus != null && focus.isAltitudeValid())
                    ? focus.getAltitude()
                    : 0d;
            double scale = _mapView.getMapScale();
            double rotation = _mapView.getMapRotation();
            double tilt = _mapView.getMapTilt();

            if (!ClearContentTask.isClearContent(_prefs.getSharedPrefs())) {
                _prefs.set("screenViewLat", lat);
                _prefs.set("screenViewLon", lon);
                _prefs.set("screenViewAlt", alt);
                _prefs.set("screenViewMapScale", scale);
                _prefs.set("screenViewMapRotation", rotation);
                _prefs.set("screenViewMapTilt", tilt);
                Log.d(TAG, "saving screen location lat: " + lat + " lon: "
                        + lon
                        + " scale: " + scale);
            }
        } catch (Exception e) {
            Log.d(TAG,
                    "error occurred attempting to save the screen positional information: "
                            + e);
            Log.e(TAG, "error: ", e);
        }

        try {
            if (_pluginLoadedRec != null) {
                AtakBroadcast.getInstance().unregisterReceiver(
                        _pluginLoadedRec);
            }
        } catch (Exception e) {
            Log.e(TAG, "error unregistering pluginLoadedRec: ", e);
        }

        try {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _setupWizardRec);
        } catch (Exception e) {
            Log.e(TAG, "error unregistering setupWizardRec: ", e);
        }

        try {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _exportCrashLogRec);
        } catch (Exception e) {
            Log.e(TAG, "error unregistering exportCrashLogRec: ", e);
        }

        try {
            AtakBroadcast.getInstance().unregisterSystemReceiver(
                    shutdownReceiver);
            shutdownReceiver = null;
        } catch (Exception e) {
            Log.e(TAG, "error unregistering shutdownReceiver: ", e);
        }

        try {
            btFrag.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "error stopping btFrag", e);
        }

        try {
            final File sendtoLocation = FileSystemUtils.getItem("tools/sendto");
            FileSystemUtils.deleteDirectory(sendtoLocation, false);
        } catch (Exception e) {
            Log.e(TAG, "error removing sendto directory", e);
        }

        try {

            AtakBroadcast.getInstance().unregisterReceiver(backReceiver);
            backReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(_unitChangeReceiver);
            _unitChangeReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(_quitReceiver);
            _quitReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(
                    _advancedSettingsReceiver);
            _advancedSettingsReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(
                    lockBroadcastReceiver);
            _mapView.getMapController()
                    .removeOnPanRequestedListener(lockBroadcastReceiver);
            lockBroadcastReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(orientationReceiver);
            orientationReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(_linkLineReceiver);
            _linkLineReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(_zoomReceiver);
            _zoomReceiver = null;
            AtakBroadcast.getInstance().unregisterReceiver(
                    _mediaMountedUnmountedReceiver);
            _mediaMountedUnmountedReceiver = null;
            if (_actionBarReceiver != null) {
                AtakBroadcast.getInstance().unregisterReceiver(
                        _actionBarReceiver);
                _actionBarReceiver.dispose();
                _actionBarReceiver = null;
            }

            _lockActionMenu = null;

            _prefs.unregisterListener(_prefsChangedListener);
            _prefsChangedListener = null;
            _prefs = null;

            PreferenceControl.dispose();

            AtakLayerDrawableUtil.getInstance(_mapView.getContext()).dispose();

            KeyManagerFactory.getInstance(
                    _mapView.getContext()).dispose();

            AtakPreferenceFragment.removeMainWindowActivity();

            _mapView.destroy();

            _mapView = null;

            // just make sure to release the wakelock if it is held.
            if (_wakeLock.isHeld())
                _wakeLock.release();

            NotificationUtil.getInstance().cancelAll();

            AtakCertificateDatabase.dispose();
            AtakAuthenticationDatabase.dispose();
            AtakAuthenticationHandlerHTTP.setCallback(null);
            DozeManagement.dispose();
            AtakBroadcast.getInstance().dispose();
            NotificationUtil.getInstance().dispose();

            SystemComponentLoader
                    .notify(AbstractSystemComponent.SystemState.DESTROY);

        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        } finally {

            // DO NOT ADD ANYTHING IN THIS //

            // even though this is being called, there are still threads according
            // to the ddms monitor that are still running.
            // SHB: This threaded call to exit is not an accident and allows for
            // me to continue to track down inproper shutdown throughout all of ATAK
            // while retaining the behavior that ATAK does actually die as it has
            // done
            // previously.
            Log.v(TAG,
                    "finishing onDestroy, preparing to call exit in 1/4 second.");

            // move the icon change till the end of the the shutdown in order to prevent forced crash
            // on some Pixel devices and the Samsung S20.
            ensureCorrectIcon();

            monitorThread.setDaemon(true); // just in cases ATAK exits early
            monitorThread.start();
        }
    }

    private final Thread monitorThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
            Log.v(TAG, "shutting down now...");
            if (!callSystemExit)
                Log.v(TAG,
                        " ---- set to ignore final system shutdown for automated testing ----");

            if (fileLogger != null)
                Log.unregisterLogListener(fileLogger);

            if (callSystemExit)
                System.exit(0);

        }
    }, TAG + "-Monitor");

    @Override
    public void onStop() {
        super.onStop();

        if (!acceptedPermissions)
            return;

    }

    @Override
    public void onTrimMemory(final int type) {
        Log.d(TAG, "a call has been recorded onTrimMemory with type: " + type);

        if (MetricsApi.shouldRecordMetric()) {
            Bundle b = new Bundle();
            b.putInt("type", type);
            MetricsApi.record("onTrimMemory", b);
        }
    }

    @Override
    public void onLowMemory() {
        Log.d(TAG, "a call has been recorded onLowMemory");

        if (MetricsApi.shouldRecordMetric()) {
            Bundle b = new Bundle();
            com.atakmap.android.metrics.MetricsApi.record("onLowMemory", b);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.d(TAG, "new intent received");
        super.onNewIntent(intent);

        if ((intent != null) && (intent.getExtras() != null)
                && intent.getExtras().containsKey("internalIntent")) {
            Intent s = intent.getExtras().getParcelable(
                    "internalIntent");
            intent.getExtras().remove("internalIntent");
            if (s != null) {
                if (s.getAction() != null
                        && s.getAction().equals(BACKGROUND_IMMEDIATELY)) {
                    Log.d(TAG, "call to background immediately");
                    if (!runningSettings) {
                        scheduleForeground(true);
                        moveTaskToBack(true);
                    }
                    return;
                }
                Log.d(TAG, "new intent for Activity with internal intent: "
                        + s);
                try {
                    AtakBroadcast.getInstance().sendBroadcast(s);
                } catch (IllegalStateException ignore) {

                }
            } else {
                Log.d(TAG,
                        "new intent for Activity with internal intent but intent null");
            }
        } else {
            Log.d(TAG, "new intent for Activity with no additional intents");
        }

    }

    @Override
    public void onStart() {
        super.onStart();

        if (!acceptedPermissions)
            return;

    }

    @Override
    public MapView getMapView() {
        return _mapView;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if (MetricsApi.shouldRecordMetric()) {
            Bundle b = new Bundle();
            b.putString("method", "onPrepareOptionsMenu");
            MetricsApi.record("actionbar", b);
        }

        final PowerManager powerManager = (PowerManager) getSystemService(
                POWER_SERVICE);

        super.onPrepareOptionsMenu(menu);
        return true;
    }

    /**
     * Attempt to set the NavigationBar color on the S8,  but does not work.
     * leaving in as an example.
     */
    //private void  setNavBar() { 
    //getWindow()
    //    .setNavigationBarColor(getResources().getColor(R.color.light_blue));
    //          try {
    //              Method m = getWindow().getClass().getDeclaredMethod(
    //                      "setNavigationBarColor", Integer.TYPE);
    //              m.setAccessible(true);
    //              m.invoke(getWindow(), getResources().getColor(R.color.green));
    //          } catch (NoSuchMethodException e) {
    //              Log.e(TAG, "onMenuOpened", e);
    //          } catch (Exception e) {
    //              throw new RuntimeException(e);
    //          }

    //}

    @Override
    @SuppressLint("PrivateApi")
    public boolean onMenuOpened(int featureId, Menu menu) {
        // make icon visible in the overflow menu
        if (featureId == Window.FEATURE_ACTION_BAR && menu != null) {
            if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
                try {
                    Method m = menu.getClass().getDeclaredMethod(
                            "setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                } catch (NoSuchMethodException e) {
                    Log.e(TAG, "onMenuOpened", e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    /**
     * Toggle Lock - on or off
     *
     * @param bSelf If toggle on, true to lock on self, or false to select an item to lock
     */
    private void _toggleLock(boolean bSelf) {
        if (_lockActionMenu == null) {
            Log.w(TAG, "Cannot toggle lock icon w/out action menu");
            return;
        }

        if (_lockActive) {
            Log.d(TAG, "_toggleLock unlocking");
            Intent intent = new Intent();
            intent.setAction(CamLockerReceiver.UNLOCK_CAM);
            AtakBroadcast.getInstance().sendBroadcast(intent);
            return;
        }

        if (bSelf) {
            final MapItem item = ATAKUtilities.findSelf(_mapView);
            if (item == null) {
                Toast.makeText(this, R.string.self_marker_required,
                        Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(TAG, "_toggleLock locking self");

            Intent camlock = new Intent();
            camlock.setAction(CamLockerReceiver.LOCK_CAM);
            camlock.putExtra("uid", item.getUID());
            // Mark it, so that we don't disable it because the listener
            // above will hear it immediatly. AS.
            camlock.putExtra("toolbarGenerated", true);
            AtakBroadcast.getInstance().sendBroadcast(camlock);

            setLockSelected(true);
            _currentUID = item.getUID();
            _lockActive = true;
        } else {
            Log.d(TAG, "_toggleLock selecting lock item");

            ToolManagerBroadcastReceiver.getInstance().startTool(
                    SpecifyLockItemTool.TOOL_IDENTIFIER, new Bundle());
        }
    }

    private BroadcastReceiver _advancedSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ("com.atakmap.app.ADVANCED_SETTINGS".equals(intent
                            .getAction())) {
                        Intent advancedPrefsActivity = new Intent(
                                getBaseContext(),
                                SettingsActivity.class);
                        final MapSceneModel sm = _mapView.getRenderer3()
                                .getMapSceneModel(false,
                                        MapRenderer2.DisplayOrigin.UpperLeft);
                        GeoPoint focus = sm.mapProjection
                                .inverse(sm.camera.target, null);
                        double lat = (focus != null) ? focus.getLatitude()
                                : _mapView.getLatitude();
                        double lon = (focus != null) ? focus.getLongitude()
                                : _mapView.getLongitude();
                        double alt = (focus != null && focus.isAltitudeValid())
                                ? focus.getAltitude()
                                : 0d;
                        advancedPrefsActivity.putExtra("screenViewLat",
                                lat);
                        advancedPrefsActivity.putExtra("screenViewLon",
                                lon);
                        advancedPrefsActivity.putExtra("screenViewAlt",
                                alt);
                        advancedPrefsActivity.putExtra("screenViewScale",
                                _mapView.getMapScale());

                        Bundle extraParams = new Bundle();

                        advancedPrefsActivity.putExtra("extra_params",
                                extraParams);

                        String toolkey = intent.getStringExtra("toolkey");
                        if (FileSystemUtils.isEmpty(toolkey)) {
                            toolkey = DropDownManager.getInstance()
                                    .getTopDropDownKey();
                        }
                        if (!FileSystemUtils.isEmpty(toolkey))
                            advancedPrefsActivity.putExtra("toolkey", toolkey);

                        String prefkey = intent.getStringExtra("prefkey");
                        if (!FileSystemUtils.isEmpty(prefkey))
                            advancedPrefsActivity.putExtra("prefkey", prefkey);

                        runningSettings = true;
                        startActivityForResult(advancedPrefsActivity,
                                SETTINGS_REQUEST_CODE);
                    } else if ("com.atakmap.app.NETWORK_SETTINGS".equals(intent
                            .getAction())) {
                        Log.d(TAG,
                                "received an intent to launch the network settings");
                        runningSettings = true;
                        startActivityForResult(new Intent(
                                _mapView.getContext(),
                                NetworkSettingsActivity.class),
                                NETWORK_SETTINGS_REQUEST_CODE);
                    } else if ("com.atakmap.app.DEVICE_SETTINGS".equals(intent
                            .getAction())) {
                        Log.d(TAG,
                                "received an intent to launch the device settings");
                        runningSettings = true;
                        startActivityForResult(new Intent(
                                _mapView.getContext(),
                                LocationSettingsActivity.class),
                                DEVICE_SETTINGS_REQUEST_CODE);
                    }
                }
            });
        }
    };

    private BroadcastReceiver _quitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean bForceQuit = intent.getBooleanExtra("FORCE_QUIT", false);
            if (bForceQuit) {
                _quit();
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startQuitProcess();
                    }
                });
            }
        }
    };

    private static class FileLogger implements Log.LogListener {

        PrintStream output = null;

        private boolean onlyErrors = false;

        /**
         * Set the onlyErrors flag to only print Log calls where an exception was thrown.
         */
        public void setWriteOnlyErrors(final boolean status) {
            onlyErrors = status;
        }

        public void setLogFile(FileOutputStream os) {
            //flush output
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (Exception e) {
                    android.util.Log
                            .e("Log",
                                    "error flushing the logging information to the output stream",
                                    e);
                }
            }

            //use new output stream, if provided
            output = null;
            if (os != null) {
                try {
                    output = new PrintStream(os, false,
                            FileSystemUtils.UTF8_CHARSET.name());
                } catch (UnsupportedEncodingException ignore) {
                }
            }

        }

        public void write(String tag, String msg, Throwable tr) {
            if (output != null) {
                try {
                    if (tr != null) {
                        output.println(new CoordinatedTime() + " ["
                                + tag + "]: " + msg + "\n"
                                + android.util.Log.getStackTraceString(tr));
                    } else if (!onlyErrors) {
                        output.println(new CoordinatedTime() + " ["
                                + tag + "]: " + msg);
                    }
                    output.flush();

                } catch (Exception e) {
                    android.util.Log
                            .e("Log",
                                    "error writing the logging information to the output stream",
                                    e);
                    output = null;
                }
            }

        }
    }

    /**
     * Starts a quitting process for the ATAKActivity.
     */
    private boolean startQuitProcess() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ATAKActivity.this);

        if (prefs.getBoolean("atakControlAskToQuit", true)) {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                    ATAKActivity.this);
            alertBuilder
                    .setTitle(R.string.really_quit_title)
                    .setMessage(R.string.really_quit_msg)
                    .setPositiveButton(R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    _quit();
                                    // promptSaveMissionTrack();
                                }
                            })
                    .setNegativeButton(R.string.no, null);
            AlertDialog alert = alertBuilder.create();
            alert.show();
        } else {
            _quit();
        }

        return true;
    }

    private void _quit() {

        Log.d(TAG, "Shutting down...");

        // Clean up temp files and empty directories
        FileSystemUtils.cleanup();

        // Fire onShutDown listeners (no synchronization within call)
        Set<OnShutDownListener> shutdownListeners;
        synchronized (_shutdownListeners) {
            shutdownListeners = new HashSet<>(
                    _shutdownListeners);
            _shutdownListeners.clear();
        }
        for (OnShutDownListener l : shutdownListeners)
            l.onShutDown();

        // Close any active drop-downs
        try {
            DropDownManager.getInstance().closeAllDropDowns();
        } catch (Exception e) {
            Log.d(TAG, "error occurred: " + e);
        }

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ATAKActivity.this);

        if (!skipSecureDelete && SecureDelete.getRemaining() > 0) {
            Log.d(TAG, "Waiting for SecureDelete to finish...");

            synchronized (lock) {
                // Prevent from creating more than once dialog
                if (secureDeleteDialog != null)
                    return;

                // Create quitting dialog
                secureDeleteDialog = new ProgressDialog(
                        _mapView.getContext());
            }
            secureDeleteDialog.setIcon(R.drawable.ic_menu_lock_lit);
            secureDeleteDialog.setTitle(getString(R.string.secure_delete));
            secureDeleteDialog
                    .setMessage(getString(R.string.preferences_text437));
            secureDeleteDialog.setCancelable(false);
            secureDeleteDialog.setIndeterminate(true);
            secureDeleteDialog.show();

            // Check if SecureDelete is finished
            final Handler timer = new Handler();
            final int checkInterval = 100;
            int tempTimeout = 10;
            if (prefs != null) {
                try {
                    tempTimeout = Integer.parseInt(
                            prefs.getString("secureDeleteTimeout", "10"));
                } catch (NumberFormatException ignored) {
                }
            }
            final int timeout = tempTimeout * 1000;
            final long startTime = android.os.SystemClock.elapsedRealtime();
            timer.postDelayed(new Runnable() {
                @Override
                public void run() {
                    int remaining = SecureDelete.getRemaining();
                    if (android.os.SystemClock.elapsedRealtime()
                            - startTime >= timeout) {
                        remaining = 0;
                        Log.w(TAG,
                                "Deletions taking too long to finish. Force quitting...");
                    }
                    if (remaining == 0) {
                        skipSecureDelete = true;
                        synchronized (lock) {
                            if (secureDeleteDialog != null)
                                secureDeleteDialog.dismiss();
                            secureDeleteDialog = null;
                        }
                        _quit();
                    } else
                        timer.postDelayed(this, checkInterval);
                }
            }, checkInterval);
            return;
        }

        synchronized (lock) {
            // Just in case to prevent leaks
            if (secureDeleteDialog != null) {
                secureDeleteDialog.dismiss();
                secureDeleteDialog = null;
            }
        }
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        Log.d(TAG, "Got Activity Result for request[" + requestCode + "]: " +
                (resultCode == Activity.RESULT_OK ? "OK" : "ERROR"));

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 0) {
            // HACK - sending the point data through the result_canceled
            // this means that we have to check for a null intent
            if (resultCode == RESULT_CANCELED) {
                if (data != null) {
                    GeoPoint spot = new GeoPoint(
                            data.getDoubleExtra("lat", 0d),
                            data.getDoubleExtra("lon", 0d));
                    Marker placedPoint = new PlacePointTool.MarkerCreator(spot)
                            .setCallsign(data.getStringExtra("title"))
                            .setType("a-n-G")
                            .placePoint();
                    Log.d(TAG, "point placed: " + placedPoint.getTitle());

                    _mapView.getMapController().panTo(spot, true);
                }

            }

            if (resultCode == RESULT_OK) {

                double tempLat = data.getDoubleExtra(
                        "com.atakmap.app.FavLat", 0d);
                double tempLon = data.getDoubleExtra(
                        "com.atakmap.app.FavLon", 0d);
                // legacy zoom ~4000
                double tempScale = data.getDoubleExtra(
                        "com.atakmap.app.FavMapScale", 1.0d / 98609.5);
                Log.d(TAG, "using favorite: " + tempLat + " lon: " + tempLon
                        + " scale: " + tempScale);
                GeoPoint spot = new GeoPoint(tempLat, tempLon,
                        ElevationManager.getElevation(tempLat, tempLat, null));

                _mapView.getMapController().dispatchOnPanRequested();
                final MapSceneModel sm = _mapView.getRenderer3()
                        .getMapSceneModel(
                                false, MapRenderer2.DisplayOrigin.UpperLeft);
                _mapView.getRenderer3().lookAt(
                        spot,
                        Globe.getMapResolution(_mapView.getDisplayDpi(),
                                tempScale),
                        sm.camera.azimuth,
                        90d + sm.camera.elevation,
                        true);
            } else {
                Log.d(TAG, "result not ok");
            }
        } else if (requestCode == SETTINGS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                double lat = data.getDoubleExtra("screenViewLat", 0.0);
                double lon = data.getDoubleExtra("screenViewLon", 0.0);
                double alt = data.getDoubleExtra("screenViewAlt", 0.0);
                double scale = data.getDoubleExtra("screenViewMapScale", 0.0);
                Log.d(TAG, "settingRequestCode saved screen location lat: "
                        + lat
                        + " lon: " + lon + " scale: " + scale);

                if (lat != 0 || lon != 0 || scale != 0) {
                    _mapView.getMapController().dispatchOnPanRequested();
                    final MapSceneModel sm = _mapView.getRenderer3()
                            .getMapSceneModel(
                                    false,
                                    MapRenderer2.DisplayOrigin.UpperLeft);
                    _mapView.getRenderer3().lookAt(
                            new GeoPoint(lat, lon, alt),
                            Globe.getMapResolution(_mapView.getDisplayDpi(),
                                    scale),
                            sm.camera.azimuth,
                            90d + sm.camera.elevation,
                            true);
                }
            }
        } else if (requestCode == NETWORK_SETTINGS_REQUEST_CODE) {
            //no-op
        } else if (requestCode == DEVICE_SETTINGS_REQUEST_CODE) {
            //no-op
        } else if (requestCode == Permissions.REQUEST_ID) {
            onCreate(null);
        } else {
            Intent i = new Intent(ACTIVITY_FINISHED);
            i.putExtra("requestCode", requestCode);
            i.putExtra("resultCode", resultCode);
            i.putExtra("data", data);
            AtakBroadcast.getInstance().sendBroadcast(i);
        }

    }

    private void _myLocationNoMenu() {

        final Marker self = getMapView().getSelfMarker();

        if (self.hasMetaValue("camLocked"))
            return;

        final Intent hideMenu = new Intent(
                "com.atakmap.android.maps.HIDE_MENU");
        AtakBroadcast.getInstance().sendBroadcast(hideMenu);

        final Intent myLocationIntent = new Intent(
                "com.atakmap.android.maps.ZOOM_TO_LAYER");
        myLocationIntent.putExtra("uid", self.getUID());
        myLocationIntent.putExtra("noZoom", true);
        AtakBroadcast.getInstance().sendBroadcast(myLocationIntent);
    }

    /**
     * The responsibility of the application is to check to see if the icon is correct based on the
     * flavor.   If it is incorrect, during the shutdown of the application - go ahead and switch the
     * icon.   This solves issues with the unpredictable behavior caused by disabling an application
     * that is running.   Please note - this should only be run when the application is quit so it
     * does not kill the application on startup.
     */
    private void ensureCorrectIcon() {
        final FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        boolean civilian = true;
        if (fp != null && fp.hasMilCapabilities()) {
            civilian = false;
        }
        // Enable/disable activity-aliases
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(ATAKActivity.this,
                        "com.atakmap.app.ATAKActivityCiv"),
                (civilian) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(
                new ComponentName(ATAKActivity.this,
                        "com.atakmap.app.ATAKActivityMil"),
                (civilian) ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

    }

    private void setupSplash(final View splashView) {
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null)
            fp.installCustomSplashScreen(splashView,
                    AtakPreferenceFragment.getOrientation(this));

        try {
            ((TextView) splashView.findViewById(R.id.revision))
                    .setText(String.format(LocaleUtil.getCurrent(), "%s%s",
                            getString(R.string.version),
                            getPackageManager().getPackageInfo(
                                    getPackageName(), 0).versionName));

            final String encryptorName = SystemComponentLoader
                    .getEncryptionComponentName();
            if (encryptorName != null) {
                final TextView encryptionText = splashView
                        .findViewById(R.id.encryption);
                encryptionText.setText(getString(R.string.dar_encryptor_message,
                        encryptorName));
                encryptionText.setVisibility(View.VISIBLE);

            }
            File file = null;
            File splash = FileSystemUtils
                    .getItem(FileSystemUtils.SUPPORT_DIRECTORY
                            + File.separatorChar + "atak_splash.png");
            File splashUpper = FileSystemUtils
                    .getItem(FileSystemUtils.SUPPORT_DIRECTORY
                            + File.separatorChar + "atak_splash.PNG");
            if (FileSystemUtils.isFile(splash)) {
                file = splash;
            } else if (FileSystemUtils.isFile(splashUpper)) {
                file = splashUpper;
            }
            if (file != null) {
                Bitmap bmp;
                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(file)) {
                    bmp = BitmapFactory.decodeStream(fis);
                } catch (IOException e) {
                    bmp = null;
                }

                if (bmp != null && bmp.getWidth() < 4096
                        && bmp.getHeight() < 4096) {
                    Log.d(TAG, "Loading custom splash screen");
                    ImageView atak_splash_imgView = splashView
                            .findViewById(R.id.atak_splash_imgView);
                    atak_splash_imgView.setImageBitmap(bmp);
                } else {
                    if (bmp == null) {
                        Toast.makeText(this, R.string.invalid_splash,
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG, "splash screen bitmap was null");
                    } else {
                        Toast.makeText(this,
                                R.string.invalid_splash_size,
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG,
                                "splash screen exceeds maximum 4096 in one direction:  width = "
                                        + bmp.getWidth() + " height = "
                                        + bmp.getHeight());
                        // do not use it
                        bmp.recycle();
                    }
                }
            }

        } catch (NameNotFoundException e) {
            Log.e(TAG, "Error: " + e);
        }

    }

    private void setupFileLog() {
        try {
            if (_prefs.get("loggingfile", false)) {

                int index = _prefs.get("loggingFileIndex", -1) + 1;
                if (index > 9)
                    index = 0;
                _prefs.set("loggingFileIndex", index);

                File logFile = FileSystemUtils
                        .getItem(FileSystemUtils.SUPPORT_DIRECTORY
                                + File.separatorChar + "logs"
                                + File.separatorChar + "logcat" + index
                                + ".txt");
                if (!IOProviderFactory.mkdir(logFile.getParentFile())) {
                    Log.e(TAG, "Failed to make dir at: "
                            + logFile.getParentFile().getPath());
                }
                if (logFile.getParentFile() != null)
                    if (!IOProviderFactory.mkdirs(logFile.getParentFile())) {
                        Log.e(TAG, "Failed to make dir at: "
                                + logFile.getParentFile().getPath());
                    }

                fileLogger
                        .setLogFile(IOProviderFactory.getOutputStream(logFile));
                Log.registerLogListener(fileLogger);
                fileLogger.setWriteOnlyErrors(_prefs.get(
                        "loggingfile_error_only", false));
                Log.d(TAG, "creating logcat." + index + ".txt");
            } else {
                Log.unregisterLogListener(fileLogger);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error creating logcat file", e);
        }
    }

    private void _listenToExternalStorageState() {
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        AtakBroadcast.getInstance().registerReceiver(
                _mediaMountedUnmountedReceiver, filter);
    }

    private void _handleExternalStorageState() {
        String storageState = Environment.getExternalStorageState();
        if (!storageState.equals(Environment.MEDIA_MOUNTED)) {
            showDialog(_UNMOUNTED_EXTERNAL_STORAGE_DIALOG);
        }
    }

    private BroadcastReceiver _mediaMountedUnmountedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            _handleExternalStorageState();
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        if (!acceptedPermissions)
            return;

        AtakPreferenceFragment.setOrientation(ATAKActivity.this);

        cancelForeground();

        if (_wakeLock != null && !_wakeLock.isHeld())
            _wakeLock.acquire();

        // Please restore Track Up if track up was enabled prior. There is no
        // reason to be sampling track up while the screen is off. It just wastes
        // alot of CPU cycles.
        if (_previousOrientationState != null
                && _previousOrientationState == MapMode.TRACK_UP)
            setOrientationState(_previousOrientationState);

        if (_mapView == null)
            return;

        SystemComponentLoader
                .notify(AbstractSystemComponent.SystemState.RESUME);

    }

    @Override
    public void onPause() {

        if (!acceptedPermissions) {
            super.onPause();
            return;
        }

        scheduleForeground(false);

        /**
         * The below line is commented out because when released it to shuts down
         * the flow of outgoing CotEvents when the device is not connected to
         * power. Several experiments have been run and it seems unlikely writing
         * to the network is the problem. Smaller sample programs go into sleep
         * just fine and emit multicast traffic. The output Cot messages was the
         * most obvious manifestation of the problem. When commenting out the
         * AbstractOutput onSendXml so no network traffic is actually generated
         * for the locational information and SystemClock.elapsedRealtime is used
         * to measure deltas written to the Log, large gaps occur between the
         * calls to the sending code that do not correctly correspond to the
         * period which the code actually is called when not sleeping. Steps for
         * reproducing the issue. Start up ATAK on a network without any other
         * devices and unplugged from a power source. Drop your current positional
         * Screen lock (causing the wakeLock release). Traffic on 239.2.3.1:6969
         * WiFi will come to a stop with an occasional output. On another device
         * on the network, pinging the device or sending data to the device This
         * issue might manifest itself in different and unexpected ways. DO NOT
         * UNCOMMENT THE BELOW LINE UNLESS A TESTED SOLUTION IS IN PLACE. -- The
         * fact that this wakelock is commented out is only a stop gap measure --
         * until the real problem is identified and fixed.
         */
        // _wakeLock.release();

        /**
         * Please save if Track Up was enabled prior to pausing and turn it off.
         * There is no reason to be sampling track up while the screen is off. It
         * just wastes alot of CPU cycles.
         */

        NavView nav = NavView.getInstance();
        if (nav != null) {
            _previousOrientationState = nav.getMapMode();
            if (_previousOrientationState == MapMode.TRACK_UP)
                setOrientationState(MapMode.NORTH_UP);
        }

        //TODO what is the right way to end a tool when ATAK, send "ATAK_PAUSING" intent to
        //EnterLocationDropDownReceiver so it can end the EnterLocationTool, if necessary?
        Intent endLocationTool = new Intent();
        endLocationTool.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        endLocationTool.putExtra("tool", EnterLocationTool.TOOL_NAME);
        AtakBroadcast.getInstance().sendBroadcast(endLocationTool);

        SystemComponentLoader.notify(AbstractSystemComponent.SystemState.PAUSE);
        super.onPause();
    }

    private int count = 0;

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);

        if (requestCode == Permissions.LOCATION_REQUEST_ID) {
            boolean b = Permissions.onRequestPermissionsResult(requestCode,
                    permissions, grantResults);
            if (b) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    requestPermissions(Permissions.PermissionsList,
                            Permissions.REQUEST_ID);
            } else {
                Log.d(TAG, "location permission set not granted, retry...");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    requestPermissions(Permissions.locationPermissionsList,
                            Permissions.REQUEST_ID);
            }

        } else if (requestCode == Permissions.REQUEST_ID) {
            boolean b = Permissions.onRequestPermissionsResult(requestCode,
                    permissions, grantResults);
            if (b) {
                acceptedPermissions = true;
                onCreate(null);
                return;
            }
            if (count > 3) {
                Permissions.displayNeverAskAgainDialog(this);
            } else {
                count++;
                Log.d(TAG, "need to generate a new permission request");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    requestPermissions(Permissions.PermissionsList,
                            Permissions.REQUEST_ID);
            }
        } else {
            final boolean b = Permissions.onRequestPermissionsResult(
                    requestCode,
                    permissions, grantResults);
            Log.d(TAG,
                    "permissions[" + requestCode + "]: "
                            + Arrays.toString(permissions) +
                            " " + (b ? "granted" : "not granted"));
        }

    }

    synchronized private void cancelForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "skipping the foreground task: cancel");
            return;
        }

        Log.d(TAG, "cancelling the foreground task");
        runningSettings = false;
        if (contentIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(
                    Context.ALARM_SERVICE);
            alarmManager.cancel(contentIntent);
            contentIntent.cancel();
            contentIntent = null;
        }
    }

    synchronized private void scheduleForeground(boolean forceSchedule) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "skipping the foreground task: schedule");
            return;
        }

        Log.d(TAG, "scheduling the foreground task");
        Intent atakFrontIntent = new Intent();
        atakFrontIntent.setComponent(ATAKConstants.getComponentName());

        atakFrontIntent.putExtra("internalIntent", new Intent(
                BACKGROUND_IMMEDIATELY));

        atakFrontIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        contentIntent = PendingIntent.getActivity(this, 113016,
                atakFrontIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // if the app is swiped away, do not allow for the 
        // pending intent to run.
        BackgroundServices.contentIntent = contentIntent;

        long time = System.currentTimeMillis() + (9 * 60 * 1000);
        // for testing only
        // time = System.currentTimeMillis() + (30 * 1000);
        // schedule the alarm for 9 minutes
        AlarmManager alarmManager = (AlarmManager) getSystemService(
                Context.ALARM_SERVICE);

        //set the alarm for particular time
        alarmManager.set(AlarmManager.RTC_WAKEUP, time, contentIntent);
    }

    private void setOrientationState(final MapMode state) {
        Log.d(TAG, "setting orientation state: " + state);
        NavView nav = NavView.getInstance();
        if (nav != null)
            nav.setMapMode(state);
    }

    public class ShutDownReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;
            if (action.equals(Intent.ACTION_SHUTDOWN)) {
                Log.d(TAG,
                        "system shutdown intent received, shutting down app");
                finish();
            }
        }
    }

    public interface OnShutDownListener {
        void onShutDown();
    }

    public void addOnShutDownListener(OnShutDownListener l) {
        synchronized (_shutdownListeners) {
            _shutdownListeners.add(l);
        }
    }

    public void removeOnShutDownListener(OnShutDownListener l) {
        synchronized (_shutdownListeners) {
            _shutdownListeners.remove(l);
        }
    }

    private static final int _UNMOUNTED_EXTERNAL_STORAGE_DIALOG = 0;
    private static final int SETTINGS_REQUEST_CODE = 555; // Arbitrary
    private static final int ALLTOOLS_REQUEST_CODE = 556; // Arbitrary
    private static final int NETWORK_SETTINGS_REQUEST_CODE = 559; // Arbitrary
    private static final int DEVICE_SETTINGS_REQUEST_CODE = 560; // Arbitrary
    public static final String ACTIVITY_FINISHED = "com.atakmap.android.ACTIVITY_FINISHED";

    private PendingIntent contentIntent = null;

    private PowerManager.WakeLock _wakeLock;
    private AtakPreferences _prefs;
    private UnitChangeReceiver _unitChangeReceiver;
    private ShutDownReceiver shutdownReceiver;

    private MapView _mapView;
    private View _newNavView;
    private LinkLineReceiver _linkLineReceiver;

    // default state for the map behavior on start.
    private MapMode _previousOrientationState;

    private ActionMenuData _lockActionMenu;
    private boolean _lockActive = false;
    private String _currentUID;

    private boolean orientationChangeRequestPending = false;
    private ZoomReceiver _zoomReceiver;
    private ActionBarReceiver _actionBarReceiver;
    private BroadcastReceiver _pluginLoadedRec;
    private BroadcastReceiver _exportCrashLogRec;

    // Dialog that shows secure deletion process
    private static ProgressDialog secureDeleteDialog = null;
    private final static Object lock = new Object();

    // don't wait for Secure Delete to finish
    private boolean skipSecureDelete = false;

    // Shutdown listeners - fired after quit and before onDestroy
    private final Set<OnShutDownListener> _shutdownListeners = new HashSet<>();

    com.atakmap.android.bluetooth.BluetoothFragment btFrag;

    private FauxNavBar fnb;
    private DeviceSetupWizard setupWizard;
    private BroadcastReceiver _setupWizardRec;

    public static final String BACKGROUND_IMMEDIATELY = "com.atakmap.app.BACKGROUND_IMMEDIATELY";

    private boolean acceptedPermissions = false;
    private boolean encryptionCallbackValid = true;

    private boolean runningSettings = false;

    private static final boolean DEVELOPER_MODE = false;

    private final FileLogger fileLogger = new FileLogger();

    // only ever set to false when an automated test is being run.
    private boolean callSystemExit;
}
