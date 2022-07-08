
package com.atakmap.android.missionpackage.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.filesharing.android.service.FileTransferLog;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * List adapter for File Share transactions taken by the user. Partially based on ContactListAdapter
 * 
 * 
 */
public class FileTransferLogListAdapter extends BaseAdapter {

    private final static String TAG = "FileTransferLogListAdapter";

    /**
     * Supported sort types (correspond to list view columns)
     */
    public enum SortType {
        NAME,
        DATE,
        TYPE
    }

    /**
     * List of saved packages
     */
    private final List<FileTransferLog> _logs = new ArrayList<>();

    // Copy of logs listing displayed in UI (only should be modified on UI thread)
    // Used to prevent an IllegalStateException crash
    private final List<FileTransferLog> _uiLogs = new ArrayList<>();

    /**
     * Android Context
     */
    private final Context _context;

    /**
     * maintain sorting state
     */
    private SortType _sort;
    private boolean _sortReversed;
    private final boolean _fullTime;

    /**
     * ctor
     * 
     * @param context
     */
    public FileTransferLogListAdapter(Context context) {
        this._context = context;
        this._sort = SortType.DATE;
        this._sortReversed = false;
        this._fullTime = context.getResources().getBoolean(R.bool.isTablet);
    }

    public FileTransferLog getLog(int position) {
        synchronized (_logs) {
            return position >= 0 && position < _logs.size()
                    ? _logs.get(position)
                    : null;
        }
    }

    /**
     * Reset the adapter with the specified files (remove all, add these, sort)
     * 
     * @param logs
     */
    void reset(List<FileTransferLog> logs) {

        if (logs == null)
            logs = new ArrayList<>();

        // sort and redraw
        sort(logs, _sort, false);

        synchronized (_logs) {
            _logs.clear();
            _logs.addAll(logs);
        }
        requestRedraw();
    }

    @Override
    public int getCount() {
        return _uiLogs.size();
    }

    @Override
    public Object getItem(int position) {
        return _uiLogs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return _uiLogs.get(position).hashCode();
    }

    @Override
    public View getView(final int position, View convertView,
            ViewGroup parent) {
        ViewHolder holder = convertView != null
                ? (ViewHolder) convertView.getTag()
                : null;
        if (holder == null) {
            convertView = LayoutInflater.from(_context).inflate(
                    R.layout.missionpackage_log_list_child, parent, false);
            holder = new ViewHolder();
            holder.name = convertView.findViewById(
                    R.id.missionpackage_log_child_txtName);
            holder.date = convertView.findViewById(
                    R.id.missionpackage_log_child_txtDate);
            holder.type = convertView.findViewById(
                    R.id.missionpackage_log_child_txtType);
            convertView.setTag(holder);
        }

        FileTransferLog l = (FileTransferLog) getItem(position);
        if (l == null)
            return null;

        holder.name.setText(l.name());
        holder.date.setText(MissionPackageUtils.getModifiedDate(l.getTime(),
                _fullTime));
        holder.type.setText(l.type().toString());

        return convertView;
    }

    private static class ViewHolder {
        TextView name, date, type;
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
                    List<FileTransferLog> logs;
                    synchronized (_logs) {
                        logs = new ArrayList<>(_logs);
                    }
                    // This is the only place this list should be modified
                    _uiLogs.clear();
                    _uiLogs.addAll(logs);
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
    private void sort(List<FileTransferLog> logs, SortType sort,
            boolean bRequestReverse) {

        // see if we are already sorted this way...
        if (sort.equals(_sort)) {
            // same sort type
            if (bRequestReverse) {
                // sort (maybe user resorted), then reverse
                _sortReversed = !_sortReversed;
            }
            //else {
            //    // sort (maybe a new file added or removed), no reverse
            //    _sortReversed = _sortReversed; // no-op, stays the same, just here for readability
            //}
        } else {
            // new sort type, just sort
            // no need to reverse even if requested
            _sortReversed = false;
        }

        _sort = sort;
        Log.d(TAG, "Sorting by: " + sort);

        switch (sort) {
            case DATE: {
                Collections.sort(logs, DateComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(logs,
                            Collections.reverseOrder(DateComparator));
                }
            }
                break;
            case TYPE: {
                Collections.sort(logs, TypeComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(logs,
                            Collections.reverseOrder(TypeComparator));
                }
            }
                break;
            case NAME:
            default: {
                Collections.sort(logs, NameComparator);
                if (_sortReversed) {
                    Log.d(TAG, "Reversing sort by: " + sort);
                    Collections.sort(logs,
                            Collections.reverseOrder(NameComparator));
                }
            }
                break;
        }
    }

    void sort(SortType sort, boolean bRequestReverse) {
        List<FileTransferLog> logs;
        synchronized (_logs) {
            logs = new ArrayList<>(_logs);
        }
        sort(logs, sort, bRequestReverse);
        synchronized (_logs) {
            _logs.clear();
            _logs.addAll(logs);
        }
        requestRedraw();
    }

    public void showDialog(int position) {
        FileTransferLog log = getLog(position);
        if (log == null) {
            Log.e(TAG, "Unable to load Log Detail");
            Toast.makeText(_context,
                    R.string.mission_package_unable_to_load_log,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Showing details of Log: " + log);

        View v = LayoutInflater.from(_context)
                .inflate(R.layout.missionpackage_log_detail, null);

        TextView textName = v
                .findViewById(R.id.missionpackage_log_detail_txtName);
        textName.setText(log.name());

        TextView textType = v
                .findViewById(R.id.missionpackage_log_detail_txtType);
        textType.setText(log.type().toString());

        TextView textDesc = v
                .findViewById(R.id.missionpackage_log_detail_txtDescription);
        textDesc.setText(log.description());

        TextView textDate = v
                .findViewById(R.id.missionpackage_log_detail_txtDate);
        textDate.setText(MissionPackageUtils.getModifiedDate(log.getTime()));

        TextView textSize = v
                .findViewById(R.id.missionpackage_log_detail_txtSize);
        textSize.setText(MathUtils.GetLengthString(log.sizeInBytes()));

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.log_detail);
        b.setView(v);
        b.setPositiveButton(R.string.ok, null);
        b.show();
    }

    /**
     * Comparators to support sorting
     */
    private final Comparator<FileTransferLog> NameComparator = new Comparator<FileTransferLog>() {
        @Override
        public int compare(FileTransferLog lhs, FileTransferLog rhs) {
            return lhs.name().compareToIgnoreCase(rhs.name());
        }
    };

    private final Comparator<FileTransferLog> TypeComparator = new Comparator<FileTransferLog>() {
        @Override
        public int compare(FileTransferLog lhs, FileTransferLog rhs) {
            return lhs.type().compareTo(rhs.type());
        }
    };

    private final Comparator<FileTransferLog> DateComparator = new Comparator<FileTransferLog>() {
        @Override
        public int compare(FileTransferLog lhs, FileTransferLog rhs) {
            return Long.compare(rhs.getTime(), lhs.getTime());
        }
    };
}
