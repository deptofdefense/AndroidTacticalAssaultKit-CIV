
package com.atakmap.app.preferences;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class ReportingPreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "ReportingPreferenceFragment";

    private static ReportingPreferenceFragment _instance;

    private PreferenceCategory publishCategory;
    private ListPreference reportingStrategyPref;
    private String _previousReportingStrategy = "";
    private PanEditTextPreference constantReportingRateUnreliablePref;
    private PanEditTextPreference constantReportingRateReliablePref;

    private PanEditTextPreference dynamicReportingRateStationaryUnreliablePref;
    private PanEditTextPreference dynamicReportingRateStationaryReliablePref;
    private PanEditTextPreference dynamicReportingRateMinUnreliablePref;
    private PanEditTextPreference dynamicReportingRateMinReliablePref;
    private PanEditTextPreference dynamicReportingRateMaxUnreliablePref;
    private PanEditTextPreference dynamicReportingRateMaxReliablePref;

    private PreferenceCategory alternateContactCategory;
    private ListPreference saSipAddressAssignment;
    private PanEditTextPreference saSipAddress;
    private String _previousSaSipAddressAssignment = "";

    public synchronized static ReportingPreferenceFragment getInstance() {
        if (_instance == null) {
            _instance = new ReportingPreferenceFragment();
        }
        return _instance;
    }

    public ReportingPreferenceFragment() {
        super(R.xml.reporting_preferences, R.string.reportingPreferences);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                ReportingPreferenceFragment.class,
                R.string.reportingPreferences,
                R.drawable.ic_self_pref);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.myPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        getActivity().setResult(Activity.RESULT_CANCELED, null);

        publishCategory = (PreferenceCategory) findPreference(
                "publishCategory");
        reportingStrategyPref = (ListPreference) findPreference(
                "locationReportingStrategy");
        constantReportingRateUnreliablePref = (PanEditTextPreference) findPreference(
                "constantReportingRateUnreliable");
        constantReportingRateUnreliablePref.checkValidInteger();
        constantReportingRateReliablePref = (PanEditTextPreference) findPreference(
                "constantReportingRateReliable");
        constantReportingRateReliablePref.checkValidInteger();

        dynamicReportingRateStationaryUnreliablePref = (PanEditTextPreference) findPreference(
                "dynamicReportingRateStationaryUnreliable");
        dynamicReportingRateStationaryUnreliablePref.checkValidInteger();
        dynamicReportingRateStationaryReliablePref = (PanEditTextPreference) findPreference(
                "dynamicReportingRateStationaryReliable");
        dynamicReportingRateStationaryReliablePref.checkValidInteger();
        dynamicReportingRateMinUnreliablePref = (PanEditTextPreference) findPreference(
                "dynamicReportingRateMinUnreliable");
        dynamicReportingRateMinUnreliablePref.checkValidInteger();
        dynamicReportingRateMinReliablePref = (PanEditTextPreference) findPreference(
                "dynamicReportingRateMinReliable");
        dynamicReportingRateMinReliablePref.checkValidInteger();
        dynamicReportingRateMaxUnreliablePref = (PanEditTextPreference) findPreference(
                "dynamicReportingRateMaxUnreliable");
        dynamicReportingRateMaxUnreliablePref.checkValidInteger();
        dynamicReportingRateMaxReliablePref = (PanEditTextPreference) findPreference(
                "dynamicReportingRateMaxReliable");
        dynamicReportingRateMaxReliablePref.checkValidInteger();

        if (reportingStrategyPref != null) {
            String value = reportingStrategyPref.getValue();
            if (value == null) {
                value = "Dynamic";
                reportingStrategyPref.setValue(value);
            }
            changeVisibility(value);

            reportingStrategyPref
                    .setOnPreferenceChangeListener(
                            new Preference.OnPreferenceChangeListener() {

                                @Override
                                public boolean onPreferenceChange(
                                        Preference arg0,
                                        Object arg1) {
                                    changeVisibility((String) arg1);
                                    return true;
                                }

                            });
        }

        alternateContactCategory = (PreferenceCategory) findPreference(
                "alternateContactCategory");
        saSipAddressAssignment = (ListPreference) findPreference(
                "saSipAddressAssignment");
        saSipAddress = (PanEditTextPreference) findPreference("saSipAddress");
        if (saSipAddressAssignment != null) {
            String value = saSipAddressAssignment.getValue();
            if (value == null) {
                value = getString(R.string.voip_assignment_disabled);
                saSipAddressAssignment.setValue(value);
            }
            changeVisibilityVoIP(value);

            saSipAddressAssignment
                    .setOnPreferenceChangeListener(
                            new Preference.OnPreferenceChangeListener() {

                                @Override
                                public boolean onPreferenceChange(
                                        Preference arg0,
                                        Object arg1) {
                                    changeVisibilityVoIP((String) arg1);
                                    return true;
                                }

                            });
        }
    }

    private void changeVisibility(String selection) {
        if (selection.equals(_previousReportingStrategy)) {
            return;
        }

        if (selection.equals("Dynamic")) {
            _previousReportingStrategy = selection;
            reportingStrategyPref.setSummary(
                    getString(R.string.preferences_text465) +
                            getString(R.string.reporting_strategy_summary));
            publishCategory
                    .addPreference(
                            dynamicReportingRateStationaryUnreliablePref);
            publishCategory
                    .addPreference(dynamicReportingRateStationaryReliablePref);
            publishCategory
                    .addPreference(dynamicReportingRateMinUnreliablePref);
            publishCategory.addPreference(dynamicReportingRateMinReliablePref);
            publishCategory
                    .addPreference(dynamicReportingRateMaxUnreliablePref);
            publishCategory.addPreference(dynamicReportingRateMaxReliablePref);
            publishCategory.removePreference(constantReportingRateReliablePref);
            publishCategory
                    .removePreference(constantReportingRateUnreliablePref);
        } else if (selection.equals("Constant")) {
            _previousReportingStrategy = selection;
            reportingStrategyPref.setSummary(
                    getString(R.string.preferences_text466) +
                            getString(R.string.reporting_strategy_summary));
            publishCategory
                    .removePreference(
                            dynamicReportingRateStationaryUnreliablePref);
            publishCategory
                    .removePreference(
                            dynamicReportingRateStationaryReliablePref);
            publishCategory
                    .removePreference(dynamicReportingRateMinUnreliablePref);
            publishCategory
                    .removePreference(dynamicReportingRateMinReliablePref);
            publishCategory
                    .removePreference(dynamicReportingRateMaxUnreliablePref);
            publishCategory
                    .removePreference(dynamicReportingRateMaxReliablePref);
            publishCategory.addPreference(constantReportingRateReliablePref);
            publishCategory.addPreference(constantReportingRateUnreliablePref);
        }
    }

    private void changeVisibilityVoIP(String selection) {
        if (selection.equals(_previousSaSipAddressAssignment)) {
            return;
        }

        if (selection
                .equals(getString(R.string.voip_assignment_manual_entry))) {
            _previousSaSipAddressAssignment = selection;
            alternateContactCategory.addPreference(saSipAddress);
        } else {
            //manual entry is hidden for all other assignment methods
            _previousSaSipAddressAssignment = selection;
            alternateContactCategory
                    .removePreference(saSipAddress);
        }
    }
}
