
package com.atakmap.android.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.app.SettingsActivity;
import com.atakmap.app.preferences.NetworkConnectionPreferenceFragment;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.comms.app.CotPortListActivity.CotPort;
import com.atakmap.comms.TAKServer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.CotServiceRemote;

/**
 * Display a list of server for user to select one
 *
 * 
 */
public class ServerListDialog {
    private static final String TAG = "ServerListDialog";

    /**
     * Callback for when a server is selected
     */
    public interface Callback {
        /**
         * TAK server selected from the list
         *
         * @param server TAK server or null if no servers available/selected
         */
        void onSelected(TAKServer server);
    }

    private final Context _context;

    public ServerListDialog(MapView mapView) {
        _context = mapView.getContext();
    }

    /**
     * Display dialog to select a server, and pass it to a callback
     *
     * @param title Dialog title
     * @param servers List of servers
     * @param callback Selection callback
     */
    public void show(String title, final TAKServer[] servers,
            final Callback callback) {
        if (servers == null || servers.length < 1) {
            Log.w(TAG, "No servers provided");
            promptNetworkSettings();
            if (callback != null)
                callback.onSelected(null);
            return;
        }

        final ServerListArrayAdapter adapter = new ServerListArrayAdapter(
                _context, R.layout.serverlist_select_item, servers);

        if (FileSystemUtils.isEmpty(title))
            title = _context.getString(R.string.video_text15);
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setIcon(R.drawable.ic_menu_network);
        b.setTitle(title);
        b.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                TAKServer server = adapter.getItem(i);
                if (server == null) {
                    Log.w(TAG,
                            "Failed to search selected server, no server selected");
                    if (callback != null)
                        callback.onSelected(null);
                    return;
                }
                Log.d(TAG, "Selected server: " + server);
                if (callback != null)
                    callback.onSelected(server);
            }
        });
        b.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (callback != null)
                            callback.onSelected(null);
                    }
                });

        final AlertDialog bd = b.create();
        bd.show();
    }

    public void show(String title, Callback callback) {
        show(title, TAKServerListener.getInstance().getServers(), callback);
    }

    public void promptNetworkSettings() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setIcon(R.drawable.ic_server_error);
        b.setTitle(_context.getString(
                R.string.mission_package_configure_tak_server,
                _context.getString(R.string.MARTI_sync_server)));
        b.setMessage(
                R.string.mission_package_not_connected_to_server_check_network_settings);
        b.setPositiveButton(R.string.settings,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        SettingsActivity.start(
                                NetworkConnectionPreferenceFragment.class);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    /**
     * @deprecated Use above method instead
     */
    @Deprecated
    public static void selectServer(Context context, String message,
            CotPort[] servers, final Callback callback) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;
        ServerListDialog dialog = new ServerListDialog(mv);
        dialog.show(message, servers, new Callback() {
            @Override
            public void onSelected(TAKServer server) {
                CotPort cp = null;
                if (server != null)
                    cp = new CotPort(server);
                if (callback != null)
                    callback.onSelected(cp);
            }
        });
    }

    public static String getBaseUrl(TAKServer server) {
        return getBaseUrl(server.getConnectString());
    }

    public static String getBaseUrl(String server) {
        return getBaseUrl(NetConnectString.fromString(server));
    }

    public static String getBaseUrl(NetConnectString ncs) {
        if (ncs == null) {
            Log.d(TAG, "Invalid stream info");
            return "";
        }

        if (CotServiceRemote.Proto.ssl.toString().equals(ncs.getProto()))
            return "https://" + ncs.getHost();
        else
            return "http://" + ncs.getHost();
    }

    private static class ServerListArrayAdapter extends
            ArrayAdapter<TAKServer> {

        private final Context context;
        private final int layoutResourceId;
        private final TAKServer[] data;

        ServerListArrayAdapter(Context context, int layoutResourceId,
                TAKServer[] data) {
            super(context, layoutResourceId, data);
            this.layoutResourceId = layoutResourceId;
            this.context = context;
            this.data = data;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView,
                @NonNull ViewGroup parent) {
            View row = convertView;
            ServerHolder holder;

            if (row == null) {
                LayoutInflater inflater = ((Activity) context)
                        .getLayoutInflater();
                row = inflater.inflate(layoutResourceId, parent, false);

                holder = new ServerHolder();
                holder.imgIcon = row
                        .findViewById(R.id.serverlist_select_icon);
                holder.txtLabel = row
                        .findViewById(R.id.serverlist_select_label);
                holder.txtUrl = row
                        .findViewById(R.id.serverlist_select_url);

                row.setTag(holder);
            } else {
                holder = (ServerHolder) row.getTag();
            }

            TAKServer server = data[position];
            holder.txtUrl.setText(server.getConnectString());
            holder.txtLabel.setText(server.getDescription());
            if (server.isConnected()) {
                holder.imgIcon
                        .setImageResource(
                                ATAKConstants.getServerConnection(true));
            } else {
                holder.imgIcon
                        .setImageResource(
                                ATAKConstants.getServerConnection(false));
            }

            return row;
        }

        static class ServerHolder {
            ImageView imgIcon;
            TextView txtLabel;
            TextView txtUrl;
        }
    }
}
