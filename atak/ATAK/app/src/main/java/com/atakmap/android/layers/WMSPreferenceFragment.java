
package com.atakmap.android.layers;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.app.system.ResourceUtil;

/*
 */

public class WMSPreferenceFragment extends AtakPreferenceFragment implements
        Preference.OnPreferenceClickListener {
    Context context;
    SharedPreferences _prefs;

    public WMSPreferenceFragment() {
        super(R.xml.wms_preferences, R.string.wms_preferences_summary);

    }

    @Override
    public boolean onPreferenceClick(Preference preference) {

        new AlertDialog.Builder(context)
                .setTitle(R.string.redeploy)
                .setMessage(
                        R.string.sure_next_restart)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int id) {
                                _prefs.edit()
                                        .putBoolean(
                                                "wms_deployed",
                                                false)
                                        .apply();

                            }
                        })
                .create().show();

        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(getResourceID());

        context = getActivity();
        _prefs = PreferenceManager.getDefaultSharedPreferences(context);

        Preference numberOfSpis = findPreference(
                "prefs_layer_grg_map_interaction");
        numberOfSpis.setTitle(
                ResourceUtil.getResource(R.string.civ_enable_grg_interact,
                        R.string.enable_grg_interact));
        numberOfSpis.setSummary(
                ResourceUtil.getResource(R.string.civ_enable_grg_interact_summ,
                        R.string.enable_grg_interact_summ));

        Preference redeploy = findPreference("redeploywms");
        redeploy
                .setOnPreferenceClickListener(this);

        Preference timeout = findPreference("wmsconnecttimeout");
        timeout.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        final ArrayList<String> opts = new ArrayList<>();
                        opts.add(String.valueOf(3));
                        opts.add(String.valueOf(5));
                        opts.add(String.valueOf(10));
                        opts.add(String.valueOf(15));
                        opts.add(String.valueOf(30));

                        final int sel = _prefs.getInt("wms_connect_timeout",
                                3000) / 1000;
                        if (!opts.contains(String.valueOf(sel)))
                            opts.add(0, String.valueOf(sel));

                        new AlertDialog.Builder(context)
                                .setTitle("WMS Connect Timeout (Seconds)")
                                .setCancelable(true)
                                .setSingleChoiceItems(
                                        opts.toArray(new String[0]),
                                        opts.indexOf(String.valueOf(sel)),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int id) {
                                                _prefs.edit().putInt(
                                                        "wms_connect_timeout",
                                                        Integer.parseInt(
                                                                opts.get(
                                                                        id))
                                                                * 1000)
                                                        .apply();

                                                Toast.makeText(
                                                        context,
                                                        R.string.next_restart,
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                            }
                                        })
                                .create().show();

                        return false;
                    }
                });

    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

}
