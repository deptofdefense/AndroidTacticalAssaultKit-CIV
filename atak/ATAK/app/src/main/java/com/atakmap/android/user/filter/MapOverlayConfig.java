
package com.atakmap.android.user.filter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.atakmap.android.config.FiltersConfig;
import com.atakmap.android.config.FiltersConfig.Filter;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapGroup.MapItemsCallback;
import com.atakmap.android.user.FilterMapOverlay;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nestable overlay configurations
 * 
 * 
 */
@Root(name = "overlay")
public class MapOverlayConfig {

    private static final String TAG = "MapOverlayConfig";

    @Attribute(name = "id", required = true)
    private String id;

    @Attribute(name = "parent", required = false)
    private String parent;

    @Attribute(name = "title", required = true)
    private String title;

    @Attribute(name = "icon", required = false)
    private String icon;

    @Attribute(name = "iconColor", required = false)
    private String iconColor;

    @Attribute(name = "iconColorPref", required = false)
    private String iconColorPref;

    @Attribute(name = "order", required = true)
    private int order;

    @SuppressWarnings("FieldMayBeFinal")
    @Attribute(name = "litmusFilter", required = false)
    private String _litmusFilterString;

    @Attribute(name = "exclusive", required = false)
    private String _exclusive;

    /**
     * Litmus test for this and all child overlays
     */
    private MapGroup.MapItemsCallback _litmusFilter;

    /**
     * Comibnes all configured filters
     */
    private MapGroup.MapItemsCallback _filter;

    /**
     * Optional child overlays
     */
    @ElementList(entry = "overlay", inline = true, required = false)
    private List<MapOverlayConfig> _overlays;

    /**
     * Optional filters
     */
    @ElementList(entry = "filter", inline = true, required = false)
    private List<MapOverlayFilter> _filters;

    /**
     * Loaded from filters/team_filters.xml, maps human readable colors
     * to Android parse-able colors. Not optimal for this purpose, but
     * no need to have 2 mappings for team colors
     */
    private FiltersConfig _colorFilters;

    public MapOverlayConfig() {
        _overlays = new ArrayList<>();
        _filters = new ArrayList<>();
        _filter = null;

        _litmusFilter = null;
        _litmusFilterString = null;
    }

    /**
     * Overlays or filters are required
     * @return true if the overlay or filters are valid
     */
    public boolean isValid() {
        if (FileSystemUtils.isEmpty(id) || FileSystemUtils.isEmpty(title))
            return false;

        if (FileSystemUtils.isEmpty(_overlays) &&
                FileSystemUtils.isEmpty(_filters))
            return false;

        for (MapOverlayConfig o : _overlays) {
            if (o == null || !o.isValid())
                return false;
        }

        for (MapOverlayFilter f : _filters) {
            if (f == null || !f.isValid())
                return false;
        }

        return true;
    }

    /**
     * Gather configured filters, and skip user icons
     * @param colorFilters  the filter used for user icons.
     */
    public void init(Context context, SharedPreferences prefs,
            FiltersConfig colorFilters) {
        _colorFilters = colorFilters;

        if (!FileSystemUtils.isEmpty(_litmusFilterString)) {
            Log.d(TAG, "Setting litmus filter: " + _litmusFilterString); //TODO remove logging after testing
            _litmusFilter = new FilterMapOverlay.TypeFilter(
                    _litmusFilterString);
        }

        if (FileSystemUtils.isEmpty(_filters))
            return;

        //apply configured filters
        int filterCount = 0;
        this._filter = null;
        for (MapOverlayFilter f : _filters) {
            if (f == null || !f.isValid()) {
                Log.w(TAG, "Skipping invalid filter for: " + this);
                continue;
            }

            MapGroup.MapItemsCallback cur = null;
            if (f.hasType()) {
                cur = new FilterMapOverlay.TypeFilter(f.getType());
            } else if (f.hasItemStringName()) {
                if (f.hasItemStringValue()) {
                    cur = new FilterMapOverlay.MetaStringFilter(
                            f.getItemStringName(), f.getItemStringValue());
                } else if (f.hasPrefStringName()) {
                    cur = new FilterMapOverlay.PrefStringFilter(prefs,
                            f.getItemStringName(), f.getPrefStringName());
                }
            }

            if (cur == null) {
                Log.w(TAG, "Skipping invalid filter for: " + this);
                continue;
            }

            if (this._filter == null) {
                this._filter = cur;
            } else {
                this._filter = new MapGroup.MapItemsCallback.Or(cur,
                        this._filter);
            }
            filterCount++;
        } //end filter loop

        //and skip user icons
        if (this._filter == null) {
            this._filter = new MapGroup.MapItemsCallback.Not(
                    new FilterMapOverlay.IconsetMapItemsCallback(context));
        } else {
            this._filter = new MapGroup.MapItemsCallback.And(
                    new MapGroup.MapItemsCallback.Not(
                            new FilterMapOverlay.IconsetMapItemsCallback(
                                    context)),
                    this._filter);
        }

        //Log.d(TAG, "Loaded " + filterCount + " filters, for " + toString());
    }

