
package com.atakmap.app.preferences;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.app.R;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.log.Log;

import java.util.Set;

public class MyPreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "MyPreferenceFragment";

    private static MyPreferenceFragment _instance;
    private final CotServiceRemote _remote;
    private Preference _myServers;
    private Preference _myIdentity;
    private Preference _myPlugins;

    public synchronized static MyPreferenceFragment getInstance() {
        if (_instance == null) {
            _instance = new MyPreferenceFragment();
        }
        return _instance;
    }

    public MyPreferenceFragment() {
        super(R.xml.my_preferences, R.string.myPreferences);
        // connect to the service
        _remote = new CotServiceRemote();
        //get connection state callbacks
        _remote.setOutputsChangedListener(_outputsChangedListener);
        _remote.connect(_connectionListener);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        getActivity().setResult(Activity.RESULT_CANCELED, null);

        Preference callSignAndDevicePrefs = findPreference(
                "callSignAndDevicePrefs");
        callSignAndDevicePrefs.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new CallSignAndDeviceFragment());
                        return true;
                    }
                });

        Preference networkPrefs = findPreference("networkPrefs");
        networkPrefs.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new NetworkPreferenceFragment());
                        return true;
                    }
                });

        Preference toolPrefs = findPreference("toolPrefs");
        toolPrefs.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new ToolsPreferenceFragment());
                        return true;
                    }
                });
        //refreshStatus();

        Preference displayPrefs = findPreference("displayPrefs");
        displayPrefs.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new DisplayPrefsFragment());
                        return true;
                    }
                });

        Preference controlPrefs = findPreference("controlPrefs");
        controlPrefs.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new ControlPrefsFragment());
                        return true;
                    }
                });

        Preference legacyPrefs = findPreference("legacyPrefs");
        legacyPrefs.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new LegacyPreferencesFragment());
                        return true;
                    }
                });

        Preference accounts = findPreference("accounts");
        accounts.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new AtakAccountsFragment());
                        return true;
                    }
                });

        Preference documentation = findPreference("documentation");
        documentation.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new SupportPreferenceFragment());
                        return true;
                    }
                });

        Preference about = findPreference("about");
        about.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        ATAKConstants.displayAbout(getActivity(),
                                true);
                        return true;
                    }
                });
        about.setIcon(ATAKConstants.getIcon());
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                MyPreferenceFragment.class,
                R.string.myPreferences,
                R.drawable.my_prefs_settings);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {

        final MapView mapView = MapView.getMapView();
        final CotMapComponent inst = CotMapComponent.getInstance();

        if (mapView == null || inst == null)
            return;

        final Context context = mapView.getContext();

        if (_myServers != null) {
            CotPortListActivity.CotPort[] servers = inst.getServers();
            if (servers == null || servers.length < 1) {
                _myServers.setIcon(R.drawable.ic_menu_network);
                _myServers.setTitle(R.string.no_servers_title);
                _myServers.setSummary(R.string.no_servers_summary);
            } else {
                _myServers.setTitle(R.string.my_servers);
                String summary = context
                        .getString(R.string.my_servers_summary)
                        + " (" + servers.length
                        + (servers.length == 1 ? " server " : " servers ")
                        + "configured)";
                _myServers.setSummary(summary);

                if (inst.isServerConnected()) {
                    _myServers.setIcon(ATAKConstants.getServerConnection(true));
                } else {
                    _myServers
                            .setIcon(ATAKConstants.getServerConnection(false));
                }
            }
        }

        if (_myIdentity != null) {
            String summary = context
                    .getString(R.string.callsign_pref_summary2)
                    + " (" + mapView.getDeviceCallsign() + ")";
            _myIdentity.setSummary(summary);
        }

        AtakPluginRegistry registry = AtakPluginRegistry.get();
        if (_myPlugins != null && registry != null) {
            Set<String> loaded = registry.getPluginsLoaded();
            String summary = context
                    .getString(R.string.my_plugins_summary)
                    + " (" + loaded.size()
                    + (loaded.size() == 1 ? " plugin " : " plugins ")
                    + "loaded)";
            _myPlugins.setSummary(summary);
        }
    }

    protected final CotServiceRemote.ConnectionListener _connectionListener = new CotServiceRemote.ConnectionListener() {

        @Override
        public void onCotServiceDisconnected() {

        }

        @Override
        public void onCotServiceConnected(Bundle fullServiceState) {
            Log.v(TAG, "onCotServiceConnected");
            refreshStatus();
        }
    };

    protected final CotServiceRemote.OutputsChangedListener _outputsChangedListener = new CotServiceRemote.OutputsChangedListener() {

        @Override
        public void onCotOutputRemoved(Bundle descBundle) {
            Log.v(TAG, "onCotOutputRemoved");
            refreshStatus();

        }

        @Override
        public void onCotOutputUpdated(Bundle descBundle) {
            Log.v(TAG, "onCotOutputUpdated");
            refreshStatus();
        }
    };

    /**
     * XXX - This is stupid, just use the proper text view resource for the spinner
     *
     * @param s Spinner
     * @see com.atakmap.android.gui.ThemedSpinner
     */
    public static void fixTextColor(Spinner s) {
        if (s == null)
            return;

        s.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0,
                            View arg1, int position, long id) {
                        if (arg1 instanceof TextView)
                            ((TextView) arg1).setTextColor(Color.WHITE);
                    }
                });
    }
}
