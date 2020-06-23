
package com.atakmap.android.cot.exporter;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import com.atakmap.android.attachment.DeleteAfterSendCallback;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageConfiguration;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.List;

/**
 * Map item export task for cases where the CoT message is potentially large
 * enough to require sending via Data Package (i.e. routes or mulit-polylines).
 * Map items with smaller CoT events save more time being sent directly without
 * using this task.
 */
public class DispatchMapItemTask extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "DispatchMapItemTask";

    // Can't send an item larger than 64K over streaming connection
    private static final int MAX_UDP_SIZE = 64000;

    private final MapItem _item;
    private final ProgressDialog _pd;

    public DispatchMapItemTask(MapView mapView, MapItem item) {
        _item = item;
        Context ctx = mapView.getContext();
        _pd = new ProgressDialog(ctx);
        _pd.setMessage(ctx.getString(R.string.route_prepare_send,
                _item.getTitle()));
        _pd.setCancelable(false);
    }

    @Override
    protected void onPreExecute() {
        _pd.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        CotEvent event = CotEventFactory.createCotEvent(_item);
        return event == null || event.toString().length() <= MAX_UDP_SIZE;
    }

    @Override
    protected void onPostExecute(Boolean sendUDP) {
        _pd.dismiss();

        List<File> atts = AttachmentManager.getAttachments(_item.getUID());

        if (sendUDP && atts.isEmpty()) {
            Log.d(TAG, "Sending map item: " + _item);
            Intent in = new Intent(ContactPresenceDropdown.SEND_LIST);
            in.putExtra("targetUID", _item.getUID());
            AtakBroadcast.getInstance().sendBroadcast(in);
        } else {
            Log.d(TAG, "Sending map item via Mission Package: " + _item);
            MissionPackageManifest mf = MissionPackageApi.CreateTempManifest(
                    _item.getTitle(), true, true, null);
            mf.addMapItem(_item.getUID());

            MissionPackageConfiguration cfg = mf.getConfiguration();
            cfg.setParameter(new NameValuePair("callsign", _item.getTitle()));
            cfg.setParameter(new NameValuePair("uid", _item.getUID()));

            for (File f : atts)
                mf.addFile(f, _item.getUID());

            MissionPackageApi.prepareSend(mf, DeleteAfterSendCallback.class,
                    false);
        }
    }
}
