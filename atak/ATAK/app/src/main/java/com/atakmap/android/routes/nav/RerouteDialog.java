
package com.atakmap.android.routes.nav;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.RoutePlannerInterface;
import com.atakmap.android.routes.RoutePlannerManager;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Dialog class to present the user with re-routing options upon them requesting to start navigation.
 */
public class RerouteDialog implements Dialog.OnClickListener {
    public static final String TAG = "RerouteDialog";

    public static final String PREF_IS_REROUTE_ACTIVE_KEY = "com.atakmap.android.routes.nav.RerouteDialog.is_reroute_active";
    public static final String PREF_ACTIVE_REROUTE_ENGINE = "com.atakmap.android.routes.nav.RerouteDialog.active_reroute_engine";

    private final Activity activity;
    private final MapView mapView;
    private final Context context;

    private Spinner plannerSpinner;
    private final List<String> plannerNames;
    private final List<Map.Entry<String, RoutePlannerInterface>> routePlanners;
    private LinearLayout optionsView;
    private final SharedPreferences prefs;

    private AlertDialog dlg;

    private Dialog.OnClickListener listener;

    private static final Comparator<Map.Entry<String, RoutePlannerInterface>> ALPHA_SORT = new Comparator<Map.Entry<String, RoutePlannerInterface>>() {
        @Override
        public int compare(Map.Entry<String, RoutePlannerInterface> lhs,
                Map.Entry<String, RoutePlannerInterface> rhs) {
            return lhs.getValue().getDescriptiveName()
                    .compareTo(rhs.getValue().getDescriptiveName());
        }
    };

    public RerouteDialog(Activity activity, MapView mapView,
            RoutePlannerManager rpm) {
        this.activity = activity;
        this.mapView = mapView;

        context = mapView.getContext();

        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        routePlanners = new ArrayList<>(rpm.getReroutePlanners());
        Collections.sort(routePlanners, ALPHA_SORT);
        plannerNames = extractNames(routePlanners);
    }

    /**
     * Creates and shows the underlying AlertDialog.
     */
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // Inflate the view
        View v = LayoutInflater.from(context).inflate(
                R.layout.route_navigation_dialog, mapView, false);
        builder.setView(v);
        builder.setTitle("Begin Navigation");
        builder.setPositiveButton("GO!", RerouteDialog.this);
        builder.setNegativeButton("Cancel", RerouteDialog.this);

        dlg = builder.create();

        // Get view components
        plannerSpinner = v.findViewById(R.id.route_plan_method);
        optionsView = v.findViewById(R.id.route_plan_options);

        // Setup the spinner adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, plannerNames);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        plannerSpinner.setAdapter(adapter);
        plannerSpinner.setOnItemSelectedListener(plannerSpinnerListener);

        if (plannerNames.isEmpty()) {
            throw new IllegalStateException(
                    "Re-routing cannot be active when there are no re-route capable route planners registered.");
        }

        dlg.show();
    }

    /**
     * Dismisses the dialog.
     */
    public void dismiss() {
        if (dlg != null) {
            dlg.dismiss();
        }
    }

    /**
     * Sets the listener that will receive events from the dialog.
     * @param listener the listener to receive events from the dialog
     */
    public void setListener(
            Dialog.OnClickListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (listener != null) {
            listener.onClick(dialog, which);
        }
    }

    private final AdapterView.OnItemSelectedListener plannerSpinnerListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                int position, long id) {
            // This is needed to keep the text color of the spinner correct
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(Color.WHITE);
            }

            // Clear out the options view section
            optionsView.removeAllViews();

            LinearLayout.LayoutParams lpView = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

            // Identify the planner we are after
            String plannerName = (String) parent
                    .getItemAtPosition(position);

            Map.Entry<String, RoutePlannerInterface> plannerEntry = getPlannerEntry(
                    plannerName);

            prefs.edit().putString(PREF_ACTIVE_REROUTE_ENGINE,
                    plannerEntry.getKey()).apply();

            optionsView.addView(
                    plannerEntry.getValue().getOptionsView(dlg),
                    lpView);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    private Map.Entry<String, RoutePlannerInterface> getPlannerEntry(
            String descriptivePlannerName) {
        return routePlanners.get(plannerNames.indexOf(descriptivePlannerName));
    }

    private List<String> extractNames(
            List<Map.Entry<String, RoutePlannerInterface>> planners) {
        List<String> names = new ArrayList<>(planners.size());

        for (Map.Entry<String, RoutePlannerInterface> entry : planners) {
            names.add(entry.getValue().getDescriptiveName());
        }

        return names;
    }
}
