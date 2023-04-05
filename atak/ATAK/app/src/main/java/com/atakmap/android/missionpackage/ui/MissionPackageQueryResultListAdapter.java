
package com.atakmap.android.missionpackage.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cot.CotMapServerListener;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.http.rest.ServerContact;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.http.datamodel.MissionPackageQueryResult;
import com.atakmap.app.R;
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
 * List adapter for Mission Package query results
 * 
 * 
 */
public class MissionPackageQueryResultListAdapter extends BaseAdapter {

    private final static String TAG = "MissionPackageQueryResultListAdapter";

    /**
     * Supported sort types (correspond to list view columns)
     */
    enum SortType {
        NAME(R.string.name, R.drawable.alpha_sort),
        DATE(R.string.date, R.drawable.time_sort),
        SIZE(R.string.size, R.drawable.prox_sort),
        USER(R.string.user2, R.drawable.user_sort);

        private final int nameId, iconId;

        SortType(int nameId, int iconId) {
            this.nameId = nameId;
            this.iconId = iconId;
        }
    }

    static class Sort extends HierarchyListItem.Sort {

        private final SortType sortType;
        private final String name, icon;

        Sort(Context ctx, SortType type) {
            this.sortType = type;
            this.name = ctx.getString(type.nameId);
            this.icon = "android.resource://" + ctx.getPackageName()
                    + "/" + type.iconId;
        }

        public SortType getSortType() {
            return this.sortType;
        }

        @Override
        public String getTitle() {
            return this.name;
        }

        @Override
        public String getIconUri() {
            return this.icon;
        }
    }

    /**
     * List of saved packages
     */
    private final List<MissionPackageQueryResult> _total = new ArrayList<>();
    private final List<MissionPackageQueryResult> _available = new ArrayList<>();

    /**
     * Android Context
     */
    private final Context _context;
    private final LayoutInflater _inflater;

    private String _serverConnectString;

    /**
     * maintain sorting state
     */
    private SortType _sort;
    private boolean _sortReversed;

    /**
     * ctor
     *
     * @param context
     */
    public MissionPackageQueryResultListAdapter(Context context) {
        this._context = context;
        this._inflater = LayoutInflater.from(context);
        this._sort = SortType.DATE;
        this._sortReversed = false;
    }

    synchronized public MissionPackageQueryResult getResult(int position) {
        return position >= 0 && position < _available.size()
                ? _available.get(position)
                : null;
    }

    /**
     * Reset the adapter with the specified files (remove all, add these, sort)
     * 
     * @param available
     */
    void reset(List<MissionPackageQueryResult> available,
            String serverConnectString) {
        _serverConnectString = serverConnectString;
        List<ServerContact> serverContacts = CotMapComponent.getInstance()
                .getServerContacts(null);

        // clear files and selections
        _total.clear();
        _available.clear();

        // add files and selections
        if (!FileSystemUtils.isEmpty(available)) {
            // Resolve names now so we don't need to do it every sort/display
            Map<String, String> nameMap = new HashMap<>();
            // Track latest version of each package
            Map<String, MissionPackageQueryResult> added = new HashMap<>();
            for (MissionPackageQueryResult r : available) {
                MissionPackageQueryResult newR = new MissionPackageQueryResult(
                        r);
                String mpName = newR.getName();
                MissionPackageQueryResult existing = added.get(mpName);
                if (existing != null && existing.isNewerThan(r))
                    continue;
                added.put(mpName, newR);
                if (!nameMap.containsKey(r.getCreatorUid())) {
                    String callsign = CotMapServerListener
                            .getServerCallsignFromList(serverContacts,
                                    r.getCreatorUid());
                    if (FileSystemUtils.isEmpty(callsign))
                        callsign = r.getSubmissionUser();
                    nameMap.put(r.getCreatorUid(), callsign);
                }
                newR.setSubmissionUser(nameMap.get(r.getCreatorUid()));
            }
            _total.addAll(added.values());
        }
        _available.addAll(_total);

        // sort and redraw
        sort(_sort, false);
    }

    void search(String terms) {
        if (FileSystemUtils.isEmpty(terms)) {
            _available.clear();
            _available.addAll(_total);
            sort(_sort, false);
            return;
        }
        List<MissionPackageQueryResult> filtered = new ArrayList<>();
        for (MissionPackageQueryResult r : _total) {
            if (match(r.getName(), terms)
                    || match(r.getSubmissionDateTime(), terms)
                    || match(r.getKeywords(), terms)
                    || match(r.getMIMEType(), terms)
                    || match(r.getSubmissionUser(), terms))
                filtered.add(r);
        }
        _available.clear();
        _available.addAll(filtered);
        sort(_sort, false);
    }

    private boolean match(String search, String terms) {
        if (search == null)
            return false;
        return search.toLowerCase(LocaleUtil.getCurrent()).contains(terms);
    }

    @Override
    public int getCount() {
        return _available.size();
    }

    @Override
    public Object getItem(int position) {
        return _available.get(position);
    }

    @Override
    public long getItemId(int position) {
        return _available.get(position).hashCode();
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        ViewHolder h = row != null ? (ViewHolder) row.getTag() : null;
        if (h == null) {
            row = _inflater.inflate(
                    R.layout.missionpackage_queryresults_list_child, parent,
                    false);
            h = new ViewHolder();
            h.nameText = row.findViewById(
                    R.id.missionpackage_queryresults_child_txtName);
            h.dateText = row.findViewById(
                    R.id.missionpackage_queryresults_child_txtDate);
            h.typeSize = row.findViewById(
                    R.id.missionpackage_queryresults_child_txtSize);
            h.userText = row.findViewById(
                    R.id.missionpackage_queryresults_child_txtUser);
            row.setTag(h);
        }

        MissionPackageQueryResult l = getResult(position);
        if (l == null)
            return _inflater.inflate(R.layout.empty, parent, false);

        h.nameText.setText(l.getName());
        h.dateText.setText(l.getSubmissionDateTime());
        h.typeSize.setText(MathUtils.GetLengthString(l.getSize()));
        h.userText.setText(l.getSubmissionUser());

        return row;
    }

