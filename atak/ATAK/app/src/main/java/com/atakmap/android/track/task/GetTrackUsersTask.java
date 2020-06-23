
package com.atakmap.android.track.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import com.atakmap.android.http.rest.ServerContact;
import com.atakmap.android.http.rest.operation.GetClientListOperation;
import com.atakmap.android.http.rest.request.GetClientListRequest;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.ui.TrackUser;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple background task to query track history from DB, and optionally from a server
 *
 * 
 */
public class GetTrackUsersTask extends AsyncTask<Void, Void, Void> {

    private static final String TAG = "GetTrackUsersTask";

    private final Callback callback;
    private List<TrackUser> users;
    private ProgressDialog _progressDialog;
    private final Context _context;
    private final String _serverConnectString;

    /**
     * ctor
     *
     * @param context
     * @param cb
     * @param serverConnectString include this parameter to search server and local DB. Null for local DB only search
     */
    public GetTrackUsersTask(Context context, Callback cb,
            String serverConnectString) {
        this.callback = cb;
        this._context = context;
        this._serverConnectString = serverConnectString;
    }

    @Override
    protected void onPreExecute() {
        // Before running code in background/worker thread
        _progressDialog = new ProgressDialog(_context);
        _progressDialog.setTitle(_context
                .getString(R.string.searching_without_space));
        _progressDialog.setIcon(R.drawable.ic_track_search);
        _progressDialog
                .setMessage(_context
                        .getString(R.string.searching_detailed_tracks));
        _progressDialog.setIndeterminate(true);
        _progressDialog.setCancelable(true);
        _progressDialog.show();
    }

    @Override
    protected Void doInBackground(Void... params) {
        Thread.currentThread().setName("GetTrackUsersTask");
        Log.d(TAG, "Executing GetTrackUsersTask");

        //get list from local DB

        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG, "Crumb DB not available, cannot show track history");
            users = new ArrayList<>();
        } else
            users = db.getUserList();

        if (users == null)
            return null;

        //get list from server
        if (!FileSystemUtils.isEmpty(_serverConnectString)) {
            Log.d(TAG, "Getting callsign list from server");
            String baseUrl = ServerListDialog
                    .getBaseUrl(_serverConnectString);
            int notificationId = 19875;
            GetClientListRequest request = new GetClientListRequest(
                    baseUrl,
                    _serverConnectString,
                    GetClientListRequest.CLIENT_LIST_MATCHER,
                    notificationId);

            try {
                List<ServerContact> serverContacts = GetClientListOperation
                        .query(request);
                List<TrackUser> serverUsers = TrackUser
                        .convert(serverContacts);
                if (!FileSystemUtils.isEmpty(serverUsers)) {
                    users.addAll(serverUsers);
                }
            } catch (ConnectionException e) {
                Log.w(TAG, "Failed to get callsign list from: "
                        + _serverConnectString, e);
            }
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (callback != null) {
            callback.onComplete(users);
        }

        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }
    }

    public interface Callback {
        void onComplete(List<TrackUser> users);
    }
}
