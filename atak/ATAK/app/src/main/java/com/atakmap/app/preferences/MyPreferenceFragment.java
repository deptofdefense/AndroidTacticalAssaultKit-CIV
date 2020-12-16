
package com.atakmap.app.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.atakmap.android.preference.PreferenceSearchIndex;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.importfiles.sort.ImportMissionPackageSort;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.update.AppMgmtActivity;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.app.R;
import com.atakmap.app.SettingsActivity;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.comms.app.CotStreamListActivity;
import com.atakmap.coremap.filesystem.FileSystemUtils;
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

        _myIdentity = findPreference("myIdentity");
        _myIdentity.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        promptIdentity(getActivity());
                        return true;
                    }
                });

        _myPlugins = findPreference("myPlugins");
        _myPlugins.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent mgmtPlugins = new Intent(getActivity(),
                                AppMgmtActivity.class);
                        startActivityForResult(mgmtPlugins,
                                ToolsPreferenceFragment.APP_MGMT_REQUEST_CODE);
                        return true;
                    }
                });

        _myServers = findPreference("myServers");
        _myServers.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (CotMapComponent.hasServer()) {
                            startActivity(new Intent(getActivity(),
                                    CotStreamListActivity.class));
                        } else {
                            promptNetwork(getActivity());
                        }

                        return true;
                    }
                });
        refreshStatus();

        Preference toolsPref = findPreference("toolsPref");
        toolsPref.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new ToolsPreferenceFragment());
                        return true;
                    }
                });

        Preference displayPref = findPreference("generalDisplayPref");
        displayPref.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new DisplayPrefsFragment());
                        return true;
                    }
                });

        Preference atakControlOptions = findPreference("atakControlOptions");
        atakControlOptions.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new MainPreferencesFragment());
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

    public static void promptNetwork(final Context context) {
        //TODO add option to open for Serial Manager (if installed)?
        MapView mapView = MapView.getMapView();
        if (mapView == null)
            return;

        Resources r = mapView.getResources();
        TileButtonDialog d = new TileButtonDialog(mapView, context, context,
                false);
        d.addButton(r.getDrawable(R.drawable.missionpackage_icon),
                r.getString(R.string.mission_package_name));
        d.addButton(r.getDrawable(R.drawable.ic_menu_network),
                r.getString(R.string.MARTI_sync_server));
        d.addButton(r.getDrawable(R.drawable.ic_menu_settings),
                r.getString(R.string.advanced_network_settings));
        d.show(R.string.preferences_text4373, R.string.choose_config_method,
                true);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                switch (which) {
                    case TileButtonDialog.WHICH_CANCEL:

                        break;
                    case 0:
                        //NavUtils.navigateUpFromSameTask(getActivity());
                        ImportMissionPackageSort.importMissionPackage(context);
                        break;
                    case 1:
                        context.startActivity(new Intent(context,
                                CotStreamListActivity.class)
                                        .putExtra("add", true));
                        break;
                    case 2:
                        SettingsActivity
                                .start(NetworkConnectionPreferenceFragment.class);
                        break;
                }
            }
        });
    }

    private void refreshStatus() {

        final MapView mapView = MapView.getMapView();
        final Context context = mapView.getContext();

        CotMapComponent inst = CotMapComponent.getInstance();

        if (mapView == null || inst == null)
            return;

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

    public static void promptIdentity(final Context context) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        View view = LayoutInflater.from(context).inflate(
                R.layout.callsign_identity, MapView.getMapView(), false);

        final EditText identityCallsign = view
                .findViewById(R.id.identityCallsign);
        final Spinner identityTeam = view
                .findViewById(R.id.identityTeam);
        final Spinner identityRole = view
                .findViewById(R.id.identityRole);

        String name = prefs.getString("locationCallsign", "");
        identityCallsign.setText(name);
        identityCallsign.setSelection(name.length());

        String locationTeam = prefs.getString("locationTeam", "Cyan");
        String[] colors = context.getResources()
                .getStringArray(R.array.squad_values);
        identityTeam.setSelection(0);
        if (colors != null && !FileSystemUtils.isEmpty(locationTeam)) {
            for (int i = 0; i < colors.length; i++) {
                if (FileSystemUtils.isEquals(locationTeam, colors[i])) {
                    identityTeam.setSelection(i);
                    break;
                }
            }
        }

        String atakRoleType = prefs.getString("atakRoleType",
                context.getString(R.string.preferences_text82));
        String[] roles = context.getResources()
                .getStringArray(R.array.role_values);
        identityRole.setSelection(0);
        if (roles != null && !FileSystemUtils.isEmpty(atakRoleType)) {
            for (int i = 0; i < roles.length; i++) {
                if (FileSystemUtils.isEquals(atakRoleType, roles[i])) {
                    identityRole.setSelection(i);
                    break;
                }
            }
        }

        identityTeam.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0, View arg1,
                            int position, long id) {
                        if (position < 0)
                            return;

                        final String myTeam = identityTeam.getSelectedItem()
                                .toString();
                        prefs.edit().putString("locationTeam", myTeam).apply();
                    }
                });
        identityRole.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0, View arg1,
                            int position, long id) {
                        if (position < 0)
                            return;

                        final String myRole = identityRole.getSelectedItem()
                                .toString();
                        prefs.edit().putString("atakRoleType", myRole).apply();
                    }
                });

        DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String callsign = String.valueOf(identityCallsign.getText());
                if (!callsign.isEmpty()) {
                    prefs.edit().putString("locationCallsign", callsign)
                            .apply();
                }
                if (which == DialogInterface.BUTTON_NEGATIVE)
                    SettingsActivity.start(DevicePreferenceFragment.class);
            }
        };

        AlertDialog.Builder adb = new AlertDialog.Builder(context);
        adb.setTitle(R.string.identity_title);
        adb.setIcon(R.drawable.my_prefs_settings);
        adb.setView(view);
        adb.setPositiveButton(R.string.done, onClick);
        adb.setNegativeButton(R.string.more, onClick);
        adb.setCancelable(true);
        adb.show();
    }

    /**
     * XXX - This is stupid, just use the proper text view resource for the spinner
     * @see com.atakmap.android.gui.ThemedSpinner
     * @param s Spinner
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
