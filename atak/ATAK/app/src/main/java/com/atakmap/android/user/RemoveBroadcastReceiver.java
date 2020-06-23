
package com.atakmap.android.user;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;

public class RemoveBroadcastReceiver extends BroadcastReceiver {
    private final MapGroup _removeGroup;

    public RemoveBroadcastReceiver(MapGroup removeGroup) {
        _removeGroup = removeGroup;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String uid = intent.getStringExtra("uid");
        MapItem item = null;

        if (uid != null
                && null != (item = _removeGroup.deepFindUID(uid))) {

            // a few fallbacks just in case the callsign is not set, 
            // check for the metastring title, then the metastring shapeName. 
            String title = ATAKUtilities.getDisplayName(item);
            if (FileSystemUtils.isEmpty(title))
                title = "Untitled";

            final MapGroup itemGroup = item.getGroup();
            final MapItem finalItem = item;

            AlertDialog.Builder b = new AlertDialog.Builder(MapView
                    .getMapView().getContext());
            b.setTitle(context.getString(R.string.confirmation_dialogue))
                    .setMessage(
                            MapView.getMapView().getContext()
                                    .getString(R.string.remove)
                                    + title
                                    + MapView
                                            .getMapView()
                                            .getContext()
                                            .getString(
                                                    R.string.question_mark_symbol))
                    .setPositiveButton(R.string.yes, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int arg1) {
                            if (itemGroup != null)
                                itemGroup.removeItem(finalItem);

                            //TODO remove SSE intent from baseline?
                            if (intent.hasExtra("entity_id")
                                    && intent.hasExtra("entity_type")) {
                                Intent removeSseEntity = new Intent(
                                        "com.atakmap.android.toolbars.REMOVE_ENTITY");
                                removeSseEntity.putExtra("entity_id",
                                        intent.getStringExtra("entity_id"));
                                removeSseEntity.putExtra("entity_type",
                                        intent.getStringExtra("entity_type"));
                                removeSseEntity.putExtra("grg_uid",
                                        intent.getStringExtra("grg_uid"));
                                AtakBroadcast.getInstance().sendBroadcast(
                                        removeSseEntity);
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, null);

            AlertDialog d = b.create();
            d.show();
        }
    }

}