    public boolean hasFilter() {
        return _filter != null;
    }

    public MapGroup.MapItemsCallback getFilter() {
        return _filter;
    }

    public String getId() {
        return id;
    }

    public String getParent() {
        return parent;
    }

    public String getTitle() {

        return translate(title);

    }

    private String translate(String title) {
        if (title.startsWith("filter_title_")) {
            try {
                Class<?> c = com.atakmap.app.R.string.class;
                Field f = null;
                FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
                if (fp != null && fp.hasMilCapabilities()) {
                    try {
                        f = c.getField("mil_" + title);
                    } catch (Exception ignored) {
                    }
                }

                if (f == null)
                    f = c.getField("civ_" + title);

                int i = f.getInt(null);
                Context ctx = com.atakmap.android.maps.MapView.getMapView()
                        .getContext();
                if (ctx != null)
                    return ctx.getString(i);
            } catch (Exception e) {
                //                Log.e(TAG, "error, could not find id: " + pam.title, e);
                Log.e(TAG, "error, could not find id: " + title);
            }
        }
        return title;
    }

    public String getIcon() {
        return icon;
    }

    public boolean hasIconColor() {
        return !FileSystemUtils.isEmpty(iconColor) ||
                !FileSystemUtils.isEmpty(iconColorPref);
    }

    public int getIconColor(SharedPreferences prefs) {
        if (!FileSystemUtils.isEmpty(iconColor)) {
            try {
                return Color.parseColor(iconColor);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Failed to parse: " + iconColor, e);
            }
        } else if (!FileSystemUtils.isEmpty(iconColorPref)) {
            String prefValue = prefs.getString(iconColorPref, "white");

            //first check color filters. This filter impl was designed to match
            //map item's metadata, so we have to bundle it...
            Map<String, Object> data = new HashMap<>();
            data.put("team", prefValue);
            Filter f = _colorFilters.lookupFilter(data);
            if (f != null) {
                prefValue = f.getValue();
            }

            //now attempt to parse color
            try {
                return Color.parseColor(prefValue);
            } catch (Exception e) {
                Log.w(TAG,
                        "Failed to parse filter overlay color: " + prefValue);
                return Color.WHITE;
            }
        }

        return Color.WHITE;
    }

    public int getOrder() {
        return order;
    }

    public boolean hasLitmusFilter() {
        return _litmusFilter != null;
    }

    public String getLitmusFilterString() {
        return _litmusFilterString;
    }

    public MapItemsCallback getLitmusFilter() {
        return _litmusFilter;
    }

    public boolean isExclusive() {
        return _exclusive == null || _exclusive.equals("true");
    }

    public List<MapOverlayConfig> getOverlays() {
        if (_overlays == null)
            _overlays = new ArrayList<>();

        return _overlays;
    }

    public List<MapOverlayFilter> getFilters() {
        if (_filters == null)
            _filters = new ArrayList<>();

        return _filters;
    }

    /**
     * Recursively get all filters
     * @return
     */
    public List<MapOverlayFilter> getAllFilters() {
        List<MapOverlayFilter> filters = new ArrayList<>();
        for (MapOverlayConfig config : getOverlays()) {
            List<MapOverlayFilter> f = config.getAllFilters();
            if (!FileSystemUtils.isEmpty(f))
                filters.addAll(f);
        }

        if (!FileSystemUtils.isEmpty(this._filters))
            filters.addAll(_filters);

        return filters;
    }

    @Override
    public String toString() {
        return id + "," + title + "," + order + "," + icon + ", children="
                + _overlays.size();
    }
}
