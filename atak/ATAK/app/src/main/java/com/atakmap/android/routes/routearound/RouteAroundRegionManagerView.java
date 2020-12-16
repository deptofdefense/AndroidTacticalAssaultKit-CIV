
package com.atakmap.android.routes.routearound;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.geofence.data.ShapeUtils;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.app.R;

/** Fragment to use for the UI for the user to configure route around regions. */
public class RouteAroundRegionManagerView {

    public static final String OPT_AVOID_GEOFENCES = "com.atakmap.android.routes.routearound.avoid_geofences";
    public static final String OPT_AVOID_ROUTE_AROUND_REGIONS = "com.atakmap.android.routes.routearound.avoid_route_around_regions";
    private final MapView mapView;

    private ListView listView;
    private ImageButton addRegionButton;

    // This needs access to the parent dialog, and the parent of the parent
    // so that hiding the dialog can be handled.
    private AlertDialog parentDialog = null;
    private AlertDialog parentParentDialog = null;
    RouteAroundRegionViewModel viewModel;

    private ShapeToolUtils shapeUtil;
    private RegionSelectionMethodDialog regionSelectionMethodDialog;

    public RouteAroundRegionManagerView(MapView mapView,
            RouteAroundRegionViewModel viewModel) {
        this.viewModel = viewModel;
        this.mapView = mapView;

        shapeUtil = new ShapeToolUtils(mapView);
        regionSelectionMethodDialog = new RegionSelectionMethodDialog(
                mapView.getContext(), mapView);
    }

    private RegionSelectionMethodDialog.MethodSelectionHandler methodSelectedHandler = new RegionSelectionMethodDialog.MethodSelectionHandler() {
        @Override
        public void accept(
                RegionSelectionMethodDialog.RegionSelectionMethod method) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (parentDialog != null) {
                        parentDialog.hide();
                    }
                    if (parentParentDialog != null) {
                        parentParentDialog.hide();
                    }
                }
            });
            switch (method) {
                case NEW_CIRCLE:
                    shapeUtil.runCircleCreationTool(
                            (ShapeToolUtils.Callback<DrawingCircle, Object>) shapeHandler(),
                            errorHandler());
                    break;
                case NEW_POLYGONAL_REGION:
                    shapeUtil.runPolygonCreationTool(
                            (ShapeToolUtils.Callback<Shape, Object>) shapeHandler(),
                            errorHandler());
                    break;
                case NEW_RECTANGLE:
                    shapeUtil.runRectangleCreationTool(
                            (ShapeToolUtils.Callback<Shape, Object>) shapeHandler(),
                            errorHandler());
                    break;
                default:
                    shapeUtil.runRegionSelectionTool(
                            (ShapeToolUtils.Callback<MapItem, Object>) regionHandler(),
                            errorHandler());
                    break;
            }
        }
    };

    private ShapeToolUtils.Callback<? extends Shape, Object> shapeHandler() {
        return new ShapeToolUtils.Callback<Shape, Object>() {
            @Override
            public Object apply(Shape selectedRegion) {
                viewModel.addRegion(selectedRegion);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (parentParentDialog != null) {
                            parentParentDialog.show();
                        }
                        if (parentDialog != null) {
                            parentDialog.show();
                        }
                    }
                });
                return null;
            }
        };
    }

    private ShapeToolUtils.Callback<? extends MapItem, Object> regionHandler() {
        return new ShapeToolUtils.Callback<MapItem, Object>() {
            @Override
            public Object apply(MapItem selectedRegion) {
                if (selectedRegion instanceof Shape)
                    viewModel.addRegion((Shape) selectedRegion);
                String shapeUid = ShapeUtils.getShapeUID(selectedRegion);
                if (shapeUid != null) {
                    viewModel.addRegion((Shape) mapView.getMapItem(shapeUid));
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (parentParentDialog != null) {
                            parentParentDialog.show();
                        }
                        if (parentDialog != null) {
                            parentDialog.show();
                        }
                    }
                });
                return null;
            }
        };
    }

    private <A> ShapeToolUtils.Callback<Error, A> errorHandler() {
        return new ShapeToolUtils.Callback<Error, A>() {
            @Override
            public A apply(Error x) {
                throw x;
            }
        };
    }

    /** Set the dialog that the view is displayed in, if any. */
    public void setParentDialog(AlertDialog parentDialog) {
        this.parentDialog = parentDialog;
    }

    /** Set the dialog that the view was opened from, if any. */
    public void setParentParentDialog(AlertDialog parentParentDialog) {
        this.parentParentDialog = parentParentDialog;
    }

    /** Creates the view for the route around region manager. */
    public View createView(final Context pluginContext, ViewGroup parent) {
        LayoutInflater pluginInflater = LayoutInflater.from(pluginContext);
        View view = pluginInflater.inflate(R.layout.route_around_fragment,
                parent);

        if (!viewModel.isLoaded()) {
            viewModel.loadState();
        }

        RouteAroundRegionManagerAdapter adapter = new RouteAroundRegionManagerAdapter(
                pluginContext, viewModel, viewModel.getRegions());

        addRegionButton = view.findViewById(R.id.btn_add_route_around_region);

        listView = view.findViewById(R.id.route_around_region_list);

        listView.setAdapter(adapter);

        addRegionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new RegionSelectionMethodDialog(pluginContext,
                        mapView)
                                .getBuilder(methodSelectedHandler).create()
                                .show();
            }
        });

        return view;
    }
}
