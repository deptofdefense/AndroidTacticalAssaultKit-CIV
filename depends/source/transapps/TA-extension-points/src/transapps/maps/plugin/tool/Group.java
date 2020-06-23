package transapps.maps.plugin.tool;

import transapps.maps.plugin.layer.LayerConfigurer;

/**
 * Groups for tools.  This helps maps organize and display the correct
 * tools for the specific gesture used in maps.
 * 
 * @author mriley
 */
public enum Group {
    /**
     * A tool that is given a pre-selected lat/lon in the extra bundle and
     * shows up in the '+' list.
     */
    EDIT_CONTENT,
    /**
     * A tool that will configure a layer.  This tool will be passed the layer
     * descriptor in the extras bundle.  See {@link LayerConfigurer} for help
     *
     * @deprecated in NW SDK 1.1.6.3 use the extension point
     * {@link mil.army.nettwarrior.core.map.plugin.MapPreferenceDescriptor} instead
     */
    CONFIGURE_LAYER,
    /**
     * A tool that is given a pre-selected lat/lon in the extra bundle
     */
    LONG_PRESS,
    /**
     * Other, non-specific kind of tool
     */
    GENERAL,
    /**
     * XXX: Not sure if this will fit into the new UI
     * Tool given a lat/lon from the users current location
     */
    SELF,
    /**
     * Spot touch menu.
     * Text only.
     */
    SPOT,
    /**
     * Items that appear in the slider drawer
     */
    SLIDER_DRAWER
}
