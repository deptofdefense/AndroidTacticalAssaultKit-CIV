
package com.atakmap.android.hierarchy;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.android.grg.GRGMapOverlay;
import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.ui.MissionPackageMapOverlay;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.user.FilterMapOverlay;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.FileDatabaseMapGroupHierarchyListItem;

import java.util.Set;

/**
 * Deletes all items selected by user in the Hierarchy List
 * 
 * 
 */
public class HierarchyListUserDelete extends AsyncListUserSelect {

    public static final String TAG = "HierarchyListUserDelete";

    /**
     * Close by default, clear if this flag is set
     */
    private boolean bCloseHierarchyWhenComplete;
    private boolean confirmDelete = false;

    public HierarchyListUserDelete() {
        super("Delete", Actions.ACTION_DELETE);
        bCloseHierarchyWhenComplete = true;
    }

    public void setCloseHierarchyWhenComplete(boolean b) {
        bCloseHierarchyWhenComplete = b;
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext()
                .getString(R.string.delete_items);
    }

    @Override
    public String getButtonText() {
        return MapView.getMapView().getContext().getString(R.string.delete);
    }

    @Override
    public ButtonMode getButtonMode() {
        return ButtonMode.VISIBLE_WHEN_SELECTED;
    }

    @Override
    public void processUserSelections(final Context context,
            final Set<HierarchyListItem> selected, final Runnable onFinished) {
        Log.d(TAG, "processUserSelections count: " + selected.size());

        this.confirmDelete = false;
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setIcon(R.drawable.ic_menu_delete);
        b.setTitle(R.string.confirmation_dialogue);
        b.setMessage(R.string.delete_items_confirmation);
        b.setPositiveButton(R.string.yes,
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        confirmDelete = true;
                        new DeleteItemsTask(context, selected, onFinished)
                                .execute();
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        AlertDialog d = b.create();
        d.show();
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (!confirmDelete && onFinished != null)
                    onFinished.run();
            }
        });
    }

    @Override
    protected boolean filterOverlay(MapOverlay overlay) {
        String overId = overlay.getIdentifier();
        //Log.d(TAG, "filterOverlay " + overId);

        if (!(overlay instanceof FilterMapOverlay) &&
                !(overlay instanceof GRGMapOverlay) &&
                !(overlay instanceof MissionPackageMapOverlay) &&
                !(overlay instanceof MapOverlayParent) &&
                !(overlay instanceof Delete))
            return true;

        //do not display this specific FilterMapOverlay
        if ("trackhistory".equals(overId))
            return true;

        return super.filterOverlay(overlay);
    }

    @Override
    protected boolean filterGroup(MapGroup group) {
        String grpName = group.getFriendlyName();
        //Log.d(TAG, "filterGroup " + grpName);

        //hide Layer Outlines
        if ("Layer Outlines".equals(grpName))
            return true;

        return super.filterGroup(group);
    }

    @Override
    protected boolean filterItem(MapItem item) {
        return false;
    }

    @Override
    protected boolean filterListItemImpl(HierarchyListItem item) {
        // As long as the item has a delete action
        if (item.getAction(Delete.class) != null)
            return false;

        return super.filterListItemImpl(item);
    }

    @Override
    public boolean acceptEntry(HierarchyListItem item) {
        return super.acceptEntry(item)
                && !(item instanceof FileDatabaseMapGroupHierarchyListItem);
    }

    private class DeleteItemsTask extends AsyncTask<Void, Integer, Boolean> {

        private final Context _context;
        private final Set<HierarchyListItem> _items;
        private final ProgressDialog _pd;
        private final Runnable _onFinished;

        private DeleteItemsTask(Context ctx, Set<HierarchyListItem> items,
                Runnable onFinished) {
            _context = ctx;
            _items = items;
            _pd = new ProgressDialog(_context);
            _onFinished = onFinished;
        }

        @Override
        protected void onPreExecute() {
            _pd.setMessage(_context.getString(R.string.delete_items_busy));
            _pd.setProgress(0);
            _pd.setMax(_items.size());
            _pd.setCancelable(false);
            _pd.setIndeterminate(false);
            _pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            _pd.show();
        }

        @Override
        public Boolean doInBackground(Void... params) {
            if (_items.isEmpty())
                return true;
            boolean res = false;
            int prog = 0;
            Delete delete;
            long start;
            for (HierarchyListItem mi : _items) {
                delete = mi.getAction(Delete.class);
                if (delete != null) {
                    start = System.currentTimeMillis();
                    res |= delete.delete();
                    Log.v(TAG,
                            "Remove #" + prog + " took "
                                    + (System.currentTimeMillis() - start));
                } else {
                    Log.w(TAG, "No delete action for: "
                            + mi.getClass().getName());
                }
                publishProgress(++prog);
            }
            return res;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            _pd.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean res) {
            _pd.dismiss();
            if (_onFinished != null)
                _onFinished.run();
            if (res == null || !res)
                Toast.makeText(_context, R.string.delete_items_failed,
                        Toast.LENGTH_LONG).show();
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    bCloseHierarchyWhenComplete
                            ? HierarchyListReceiver.CLOSE_HIERARCHY
                            : HierarchyListReceiver.CLEAR_HIERARCHY));
        }
    }
}
