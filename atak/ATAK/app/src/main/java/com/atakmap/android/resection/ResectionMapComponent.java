
package com.atakmap.android.resection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ResectionMapComponent extends DropDownMapComponent {
    public static final String TAG = "ResectionMapComponent";

    ResectionWorkflowReceiver _resectionWorkflowReceiver;

    private static ResectionMapComponent _instance;

    private Context context;
    private MapView _mapView;
    private final Map<ResectionWorkflow, TileButtonDialog.TileButton> rwfList = new HashMap<>();
    private TileButtonDialog tileButtonDialog;
    private ResectionDropDownReceiver _defaultWorkflow;

    private final List<ResectionLocationEstimate> locationEstimates = new ArrayList<>();
    private int selectedEstimateIndex = 0;
    private static final String RESULT_GROUP_NAME = "Resection Estimate Results";
    private final ResectionWorkflowResultHandler resectionWorkflowResultHandler = new ResectionWorkflowResultHandler();

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView view) {

        this.context = context;
        this._mapView = view;
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
     * Get the ResectionMapComponent in order to register additional or unregister new Resectioning
     * or Denied GPS workflows.
     * 
     * @return the ResectionMapComponent
     */
    public static ResectionMapComponent getInstance() {
        return _instance;
    }

    /**
     * Installs a resectioning workflow with the system.
     * 
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
                Toast.makeText(context, "Workflow " + rwf.getName(),
                        Toast.LENGTH_LONG).show();

                // Get our detail view
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View descriptionView = inflater.inflate(
                        R.layout.resection_workflow_description, null, false);

                // Populate some details into our view
                TextView txtDescription = descriptionView
                        .findViewById(R.id.txtDescription);
                txtDescription.setText(rwf.getDescription());
                TextView txtIdealConditions = descriptionView
                        .findViewById(R.id.txtIdealConditions);
                txtIdealConditions.setText(rwf.getIdealConditions());
                TextView txtRelativeAccuracy = descriptionView
                        .findViewById(R.id.txtRelativeAccuracy);
                txtRelativeAccuracy.setText(rwf.getRelativeAccuracy());

                final TextView txtRequiredData = descriptionView
                        .findViewById(R.id.txtRequiredData);
                final String requiredData = rwf.getRequiredData();
                if (FileSystemUtils.isEmpty(requiredData)) {
                    txtRequiredData.setVisibility(View.GONE);
                } else {
                    txtRequiredData.setText(requiredData);
                }

                final TextView txtRequiredHardware = descriptionView
                        .findViewById(R.id.txtRequiredHardware);
                final String requiredHardware = rwf.getRequiredHardware();
                if (FileSystemUtils.isEmpty(requiredHardware)) {
                    txtRequiredHardware.setVisibility(View.GONE);
                } else {
                    txtRequiredHardware.setText(requiredHardware);
                }

                // Scale our icon if necessary
                float targetDimPixels = 32f
                        * context.getResources().getDisplayMetrics().density;
                float width = icon.getIntrinsicWidth();
                float height = icon.getIntrinsicHeight();
                float scaleX = targetDimPixels / width;
                float scaleY = targetDimPixels / height;

                int newX = (int) (width * scaleX);
                int newY = (int) (height * scaleY);

                Bitmap bitmap = ((BitmapDrawable) icon).getBitmap();
                BitmapDrawable bIcon = new BitmapDrawable(
                        context.getResources(),
                        Bitmap.createScaledBitmap(bitmap, newX, newY, true));

                AlertDialog dlg = new AlertDialog.Builder(context)
                        .setIcon(bIcon)
                        .setView(descriptionView)
                        .setTitle(context.getString(
                                R.string.resection_details_label,
                                rwf.getName()))
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        dialog.dismiss();
                                    }
                                })
                        .create();
                Window window = dlg.getWindow();
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
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
     * 
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
             * TileButtonDialog.TileButton tb = tileButtonDialog .createButton(
             * context.getResources() .getDrawable(R.drawable.resection),
             * context.getString(R.string.resection_workflow));
             **/
        }

        @Override
        public void onReceive(Context ignoreCtx, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(RESECTION_WORKFLOW)) {
                // Fresh round so clear out all of our old location estimates
                locationEstimates.clear();

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
    public class ResectionWorkflowResultHandler
            implements OnResectionResult, BackButtonCallback {
        public ResectionWorkflowResultHandler() {
        }

        /**
         * Builds an Alert Dialog for asking the user if they want to change their location to the
         * resection estimate.
         * 
         * @param estimate Location estimate from resectioning
         * @return The dialog itself
         */
        private AlertDialog buildResultDialog(
                final ResectionLocationEstimate estimate) {
            String locString = getPointLabel(estimate.getPoint());

            AlertDialog dlg = new AlertDialog.Builder(context)
                    .setTitle(R.string.resection_update_location_title)
                    .setMessage(context.getString(
                            R.string.resection_update_location_message,
                            locString))
                    .setNegativeButton(R.string.no,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    dialog.dismiss();
                                }
                            })
                    .setPositiveButton(R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    final MapData data = _mapView.getMapData();
                                    final GeoPoint gp = estimate.getPoint();
                                    data.putString("locationSourcePrefix",
                                            "resection");
                                    data.putBoolean(
                                            "resectionLocationAvailable", true);
                                    data.putParcelable("resectionLocation", gp);
                                    // only keep it valid for a very short period of time //
                                    data.putLong("resectionLocationTime",
                                            SystemClock.elapsedRealtime()
                                                    - 5000);
                                    data.putString("resectionLocationSource",
                                            "Resection Estimation");
                                    data.putString(
                                            "resectionLocationSourceColor",
                                            "#FFAFFF00");

                                    Intent gpsReceived = new Intent();
                                    gpsReceived
                                            .setAction(
                                                    "com.atakmap.android.map.WR_GPS_RECEIVED");
                                    AtakBroadcast.getInstance()
                                            .sendBroadcast(gpsReceived);

                                }
                            })
                    .create();

            return dlg;
        }

        private String getPointLabel(GeoPoint pt) {
            CoordinateFormat fmt = CoordinateFormat.find(
                    PreferenceManager.getDefaultSharedPreferences(context));
            return CoordinateFormatUtilities.formatToString(pt, fmt);
        }

        private String buildEstimateLabel(ResectionLocationEstimate estimate) {
            String confidenceLabel;
            if (estimate.getConfidence() == Double.MIN_VALUE
                    || estimate.getConfidence() > 1
                    || estimate.getConfidence() < 0) {
                confidenceLabel = "--";
            } else {
                int percent = (int) (estimate.getConfidence() * 100);
                confidenceLabel = String.valueOf(percent);
            }

            return context.getString(R.string.resection_estimate_label,
                    estimate.getSource(), getPointLabel(estimate.getPoint()),
                    confidenceLabel);
        }

        @Override
        public void result(ResectionWorkflow rwf,
                final ResectionLocationEstimate estimate) {
            if (estimate.getPoint() == null) {
                // No estimated location, therefore no action required--go quietly into the night
                return;
            }

            Log.d(TAG, "Got a location estimate back of "
                    + estimate.getPoint().getLatitude()
                    + "," + estimate.getPoint().getLongitude()
                    + " from " + rwf.getName());

            locationEstimates.add(estimate);

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
                        .setNegativeButton(R.string.no,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        // If we only have one estimate available, then no need to pick
                                        // which one to use as there IS only one
                                        if (locationEstimates.size() == 1) {
                                            finalizeEstimateSelection(
                                                    locationEstimates.get(0));
                                        } else {
                                            Log.d(TAG,
                                                    "component available estimate size: "
                                                            + locationEstimates
                                                                    .size());

                                            // Figure out which of our location estimates we should use
                                            showResultSelection();
                                        }
                                    }
                                })
                        .setPositiveButton(R.string.yes,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        tileButtonDialog.show();
                                    }
                                })
                        .create();

                dlg.show();
            }
        }

        private void showResultSelection() {
            String[] choices = new String[locationEstimates.size()];
            for (int i = 0; i < locationEstimates.size(); i++) {
                choices[i] = buildEstimateLabel(locationEstimates.get(i));
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                    R.layout.three_line_list_item, choices);

            View v = LayoutInflater.from(context).inflate(
                    R.layout.resection_result_select, null,
                    false);

            ListView list = v.findViewById(R.id.resection_result_list);
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            list.setAdapter(adapter);

            list.setItemChecked(0, true);
            drawEstimatesToMap();
            panAndZoomToEstimates();

            list.setOnItemClickListener(new ListView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                        int position, long id) {
                    selectedEstimateIndex = position;
                    ResectionLocationEstimate estimate = locationEstimates
                            .get(selectedEstimateIndex);
                    drawEstimatesToMap();
                    Shape errorBounds = estimate.getErrorBounds();

                    GeoPoint[] points;
                    if (errorBounds != null) {
                        points = errorBounds.getPoints();
                    } else {
                        points = new GeoPoint[1];
                        points[0] = estimate.getPoint();
                    }
                    ATAKUtilities.scaleToFit(_mapView, points,
                            _mapView.getWidth(), _mapView.getHeight());
                }
            });

            v.findViewById(R.id.resection_result_cancel)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            finalizeEstimateSelection(null);
                            _defaultWorkflow.closeDropDown();
                        }
                    });

            v.findViewById(R.id.resection_result_ok)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            finalizeEstimateSelection(
                                    locationEstimates
                                            .get(selectedEstimateIndex));
                            _defaultWorkflow.closeDropDown();
                        }
                    });

            _defaultWorkflow.showEstimatesDropDown(v, this);
        }

        private void panAndZoomToEstimates() {
            List<MapItem> shapes = new ArrayList<>();
            for (ResectionLocationEstimate estimate : locationEstimates) {
                Shape errorBounds = estimate.getErrorBounds();
                if (errorBounds != null) {
                    shapes.add(errorBounds);
                }
            }

            ATAKUtilities.scaleToFit(_mapView, shapes.toArray(new MapItem[0]),
                    _mapView.getWidth(),
                    _mapView.getHeight());
        }

        private void drawEstimatesToMap() {
            MapGroup mg = _mapView.getRootGroup()
                    .findMapGroup(RESULT_GROUP_NAME);
            if (mg == null) {
                mg = _mapView.getRootGroup().addGroup(RESULT_GROUP_NAME);
            }

            for (ResectionLocationEstimate estimate : locationEstimates) {
                Shape errorBounds = estimate.getErrorBounds();
                if (errorBounds != null) {
                    mg.addItem(errorBounds);
                }

                Marker marker = new PlacePointTool.MarkerCreator(
                        estimate.getPoint())
                                .setUid(UUID.randomUUID().toString())
                                .setCallsign("Estimate (" + estimate.getSource()
                                        + ")")
                                .setType("b-m-p-s-m")
                                .showCotDetails(false)
                                .placePoint();

                mg.addItem(marker);
            }
        }

        private void removeEstimatesFromMap() {
            MapGroup resultGroup = _mapView.getRootGroup()
                    .findMapGroup(RESULT_GROUP_NAME);
            if (resultGroup == null)
                return;
            resultGroup.getParentGroup().removeGroup(resultGroup);
        }

        private void finalizeEstimateSelection(
                ResectionLocationEstimate estimate) {
            removeEstimatesFromMap();
            if (estimate == null) {
                Toast.makeText(context,
                        R.string.resection_no_estimates_selected,
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "No estimate selected for use");
            } else {
                buildResultDialog(estimate).show();
            }
        }

        @Override
        public void backButtonPressed() {
            finalizeEstimateSelection(null);
        }
    }

    /**
     * For canceling the estimate selection process on a back button press in the DropDown
     */
    public interface BackButtonCallback {
        void backButtonPressed();
    }
}
