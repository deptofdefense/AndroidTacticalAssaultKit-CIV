
package com.atakmap.android.radiolibrary;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Toast;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.atakmap.coremap.locale.LocaleUtil;

public class RecentlyUsed {

    public static final String TAG = "RecentlyUsed";

    private static final int SIZE = 3;

    private final List<Connection> list = new ArrayList<>();
    private final SharedPreferences _prefs;

    private static class Connection {
        final public double frequency;
        final public String moduleType;
        final public int channel;

        public Connection(double frequency, String moduleType,
                int channel) {
            this.frequency = frequency;
            this.moduleType = moduleType;
            this.channel = channel;
        }

        public static Connection parse(String connectionString) {
            String[] vals = connectionString.split(",");
            try {
                final double d = Double.parseDouble(vals[0]);
                final String mt = vals[1];
                final int c = Integer.parseInt(vals[2]);
                return new Connection(d, mt, c);
            } catch (Exception e) {
                Log.d(TAG, "line invalid: " + connectionString);

            }
            return null;

        }

        private String serialize() {
            return frequency + "," + moduleType + "," + channel;
        }

        @Override
        public String toString() {
            if (frequency > 0) {
                DecimalFormat df = LocaleUtil.getDecimalFormat("#.000");
                return df.format(frequency / 1000d) + " GHz";
            } else {
                return moduleType
                        + " "
                        + String.format(LocaleUtil.getCurrent(), "%03d",
                                channel);
            }

        }

        @Override
        public boolean equals(Object o) {

            if (o instanceof Connection) {
                Connection r = (Connection) o;
                return this.frequency == r.frequency &&
                        this.channel == r.channel &&
                        Objects.equals(this.moduleType, r.moduleType);
            }

            return false;

        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(frequency);
            result = (int) (temp ^ (temp >>> 32));
            result = 31 * result
                    + ((moduleType == null) ? 0 : moduleType.hashCode());
            result = 31 * result + channel;
            return result;
        }
    }

    public RecentlyUsed(Context con) {
        _prefs = PreferenceManager.getDefaultSharedPreferences(con);
        Set<String> connections = _prefs
                .getStringSet("roverRecentlyUsed", null);
        if (connections != null) {
            for (String connection : connections) {
                Connection c = Connection.parse(connection);
                if (c != null)
                    list.add(0, c);
            }
        }

    }

    /**
     * Inserts the previous connection registered into the list
     */
    synchronized public void commit(double frequency,
            String moduleType,
            int channel) {

        final Connection pending;

        if (moduleType == null)
            return;

        if (channel > 0 && !moduleType.equalsIgnoreCase(RoverInterface.NONE)) {
            pending = new Connection(-1, moduleType, channel);
        } else {
            pending = new Connection(frequency, null, -1);
        }

        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i).equals(pending)) {
                Log.d(TAG, "pending connection already exists in list: "
                        + pending);
                return;
            }
        }

        if (list.size() > SIZE) {
            list.remove(list.size() - 1);
        }
        Log.d(TAG, "added pending connection to the list: " + pending);
        list.add(0, pending);

        Set<String> connections = new HashSet<>();
        for (Connection c : list) {
            connections.add(c.serialize());
        }

        _prefs.edit().putStringSet("roverRecentlyUsed", connections).apply();
    }

    synchronized public void showDialog(final RoverInterface r, final View v,
            final Context con) {
        if (list.size() == 0) {
            v.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(con, R.string.radio_no_previous_connections,
                            Toast.LENGTH_SHORT)
                            .show();

                }
            });
            return;
        }
        final AlertDialog.Builder adb = new AlertDialog.Builder(con);
        final String[] arr = new String[list.size()];
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = list.get(i).toString();
        }
        adb.setSingleChoiceItems(arr, -1, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface d, final int n) {
                d.dismiss();
                final Connection c = list.get(n);
                Log.d(TAG, "selected: " + c);

                if (c.frequency > 0) {
                    r.setReceiverFrequency((int) (c.frequency * 1000));
                } else {
                    r.setChannel(c.moduleType, c.channel);
                }
                Toast.makeText(
                        con,
                        String.format(con
                                .getString(R.string.radio_changing_connection),
                                c),
                        Toast.LENGTH_SHORT)
                        .show();
            }

        });
        adb.setNegativeButton(R.string.cancel, null);
        adb.setTitle(R.string.recently_used);

        v.post(new Runnable() {
            @Override
            public void run() {
                adb.show();
            }
        });

    }

}
