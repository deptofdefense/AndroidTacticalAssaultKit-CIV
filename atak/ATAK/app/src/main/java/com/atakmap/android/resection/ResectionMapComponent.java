
package com.atakmap.android.resection;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import android.view.View;

import com.atakmap.app.R;

import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.maps.MapView;

import java.util.Map;
import java.util.HashMap;

public class ResectionMapComponent extends DropDownMapComponent {

    ResectionWorkflowReceiver _resectionWorkflowReceiver;

    private static ResectionMapComponent _instance;

    private Context context;
    private final Map<ResectionWorkflow, TileButtonDialog.TileButton> rwfList = new HashMap<>();
    private TileButtonDialog tileButtonDialog;
    private ResectionDropDownReceiver _defaultWorkflow;

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView view) {

        this.context = context;
        _resectionWorkflowReceiver = new ResectionWorkflowReceiver(view);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ResectionWorkflowReceiver.RESECTION_WORKFLOW,
                "Intent to launch the Resection Workflow which could contain one or more resectioning tools.  If it only contains one tool, then it will enter into the standard resectioning capability.");
        registerReceiver(context, _resectionWorkflowReceiver, filter);

        // Default resection workflow
        _defaultWorkflow = new ResectionDropDownReceiver(view);
        addResectionWorkflow(
                context.getDrawable(R.drawable.ic_resection_compass),
                context.getString(R.string.resection),
                _defaultWorkflow);

        _instance = this;
    }

    @Override
    public void onDestroyImpl(Context context, MapView view) {
        _defaultWorkflow.disposeImpl();
        super.onDestroyImpl(context, view);
    }

    /**
     * Get the ResectionMapComponent in order to register additional
     * or unregister new Resectioning or Denied GPS workflows.
     * @return the ResectionMapComponent
     */
    public static ResectionMapComponent getInstance() {
        return _instance;
    }

    /**
     * Installs a resectioning workflow with the system.
     * @param icon the icon used when the selection dialog is shown.
     * @param txt the text that appears under the icon.
     * @param rwf the resectioning workflow.
     */
    synchronized public void addResectionWorkflow(final Drawable icon,
            String txt, final ResectionWorkflow rwf) {
        TileButtonDialog.TileButton tb = tileButtonDialog.createButton(icon,
                txt);
        tb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rwf.start();
            }
        });
        tileButtonDialog.addButton(tb);
        rwfList.put(rwf, tb);
    }

    /**
     * Removes a resectioning workflow with the system.
     * @param rwf the resectioning workflow to remove
     */
    synchronized public void removeResectionWorkflow(
            final ResectionWorkflow rwf) {
        TileButtonDialog.TileButton tb = rwfList.get(rwf);
        if (tb != null)
            tileButtonDialog.removeButton(tb);
        rwfList.remove(rwf);
    }

    /**
     * Provides for the Resectioning Launcher as a capability that external or internal resectioning
     * capabililities can utilize.
     */
    public class ResectionWorkflowReceiver extends BroadcastReceiver {

        public static final String RESECTION_WORKFLOW = "com.atakmap.andrdoid.resection.RESECTION_WORFLOW";

        ResectionWorkflowReceiver(final MapView view) {
            tileButtonDialog = new TileButtonDialog(view);

            /**
            TileButtonDialog.TileButton tb = tileButtonDialog
                    .createButton(
                            context.getResources()
                                    .getDrawable(R.drawable.resection),
                            context.getString(R.string.resection_workflow));
            **/
        }

        @Override
        public void onReceive(Context ignoreCtx, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(RESECTION_WORKFLOW)) {
                if (rwfList.size() == 1) {
                    ResectionWorkflow rwf = rwfList.keySet().iterator().next();
                    if (rwf != null)
                        rwf.start();
                } else {
                    tileButtonDialog.show(
                            context.getString(R.string.resection_options), "");
                }
            }
        }
    }

}
