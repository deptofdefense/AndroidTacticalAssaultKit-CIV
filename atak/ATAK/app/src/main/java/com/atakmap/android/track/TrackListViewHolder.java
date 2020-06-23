
package com.atakmap.android.track;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class TrackListViewHolder implements View.OnClickListener,
        View.OnLongClickListener, CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "TrackListViewHolder";

    private final MapView mapView;
    private final Context context;
    private final TrackListAdapter adapter;

    public View root;
    public TrackDetails track;
    public TextView callsign, curTrack, startTime, elapsedTime, distance;
    public ImageView icon;
    public Button titleBtn;
    public ImageButton detailsBtn, exportBtn, deleteBtn;
    public CheckBox selectedCB, viewCB;

    public TrackListViewHolder(MapView mapView, TrackListAdapter adapter) {
        this.mapView = mapView;
        this.context = mapView.getContext();
        this.adapter = adapter;
    }

    @Override
    public void onClick(View v) {

        // Change track name
        if (v == titleBtn) {
            final EditText editName = new EditText(context);
            editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editName.setText(track.getTitle());

            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle(R.string.enter_track_name);
            b.setView(editName);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            String name = editName.getText().toString();
                            track.setTitle(name, context);
                            adapter.requestRedraw();
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }

        // Track details
        else if (v == detailsBtn) {
            if (!track.hasPoints()) {
                Toast.makeText(context, R.string.no_logged_points,
                        Toast.LENGTH_LONG).show();
                return;
            }
            adapter.getDropDown().showTrackDetailsView(track, true,
                    !track.getVisible());
        }

        // Export track
        else if (v == exportBtn)
            TrackHistoryDropDown.exportTrack(mapView, track);

        // Delete track
        else if (v == deleteBtn) {
            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle(context.getString(R.string.delete)
                    + track.getUserCallsign() + " "
                    + context.getString(R.string.track));
            b.setMessage(context.getString(R.string.remove_track)
                    + track.getTitle() + mapView.getContext().getString(
                            R.string.question_mark_symbol));
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int id) {
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            "com.atakmap.android.bread.DELETE_TRACK")
                                                    .putExtra(
                                                            CrumbDatabase.META_TRACK_DBID,
                                                            track.getTrackDbId()));
                            adapter.removeResult(track);
                            track.dispose();
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }

        // Select or pan to track
        else if (v == root) {
            if (adapter.getMultiSelectEnabled()) {
                Log.d(TAG, "Multiselect ignoring Selected row: " + track);
                return;
            }
            Log.d(TAG, "Selected row: " + track.toString());
            if (!viewCB.isChecked())
                viewCB.setChecked(true);
            else
                ATAKUtilities.scaleToFit(track.getPolyline());
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == root) {
            onClick(v);
            return true;
        }
        return false;
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        // Toggle selection on track in multi-select mode
        if (cb == selectedCB) {
            if (checked)
                adapter.select(track);
            else
                adapter.unselect(track);
        }

        // Toggle visibility of track
        else if (cb == viewCB) {
            adapter.toggleTrackPolyline(track, checked);
        }
    }
}
