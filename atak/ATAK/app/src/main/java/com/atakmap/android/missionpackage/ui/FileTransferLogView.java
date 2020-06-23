
package com.atakmap.android.missionpackage.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileTransferLog;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;

public class FileTransferLogView extends LinearLayout implements
        View.OnClickListener, OnItemClickListener, OnItemLongClickListener {

    private static final String TAG = "FileTransferLogView";

    private FileTransferLogListAdapter _adapter;
    private ListView _list;

    public FileTransferLogView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void refresh() {

        if (_list == null) {
            _list = findViewById(R.id.missionpackage_log_listView);
            View header = LayoutInflater.from(getContext()).inflate(
                    R.layout.missionpackage_log_list_header, null);
            _list.addHeaderView(header);

            findViewById(R.id.missionpackage_log_header_txtName)
                    .setOnClickListener(this);
            findViewById(R.id.missionpackage_log_header_txtDate)
                    .setOnClickListener(this);
            findViewById(R.id.missionpackage_log_header_txtType)
                    .setOnClickListener(this);
            findViewById(R.id.btnMissionPackageLogDelete)
                    .setOnClickListener(this);

            _adapter = new FileTransferLogListAdapter(getContext());
            _list.setAdapter(_adapter);
            _list.setOnItemClickListener(this);
            _list.setOnItemLongClickListener(this);
        }
        _adapter.reset(getLogs());
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.missionpackage_log_header_txtName) {
            _adapter.sort(FileTransferLogListAdapter.SortType.NAME, true);

        } else if (i == R.id.missionpackage_log_header_txtDate) {
            _adapter.sort(FileTransferLogListAdapter.SortType.DATE, true);

        } else if (i == R.id.missionpackage_log_header_txtType) {
            _adapter.sort(FileTransferLogListAdapter.SortType.TYPE, true);

        } else if (i == R.id.btnMissionPackageLogDelete) {// delete all saved Mission Packages
            if (hasLogs()) {
                AlertDialog.Builder b = new AlertDialog.Builder(
                        getContext());
                b.setTitle(R.string.confirm_delete);
                b.setMessage(
                        R.string.mission_package_delete_all_file_share_logs);
                b.setCancelable(false);
                b.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int id) {
                                Toast.makeText(
                                        getContext(),
                                        R.string.mission_package_deleting_all_logs,
                                        Toast.LENGTH_SHORT).show();
                                clearLogs();
                                refresh();
                            }
                        });
                b.setNegativeButton(R.string.no, null);
                b.show();
            } else {
                Log.d(TAG, "Cannot delete all - No Logs currently saved");
                Toast.makeText(getContext(),
                        R.string.mission_package_no_logs_found,
                        Toast.LENGTH_SHORT).show();
            }

        }
    }

    @Override
    public void onItemClick(AdapterView<?> parentView, View childView,
            int pos, long id) {
        // subtract one for the header row
        _adapter.showDialog(pos - 1);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view,
            int pos, long id) {
        _adapter.showDialog(pos - 1);
        return true;
    }

    public static void clearLogs() {
        Log.d(TAG, "Deleting all logs");
        FileInfoPersistanceHelper.instance().truncateLogs();
    }

    public static List<FileTransferLog> getLogs() {
        return FileInfoPersistanceHelper.instance().allLogs();
    }

    public static boolean hasLogs() {
        List<FileTransferLog> logs = getLogs();
        return logs != null && logs.size() > 0;
    }
}
