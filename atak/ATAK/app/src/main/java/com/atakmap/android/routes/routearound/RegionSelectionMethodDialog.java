
package com.atakmap.android.routes.routearound;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;

import com.atakmap.app.R;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

/**
 * This dialog is used to prompt the user what method of route selection they
 * would like to use when creating a route drifter.
 */
public class RegionSelectionMethodDialog {

    private final MapView mapView;
    private final Context pluginContext;

    private static final String TAG = "RegionSelectionMethodDialog";

    public RegionSelectionMethodDialog(Context pluginContext, MapView mapView) {
        this.pluginContext = pluginContext;
        this.mapView = mapView;
    }

    /** Constructs the AlertDialog builder for the region selection method interface. */
    public AlertDialog.Builder getBuilder(
            final MethodSelectionHandler methodSelectedHandler) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(
                mapView.getContext());
        builderSingle.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter(pluginContext,
                android.R.layout.simple_list_item_1);
        for (RegionSelectionMethod item : RegionSelectionMethod.values()) {
            arrayAdapter.add(item.toString());
        }
        builderSingle.setAdapter(arrayAdapter,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface,
                            int i) {
                        try {
                            RegionSelectionMethod value = RegionSelectionMethod
                                    .getValue(i);
                            if (value != null) {
                                methodSelectedHandler.accept(value);
                            }
                        } catch (Exception e) {
                            Log.e(TAG,
                                    "Problem parsing region selection option",
                                    e);
                        }
                    }
                });
        return builderSingle;
    }

    public interface MethodSelectionHandler {
        void accept(
                RegionSelectionMethodDialog.RegionSelectionMethod methodSelected);
    }

    /** An enum representing the different results that a user can select from this dialog. */
    public enum RegionSelectionMethod {
        NEW_CIRCLE,
        NEW_RECTANGLE,
        NEW_POLYGONAL_REGION,
        SELECT_REGION_ON_MAP;

        @Override
        public String toString() {
            switch (this) {
                case NEW_CIRCLE:
                    return "Create a new circular region";
                case NEW_RECTANGLE:
                    return "Create a new rectangular region";
                case NEW_POLYGONAL_REGION:
                    return "Create a new polygonal region";
                case SELECT_REGION_ON_MAP:
                    return "Select a region on the map";
                default:
                    throw (new IllegalArgumentException());
            }
        }

        public static RegionSelectionMethod getValue(int value) {
            switch (value) {
                case 0:
                    return NEW_CIRCLE;
                case 1:
                    return NEW_RECTANGLE;
                case 2:
                    return NEW_POLYGONAL_REGION;
                case 3:
                    return SELECT_REGION_ON_MAP;
                default:
                    throw (new IllegalArgumentException(
                            "function only valid for numbers 0-3"));
            }
        }
    }
}
