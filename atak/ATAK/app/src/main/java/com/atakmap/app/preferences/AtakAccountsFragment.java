
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.app.CotPortListActivity.CotPort;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

import java.util.HashMap;
import java.util.Map;

public class AtakAccountsFragment extends AtakPreferenceFragment
        implements Preference.OnPreferenceClickListener {

    private static final String TAG = "AtakAccountsFragment";

    private MapView _mapView;

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                AtakAccountsFragment.class,
                R.string.accounts,
                R.drawable.passphrase);
    }

    public AtakAccountsFragment() {
        super(R.xml.accounts, R.string.accounts);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
        _mapView = MapView.getMapView();

        if (!_initialized) {
            // Register defaults
            register(AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                    "com.atakmap.app", null, null, false, false);
            register(AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                    "com.atakmap.app.v2", null, null, false, false);
            register(AtakAuthenticationCredentials.TYPE_caPassword,
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    getString(R.string.preferences_text234),
                    null, false, false);
            register(AtakAuthenticationCredentials.TYPE_clientPassword,
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    getString(R.string.preferences_text237),
                    null, false, false);
            _initialized = true;
        }

        // TAK servers
        CotPort[] servers = CotMapComponent.getInstance().getServers();
        if (servers != null) {
            for (CotPort server : servers) {
                NetConnectString ncs = NetConnectString.fromString(
                        server.getConnectString());
                if (ncs == null)
                    continue;
                register(AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                        ncs.getHost(), server.getDescription()
                                + " Authentication",
                        null, true);
                register(AtakAuthenticationCredentials.TYPE_caPassword,
                        ncs.getHost(), server.getDescription()
                                + " Truststore Password",
                        null, false);
                register(AtakAuthenticationCredentials.TYPE_clientPassword,
                        ncs.getHost(), server.getDescription()
                                + " Client Password",
                        null, false);
            }
        }

        AtakAuthenticationCredentials[] creds = AtakAuthenticationDatabase
                .getDistinctSitesAndTypes();
        if (creds != null) {
            for (AtakAuthenticationCredentials cred : creds)
                addAccount(cred);
        }
    }

    private void addAccount(AtakAuthenticationCredentials cred) {
        if (cred == null)
            return;
        String key = cred.site + "\\" + cred.type;
        AccountMetadata md = _metadata.get(key);
        if (md == null || md.title == null && md.icon == null)
            return;
        String title = md.title;
        Drawable icon = md.icon;
        if (title == null)
            title = cred.site + " - " + cred.type;
        if (icon == null)
            icon = getResources().getDrawable(
                    R.drawable.passphrase);
        Preference p = new Preference(getActivity());
        p.setTitle(title);
        if (!FileSystemUtils.isEmpty(cred.site) && !FileSystemUtils.isEquals(
                cred.site, cred.type))
            p.setSummary(cred.site);
        p.setKey(key);
        p.setIcon(icon);
        p.setOnPreferenceClickListener(this);
        getPreferenceScreen().addPreference(p);
    }

    @Override
    public boolean onPreferenceClick(final Preference p) {
        String[] siteType = p.getKey().split("\\\\");
        if (siteType.length < 2)
            return false;
        AccountMetadata md = _metadata.get(p.getKey());
        final AtakAuthenticationCredentials cred = AtakAuthenticationDatabase
                .getCredentials(siteType[1], siteType[0]);
        if (cred == null || md == null)
            return false;

        View v = LayoutInflater.from(getActivity()).inflate(
                R.layout.add_account,
                _mapView, false);
        View userLayout = v.findViewById(R.id.username_layout);
        userLayout.setVisibility(md.showUser ? View.VISIBLE : View.GONE);
        final EditText username = v.findViewById(R.id.username);
        username.setText(cred.username);
        final EditText password = v.findViewById(R.id.password);
        password.setText(cred.password);
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
        b.setTitle(p.getTitle());
        b.setIcon(p.getIcon());
        b.setView(v);
        b.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String user = String.valueOf(username.getText());
                        String pass = String.valueOf(password.getText());
                        AtakAuthenticationDatabase.saveCredentials(cred.type,
                                cred.site,
                                user, pass, false);
                    }
                });
        if (md.removable) {
            b.setNeutralButton(getString(R.string.delete_no_space),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            promptRemoveAccount(p, cred);
                        }
                    });
        }
        b.setNegativeButton(getString(R.string.cancel), null);
        b.show();
        return true;
    }

    private void promptRemoveAccount(final Preference p,
            final AtakAuthenticationCredentials cred) {
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
        b.setTitle(getString(R.string.confirmation_dialogue));
        b.setMessage(getString(R.string.delete_account_msg,
                p.getTitle()));
        b.setPositiveButton(getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        AtakAuthenticationDatabase.delete(cred.type, cred.site);
                        getPreferenceScreen().removePreference(p);
                    }
                });
        b.setNegativeButton(getString(R.string.cancel), null);
        b.show();
    }

    private static final Map<String, AccountMetadata> _metadata = new HashMap<>();
    private static boolean _initialized = false;

    private static class AccountMetadata {
        final String title;
        final Drawable icon;
        final boolean showUser, removable;

        AccountMetadata(String title, Drawable icon, boolean showUser,
                boolean removable) {
            this.title = title;
            this.icon = icon;
            this.showUser = showUser;
            this.removable = removable;
        }
    }

    private static void register(String type, String site, String title,
            Drawable icon, boolean showUser, boolean removable) {
        _metadata.put(site + "\\" + type, new AccountMetadata(title, icon,
                showUser, removable));
    }

    public static void register(String type, String site, String title,
            Drawable icon, boolean showUser) {
        register(type, site, title, icon, showUser, true);
    }

    public static void register(String type, String title, Drawable icon,
            boolean showUser) {
        register(type, type, title, icon, showUser);
    }
}
