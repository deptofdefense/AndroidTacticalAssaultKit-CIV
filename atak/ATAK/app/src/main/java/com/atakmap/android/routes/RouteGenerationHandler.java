
package com.atakmap.android.routes;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.net.UnknownHostException;

/**
 * Handler for Route Generation events emitted by implementations of {@link RouteGenerationTask}. This
 * handler expects to be run on a UI thread.
 */
public class RouteGenerationHandler
        implements RouteGenerationTask.RouteGenerationEventListener {

    private static final String TAG = "RouteGenerationHandler";
    public static final String ROUTE_GENERATED = "com.atakmap.android.routes.ROUTE_GENERATED";
    private ProgressDialog dlg;
    private final PointMapItem origin;
    private final PointMapItem dest;
    private final Context _context;
    private final MapView _mapView;
    private final Route _route;

    public RouteGenerationHandler(MapView mapView, PointMapItem origin,
            PointMapItem dest, Route route) {
        this.origin = origin;
        this.dest = dest;
        _mapView = mapView;
        _context = mapView.getContext();
        _route = route;
    }

    @Override
    public void onBeforeRouteGenerated(final RouteGenerationTask task,
            boolean displayDialog) {
        if (displayDialog) {
            dlg = new ProgressDialog(_context);
            dlg.setTitle(R.string.route_plan_calculating);
            dlg.setMessage(
                    _context.getString(R.string.route_plan_calculating_msg));
            dlg.setIndeterminate(true);
            dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dlg.setCanceledOnTouchOutside(false);
            dlg.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            task.cancel(true);
                            Toast.makeText(_context,
                                    R.string.route_plan_cancelling,
                                    Toast.LENGTH_LONG).show();

                            dlg.dismiss();
                        }
                    });

            dlg.show();
        }
    }

    @Override
    public void onProgressUpdated(double progress) {
        //NOTE:: We still have about 40% more to do after the base task, so normalize here.
        updateProgress(progress * .6);
    }

    private void updateProgress(final double progress) {
        if (dlg == null)
            return;
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                dlg.setProgress((int) progress * 100);
                dlg.setMax(100);
            }
        });
    }

    public ProgressDialog getDialog() {
        return dlg;
    }

    @Override
    public void onException(Exception ex) {
        if (ex instanceof UnknownHostException) {
            Log.e(TAG,
                    "Unable to connect to host, is there a valid network connection?",
                    ex);

            dlg.dismiss();
            Toast.makeText(
                    _context,
                    R.string.unable_to_resolve_host,
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(_context, R.string.unexpected_error,
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Unexpected route generation error", ex);
        }
    }

    @Override
    public void onRouteGenerated(RoutePointPackage routePointPackage) {
        //NOTE:: This does not run on the UI thread.

        if (routePointPackage == null || routePointPackage.getError() != null) {

            Log.d(TAG, "no routes found");

            return; //Nothing we can do with it
        }

        updateProgress(.8);

        // Create action and add to undo queue
        RoutePlannerView.PlanRouteAction act = new RoutePlannerView.PlanRouteAction(
                _route, origin, dest,
                routePointPackage);

        Undoable undo = _route.getUndoable();
        if (undo != null)
            undo.run(act);
        else
            act.run();

        // Make sure to persist the finished route
        _route.removeMetaData("creating");
        _route.persist(_mapView.getMapEventDispatcher(), null, getClass());

        Intent routeGeneratedIntent = new Intent();
        routeGeneratedIntent.setAction(ROUTE_GENERATED);
        routeGeneratedIntent.putExtra("route_uid", _route.getUID());
        AtakBroadcast.getInstance().sendSystemBroadcast(routeGeneratedIntent);

        updateProgress(1);
    }

    @Override
    public void onAfterRouteGenerated(RoutePointPackage routePointPackage) {
        if (routePointPackage != null) {
            String errorMsg = routePointPackage.getError();
            if (errorMsg != null) {
                Toast.makeText(_context,
                        errorMsg,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(_context,
                    R.string.route_plan_unable_to_find_route,
                    Toast.LENGTH_LONG).show();
        }

        if (dlg != null)
            dlg.dismiss();
    }

    @Override
    public void onCancelled() {
        dlg.dismiss();
    }
}
