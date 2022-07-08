
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
import com.atakmap.android.hierarchy.action.GroupDelete;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import gov.tak.api.annotation.ModifierApi;

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
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected boolean confirmDelete = false;

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

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })

    protected class DeleteItemsTask extends AsyncTask<Void, Integer, Boolean> {

        private final Context _context;
        private final Set<HierarchyListItem> _items;
        private final ProgressDialog _pd;
        private final Runnable _onFinished;

        @ModifierApi(since = "4.5", target = "4.8", modifiers = {
                "private"
        })
        protected DeleteItemsTask(Context ctx, Set<HierarchyListItem> items,
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

            // Convert items to delete actions
            List<Delete> topActions = new ArrayList<>();
            for (HierarchyListItem mi : _items) {
                Delete del = mi.getAction(Delete.class);
                if (del == null) {
                    Log.w(TAG, "No delete action for: " + mi.getClass());
                    continue;
                }
                topActions.add(del);
            }

            // Gather a flat list of all the delete actions that need to be
            // executed
            List<Delete> actions = getRecursiveDeleteActions(topActions);
            if (actions.isEmpty())
                return true;

            // Begin execution
            publishProgress(0, actions.size());
            boolean res = true;
            int prog = 0;
            for (Delete d : actions) {
                res &= d.delete();
                publishProgress(++prog);
            }
            return res;
        }

        private List<Delete> getRecursiveDeleteActions(List<Delete> actions) {
            List<Delete> ret = new ArrayList<>();
            if (actions == null)
                return ret;
            for (Delete d : actions) {
                if (d instanceof GroupDelete)
                    ret.addAll(getRecursiveDeleteActions(((GroupDelete) d)
                            .getDeleteActions()));
                else if (d != null)
                    ret.add(d);
            }
            return ret;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            _pd.setProgress(values[0]);
            if (values.length > 1)
                _pd.setMax(values[1]);
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
