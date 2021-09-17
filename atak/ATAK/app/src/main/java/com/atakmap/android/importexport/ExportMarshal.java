
package com.atakmap.android.importexport;

import android.content.Context;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.missionpackage.ui.MissionPackageMapOverlay;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.overlay.NonExportableMapGroupOverlay;
import com.atakmap.android.user.FilterMapOverlay;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Interface responsible for marshaling data based on MIME Type. Provides
 * progress dialog and worker thread pattern, at the cost of some
 * interface complexity. Also provides support for UI and late-binding
 * export filtering
 * 
 * Pattern:
 *     1) Invoker initiates export by invoking <code>execute</code>.
 *         Implementations may confirm or collect additional information
 *         from the user
 *  2) <code>execute</code> then invokes <code>beginMarshal</code>. Default
 *      implementation simply launches a worker thread to handle the marshaling
 *      (see <code>ExportMarshalTask</code>)
 *  3) <code>ExportMarshalTask</code> displays a progress dialog while worker 
 *      thread is busy
 *  4) <code>ExportMarshalTask</code> invokes <code>marshal</code> on the
 *      worker thread to export the user specified data
 *  5) <code>ExportMarshalTask</code> invokes <code>finalizeMarshal</code> on
 *      the worker thread to complete the exports
 *  6) <code>ExportMarshalTask</code> invokes <code>postMarshal</code> on the
 *      UI thread. Implementations may notify the user of export completion
 * 
 * 
 */
public abstract class ExportMarshal extends HierarchyListFilter {

    /**
     * Interface for callback during export
     * 
     * 
     */
    public interface Progress {
        void publish(int progress);
    }

    /**
     * Optional progress callback
     */
    protected Progress progress;

    /**
     * Export filters provide late binding for export filter e.g. at export
     * time. This filtering is separate from the UI filtering provided by
     * HierarchyListFilter
     */
    private final ExportFilters filters;

    public ExportMarshal() {
        super(new HierarchyListItem.SortAlphabet());
        filters = new ExportFilters();
        filters.add(new ExportFilters.TargetClassFilter(getTargetClass()));
    }

    public boolean hasProgress() {
        return this.progress != null;
    }

    public void setProgress(Progress progress) {
        this.progress = progress;
    }

    public ExportFilters getFilters() {
        return filters;
    }

    public void addFilter(ExportFilter filter) {
        this.filters.add(filter);
    }

    /**
     * Target class this implementation exports to
     * @return
     */
    public abstract Class<?> getTargetClass();

    /**
     * UI type and icon
     * @return
     */
    public abstract String getContentType();

    public abstract String getMIMEType();

    public abstract int getIconId();

    /**
     * Return true if item should be shown in the UI, otherwise item will be hidden
     * 
     * @param item
     * @return
     */
    @Override
    public boolean accept(HierarchyListItem item) {
        final Object userObject = item.getUserObject();
        if (userObject instanceof MapItem)
            return !filterItem((MapItem) userObject);
        else if (userObject instanceof MapGroup)
            return !filterGroup((MapGroup) userObject);
        else if (userObject instanceof MapOverlay)
            return !filterOverlay((MapOverlay) userObject);
        else
            return !filterListItemImpl(item); // XXX - no API to delete???
    }

    /*
     * The below filter methods accept items if you return FALSE, not TRUE
     * XXX - Whoever wrote these methods thought that wouldn't be confusing...
     */

    /**
     * @param item
     * @return true to filter (don't display) specified item
     */
    public boolean filterListItemImpl(HierarchyListItem item) {
        return !(item instanceof Exportable
                && ((Exportable) item).isSupported(getTargetClass()));
    }

    /**
     * Default implementation filters non-Exportable and then
     * defers to the <code>ExportFilters</code>
     * 
     * @param item
     * @return true to filter (don't display) specified item
     */
    public boolean filterItem(MapItem item) {
        if (!(item instanceof Exportable))
            return true;

        return filters.filter(item);
    }

    /**
     * Default implementation filters on the "Layer Outlines" group
     *
     * @param group
     * @return true to filter (don't display) specified group
     */
    public boolean filterGroup(MapGroup group) {
        //TODO ideally we would see if this group is exportable e.g. via its MapOverlay
        //see NonExportableMapGroupHierarchyListItem
        //for now we just check some map groups we want to exclude

        String grpName = group.getFriendlyName();
        return (grpName.equals("Layer Outlines")) ||
                (grpName.equals("Cursor on Target")) ||
                (grpName.equals("LPT")) ||
                (grpName.equals("DRW"));
    }

    /**
     * Default implementation display 4 top level affiliations
     * 
     * @param overlay
     * @return true to filter (don't display) specified overlay
     */
    public boolean filterOverlay(MapOverlay overlay) {
        if (overlay == null || overlay instanceof NonExportableMapGroupOverlay)
            return true;

        //        String overId = overlay.getIdentifier();

        if (overlay instanceof MissionPackageMapOverlay)
            return false;

        return !(overlay instanceof FilterMapOverlay) &&
                !(overlay instanceof MapOverlayParent);
    }

    /**
    * Initiate export. Provides implementations an opportunity to confirm the
    * export with the user (on UI thread) prior to beginning export on
    * background task
    * 
    * If confirmed, implementations should invoke: <code>beginMarshal</code>
    * 
    * @param exports
     */
    public abstract void execute(List<Exportable> exports)
            throws IOException, FormatNotSupportedException;

    /**
     * Begin the export. Default implementations does so on a background task
     * 
     * @param context
     * @param exports
     */
    protected void beginMarshal(Context context, List<Exportable> exports) {
        new ExportMarshalTask(context, this, exports, true).execute();
    }

    /**
     * Marshal or organize export for marshaling during finalize().
     * Default pattern is for this to be invoked indirectly by ExportMarshalTask as
     * a result of invoking confirm
     * 
     * May be invoked on non-UI thread
     * 
     * @param exports
     * @return
     * @throws IOException
     */
    protected abstract boolean marshal(Collection<Exportable> exports)
            throws IOException, FormatNotSupportedException;

    /**
     * Finalize the export, e.g. close file handles or clean up other resources
     * Default pattern is for this to be invoked indirectly by ExportMarshalTask as
     * a result of invoking confirm
     * 
     * May be invoked on non-UI thread
     * 
     * @throws IOException
     */
    protected abstract void finalizeMarshal() throws IOException;

    /**
     * Will be invoked on UI thread, upon successful export
     */
    protected abstract void postMarshal();

    /**
     * Cancel the export e.g. user pressed back button
     */
    protected abstract void cancelMarshal();
}
