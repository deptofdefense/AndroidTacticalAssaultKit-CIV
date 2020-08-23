
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

    private final Context _context;
    private final MapGroup _removeGroup;

    public RemoveBroadcastReceiver(MapView mapView, MapGroup removeGroup) {
        _context = mapView.getContext();
        _removeGroup = removeGroup;
    }

    public RemoveBroadcastReceiver(MapGroup removeGroup) {
        this(MapView.getMapView(), removeGroup);
    }

    public RemoveBroadcastReceiver(MapView mapView) {
        this(mapView, mapView.getRootGroup());
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String uid = intent.getStringExtra("uid");
        if (FileSystemUtils.isEmpty(uid))
            return;

        final MapItem item = _removeGroup.deepFindUID(uid);
        String title = ATAKUtilities.getDisplayName(item);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.confirmation_dialogue);
        b.setMessage(_context.getString(R.string.confirmation_remove_details,
                title));
        b.setPositiveButton(R.string.yes, new OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                if (item.getMetaBoolean("removable", true)) {
                    // Remove from map group
                    item.removeFromGroup();
                } else if (item.hasMetaValue("deleteAction")) {
                    // Special delete action
                    Intent delete = new Intent(item
                            .getMetaString("deleteAction", ""));
                    delete.putExtra("targetUID", item.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(delete);
                    return;
                }

                //TODO remove SSE intent from baseline?
                //  See the deleteAction intent meta string above
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
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }
}
