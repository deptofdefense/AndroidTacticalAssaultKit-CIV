
package com.atakmap.comms.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.core.app.NavUtils;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;

import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.net.CertificateEnrollmentClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

public abstract class CotPortListActivity extends MetricActivity {

    public static final String TAG = "CotPortListActivity";
    private boolean ascending = true;

    /**
     * Class that encapsulates the relevant attributes of a CotNetPort for display. This is only the
     * UI representation of the port; the actual port is an instance of a class that inherits from
     * AbstractPort.
     * 
     * @deprecated Use {@link TAKServer} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public static class CotPort extends TAKServer {
        public CotPort(Bundle bundle) throws IllegalArgumentException {
            super(bundle);
        }

        public CotPort(TAKServer other) {
            super(other);
        }
    }

    /**
     * Portrait or Landscape.
     */
    public boolean isPortrait() {
        return (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    /**
     * Custom adapter for displaying a list of CotPort objects.
     * 
     * 
     */
    private static class CotPortAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private List<CotPort> portDestinations;
        private final CotPortListActivity listActivity;

        CotPortAdapter(CotPortListActivity listActivity,
                ArrayList<CotPort> list) {
            super();
            this.inflater = LayoutInflater.from(listActivity);
            this.portDestinations = list;
            this.listActivity = listActivity;
        }

        public void refresh(ArrayList<CotPort> list) {
            this.portDestinations = list;

            try {
                listActivity.sort();
            } catch (Exception e) {
                Log.e(TAG, "exception sorting portDestinations!", e);
            }

            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return this.portDestinations.size();
        }

        @Override
        public Object getItem(int postion) {
            return portDestinations.get(postion);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                if (listActivity.isPortrait()) {
                    convertView = inflater.inflate(R.layout.manage_ports_port,
                            null);
                } else {
                    convertView = inflater.inflate(R.layout.manage_ports_land,
                            null);
                }
                holder = new ViewHolder();
                holder.checkbox = convertView
                        .findViewById(R.id.manage_ports_checkbox);
                holder.checkbox.setClickable(false); // Checkbox is toggled by selecting the list
                                                     // item
                holder.checkbox.setFocusable(false); // Necessary so CheckBox does not intercept
                                                     // clicks
                holder.connectString = convertView
                        .findViewById(R.id.manage_ports_connection_string);
                holder.connectVersion = convertView
                        .findViewById(R.id.manage_ports_connection_version);
                holder.description = convertView
                        .findViewById(R.id.manage_ports_description);
                holder.error = convertView
                        .findViewById(R.id.manage_ports_error_string);
                holder.connected = convertView
                        .findViewById(R.id.manage_ports_connected);

                holder.deleteButton = convertView
                        .findViewById(R.id.manage_ports_delete);
                holder.editButton = convertView
                        .findViewById(R.id.manage_ports_edit);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final CotPort destination = portDestinations
                    .get(position);
            holder.connectString.setText(destination.getConnectString());
            if (destination.isCompressed()) {
                holder.description.setText(destination.getDescription()
                        + "; compressed");
            } else {
                holder.description.setText(destination.getDescription());
            }

            String version = destination.getServerVersion();
            if (!FileSystemUtils.isEmpty(version)) {
                holder.connectVersion.setVisibility(View.VISIBLE);
                holder.connectVersion.setText(version);
            } else {
                holder.connectVersion.setVisibility(View.GONE);
            }

            Log.d(TAG,
                    "holder is getting refreshed: " + destination.isEnabled());
            holder.checkbox.setChecked(destination.isEnabled());
            holder.checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean newEnabledState = !destination.isEnabled();
                    if (newEnabledState) {
                        Log.v(TAG, "Enabling " + destination.getDescription());
                    } else {
                        Log.v(TAG, "Disabling " + destination.getDescription());
                    }
                    try {
                        destination.setEnabled(newEnabledState);
                        listActivity.enabledFlagSet(destination);
                        if (v instanceof CheckBox)
                            ((CheckBox) v).setChecked(newEnabledState);
                    } catch (Exception e) {
                        Toast.makeText(
                                listActivity,
                                "invalid configuration, please delete it",
                                Toast.LENGTH_LONG)
                                .show();
                        Log.e(TAG, "error", e);
                    }
                }
            });
            holder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Delete button pressed for " + destination);
                    listActivity.showDeleteDialog(destination);
                }
            });
            holder.editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Edit button pressed for " + v);
                    listActivity.showEdit(destination);
                }
            });

            if (destination.isConnected())
                holder.connected
                        .setImageResource(
                                ATAKConstants.getServerConnection(true));
            else
                holder.connected
                        .setImageResource(
                                ATAKConstants.getServerConnection(false));

            String error = destination.getErrorString();
            if (!FileSystemUtils.isEmpty(error)) {
                holder.error.setVisibility(View.VISIBLE);
                holder.error.setText(error);
            } else {
                holder.error.setVisibility(View.GONE);
            }

            if (listActivity.displayConnectionStatus()) {
                holder.connected.setVisibility(ImageView.VISIBLE);
            } else {
                holder.connected.setVisibility(ImageView.GONE);
            }

            return convertView;
        }
    }

    /**
     * Class to locally cache the sub-views of R.layout.manage_ports. Use an instance of this as a
     * tag on a View to associate that View with its sub-views. This allows us to eliminate repeated
     * calls to findViewById.
     * 
     * 
     */
    protected static class ViewHolder {
        ImageView connected;
        TextView connectString;
        TextView connectVersion;
        TextView description;
        CheckBox checkbox;
        ImageButton deleteButton;
        ImageButton editButton;
        TextView error;
    }

    protected Bundle _showDialogData;

    protected void _myShowDialog(int id, Bundle data) {
        _showDialogData = data;
        showDialog(id);
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(null);

        AtakPreferenceFragment.setOrientation(this);

        setContentView(R.layout.net_list_layout);

        ListView listView = findViewById(R.id.netlist);

        _portAdapter = new CotPortAdapter(this, _portList);
        listView.setAdapter(_portAdapter);

        listView.setItemsCanFocus(false);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        AtakPreferenceFragment.setOrientation(this);
        super.onResume();
    }

    private void showDeleteDialog(final CotPort cotPort) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.remove_connection);
        builder.setMessage(this.getString(R.string.are_you_sure_remove)
                + cotPort.getDescription()
                + this.getString(R.string.question_mark_symbol));
        builder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        removeFromRemote(cotPort.getConnectString());
                        _removeItem(cotPort);
                    }
                });
        builder.setNegativeButton(R.string.no, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private CotPort currentlyEditingCotPort = null;

    private void showEdit(final CotPort cotPort) {
        // Set currentlyEditingCotPort so that we know what to lookup in onActivityResult
        currentlyEditingCotPort = cotPort;

        Intent editNetInfoIntent = new Intent(this, AddNetInfoActivity.class);
        NetConnectString connectString = NetConnectString.fromString(cotPort
                .getConnectString());
        editNetInfoIntent.putExtra("type", getPortType());
        editNetInfoIntent.putExtra("protocol", connectString.getProto());
        editNetInfoIntent.putExtra("host", connectString.getHost());
        editNetInfoIntent.putExtra("port", connectString.getPort());
        editNetInfoIntent.putExtra("description", cotPort.getDescription());
        editNetInfoIntent.putExtra("compress", cotPort.isCompressed());
        editNetInfoIntent.putExtra("useAuth", cotPort.isUsingAuth());
        editNetInfoIntent.putExtra("username", cotPort.getUsername());
        editNetInfoIntent.putExtra("password", cotPort.getPassword());
        editNetInfoIntent.putExtra("cacheCreds", cotPort.getCacheCredentials());
        editNetInfoIntent.putExtra("enrollForCertificateWithTrust",
                cotPort.enrollForCert());
        startActivityForResult(editNetInfoIntent, _REQUEST_EDIT_PORT);
        // look in onActivityResult for _REQUEST_EDIT_PORT
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        if (requestCode == _REQUEST_EDIT_PORT) {
            if (resultCode == Activity.RESULT_OK)
                saveConnection(data);
            currentlyEditingCotPort = null;
        }
    }

    private void saveConnection(Intent data) {
        //
        // cache off certs and credentials
        //

        if (currentlyEditingCotPort == null ||
                currentlyEditingCotPort.getConnectString() == null)
            return;

        NetConnectString oldNcs = NetConnectString.fromString(
                currentlyEditingCotPort.getConnectString());
        String oldServer = oldNcs.getHost();

        byte[] clientCert = AtakCertificateDatabase
                .getCertificateForServerAndPort(
                        AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                        oldServer, oldNcs.getPort());
        byte[] caCert = AtakCertificateDatabase.getCertificateForServerAndPort(
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                oldServer, oldNcs.getPort());
        AtakAuthenticationCredentials clientCertPw = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_clientPassword,
                        oldServer);
        AtakAuthenticationCredentials caCertPw = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_caPassword,
                        oldServer);

        AtakAuthenticationCredentials creds = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                        oldServer);

        // Remove old one (from CotService)
        removeFromRemote(
                currentlyEditingCotPort.getConnectString());
        // Remove old one (from my GUI)
        _removeItem(currentlyEditingCotPort);

        // Get data to add new one
        Bundle inputData = data.getBundleExtra("data");
        String connectString = data
                .getStringExtra(CotPort.CONNECT_STRING_KEY);
        inputData.putString(CotPort.CONNECT_STRING_KEY,
                connectString);
        CotPort newConnection = new CotPort(inputData);

        //
        // restore certs and credentials
        //
        NetConnectString newNcs = NetConnectString.fromString(
                connectString);
        String newServer = newNcs.getHost();

        if (clientCert != null) {
            AtakCertificateDatabase.saveCertificateForServerAndPort(
                    AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                    newServer, newNcs.getPort(), clientCert);
        }

        if (caCert != null) {
            AtakCertificateDatabase.saveCertificateForServerAndPort(
                    AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                    newServer, newNcs.getPort(), caCert);
        }

        if (clientCertPw != null && clientCertPw.password != null
                && clientCertPw.password.length() != 0) {
            AtakAuthenticationDatabase.saveCredentials(
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    newServer, "", clientCertPw.password, false);
        }

        if (caCertPw != null && caCertPw.password != null
                && caCertPw.password.length() != 0) {
            AtakAuthenticationDatabase.saveCredentials(
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    newServer, "", caCertPw.password, false);
        }

        // we don't need to restore credentials here, they are saved to new server
        // downstream from to addToRemote within CotService._saveInputOutput

        // Add new one (to GUI)
        _putItem(newConnection);
        // Add new one (to CotService)
        addToRemote(connectString, inputData);

        //For all known implementations, enabledFlagSet is equivalent to addToRemote() above
        //and results in duplicate call to CoTServer.addStreaming() / _saveInputOutput()
        //enabledFlagSet(newConnection);

        // cleanup any dangling certs if the server changed
        if (!newServer.equalsIgnoreCase(oldServer)) {
            AtakCertificateDatabase.deleteCertificateForServerAndPort(
                    AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                    oldServer, oldNcs.getPort());

            AtakCertificateDatabase.deleteCertificateForServerAndPort(
                    AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                    oldServer, oldNcs.getPort());

            AtakAuthenticationDatabase.delete(
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    oldServer);

            AtakAuthenticationDatabase.delete(
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    oldServer);
            AtakAuthenticationDatabase.delete(
                    AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                    oldServer);
        }

        enrollForCertificate(inputData, creds);
    }

    protected void enrollForCertificate(Bundle inputData,
            AtakAuthenticationCredentials creds) {

        boolean enrollForCertificateWithTrust = inputData
                .getBoolean(CotPort.ENROLL_FOR_CERT_KEY, false);
        if (!enrollForCertificateWithTrust) {
            return;
        }

        String connectString = inputData.getString(CotPort.CONNECT_STRING_KEY);
        NetConnectString newNcs = NetConnectString.fromString(connectString);
        String newServer = newNcs.getHost();

        // Enroll new credentials
        String username = inputData.getString(CotPort.USERNAME_KEY);
        String password = inputData.getString(CotPort.PASSWORD_KEY);
        String cacheCreds = inputData.getString(CotPort.CACHECREDS_KEY);
        String description = inputData.getString(CotPort.DESCRIPTION_KEY);
        Long expiration = inputData.getLong(CotPort.EXPIRATION_KEY);

        if (creds == null
                || !FileSystemUtils.isEquals(username, creds.username)
                || !FileSystemUtils.isEquals(password, creds.password)
                || AtakCertificateDatabase
                        .getCertificateForServerAndPort(
                                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                                newServer, newNcs.getPort()) == null) {
            CertificateEnrollmentClient.getInstance().enroll(this,
                    description, connectString, cacheCreds, expiration, null,
                    true);
        }
    }

    @Override
    protected void onDestroy() {
        if (_remote != null) {
            _remote.disconnect();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.network_connections_menu, menu);

        final MenuItem item = menu.findItem(R.id.network_connections_menu_sort);
        if (item != null) {
            item.setIcon(ContextCompat.getDrawable(this,
                    ascending ? R.drawable.alpha_sort
                            : R.drawable.alpha_sort_desc));
        }
        return true;
    }

    private void sort() {
        try {
            if (_portList == null) {
                Log.e(TAG, "_portList! is null in sort!");
                return;
            }

            Collections.sort(_portList, new Comparator<CotPort>() {
                @Override
                public int compare(CotPort lhs, CotPort rhs) {
                    if (lhs == null || rhs == null ||
                            lhs.getDescription() == null
                            || rhs.getDescription() == null) {
                        Log.e(TAG, "null CotPort or description in compare!");
                        return 0;
                    }

                    return ascending
                            ? lhs.getDescription()
                                    .compareToIgnoreCase(rhs.getDescription())
                            : rhs.getDescription()
                                    .compareToIgnoreCase(lhs.getDescription());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "exception in sort!", e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            try {
                NavUtils.navigateUpFromSameTask(this);
            } catch (IllegalArgumentException iae) {
                Log.d(TAG, "error occurred", iae);
                finish();
            }
            return true;
        } else if (id == R.id.network_connections_menu_sort) {
            Log.d(TAG, "network_connections_menu_sort selected");
            ascending = !ascending;
            item.setIcon(ContextCompat.getDrawable(this,
                    ascending ? R.drawable.alpha_sort
                            : R.drawable.alpha_sort_desc));
            sort();
            _portAdapter.refresh(_portList);
            return true;
        }

        return false;
    }

    protected void removeAllCotPorts() {
        // for w/e reason just doing a for loop once doesn't get every item
        while (_portAdapter.getCount() > 0) {
            CotPort item = (CotPort) _portAdapter.getItem(0);
            removeFromRemote(item.getConnectString());
            _removeItem(item);
        }
    }

    protected final static int _REQUEST_EDIT_PORT = 3;
    protected final static int _REMOVE_ALL_DIALOG = 2;
    protected final static int _REMOVE_ITEM_DIALOG = 1;
    protected final static int _REQUEST_ADD_PORT = 0;

    protected final ArrayList<CotPort> _portList = new ArrayList<>();
    protected CotPortAdapter _portAdapter;
    protected CotServiceRemote _remote;

    abstract String getPortType();

    abstract void enabledFlagSet(CotPort port);

    abstract void addToRemote(String connectString, Bundle data);

    abstract void removeFromRemote(String connectString);

    protected final CotServiceRemote.ConnectionListener _connectionListener = new CotServiceRemote.ConnectionListener() {

        @Override
        public void onCotServiceDisconnected() {

        }

        @Override
        public void onCotServiceConnected(Bundle fullServiceState) {
            Log.v(TAG, "onCotServiceConnected");
            Bundle[] ports = (Bundle[]) fullServiceState
                    .getParcelableArray(getPortType());
            if (ports != null) {
                for (Bundle port : ports) {
                    try {
                        _putItem(new CotPort(port));
                    } catch (IllegalArgumentException iaex) {
                        Log.e(TAG, iaex.getMessage());
                    }
                }
            }
            _portAdapter.refresh(_portList);
        }
    };

    protected final CotServiceRemote.OutputsChangedListener _outputsChangedListener = new CotServiceRemote.OutputsChangedListener() {

        @Override
        public void onCotOutputRemoved(Bundle descBundle) {
            // Mark the port disabled but do not remove it from the list
            CotPort port = new CotPort(descBundle);
            int removedIndex = _portList.indexOf(port);
            if (removedIndex >= 0) {
                Log.v(TAG,
                        "Received REMOVE message for "
                                + port.getDescription());
                //_list.get(removedIndex).setEnabled(false);
                _portList.get(removedIndex).setConnected(false);
                _portAdapter.refresh(_portList);
            }
        }

        @Override
        public void onCotOutputUpdated(Bundle descBundle) {
            Log.v(TAG,
                    "Received ADD message for "
                            + descBundle
                                    .getString(CotPort.DESCRIPTION_KEY)
                            + ": enabled="
                            + descBundle.getBoolean(
                                    CotPort.ENABLED_KEY, true)
                            + ": connected="
                            + descBundle.getBoolean(
                                    CotPort.CONNECTED_KEY, false));
            _putItem(new CotPort(descBundle));
        }
    };

    protected final CotServiceRemote.InputsChangedListener _inputsChangedListener = new CotServiceRemote.InputsChangedListener() {
        @Override
        public void onCotInputRemoved(Bundle descBundle) {
            // Mark the port disabled but do not remove it from the list
            CotPort port = new CotPort(descBundle);
            int removedIndex = _portList.indexOf(port);
            if (removedIndex >= 0) {
                Log.v(TAG,
                        "Received REMOVE message for " + port.getDescription());
                _portList.get(removedIndex).setEnabled(false);
                _portAdapter.refresh(_portList);
            }
        }

        @Override
        public void onCotInputAdded(Bundle descBundle) {
            Log.v(TAG,
                    "Received ADD message for "
                            + descBundle.getString(CotPort.DESCRIPTION_KEY)
                            + ": enabled="
                            + descBundle.getBoolean(CotPort.ENABLED_KEY, true));
            _putItem(new CotPort(descBundle));
        }
    };

    /**
     * Add an item to the list of ports, or updates its enabled state if it is already present
     * 
     * @param port Port to add or update
     * @return true if the port was added, false if not added (because already present)
     */
    protected boolean _putItem(CotPort port) {
        boolean added = false;
        int indexToAdd = _portList.indexOf(port);
        if (indexToAdd < 0) {
            // Append to list
            _portList.add(port);
            added = true;
            // Log.v(TAG, "Adding port " + port.getDescription() + ", enabled= " +
            // port.isEnabled());
        } else {
            // Replace in list
            // Log.v(TAG, "Replacing port " + port.getDescription() + ", enabled= " +
            // port.isEnabled());
            ListIterator<CotPort> itr = _portList.listIterator(indexToAdd);
            itr.next();
            itr.set(port);
        }
        _portAdapter.refresh(_portList);
        return added;
    }

    /**
     * Remove an port from this Activity's list of displayed ports and tell the remote service to
     * stop using it.
     * 
     * @param port port to remove
     * @return true if the port was removed, false if no match was found
     */
    protected boolean _removeItem(CotPort port) {
        // Log.v(TAG, "Removing " + port.getDescription());
        boolean removed = false;
        int indexToRemove = _portList.indexOf(port);
        if (indexToRemove >= 0) {
            _portList.remove(indexToRemove);
            removed = true;
            _portAdapter.refresh(_portList);
        }
        return removed;
    }

    protected boolean displayConnectionStatus() {
        return false;
    }
}
