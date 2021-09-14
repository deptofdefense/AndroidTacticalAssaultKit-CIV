
package com.atakmap.android.helloworld.routes;

import android.app.AlertDialog;
import android.content.Context;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.importexport.ExportMarshal;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.routes.Route;

import java.util.Collection;
import java.util.List;

/**
 * Marshal for exporting routes from Overlay Manager
 */
public class RouteExportMarshal extends ExportMarshal {

    private static final String TAG = "RouteExportMarshal";

    private final Context context;

    public RouteExportMarshal(Context context) {
        this.context = context;
    }

    /**
     * Execute route export - called once a set of routes is selected in Overlay Manager
     * @param exports List of selected hierarchy list items
     */
    @Override
    public void execute(final List<Exportable> exports) {
        int exported = 0;
        for (Exportable e : exports) {
            // Only process list items with map items attached
            if (!(e instanceof MapItem) && !(e instanceof MapItemUser))
                continue;

            // Make sure the map item is a route
            MapItem mi;
            if (e instanceof MapItem)
                mi = (MapItem) e;
            else
                mi = ((MapItemUser) e).getMapItem();
            if (!(mi instanceof Route))
                continue;

            // Process route accordingly
            Route route = (Route) mi;
            exported++;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Route Export");
        b.setMessage("Exported " + exported + " routes.");
        b.setPositiveButton("OK", null);
        b.show();
    }

    // Filter out any items that aren't routes
    @Override
    public boolean filterItem(MapItem item) {
        return !(item instanceof Route);
    }

    @Override
    public boolean filterListItemImpl(HierarchyListItem item) {
        return false;
    }

    @Override
    public boolean filterGroup(MapGroup group) {
        return false;
    }

    /* Unused methods */

    @Override
    public String getContentType() {
        return context.getString(R.string.route_exporter_name);
    }

    @Override
    public String getMIMEType() {
        return "application/octet-stream";
    }

    @Override
    public int getIconId() {
        return R.drawable.ic_route;
    }

    @Override
    public Class getTargetClass() {
        return null;
    }

    @Override
    protected void beginMarshal(Context context, List<Exportable> exports) {
    }

    @Override
    public boolean marshal(Collection<Exportable> exports) {
        return true;
    }

    @Override
    public void finalizeMarshal() {
    }

    @Override
    public void postMarshal() {
    }

    @Override
    protected void cancelMarshal() {
    }
}
