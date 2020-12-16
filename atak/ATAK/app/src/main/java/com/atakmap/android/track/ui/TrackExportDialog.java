
package com.atakmap.android.track.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.EditText;
import android.widget.Toast;

import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.track.TrackDetails;
import com.atakmap.android.track.TrackHistoryDropDown;
import com.atakmap.android.track.task.ExportTrackParams;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

public class TrackExportDialog implements Dialog.OnClickListener {

    private static final String TAG = "TrackExportDialog";

    private static final int TRACK_EXPORT_NOTIF_ID = 54433; //arbitrary
    private static final String[] FORMATS = {
            "Route", "TAK Server", "KML", "KMZ", "GPX", "CSV"
    };

    private final MapView _mapView;
    private final Context _context;

    private String _trackName;
    private boolean _allSelfTracks;
    private int[] _trackIds;

    public TrackExportDialog(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    public void show(String trackName, TrackDetails[] tracks) {
        _trackName = FileSystemUtils.sanitizeFilename(trackName);
        if (FileSystemUtils.isEmpty(_trackName))
            _trackName = _mapView.getDeviceCallsign() + " Track Export";

        if (FileSystemUtils.isEmpty(tracks)) {
            Toast.makeText(_context, R.string.select_one_export,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // setup intent
        _allSelfTracks = true;
        _trackIds = new int[tracks.length];
        for (int i = 0; i < tracks.length; i++) {
            _trackIds[i] = tracks[i].getTrackDbId();
            if (!FileSystemUtils.isEquals(MapView.getDeviceUid(),
                    tracks[i].getUserUID()))
                _allSelfTracks = false;
        }

        TileButtonDialog d = new TileButtonDialog(_mapView);
        d.addButton(R.drawable.ic_route, R.string.gpx_route_file);
        d.addButton(ATAKConstants.getServerConnection(true), R.string.publish);
        d.addButton(R.drawable.ic_kml, R.string.kml_file);
        d.addButton(R.drawable.ic_kmz, R.string.kmz_file);
        d.addButton(R.drawable.ic_gpx, R.string.gpx_file);
        d.addButton(R.drawable.ic_csv, R.string.csv_file);
        d.setOnClickListener(this);
        d.show(R.string.choose_export_format, true);
    }

    public void show(String trackName, TrackDetails track) {
        show(trackName, new TrackDetails[] {
                track
        });
    }

    public void show(final TrackDetails[] tracks) {
        // pop up dialog for user to enter name
        String defaultName;
        if (tracks.length == 1)
            defaultName = FileSystemUtils.sanitizeFilename(
                    tracks[0].getTitle());
        else
            defaultName = _context.getString(R.string.default_track_name);

        final EditText editName = new EditText(_context);
        editName.setText(defaultName);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.enter_export_name);
        b.setView(editName);
        b.setPositiveButton(R.string.next,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String name = editName.getText().toString();
                        show(name, tracks);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    public void show(TrackDetails track) {
        if (track == null || !track.hasPoints()) {
            Toast.makeText(_context, R.string.no_logged_points,
                    Toast.LENGTH_LONG).show();
            return;
        }
        show(new TrackDetails[] {
                track
        });
    }

    @Override
    public void onClick(DialogInterface d, int w) {
        if (_trackIds == null || w < 0)
            return;

        String format = FORMATS[w];
        File exportFile = new File(FileSystemUtils.getItem(
                FileSystemUtils.EXPORT_DIRECTORY), _trackName);
        final Intent intent = new Intent(
                TrackHistoryDropDown.EXPORT_TRACK_HISTORY);
        ExportTrackParams exportParams;

        if (format.equals("TAK Server")) {
            if (!_allSelfTracks) {
                Log.w(TAG,
                        "Cannot export non self tracks to TAK Server");
                Toast.makeText(_context, _context.getString(
                        R.string.send_own_tracks)
                        + _context.getString(
                                R.string.MARTI_sync_server)
                        + "...",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "Beginning server track export...");
            exportParams = new ExportTrackParams(
                    TRACK_EXPORT_NOTIF_ID,
                    MapView.getDeviceUid(), _trackName,
                    exportFile.getAbsolutePath(),
                    "KML", false, -1, _trackIds,
                    TrackHistoryDropDown.SERVERTRACKSEXPORTED, true);
        } else {
            Log.d(TAG, "Beginning file track export... " + format);
            exportParams = new ExportTrackParams(
                    TRACK_EXPORT_NOTIF_ID, MapView.getDeviceUid(),
                    _trackName, exportFile.getAbsolutePath(), format,
                    _trackIds, TrackHistoryDropDown.TRACKSEXPORTED);
        }
        intent.putExtra("exportparams", exportParams);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }
}