    private static class ViewHolder {
        TextView nameText, dateText, typeSize, userText;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private void requestRedraw() {
        try {
            ((Activity) _context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "requestRedraw failure", e);
        }
    }

    @SuppressWarnings("unused")
    private void logBundle(Bundle b) {
        java.util.Set<String> keys = b.keySet();
        for (String key : keys) {
            Log.d(TAG, key + ":" + b.getString(key));
        }
    }

    /**
     * Perform specified sort Optionally force reversal if sort type matches (e.g. user sorted on
     * that type/column a second time)
     */
    public synchronized void sort(SortType sort, boolean bRequestReverse) {

        // see if we are already sorted this way...
        if (sort.equals(_sort)) {
            // same sort type
            if (bRequestReverse) {
                // sort (maybe user resorted), then reverse
                _sortReversed = !_sortReversed;
            } else {
                // sort (maybe a new file added or removed), no reverse
                _sortReversed = _sortReversed; // no-op, stays the same, just here for readability
            }
        } else {
            // new sort type, just sort
            // no need to reverse even if requested
            _sortReversed = false;
        }

        _sort = sort;
        Log.d(TAG, "Sorting by: " + sort);

        switch (sort) {
            case DATE: {
                Collections.sort(_available, DateComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(_available,
                            Collections.reverseOrder(DateComparator));
                }
            }
                break;
            case SIZE: {
                Collections.sort(_available, SizeComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(_available,
                            Collections.reverseOrder(SizeComparator));
                }
            }
                break;
            case USER: {
                Collections.sort(_available, UserComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(_available,
                            Collections.reverseOrder(UserComparator));
                }
            }
                break;
            case NAME:
            default: {
                Collections.sort(_available, NameComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(_available,
                            Collections.reverseOrder(NameComparator));
                }
            }
                break;
        }

        // redraw the view
        requestRedraw();
    }

    public void showDialog(final MissionPackageQueryResult result) {
        if (result == null) {
            Log.e(TAG, "Unable to load result Detail");
            Toast.makeText(
                    _context,
                    R.string.unable_to_load_mission_package,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Showing details of result: " + result);

        View v = _inflater.inflate(R.layout.missionpackage_queryresults_detail,
                null);

        TextView textName = v
                .findViewById(R.id.missionpackage_queryresults_detail_txtName);
        textName.setText(result.getName());

        TextView textType = v
                .findViewById(R.id.missionpackage_queryresults_detail_txtSize);
        textType.setText(MathUtils.GetLengthString(result.getSize()));

        TextView textDate = v
                .findViewById(R.id.missionpackage_queryresults_detail_txtDate);
        textDate.setText(result.getSubmissionDateTime());

        //map that UID to callsign and display callsign
        TextView textUser = v
                .findViewById(R.id.missionpackage_queryresults_detail_txtUser);
        textUser.setText(result.getSubmissionUser());

        TextView textHash = v
                .findViewById(R.id.missionpackage_queryresults_detail_txtHash);
        textHash.setText(result.getHash());

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.available_mission_package);
        b.setView(v);
        b.setPositiveButton(R.string.download,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "Importing: " + result);
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                DropDownManager.CLOSE_DROPDOWN));
                        AtakBroadcast
                                .getInstance()
                                .sendBroadcast(
                                        new Intent(
                                                MissionPackageReceiver.MISSIONPACKAGE_DOWNLOAD)
                                                        .putExtra("package",
                                                                result)
                                                        .putExtra(
                                                                "serverConnectString",
                                                                _serverConnectString));
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    /**
     * Comparators to support sorting
     */
    private final Comparator<MissionPackageQueryResult> NameComparator = new Comparator<MissionPackageQueryResult>() {
        @Override
        public int compare(MissionPackageQueryResult lhs,
                MissionPackageQueryResult rhs) {
            return lhs.getName().compareToIgnoreCase(rhs.getName());
        }
    };

    private final Comparator<MissionPackageQueryResult> UserComparator = new Comparator<MissionPackageQueryResult>() {
        @Override
        public int compare(MissionPackageQueryResult lhs,
                MissionPackageQueryResult rhs) {
            if (FileSystemUtils.isEquals(lhs.getSubmissionUser(),
                    rhs.getSubmissionUser()))
                return 0;
            if (FileSystemUtils.isEmpty(lhs.getSubmissionUser()))
                return -1;
            if (FileSystemUtils.isEmpty(rhs.getSubmissionUser()))
                return 1;

            return lhs.getSubmissionUser().compareToIgnoreCase(
                    rhs.getSubmissionUser());
        }
    };

    private final Comparator<MissionPackageQueryResult> SizeComparator = new Comparator<MissionPackageQueryResult>() {
        @Override
        public int compare(MissionPackageQueryResult lhs,
                MissionPackageQueryResult rhs) {
            return Long.compare(rhs.getSize(), lhs.getSize());
        }
    };

    private final Comparator<MissionPackageQueryResult> DateComparator = new Comparator<MissionPackageQueryResult>() {
        @Override
        public int compare(MissionPackageQueryResult lhs,
                MissionPackageQueryResult rhs) {
            //By default new dates up top of list
            return rhs.getSubmissionDateTime().compareToIgnoreCase(
                    lhs.getSubmissionDateTime());
        }
    };
}
