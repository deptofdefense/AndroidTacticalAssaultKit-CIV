
package com.atakmap.android.user;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;

/**
 * Listens to task broadcasts and provides user input to task a specific item
 * 
 * 
 */
public class TaskBroadcastReceiver extends BroadcastReceiver {

    public TaskBroadcastReceiver(MapEventDispatcher dispatcher,
            TaskableMarkerListAdapter taskList) {
        _taskList = taskList;
        _eventDispatcher = dispatcher;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {

        if ((intent.hasExtra("point") || intent.hasExtra("uid"))
                && !intent.hasExtra("subjectUID") && _taskList.getCount() > 0) {

            //The context provided is not the correct context - pull from mapview
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MapView
                    .getMapView().getContext());
            alertBuilder.setTitle(R.string.point_dropper_text41)
                    .setCancelable(true)
                    .setAdapter(_taskList, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int item) {
                            Marker marker = (Marker) _taskList.getItem(item);
                            if (marker == null) {
                                return;
                            }

                            Intent taskIntent = new Intent();
                            taskIntent
                                    .setAction("com.atakmap.android.maps.TASK");
                            taskIntent.putExtra("subjectUID", marker.getUID());
                            if (intent.hasExtra("uid")) {
                                String uid = intent.getStringExtra("uid");
                                if (uid != null) {
                                    taskIntent.putExtra("uid", uid);
                                }
                            } else {
                                taskIntent.putExtra("point",
                                        intent.getStringExtra("point"));
                            }

                            if (intent.hasExtra("taskType")) {
                                taskIntent.putExtra("taskType",
                                        intent.getStringExtra("taskType"));
                            }
                            if (intent.hasExtra("alt")) {
                                taskIntent.putExtra("alt",
                                        intent.getDoubleExtra("alt", -500));
                            }

                            AtakBroadcast.getInstance().sendBroadcast(
                                    taskIntent);

                            dialog.dismiss();
                        }
                    });

            AlertDialog dialog = alertBuilder.create();
            dialog.show();
        }

    }

    @SuppressWarnings("unused")
    private final MapEventDispatcher _eventDispatcher;
    private final TaskableMarkerListAdapter _taskList;
}
