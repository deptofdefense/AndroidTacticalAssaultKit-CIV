
package com.atakmap.android.resection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class ResectionMapComponent extends DropDownMapComponent {
    public static final String TAG = "ResectionMapComponent";

    ResectionWorkflowReceiver _resectionWorkflowReceiver;

    private static ResectionMapComponent _instance;

    private Context context;
    private final Map<ResectionWorkflow, TileButtonDialog.TileButton> rwfList = new HashMap<>();
    private TileButtonDialog tileButtonDialog;
    private ResectionDropDownReceiver _defaultWorkflow;

    private final ResectionReconciliationViewModel resectionViewModel = new ResectionReconciliationViewModel();
    private final ResectionWorkflowResultHandler resectionWorkflowResultHandler =
            new ResectionWorkflowResultHandler();

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView view) {

        this.context = context;
        _resectionWorkflowReceiver = new ResectionWorkflowReceiver(view);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ResectionWorkflowReceiver.RESECTION_WORKFLOW,
                "Intent to launch the Resection Workflow which could contain one or "
                + "more resectioning tools.  If it only contains one tool, then it will enter "
                + "into the standard resectioning capability.");
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
            startResection(rwf);
            }
        });
        tb.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(context, "Workflow " + rwf.getName(), Toast.LENGTH_LONG).show();

                // Get our detail view
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View descriptionView = inflater.inflate(R.layout.resection_workflow_description, null, false);

                // Populate some details into our view
                TextView txtDescription = descriptionView.findViewById(R.id.txtDescription);
                txtDescription.setText(rwf.getDescription());
                TextView txtIdealConditions = descriptionView.findViewById(R.id.txtIdealConditions);
                txtIdealConditions.setText(rwf.getIdealConditions());
                TextView txtRelativeAccuracy = descriptionView.findViewById(R.id.txtRelativeAccuracy);
                txtRelativeAccuracy.setText(rwf.getRelativeAccuracy());

                // Scale our icon if necessary
                float targetDimPixels = 32f * context.getResources().getDisplayMetrics().density;
                float width = icon.getIntrinsicWidth();
                float height = icon.getIntrinsicHeight();
                float scaleX = targetDimPixels / width;
                float scaleY = targetDimPixels / height;

                int newX = (int) (width * scaleX);
                int newY = (int) (height * scaleY);

                Bitmap bitmap = ((BitmapDrawable) icon).getBitmap();
                BitmapDrawable bIcon = new BitmapDrawable(context.getResources(), Bitmap.createScaledBitmap(bitmap, newX, newY, true));

                AlertDialog dlg = new AlertDialog.Builder(context)
                        .setIcon(bIcon)
                        .setView(descriptionView)
                        .setTitle(context.getString(R.string.resection_details_label, rwf.getName()))
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create();
                Window window = dlg.getWindow();
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
                dlg.show();

                return true;
            }
        });

        tileButtonDialog.addButton(tb);
        rwfList.put(rwf, tb);
    }

    private void startResection(final ResectionWorkflow rwf) {
        rwf.start(resectionWorkflowResultHandler);
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
     * Exposes the Resectioning Launcher as a capability that external or internal resectioning
     * capabilities can utilize.
     */
    public class ResectionWorkflowReceiver extends BroadcastReceiver {

        public static final String RESECTION_WORKFLOW = "com.atakmap.android.resection.RESECTION_WORKFLOW";

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
                // Fresh round so clear out all of our old location estimates
                resectionViewModel.clearEstimates();

                // If we only have 1 work flow available, just launch it. Otherwise put up the
                // chooser for the user to pick
                if (rwfList.size() == 1) {
                    ResectionWorkflow rwf = rwfList.keySet().iterator().next();
                    if (rwf != null)
                        startResection(rwf);
                } else {
                    tileButtonDialog.show(
                            context.getString(R.string.resection_options), "");
                }
            }
        }
    }

    /**
     * Handler class for dealing with location estimates coming in from Resection Workflows.
     */
    public class ResectionWorkflowResultHandler implements OnResectionResult, ReconciliationEvents {
        public ResectionWorkflowResultHandler() {
            resectionViewModel.registerListener(ResectionWorkflowResultHandler.this);
        }

        /**
         * Builds an Alert Dialog for asking the user if they want to change their location to the
         * resection estimate.
         * @param estimate Location estimate from resectioning
         * @return The dialog itself
         */
        private AlertDialog buildResultDialog(final ResectionLocationEstimate estimate) {
            String locString = getPointLabel(estimate.getPoint());

            AlertDialog dlg = new AlertDialog.Builder(context)
                    .setTitle(R.string.resection_update_location_title)
                    .setMessage(context.getString(R.string.resection_update_location_message, locString))
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MapView.getMapView().getSelfMarker().setPoint(estimate.getPoint());
                        }
                    })
                    .create();

            return dlg;
        }

        private String getPointLabel(GeoPoint pt) {
            CoordinateFormat fmt = CoordinateFormat.find(PreferenceManager.getDefaultSharedPreferences(context));
            return CoordinateFormatUtilities.formatToString(pt, fmt);
        }

        private String buildEstimateLabel(ResectionLocationEstimate estimate) {
            String confidenceLabel;
            if (estimate.getConfidence() == Double.MIN_VALUE || estimate.getConfidence() > 1
                    || estimate.getConfidence() < 0) {
                confidenceLabel = "--";
            } else {
                int percent = (int) (estimate.getConfidence() * 100);
                confidenceLabel = String.valueOf(percent);
            }

            return context.getString(R.string.resection_estimate_label, estimate.getSource(),
                    getPointLabel(estimate.getPoint()), confidenceLabel);
        }

        /**
         * Assembles a dialog that will help the user select which resection location estimates
         * they'd like to combine.
         * @return The constructed dialog
         */
        private AlertDialog buildEstimateSelectionDialog() {

            Log.d(TAG, "Selecting location estimates to use from a total of "
                    + resectionViewModel.getEstimates().size());

            // Build our list of choices
            String[] choices = new String[resectionViewModel.getEstimates().size()];
            for (int i = 0; i < resectionViewModel.getEstimates().size(); i++) {
                choices[i] = buildEstimateLabel(resectionViewModel.getEstimates().get(i));
            }

            final List<Integer> selectedEstimateIndices = new ArrayList<>();

            AlertDialog dlg = new AlertDialog.Builder(context)
                    .setTitle(R.string.resection_select_estimates_prompt_title)
                    .setMultiChoiceItems(choices, null,
                            new DialogInterface.OnMultiChoiceClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                            Log.d(TAG, "check state change on choice index " + which);

                            if (isChecked) {
                                selectedEstimateIndices.add(which);
                            } else if (selectedEstimateIndices.contains(which)) {
                                selectedEstimateIndices.remove(Integer.valueOf(which));
                            }
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            List<ResectionLocationEstimate> selectedEstimates =
                                    new ArrayList<>(selectedEstimateIndices.size());
                            for (Integer index : selectedEstimateIndices) {
                                selectedEstimates.add(resectionViewModel.getEstimates().get(index));
                            }

                            resectionViewModel.estimatesSelected(selectedEstimates);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create();

            return dlg;
        }

        @Override
        public void result(ResectionWorkflow rwf, final ResectionLocationEstimate estimate) {
            if (estimate.getPoint() == null) {
                // No estimated location, therefore no action required--go quietly into the night
                return;
            }

            Log.d(TAG, "Got a location estimate back of " + estimate.getPoint().getLatitude()
                    + "," + estimate.getPoint().getLongitude()  + " from " + rwf.getName());

            resectionViewModel.addEstimate(estimate);

            if (rwfList.size() == 1) {
                AlertDialog dlg = buildResultDialog(estimate);
                dlg.show();
            } else {
                // Find out if the user wants to run another re-sectioning tool. If so, bring back
                // up the chooser. If not, begin working the user through reconciling the estimated
                // locations
                AlertDialog dlg = new AlertDialog.Builder(context)
                        .setTitle(R.string.resection_run_another_title)
                        .setMessage(R.string.resection_run_another_message)
                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // If we only have one estimate available, then no need to pick
                                // which one to use as there IS only one
                                if (resectionViewModel.getEstimates().size() == 1) {
                                    resectionViewModel.estimatesSelected(resectionViewModel.getEstimates());
                                } else {
                                    Log.d(TAG, "component available estimate size: " + resectionViewModel.getEstimates().size());

                                    // Figure out which of our location estimates we should use
                                    AlertDialog selectionDlg = buildEstimateSelectionDialog();
                                    selectionDlg.show();
                                }
                            }
                        })
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                tileButtonDialog.show();
                            }
                        }).create();

                dlg.show();
            }
        }

        @Override
        public void estimatesSelected(List<ResectionLocationEstimate> estimates) {
            ResectionLocationEstimate estimate = null;

            if (estimates == null || estimates.isEmpty()) {
                Toast.makeText(context, R.string.resection_no_estimates_selected, Toast.LENGTH_LONG).show();
                Log.d(TAG, "No estimates selected for use");

                return;
            } else if (estimates.size() == 1) {
                estimate = estimates.get(0);
            } else {
                GeoPoint[] extremes = new GeoPoint[estimates.size()];
                for (int i = 0; i < estimates.size(); i++) {
                    extremes[i] = estimates.get(i).getPoint();
                }

                GeoPoint average = GeoCalculations.computeAverage(extremes);
                estimate = new ResectionLocationEstimate();
                estimate.setPoint(average);
                estimate.setSource("Multiple");
            }

            AlertDialog acceptGuessDlg = buildResultDialog(estimate);
            acceptGuessDlg.show();
        }
    }

    /**
     * Holds view state related to reconciling which resection location estimates to use.
     */
    private static class ResectionReconciliationViewModel {
        private final List<ReconciliationEvents> eventListener = new ArrayList<ReconciliationEvents>();
        private final List<ResectionLocationEstimate> availableEstimates = new ArrayList<>();

        public void addEstimate(ResectionLocationEstimate estimate) {
            availableEstimates.add(estimate);
        }

        public void clearEstimates() {
            availableEstimates.clear();
        }

        public List<ResectionLocationEstimate> getEstimates() {
            return availableEstimates;
        }

        public void registerListener(ReconciliationEvents listener) {
            eventListener.add(listener);
        }

        public void unregisterListener(ReconciliationEvents listener) {
            eventListener.remove(listener);
        }

        /**
         * Signals that the resectioning estimates that should be reconciled together have been
         * selected. Notifies all registered ReconciliationEvent listeners.
         * @param estimates The estimates that should be reconciled together
         */
        public void estimatesSelected(List<ResectionLocationEstimate> estimates) {

            for (ReconciliationEvents listener : eventListener) {
                listener.estimatesSelected(estimates);
            }
        }
    }

    /**
     * Interface for communicating events from ResectionReconciliationViewModel.
     */
    private interface ReconciliationEvents {
        /**
         * Fired when resection location estimates have been selected for reconciliation.
         * @param estimates The estimates that should be reconciled together
         */
        void estimatesSelected(List<ResectionLocationEstimate> estimates);
    }
}
