
package com.atakmap.android.missionpackage;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.filesharing.android.service.WebServer;
import com.atakmap.android.missionpackage.http.MissionPackageDownloader;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class MissionPackagePreferenceListener implements
        OnSharedPreferenceChangeListener {

    protected static final String TAG = "MissionPackagePreferenceListener";

    public static final String filesharingEnabled = "filesharingEnabled";
    public static final boolean filesharingEnabledDefault = true;

    public static final String fileshareDownloadAttempts = "fileshareDownloadAttempts";
    public static final String filesharingSizeThresholdNoGo = "filesharingSizeThresholdNoGo";
    public static final String filesharingConnectionTimeoutSecs = "filesharingConnectionTimeoutSecs";
    public static final String filesharingTransferTimeoutSecs = "filesharingTransferTimeoutSecs";

    private static final int MAX_TIMEOUT_SECS = 900; // 15 minutes seems long enough to wait...

    private final MissionPackageMapComponent _component;
    private final Context _context;
    private SharedPreferences _prefs;

    public MissionPackagePreferenceListener(Context context,
            MissionPackageMapComponent component) {
        _prefs = PreferenceManager.getDefaultSharedPreferences(context);
        _prefs.registerOnSharedPreferenceChangeListener(this);
        _component = component;
        _context = context;
    }

    public void dispose() {
        if (_prefs != null) {
            _prefs.unregisterOnSharedPreferenceChangeListener(this);
            _prefs = null;
        }
    }

    /**
     * Obtains an integer value from a preference identified by a key and returns the 
     * default value upon either the key missing or the key set but with non-numeric 
     * value.
     */
    public static int getInt(SharedPreferences pref, final String key,
            final int def) {
        try {
            return Integer.parseInt(pref.getString(key, String.valueOf(def)));
        } catch (NumberFormatException nfe) {
            pref.edit().putString(key, String.valueOf(def)).apply();
            return def;
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        switch (key) {
            case filesharingEnabled: {
                boolean bEnabled = sharedPreferences.getBoolean(
                        filesharingEnabled,
                        filesharingEnabledDefault);
                Log.d(TAG, (bEnabled ? "File Sharing enabled"
                        : "File Sharing disabled"));
                if (bEnabled) {
                    if (!_component.enable()) {
                        // failed to start, uncheck the setting
                        setEnabled(false);
                    }
                } else {
                    _component.disable(true);
                }
                break;
            }
            case WebServer.SERVER_LEGACY_HTTP_ENABLED_KEY:
            case WebServer.SECURE_SERVER_PORT_KEY:
            case WebServer.SERVER_PORT_KEY: {
                // TODO use int directly?
                int port = getInt(sharedPreferences,
                        WebServer.SERVER_PORT_KEY,
                        WebServer.DEFAULT_SERVER_PORT);
                Log.d(TAG, "File Sharing port changed to: " + port);

                // if web server is running, restart it
                boolean bEnabled = sharedPreferences.getBoolean(
                        filesharingEnabled,
                        filesharingEnabledDefault);
                if (bEnabled) {
                    if (!validatePort(port)) {
                        String message = _context.getString(
                                R.string.mission_package_invalid_file_sharing_port,
                                port, WebServer.DEFAULT_SERVER_PORT);
                        Log.w(TAG, message);
                        Toast.makeText(_context, message, Toast.LENGTH_LONG)
                                .show();
                        setPort(WebServer.DEFAULT_SERVER_PORT);
                        return;
                    }

                    Log.d(TAG, "Restarting webserver on port: " + port);
                    if (!_component.restart()) {
                        setEnabled(false);
                    }
                }
                break;
            }
            case fileshareDownloadAttempts:
                int attempts = getInt(sharedPreferences,
                        fileshareDownloadAttempts,
                        MissionPackageDownloader.DEFAULT_DOWNLOAD_RETRIES);

                if (attempts < 1) {
                    String message = _context.getString(
                            R.string.mission_package_download_attempts_must_be_greater_than_0,
                            attempts,
                            MissionPackageDownloader.DEFAULT_DOWNLOAD_RETRIES);
                    Log.w(TAG, message);
                    Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
                    _prefs.edit().putString(fileshareDownloadAttempts,
                            String.valueOf(
                                    MissionPackageDownloader.DEFAULT_DOWNLOAD_RETRIES))
                            .apply();
                    return;
                }

                Log.d(TAG, "File Sharing download attempts changed to: "
                        + attempts);
                _component.getReceiver().getDownloader()
                        .setDownloadAttempts(attempts);
                break;
            case filesharingSizeThresholdNoGo:
                int thresholdNoGo = getInt(
                        sharedPreferences,
                        filesharingSizeThresholdNoGo,
                        MissionPackageReceiver.DEFAULT_FILESIZE_THRESHOLD_NOGO_MB);

                if (thresholdNoGo < 1) {
                    String message = _context.getString(
                            R.string.mission_package_max_file_transfer_size_must_be_greater_than_0,
                            MissionPackageReceiver.DEFAULT_FILESIZE_THRESHOLD_NOGO_MB);
                    Log.w(TAG, message);
                    Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
                    _prefs.edit().putString(filesharingSizeThresholdNoGo,
                            String.valueOf(
                                    MissionPackageReceiver.DEFAULT_FILESIZE_THRESHOLD_NOGO_MB))
                            .apply();
                    return;
                }

                Log.d(TAG, filesharingSizeThresholdNoGo + " " + thresholdNoGo);
                break;
            case filesharingConnectionTimeoutSecs: {
                int currentValueSecs = (int) (_component.getReceiver()
                        .getConnectionTimeoutMS() / 1000);

                if ((currentValueSecs <= 0)
                        || (currentValueSecs > MAX_TIMEOUT_SECS)) {
                    currentValueSecs = MissionPackageReceiver.DEFAULT_CONNECTION_TIMEOUT_SECS;
                }

                int userValueSecs = currentValueSecs;
                try {
                    userValueSecs = getInt(sharedPreferences, key,
                            currentValueSecs);
                    if (userValueSecs <= 0
                            || userValueSecs > MAX_TIMEOUT_SECS) {
                        String message = _context.getString(
                                R.string.mission_package_timeout_must_be_greater_than_0,
                                userValueSecs, MAX_TIMEOUT_SECS,
                                currentValueSecs);
                        Log.w(TAG, message);
                        Toast.makeText(_context, message, Toast.LENGTH_LONG)
                                .show();
                        userValueSecs = 0;
                    }
                } catch (Exception e) {
                    String message = _context.getString(
                            R.string.mission_package_timeout_must_be_numeric,
                            currentValueSecs);
                    Log.w(TAG, message);
                    Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
                    userValueSecs = 0;
                }

                if (userValueSecs <= 0) {
                    _prefs.edit().putString(key, String.valueOf(
                            currentValueSecs)).apply();
                    userValueSecs = currentValueSecs;
                }

                Log.d(TAG, key + " " + userValueSecs);
                break;
            }
            case filesharingTransferTimeoutSecs: {
                int currentValueSecs = (int) (_component.getReceiver()
                        .getTransferTimeoutMS() / 1000);

                if ((currentValueSecs <= 0)
                        || (currentValueSecs > MAX_TIMEOUT_SECS)) {
                    currentValueSecs = MissionPackageReceiver.DEFAULT_TRANSFER_TIMEOUT_SECS;
                }

                int userValueSecs = currentValueSecs;
                try {
                    userValueSecs = getInt(sharedPreferences, key,
                            currentValueSecs);
                    if ((userValueSecs <= 0)
                            || (userValueSecs > MAX_TIMEOUT_SECS)) {
                        String message = _context.getString(
                                R.string.mission_package_timeout_must_be_greater_than_0,
                                userValueSecs, MAX_TIMEOUT_SECS,
                                currentValueSecs);
                        Log.w(TAG, message);
                        Toast.makeText(_context, message, Toast.LENGTH_LONG)
                                .show();
                        userValueSecs = 0;
                    }
                } catch (Exception e) {
                    String message = String
                            .format(
                                    _context.getString(
                                            R.string.mission_package_timeout_must_be_numeric),
                                    currentValueSecs);
                    Log.w(TAG, message);
                    Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
                    userValueSecs = 0;
                }

                if (userValueSecs <= 0) {
                    _prefs.edit()
                            .putString(key, String.valueOf(currentValueSecs))
                            .apply();
                    userValueSecs = currentValueSecs;
                }

                Log.d(TAG, key + " " + userValueSecs);
                break;
            }
        }
    }

    void setEnabled(boolean b) {
        Log.i(TAG, "Setting File Sharing enabled to: " + b);
        _prefs.edit().putBoolean(filesharingEnabled, b).apply();
    }

    void setPort(int port) {
        // validate and set
        if (!validatePort(port)) {
            Log.w(TAG, "Invalid File Sharing port: " + port);
            return;
        }
        Log.i(TAG, "Setting File Sharing port to: " + port);
        _prefs.edit().putString(WebServer.SERVER_PORT_KEY,
                String.valueOf(port)).apply();
    }

    static boolean validatePort(int port) {
        return port > 0 && port <= 65536;
    }
}
