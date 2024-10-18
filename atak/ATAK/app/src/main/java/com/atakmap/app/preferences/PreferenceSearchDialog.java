
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.SystemClock;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.android.cot.NetworkGPSPreferenceFragment;
import com.atakmap.android.gridlines.GridLinesPreferenceFragment;
import com.atakmap.android.image.MediaPreferenceFragment;
import com.atakmap.android.layers.app.ImportStyleDefaultPreferenceFragment;
import com.atakmap.android.layers.app.LayerPreferenceFragment;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.offscreenindicators.OffscreenIndicatorsPrefsFragment;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.preference.UnitDisplayPreferenceFragment;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.app.R;
import com.atakmap.app.SettingsActivity;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog to search preferences
 * See loadIndex() for parsing/indexing available preferences
 */
public class PreferenceSearchDialog
        implements DialogInterface.OnDismissListener {
    private static final String TAG = "PreferenceSearchDialog";

    private final Context context;
    private AlertDialog _dialog;
    private EditText _search;
    private LayoutInflater _inflater;

    protected SearchIndexAdapter _adapter;

    private final List<PreferenceSearchIndex> masterlist = new ArrayList<>();

    public PreferenceSearchDialog(final Context context) {
        this.context = context;

        //add preferences (not ToolPreference)
        add(masterlist, NetworkGPSPreferenceFragment.index(context));
        add(masterlist, SelfMarkerCustomFragment.index(context));
        add(masterlist, SupportPreferenceFragment.index(context));
        add(masterlist, ActionBarPreferences.index(context));
        add(masterlist, ImportStyleDefaultPreferenceFragment.index(context));
        add(masterlist, MyPreferenceFragment.index(context));
        add(masterlist, LayerPreferenceFragment.index(context));
        add(masterlist, UnitDisplayPreferenceFragment.index(context));
        add(masterlist, AtakAccountsFragment.index(context));
        add(masterlist, ControlPrefsFragment.index(context));
        add(masterlist, OffscreenIndicatorsPrefsFragment.index(context));
        add(masterlist, NetworkConnectionPreferenceFragment.index(context));
        add(masterlist, DevicePreferenceFragment.index(context));
        add(masterlist, LoggingPreferenceFragment.index(context));
        add(masterlist, GridLinesPreferenceFragment.index(context));
        add(masterlist, MediaPreferenceFragment.index(context));
        add(masterlist, AdvancedLoggingPreferenceFragment.index(context));
        add(masterlist, TadilJPreferenceFragment.index(context));
        add(masterlist, AppMgmtPreferenceFragment.index(context));
        add(masterlist, ReportingPreferenceFragment.index(context));
        add(masterlist, MainPreferencesFragment.index(context));
        add(masterlist, BluetoothPrefsFragment.index(context));
        add(masterlist, CustomActionBarFragment.index(context));
        add(masterlist, DisplayPrefsFragment.index(context));
        add(masterlist, NetworkPreferenceFragment.index(context));
        add(masterlist, SpecificPreferenceFragment.index(context));
        add(masterlist, CallSignPreferenceFragment.index(context));
        add(masterlist, CallSignAndDeviceFragment.index(context));
        add(masterlist, DexOptionsPreferenceFragment.index(context));
        add(masterlist, DeviceDetailsPreferenceFragment.index(context));
        add(masterlist, LegacyPreferencesFragment.index(context));
        add(masterlist, OtherDisplayPreferenceFragment.index(context));
        add(masterlist, PointDroppingBehaviorPreferenceFragment.index(context));
        add(masterlist, StaleDataPreferenceFragment.index(context));
        add(masterlist, SelfCoordinatePreferenceFragment.index(context));
        add(masterlist, ThreeDRenderingFragment.index(context));
        add(masterlist, UserTouchPreferenceFragment.index(context));
        add(masterlist, UsabilityPreferenceFragment.index(context));
        add(masterlist, PromptNetworkPreferenceFragment.index(context));
        add(masterlist, LockingBehaviorFragment.index(context));
    }

    /**
     * Build and show the dialog
     */
    public void show() {
        // Dialog is already showing - ignore
        if (_dialog != null && _dialog.isShowing())
            return;

        _inflater = LayoutInflater.from(context);
        View _dialogView = _inflater
                .inflate(com.atakmap.app.R.layout.search_settings, null);
        ListView list = _dialogView.findViewById(android.R.id.list);
        _adapter = new SearchIndexAdapter(list);

        _search = _dialogView.findViewById(R.id.settings_search);
        _search.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                _refreshThread.exec();
            }
        });
        _search.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                //close keyboard
                InputMethodManager imm = (InputMethodManager) context
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.hideSoftInputFromWindow(_search.getWindowToken(), 0);
            }
        });

        // Show the dialog
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(R.string.search_settings);
        b.setIcon(R.drawable.ic_action_search);
        if (_dialogView != null)
            b.setView(_dialogView);
        b.setNegativeButton(R.string.cancel, null);

        _dialog = b.create();

        int width = (int) (context.getResources()
                .getDisplayMetrics().widthPixels * 0.90);
        int height = (int) (context.getResources()
                .getDisplayMetrics().heightPixels * 0.90);

        _dialog.show();
        Window w = _dialog.getWindow();
        if (w != null)
            w.setLayout(width, height);

        _dialog.setOnDismissListener(this);

        //auto-open keyboard
        //InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        //if (imm != null)
        //    imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        _refreshThread.dispose(false);
    }

    private final LimitingThread _refreshThread = new LimitingThread(
            TAG + "-Refresh", new Runnable() {
                @Override
                public void run() {
                    if (_dialog == null || !_dialog.isShowing()
                            || _adapter == null)
                        return;
                    try {
                        _adapter.refresh();
                        Thread.sleep(500);
                    } catch (Exception e) {
                        Log.e(TAG, "Exception on refresh thread", e);
                    }
                }
            });

    private static class ViewHolder {
        ImageView icon;
        TextView desc, highlight, parent;
    }

    protected class SearchIndexAdapter extends BaseAdapter implements
            AdapterView.OnItemClickListener {

        // Full list of pref index
        private final List<PreferenceSearchIndex> _index = new ArrayList<>();

        // Filtered list of prefs (used when performing search query)
        private final List<PreferenceSearchIndex> _filtered = new ArrayList<>();

        // Search terms
        private String _searchTerms;

        // Full refresh
        private boolean _clear;

        SearchIndexAdapter(ListView list) {
            list.setOnItemClickListener(this);
            list.setAdapter(this);
        }

        protected void refresh() {
            // Load pref if there aren't any already
            if (_clear) {
                _index.clear();
                _clear = false;
            }
            if (_index.isEmpty()) {
                List<PreferenceSearchIndex> prefs = loadIndex();
                _index.addAll(prefs);
            }

            // Filter
            final List<PreferenceSearchIndex> filtered;
            _searchTerms = _search != null ? _search.getText().toString()
                    .toLowerCase(LocaleUtil.getCurrent()) : null;
            if (!FileSystemUtils.isEmpty(_searchTerms)) {
                filtered = new ArrayList<>();
                for (PreferenceSearchIndex c : _index) {
                    if (c.match(_searchTerms))
                        filtered.add(c);
                }
            } else
                filtered = new ArrayList<>(_index);

            Collections.sort(filtered, INDEX_COMP);

            MapView.getMapView().post(new Runnable() {
                @Override
                public void run() {
                    _filtered.clear();
                    _filtered.addAll(filtered);
                    notifyDataSetChanged();
                }
            });
        }

        protected void clear() {
            _clear = true;
            _refreshThread.exec();
        }

        @Override
        public int getCount() {
            return _filtered.size();
        }

        @Override
        public PreferenceSearchIndex getItem(int position) {
            return _filtered.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View row, ViewGroup parent) {
            ViewHolder h = row != null ? (ViewHolder) row.getTag() : null;
            if (h == null) {
                h = new ViewHolder();
                row = _inflater.inflate(R.layout.search_settings_row, null);
                h.icon = row.findViewById(R.id.settings_icon);
                h.desc = row.findViewById(R.id.settings_label);
                h.highlight = row.findViewById(R.id.settings_match);
                h.parent = row.findViewById(R.id.settings_parent);
                row.setTag(h);
            }

            PreferenceSearchIndex c = getItem(position);
            if (c == null)
                return null;

            if (c.hasIcon()) {
                h.icon.setImageDrawable(c.getIcon());
            } else {
                h.icon.setImageResource(R.drawable.ic_menu_settings);
            }
            h.desc.setText(c.getSummary());

            PreferenceSearchIndex.MatchResults match = c
                    .getMatch(_adapter._searchTerms);
            if (match == null) {
                h.highlight.setVisibility(View.INVISIBLE);
            } else {
                h.highlight.setText(match.getHighlight());
                h.highlight.setVisibility(View.VISIBLE);
            }

            String parentTitle = c.getParentSummary();
            if (FileSystemUtils.isEmpty(parentTitle)) {
                h.parent.setVisibility(View.INVISIBLE);
            } else {
                h.parent.setText(context.getString(R.string.settings_found_in,
                        parentTitle));
                h.parent.setVisibility(View.VISIBLE);
            }

            return row;
        }

        @Override
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            PreferenceSearchIndex c = getItem(pos);
            if (c == null)
                return;

            //Log.d(TAG, "onItemClick: " + c.toString());
            if (_dialog != null)
                _dialog.dismiss();

            //if on current screen, the no-op
            if (c.getFragClass().equals(MyPreferenceFragment.class))
                return;

            //display the selected settings
            if (PluginPreferenceFragment.class
                    .isAssignableFrom(c.getFragClass())
                    && !FileSystemUtils.isEmpty(c.getKey())) {
                SettingsActivity.start(c.getParentKey(), c.getKey());
            } else {
                SettingsActivity.start(c.getFragClass(), c.getKey());
            }
        }
    }

    private List<PreferenceSearchIndex> loadIndex() {
        Map<String, PreferenceSearchIndex> index = new HashMap<>();
        long start = SystemClock.elapsedRealtime();

        add(index, masterlist);

        //now add plugins and other ToolPreferences
        List<ToolsPreferenceFragment.ToolPreference> tools = ToolsPreferenceFragment
                .getPreferenceFragments();
        for (ToolsPreferenceFragment.ToolPreference tool : tools) {
            add(index, AtakPreferenceFragment.index(tool));
        }

        Log.d(TAG, "preference search index complete ms: "
                + (SystemClock.elapsedRealtime() - start));
        if (index.size() < 1) {
            Log.w(TAG, "No prefs indexed");
            return new ArrayList<>();
        }

        return new ArrayList<>(index.values());
    }

    private void add(Map<String, PreferenceSearchIndex> list,
            List<PreferenceSearchIndex> toAdd) {
        if (list == null || toAdd == null) {
            Log.w(TAG, "Index add failed");
            return;
        }

        for (PreferenceSearchIndex i : toAdd) {
            //!FileSystemUtils.isEmpty(i.getKey()) &&
            if (list.containsKey(i.getKey())) {
                Log.d(TAG, "Skipping duplicate key: " + i.getKey());
                continue;
            }

            list.put(i.getKey(), i);
        }

        //Log.d(TAG, "Index added: " + toAdd.toString());
    }

    private void add(List<PreferenceSearchIndex> list,
            List<PreferenceSearchIndex> toAdd) {
        if (list == null || toAdd == null) {
            Log.w(TAG, "Index add failed");
            return;
        }

        //Log.d(TAG, "Index added: " + toAdd.toString());
        list.addAll(toAdd);
    }

    private static final Comparator<PreferenceSearchIndex> INDEX_COMP = new Comparator<PreferenceSearchIndex>() {

        @Override
        public int compare(PreferenceSearchIndex lhs,
                PreferenceSearchIndex rhs) {

            if (lhs == null && rhs == null)
                return 0;
            else if (lhs == null || FileSystemUtils.isEmpty(lhs.getSummary()))
                return 1;
            if (rhs == null || FileSystemUtils.isEmpty(rhs.getSummary()))
                return -1;

            PreferenceSearchIndex.MatchResults lmatch = lhs.getMatch();
            PreferenceSearchIndex.MatchResults rmatch = rhs.getMatch();
            if (lmatch == null || rmatch == null
                    || lmatch.getScore() == rmatch.getScore())
                return lhs.getSummary().compareToIgnoreCase(rhs.getSummary());

            return Integer.compare(rmatch.getScore(), lmatch.getScore());
        }
    };
}
